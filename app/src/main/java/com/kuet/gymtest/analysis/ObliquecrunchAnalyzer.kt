package com.kuet.gymtest.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** Oblique-crunch-owned messages; keys are stable and priorities follow the shared 1-6 scale. */
enum class ObliqueCrunchFeedback(override val text: String, override val priority: Int) : AnalysisFeedback {
    NO_POSE("Pose unclear — adjust the camera or lighting", 1),
    MOVE_BACK("Keep your shoulders, hips and knees in view", 2),
    FACE_CAMERA("Face the camera", 2),
    UNCERTAIN("Pose unclear — adjust the camera or lighting", 3),
    READY("Stand tall with your hands behind your head", 4),
    RAISE("Lift one knee out to the side toward your elbow", 4),
    LOWER("Lower your leg and stand tall", 4),
    SHALLOW("Lift your knee higher and crunch your elbow toward it", 4),
    COUNTED("Repetition counted", 5);

    override val key get() = "obliquecrunch.$name"
}

enum class ObliqueCrunchPhase { NOT_READY, STANDING, RAISING, CRUNCH, LOWERING }

/** Lift is knee height below the hip in torso lengths (small = knee raised); close is elbow-to-knee in torso lengths. */
data class ObliqueCrunchMetrics(val liftA: Double, val liftB: Double, val closeA: Double, val closeB: Double)

data class ObliqueCrunchAnalysisResult(
    override val key: FrameKey, override val timestampMs: Long,
    override val repetitions: Int, override val repetition: RepetitionCompleted?,
    override val feedback: ObliqueCrunchFeedback, override val trackingValid: Boolean,
    val phase: ObliqueCrunchPhase, val metrics: ObliqueCrunchMetrics?
) : ExerciseAnalysisResult {
    override val presentation get() = AnalysisPresentation(
        phaseKey = phase.name,
        phaseLabel = phase.name.lowercase().replace('_', ' '),
        ready = phase != ObliqueCrunchPhase.NOT_READY,
        detail = when {
            !trackingValid -> null
            phase == ObliqueCrunchPhase.CRUNCH || phase == ObliqueCrunchPhase.LOWERING -> "Crunch reached"
            else -> "Crunch: not yet reached"
        }
    )
}

/** Provisional 2D front-view defaults, not calibrated limits. Tune from ObliqueCrunchTrace logs. */
data class ObliqueCrunchConfig(
    val standingLift: Double = .70, val standingExit: Double = .60, val raiseLift: Double = .55,
    val peakLift: Double = .40, val peakExitLift: Double = .50, val supportLift: Double = .60,
    val closeEntry: Double = .60, val frontRatio: Double = .45,
    val readinessMs: Long = 300, val phaseMs: Long = 80, val finalStandingMs: Long = 150,
    val minimumCycleMs: Long = 500, val maximumGapMs: Long = 300,
    val filterMs: Double = 80.0, val maximumLiftSpeed: Double = 8.0,
    val maximumSegmentChange: Double = .4, val chainVisibility: Float = .5f,
    val presence: Float = .5f, val edgeMargin: Float = .02f, val minimumSegment: Double = .025,
    val orientationMs: Long = 300, val maximumTransientMs: Long = 300, val maximumTransientFrames: Int = 3
) {
    init {
        require(peakLift > 0 && peakLift < peakExitLift && peakExitLift < raiseLift && raiseLift < standingExit &&
                standingExit < standingLift && supportLift in peakExitLift..standingLift)
        require(listOf(closeEntry, frontRatio, filterMs, maximumLiftSpeed, minimumSegment).all { it.isFinite() && it > 0 })
        require(listOf(readinessMs, phaseMs, finalStandingMs, minimumCycleMs, maximumGapMs, orientationMs,
            maximumTransientMs).all { it > 0 })
        require(maximumTransientFrames > 0 && maximumSegmentChange in 0.0..1.0 && edgeMargin in 0f..0.25f)
        require(listOf(chainVisibility, presence).all { it in 0f..1f })
    }
}

