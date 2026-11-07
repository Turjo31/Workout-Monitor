package com.kuet.gymtest.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Lunge-owned messages; keys are stable and priorities follow the shared 1-6 scale. */
enum class LungeFeedback(override val text: String, override val priority: Int) : AnalysisFeedback {
    NO_POSE("Pose unclear — adjust the camera or lighting", 1),
    MOVE_BACK("Keep your whole body in view", 2),
    SIDEWAYS("Stand sideways to the camera", 2),
    UNCERTAIN("Pose unclear — adjust the camera or lighting", 3),
    READY("Stand tall with your feet together to begin", 4),
    STEP("Step forward and lower your back knee", 4),
    RISE("Push through your front foot to stand", 4),
    SHALLOW("Step farther and lower next time", 4),
    COUNTED("Repetition counted", 5);

    override val key get() = "lunge.$name"
}

enum class LungePhase { NOT_READY, STANDING, DESCENDING, BOTTOM, ASCENDING }

/** Knee angles in degrees (hip-knee-ankle); separation is the horizontal ankle gap in average leg lengths. */
data class LungeMetrics(val minKnee: Double, val maxKnee: Double, val separation: Double)

data class LungeAnalysisResult(
    override val key: FrameKey, override val timestampMs: Long,
    override val repetitions: Int, override val repetition: RepetitionCompleted?,
    override val feedback: LungeFeedback, override val trackingValid: Boolean,
    val phase: LungePhase, val metrics: LungeMetrics?
) : ExerciseAnalysisResult {
    override val presentation get() = AnalysisPresentation(
        phaseKey = phase.name,
        phaseLabel = phase.name.lowercase().replace('_', ' '),
        ready = phase != LungePhase.NOT_READY,
        detail = when {
            !trackingValid -> null
            phase == LungePhase.BOTTOM || phase == LungePhase.ASCENDING -> "Target depth reached"
            else -> "Depth: above target"
        }
    )
}

/** Provisional 2D side-view defaults, not calibrated limits. Tune from LungeTrace logs. */
data class LungeConfig(
    val standingEntry: Double = 165.0, val standingExit: Double = 155.0,
    val descent: Double = 145.0, val bottomEntry: Double = 110.0, val bottomExit: Double = 125.0,
    val standingSeparation: Double = .30, val stepSeparation: Double = .40, val bottomSeparation: Double = .50,
    val readinessMs: Long = 300, val phaseMs: Long = 80, val finalStandingMs: Long = 200,
    val minimumCycleMs: Long = 600, val maximumGapMs: Long = 300,
    val filterMs: Double = 80.0, val maximumSpeed: Double = 700.0,
    val maximumSegmentChange: Double = .5, val chainVisibility: Float = .5f,
    val presence: Float = .5f, val edgeMargin: Float = .02f,
    val minimumSegment: Double = .025, val shoulderRatio: Double = .75,
    val hipRatio: Double = .55, val orientationMs: Long = 300,
    val maximumTransientMs: Long = 300, val maximumTransientFrames: Int = 3
) {
    init {
        require(bottomEntry > 0 && bottomEntry < bottomExit && bottomExit < descent &&
                descent <= standingExit && standingExit < standingEntry && standingEntry <= 180)
        require(standingSeparation.isFinite() && stepSeparation.isFinite() && bottomSeparation.isFinite() &&
                standingSeparation > 0 && standingSeparation < stepSeparation && stepSeparation < bottomSeparation)
        require(listOf(filterMs, maximumSpeed, minimumSegment, shoulderRatio, hipRatio).all { it.isFinite() && it > 0 })
        require(listOf(readinessMs, phaseMs, finalStandingMs, minimumCycleMs, maximumGapMs, orientationMs, maximumTransientMs).all { it > 0 })
        require(maximumTransientFrames > 0)
        require(maximumSegmentChange in 0.0..1.0 && edgeMargin in 0f..0.25f)
        require(listOf(chainVisibility, presence).all { it in 0f..1f })
    }
}

/**
 * Side-view lunge: standing (feet together) -> step out and lower -> bottom (stride wide, deepest knee bent)
 * -> return to standing. Both legs are measured, so the stepping leg does not matter and alternating legs work.
 * A squat never counts because the feet never separate. One worker owns this state.
 */