/**
 * Front-view standing oblique crunch: stand with both knees low, lift one knee out to the side while the same-side
 * elbow comes down to it, then lower. Either side can lead and sides may alternate; each crunch is one repetition.
 * The working side is locked for the attempt; the other knee must stay down, so jumping or lifting both knees
 * never counts. One worker owns this state.
 */
class ObliqueCrunchAnalyzer(
    private val config: ObliqueCrunchConfig = ObliqueCrunchConfig(),
    private val diagnostic: ((String) -> Unit)? = null
) : ExerciseAnalyzer {
    private var phase = ObliqueCrunchPhase.NOT_READY
    private var repetitions = 0
    private var previous: PoseFrame? = null
    private var working = 0
    private var rawLifts: Pair<Double, Double>? = null
    private var filtered: DoubleArray? = null
    private var torsoLength: Double? = null
    private var candidate: String? = null
    private var candidateAt = 0L
    private var candidateSamples = 0
    private var attemptAt = 0L
    private var broadAt: Long? = null
    private var countedAt: Long? = null
    private var shallowAt: Long? = null
    private var lastValidAt: Long? = null
    private var transientFrames = 0
    private var lastFeedback = ObliqueCrunchFeedback.READY

    override fun reset() {
        repetitions = 0
        previous = null
        invalidateTracking(DiscontinuityReason.EPOCH)
    }

    override fun invalidateTracking(reason: DiscontinuityReason) {
        diagnostic?.invoke("invalidate reason=$reason phase=$phase previous=${previous?.timestampMs}")
        phase = ObliqueCrunchPhase.NOT_READY
        rawLifts = null
        filtered = null
        torsoLength = null
        candidate = null
        candidateSamples = 0
        attemptAt = 0
        countedAt = null
        shallowAt = null
        broadAt = null
        lastValidAt = null
        transientFrames = 0
        lastFeedback = ObliqueCrunchFeedback.READY
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

    private fun transition(next: ObliqueCrunchPhase) { phase = next; candidate = null; candidateSamples = 0 }

    override fun processFrame(pose: PoseFrame): ObliqueCrunchAnalysisResult {
        val phaseBefore = phase
        var failure: String? = null
        var traceRaw = "unmeasured"
        fun result(feedback: ObliqueCrunchFeedback, valid: Boolean = false, metrics: ObliqueCrunchMetrics? = null,
                   event: RepetitionCompleted? = null): ObliqueCrunchAnalysisResult {
            diagnostic?.invoke("frame time=${pose.timestampMs} seq=${pose.key.sequence} from=$phaseBefore to=$phase " +
                    "valid=$valid failure=${failure ?: "none"} raw=$traceRaw working=$working smooth=$metrics " +
                    "candidate=${candidate ?: "none"} samples=$candidateSamples event=${event?.ordinal}")
            return ObliqueCrunchAnalysisResult(pose.key, pose.timestampMs, repetitions, event, feedback, valid, phase, metrics)
        }
        fun invalid(feedback: ObliqueCrunchFeedback, reason: String): ObliqueCrunchAnalysisResult {
            failure = reason
            invalidateTracking(DiscontinuityReason.TRACKING)
            return result(feedback)
        }
        // Brief landmark dropouts hold the phase instead of cancelling the attempt.
        fun transient(feedback: ObliqueCrunchFeedback, reason: String): ObliqueCrunchAnalysisResult {
            val last = lastValidAt
            if (phase == ObliqueCrunchPhase.NOT_READY || last == null || transientFrames >= config.maximumTransientFrames ||
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
        if (!sameEpoch) return invalid(ObliqueCrunchFeedback.UNCERTAIN, "epoch changed")
        if (old != null && (pose.timestampMs <= old.timestampMs || pose.key.sequence <= old.key.sequence)) {
            return invalid(ObliqueCrunchFeedback.UNCERTAIN, "timestamp or sequence nonmonotonic")
        }
        previous = pose
        val dt = old?.let { pose.timestampMs - it.timestampMs }
        if (dt != null && dt > config.maximumGapMs) {
            failure = "gap ${dt}ms > ${config.maximumGapMs}ms"
            invalidateTracking(DiscontinuityReason.GAP)
        }
        if (pose.timestampMs < 0 || pose.imageWidth <= 0 || pose.imageHeight <= 0)
            return invalid(ObliqueCrunchFeedback.UNCERTAIN, "invalid timestamp or image size")
        if (pose.landmarks.isEmpty()) return invalid(ObliqueCrunchFeedback.NO_POSE, "no pose landmarks")
        fun landmark(i: Int) = pose.landmarks.getOrNull(i)
        fun confident(i: Int) = PoseGeometry.confident(landmark(i), config.chainVisibility, config.presence)
        fun point(i: Int) = PoseGeometry.point(pose.landmarks[i], pose.imageWidth, pose.imageHeight)!!
        // Side A uses the left-body landmarks, side B the right-body ones.
        val required = listOf(11, 12, 13, 14, 23, 24, 25, 26)
        required.firstOrNull { !confident(it) }
            ?.let { return transient(ObliqueCrunchFeedback.UNCERTAIN, "landmark $it low confidence") }
        required.firstOrNull { !PoseGeometry.inside(pose.landmarks[it], config.edgeMargin) }
            ?.let { return invalid(ObliqueCrunchFeedback.MOVE_BACK, "landmark $it outside frame") }
        val shoulderA = point(11); val shoulderB = point(12)
        val elbowA = point(13); val elbowB = point(14)
        val hipA = point(23); val hipB = point(24)
        val kneeA = point(25); val kneeB = point(26)
        val minLength = config.minimumSegment * min(pose.imageWidth, pose.imageHeight)
        val torso = (PoseGeometry.distance(shoulderA, hipA) + PoseGeometry.distance(shoulderB, hipB)) / 2
        if (torso < minLength) return transient(ObliqueCrunchFeedback.UNCERTAIN, "torso too short")
        val rawLiftA = (kneeA.y - hipA.y) / torso
        val rawLiftB = (kneeB.y - hipB.y) / torso
        val rawCloseA = PoseGeometry.distance(elbowA, kneeA) / torso
        val rawCloseB = PoseGeometry.distance(elbowB, kneeB) / torso
        traceRaw = "[liftA=$rawLiftA liftB=$rawLiftB closeA=$rawCloseA closeB=$rawCloseB]"
        if (PoseGeometry.distance(shoulderA, shoulderB) / torso < config.frontRatio) {
            val since = broadAt ?: pose.timestampMs
            failure = "shoulders too narrow for a front view"
            invalidateTracking(DiscontinuityReason.TRACKING)
            broadAt = since
            return result(if (pose.timestampMs - since >= config.orientationMs) ObliqueCrunchFeedback.FACE_CAMERA
            else ObliqueCrunchFeedback.UNCERTAIN)
        }
        broadAt = null
        val sinceValid = lastValidAt?.let { pose.timestampMs - it }
        if (sinceValid != null && sinceValid in 1..config.maximumGapMs) {
            val before = rawLifts
            if (before != null && max(abs(rawLiftA - before.first), abs(rawLiftB - before.second)) * 1000 / sinceValid >
                config.maximumLiftSpeed) return transient(ObliqueCrunchFeedback.UNCERTAIN, "knee speed above limit")
            val lastTorso = torsoLength
            if (lastTorso != null && abs(torso - lastTorso) / lastTorso > config.maximumSegmentChange)
                return transient(ObliqueCrunchFeedback.UNCERTAIN, "torso length changed above limit")
        }
        val alpha = sinceValid?.let { 1 - exp(-it.toDouble() / config.filterMs) } ?: 1.0
        val raw = doubleArrayOf(rawLiftA, rawLiftB, rawCloseA, rawCloseB)
        val before = filtered
        val smooth = DoubleArray(4) { i -> before?.let { it[i] + alpha * (raw[i] - it[i]) } ?: raw[i] }
        filtered = smooth
        rawLifts = Pair(rawLiftA, rawLiftB)
        torsoLength = torso
        val metrics = ObliqueCrunchMetrics(smooth[0], smooth[1], smooth[2], smooth[3])
        val now = pose.timestampMs
        lastValidAt = now
        transientFrames = 0
        fun lift(side: Int, smoothed: Boolean) = if (smoothed) smooth[side] else raw[side]
        fun close(side: Int, smoothed: Boolean) = if (smoothed) smooth[2 + side] else raw[2 + side]
        val standing = listOf(true, false).all { s -> lift(0, s) >= config.standingLift && lift(1, s) >= config.standingLift }
        val leader = if (smooth[0] <= smooth[1]) 0 else 1
        val rising = listOf(true, false).all { s ->
            lift(leader, s) <= config.raiseLift && lift(1 - leader, s) >= config.supportLift
        }
        fun peak(side: Int) = listOf(true, false).all { s ->
            lift(side, s) <= config.peakLift && close(side, s) <= config.closeEntry && lift(1 - side, s) >= config.supportLift
        }
        var event: RepetitionCompleted? = null
        when (phase) {
            ObliqueCrunchPhase.NOT_READY -> if (confirm("ready", standing, now, config.readinessMs)) transition(ObliqueCrunchPhase.STANDING)
            ObliqueCrunchPhase.STANDING -> {
                if (min(smooth[0], smooth[1]) < config.standingExit) countedAt = null
                if (confirm("raise", rising, now, config.phaseMs)) {
                    working = leader
                    attemptAt = candidateAt
                    shallowAt = null
                    transition(ObliqueCrunchPhase.RAISING)
                }
            }
            ObliqueCrunchPhase.RAISING -> {
                if (confirm("peak", peak(working), now, config.phaseMs)) transition(ObliqueCrunchPhase.CRUNCH)
                else if (confirm("partial", standing, now, config.finalStandingMs)) {
                    shallowAt = now
                    transition(ObliqueCrunchPhase.STANDING)
                }
            }
            ObliqueCrunchPhase.CRUNCH -> if (confirm("lower", lift(working, true) >= config.peakExitLift &&
                        lift(working, false) >= config.peakExitLift, now, config.phaseMs)) transition(ObliqueCrunchPhase.LOWERING)
            ObliqueCrunchPhase.LOWERING -> {
                if (confirm("top", standing, now, config.finalStandingMs)) {
                    if (now - attemptAt >= config.minimumCycleMs) {
                        repetitions++
                        event = RepetitionCompleted(repetitions, now)
                        countedAt = now
                        transition(ObliqueCrunchPhase.STANDING)
                    } else invalidateTracking(DiscontinuityReason.TRACKING)
                } else if (confirm("peakAgain", peak(working), now, config.phaseMs)) transition(ObliqueCrunchPhase.CRUNCH)
            }
        }
        val feedback = when (phase) {
            ObliqueCrunchPhase.NOT_READY -> ObliqueCrunchFeedback.READY
            ObliqueCrunchPhase.STANDING -> when {
                countedAt?.let { now - it < 700 } == true -> ObliqueCrunchFeedback.COUNTED
                shallowAt?.let { now - it < 1500 } == true -> ObliqueCrunchFeedback.SHALLOW
                else -> ObliqueCrunchFeedback.RAISE
            }
            ObliqueCrunchPhase.RAISING -> ObliqueCrunchFeedback.RAISE
            ObliqueCrunchPhase.CRUNCH, ObliqueCrunchPhase.LOWERING -> ObliqueCrunchFeedback.LOWER
        }
        lastFeedback = feedback
        return result(feedback, true, metrics, event)
    }
}