class LungeAnalyzer(
    private val config: LungeConfig = LungeConfig(),
    private val diagnostic: ((String) -> Unit)? = null
) : ExerciseAnalyzer {
    private var phase = LungePhase.NOT_READY
    private var repetitions = 0
    private var previous: PoseFrame? = null
    private var rawMinKnee: Double? = null
    private var filteredMin: Double? = null
    private var filteredMax: Double? = null
    private var filteredSeparation: Double? = null
    private var lengths: List<Double>? = null
    private var candidate: String? = null
    private var candidateAt = 0L
    private var candidateSamples = 0
    private var attemptAt = 0L
    private var minimum = 180.0
    private var broadAt: Long? = null
    private var countedAt: Long? = null
    private var shallowAt: Long? = null
    private var lastValidAt: Long? = null
    private var transientFrames = 0
    private var lastFeedback = LungeFeedback.READY

    override fun reset() {
        repetitions = 0
        previous = null
        invalidateTracking(DiscontinuityReason.EPOCH)
    }

    override fun invalidateTracking(reason: DiscontinuityReason) {
        diagnostic?.invoke("invalidate reason=$reason phase=$phase previous=${previous?.timestampMs}")
        phase = LungePhase.NOT_READY
        rawMinKnee = null
        filteredMin = null
        filteredMax = null
        filteredSeparation = null
        lengths = null
        candidate = null
        candidateSamples = 0
        minimum = 180.0
        attemptAt = 0
        countedAt = null
        shallowAt = null
        broadAt = null
        lastValidAt = null
        transientFrames = 0
        lastFeedback = LungeFeedback.READY
        // Keep previous: invalidation must not reopen the timestamp/sequence acceptance gate.
    }

    private fun confirm(name: String, condition: Boolean, now: Long, duration: Long): Boolean {
        if (!condition) {
            if (candidate == name) { candidate = null; candidateSamples = 0 }
            return false
        }
        if (candidate != name) { candidate = name; candidateAt = now; candidateSamples = 1 }
        else candidateSamples++
        return candidateSamples >= 2 && now - candidateAt >= duration
    }

    private fun transition(next: LungePhase) { phase = next; candidate = null; candidateSamples = 0 }

    override fun processFrame(pose: PoseFrame): LungeAnalysisResult {
        val phaseBefore = phase
        var failure: String? = null
        var traceKnees = "unmeasured"
        var traceSeparation: Double? = null
        fun result(feedback: LungeFeedback, valid: Boolean = false, metrics: LungeMetrics? = null,
                   event: RepetitionCompleted? = null): LungeAnalysisResult {
            diagnostic?.invoke("frame time=${pose.timestampMs} seq=${pose.key.sequence} from=$phaseBefore to=$phase " +
                    "valid=$valid failure=${failure ?: "none"} rawKnees=$traceKnees rawSeparation=$traceSeparation " +
                    "smooth=$metrics candidate=${candidate ?: "none"} samples=$candidateSamples event=${event?.ordinal}")
            return LungeAnalysisResult(pose.key, pose.timestampMs, repetitions, event, feedback, valid, phase, metrics)
        }
        fun invalid(feedback: LungeFeedback, reason: String): LungeAnalysisResult {
            failure = reason
            invalidateTracking(DiscontinuityReason.TRACKING)
            return result(feedback)
        }
        // The rear leg is often hidden behind the front leg. Briefly hold the phase instead of cancelling the attempt.
        fun transient(feedback: LungeFeedback, reason: String): LungeAnalysisResult {
            val last = lastValidAt
            if (phase == LungePhase.NOT_READY || last == null || transientFrames >= config.maximumTransientFrames ||
                pose.timestampMs - last > config.maximumTransientMs) return invalid(feedback, reason)
            failure = reason
            transientFrames++
            candidate = null
            candidateSamples = 0
            diagnostic?.invoke("hold tracking phase=$phase reason=$reason frames=$transientFrames " +
                    "sinceValidMs=${pose.timestampMs - last}")
            return result(lastFeedback)
        }
        val old = previous
        val sameEpoch = old == null || old.key.let {
            it.sessionId == pose.key.sessionId && it.cameraEpoch == pose.key.cameraEpoch && it.analysisEpoch == pose.key.analysisEpoch
        }
        if (!sameEpoch) return invalid(LungeFeedback.UNCERTAIN, "epoch changed")
        if (old != null && (pose.timestampMs <= old.timestampMs || pose.key.sequence <= old.key.sequence)) {
            return invalid(LungeFeedback.UNCERTAIN, "timestamp or sequence nonmonotonic")
        }
        previous = pose
        val dt = old?.let { pose.timestampMs - it.timestampMs }
        if (dt != null && dt > config.maximumGapMs) {
            failure = "gap ${dt}ms > ${config.maximumGapMs}ms"
            invalidateTracking(DiscontinuityReason.GAP)
        }
        if (pose.timestampMs < 0 || pose.imageWidth <= 0 || pose.imageHeight <= 0)
            return invalid(LungeFeedback.UNCERTAIN, "invalid timestamp or image size")
        if (pose.landmarks.isEmpty()) return invalid(LungeFeedback.NO_POSE, "no pose landmarks")
        fun landmark(i: Int) = pose.landmarks.getOrNull(i)
        fun confident(i: Int) = PoseGeometry.confident(landmark(i), config.chainVisibility, config.presence)
        fun point(i: Int) = PoseGeometry.point(pose.landmarks[i], pose.imageWidth, pose.imageHeight)!!
        val required = listOf(23, 24, 25, 26, 27, 28)
        required.firstOrNull { !confident(it) }?.let { return transient(LungeFeedback.UNCERTAIN, "landmark $it low confidence") }
        required.firstOrNull { !PoseGeometry.inside(pose.landmarks[it], config.edgeMargin) }
            ?.let { return invalid(LungeFeedback.MOVE_BACK, "landmark $it outside frame") }
        val hipA = point(23); val hipB = point(24)
        val kneeA = point(25); val kneeB = point(26)
        val ankleA = point(27); val ankleB = point(28)
        val minLength = config.minimumSegment * min(pose.imageWidth, pose.imageHeight)
        val angleA = PoseGeometry.angle(hipA, kneeA, ankleA, minLength)
            ?: return transient(LungeFeedback.UNCERTAIN, "left knee geometry invalid")
        val angleB = PoseGeometry.angle(hipB, kneeB, ankleB, minLength)
            ?: return transient(LungeFeedback.UNCERTAIN, "right knee geometry invalid")
        val currentLengths = listOf(PoseGeometry.distance(hipA, kneeA), PoseGeometry.distance(kneeA, ankleA),
            PoseGeometry.distance(hipB, kneeB), PoseGeometry.distance(kneeB, ankleB))
        val legLength = currentLengths.sum() / 2
        val rawSeparation = abs(ankleA.x - ankleB.x) / legLength
        val rawMin = min(angleA, angleB)
        val rawMax = max(angleA, angleB)
        traceKnees = "[$angleA,$angleB]"
        traceSeparation = rawSeparation
        if (listOf(11, 12).all { confident(it) }) {
            val torso = (PoseGeometry.distance(point(11), hipA) + PoseGeometry.distance(point(12), hipB)) / 2
            val broad = torso > minLength && PoseGeometry.distance(point(11), point(12)) / torso > config.shoulderRatio &&
                    PoseGeometry.distance(hipA, hipB) / torso > config.hipRatio
            if (broad) {
                val since = broadAt ?: pose.timestampMs
                failure = "front-view width ratio"
                invalidateTracking(DiscontinuityReason.TRACKING)
                broadAt = since
                return result(if (pose.timestampMs - since >= config.orientationMs) LungeFeedback.SIDEWAYS else LungeFeedback.UNCERTAIN)
            }
        }
        broadAt = null
        val sinceValid = lastValidAt?.let { pose.timestampMs - it }
        if (sinceValid != null && sinceValid in 1..config.maximumGapMs) {
            if (rawMinKnee?.let { abs(rawMin - it) * 1000 / sinceValid > config.maximumSpeed } == true)
                return transient(LungeFeedback.UNCERTAIN, "knee speed above limit")
            if (lengths?.zip(currentLengths)?.any { (a, b) -> abs(b - a) / a > config.maximumSegmentChange } == true)
                return transient(LungeFeedback.UNCERTAIN, "segment length changed above limit")
        }
        val alpha = sinceValid?.let { 1 - exp(-it.toDouble() / config.filterMs) } ?: 1.0
        fun smooth(value: Double, before: Double?) = before?.let { it + alpha * (value - it) } ?: value
        val minKnee = smooth(rawMin, filteredMin)
        val maxKnee = smooth(rawMax, filteredMax)
        val separation = smooth(rawSeparation, filteredSeparation)
        filteredMin = minKnee
        filteredMax = maxKnee
        filteredSeparation = separation
        rawMinKnee = rawMin
        lengths = currentLengths
        val metrics = LungeMetrics(minKnee, maxKnee, separation)
        val now = pose.timestampMs
        lastValidAt = now
        transientFrames = 0
        val upright = hipA.y < kneeA.y && kneeA.y < ankleA.y && hipB.y < kneeB.y && kneeB.y < ankleB.y
        val standing = rawMin >= config.standingEntry && minKnee >= config.standingEntry &&
                rawSeparation <= config.standingSeparation && separation <= config.standingSeparation && upright
        val bottom = rawMin <= config.bottomEntry && minKnee <= config.bottomEntry &&
                rawSeparation >= config.bottomSeparation && separation >= config.bottomSeparation
        val leavingStanding = (rawMin <= config.descent && minKnee <= config.descent) ||
                (rawSeparation >= config.stepSeparation && separation >= config.stepSeparation)
        var event: RepetitionCompleted? = null
        if (phase in listOf(LungePhase.DESCENDING, LungePhase.BOTTOM, LungePhase.ASCENDING)) minimum = min(minimum, minKnee)
        when (phase) {
            LungePhase.NOT_READY -> if (confirm("ready", standing, now, config.readinessMs)) transition(LungePhase.STANDING)
            LungePhase.STANDING -> {
                if (minKnee < config.standingExit || separation > config.stepSeparation) countedAt = null
                if (confirm("down", leavingStanding, now, config.phaseMs)) {
                    attemptAt = candidateAt
                    minimum = minKnee
                    shallowAt = null
                    transition(LungePhase.DESCENDING)
                }
            }
            LungePhase.DESCENDING -> {
                if (confirm("bottom", bottom, now, config.phaseMs)) transition(LungePhase.BOTTOM)
                else if (confirm("partial", standing, now, config.finalStandingMs)) {
                    shallowAt = now
                    transition(LungePhase.STANDING)
                }
            }
            LungePhase.BOTTOM -> if (confirm("up", rawMin >= config.bottomExit && minKnee >= config.bottomExit &&
                        minKnee >= minimum + 12, now, config.phaseMs)) transition(LungePhase.ASCENDING)
            LungePhase.ASCENDING -> {
                if (confirm("top", standing, now, config.finalStandingMs)) {
                    if (now - attemptAt >= config.minimumCycleMs) {
                        repetitions++
                        event = RepetitionCompleted(repetitions, now)
                        countedAt = now
                        transition(LungePhase.STANDING)
                    } else invalidateTracking(DiscontinuityReason.TRACKING)
                } else if (confirm("bottomAgain", bottom, now, config.phaseMs)) transition(LungePhase.BOTTOM)
            }
        }
        val feedback = when (phase) {
            LungePhase.NOT_READY -> LungeFeedback.READY
            LungePhase.STANDING -> when {
                countedAt?.let { now - it < 700 } == true -> LungeFeedback.COUNTED
                shallowAt?.let { now - it < 1500 } == true -> LungeFeedback.SHALLOW
                else -> LungeFeedback.STEP
            }
            LungePhase.DESCENDING -> LungeFeedback.STEP
            LungePhase.BOTTOM, LungePhase.ASCENDING -> LungeFeedback.RISE
        }
        lastFeedback = feedback
        return result(feedback, true, metrics, event)
    }
}