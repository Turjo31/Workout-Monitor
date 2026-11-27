package com.kuet.gymtest.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.round

/** Squat-owned messages; other analyzers provide their own AnalysisFeedback values. */
enum class Feedback(override val text: String, override val priority: Int) : AnalysisFeedback {
    NO_POSE("Pose unclear — adjust the camera or lighting", 1),
    FRAMING("Keep your head and feet visible", 2),
    MOVE_BACK("Move farther back", 2),
    SIDEWAYS("Stand sideways", 2),
    UNCERTAIN("Pose unclear — adjust the camera or lighting", 3),
    STAND("Stand tall to begin", 4),
    LOWER("Lower into the squat", 4),
    RETURN("Return to standing", 4),
    SHALLOW("Try lowering farther on the next repetition", 4),
    COUNTED("Repetition counted", 5);

    override val key get() = "squat.$name"
}

enum class SquatPhase { NOT_READY, STANDING, DESCENDING, BOTTOM, ASCENDING }
enum class DepthStatus { UNKNOWN, ABOVE_TARGET, TARGET_REACHED }
data class SquatMetrics(val knee: Double, val hip: Double)
data class SquatAnalysisResult(
    override val key: FrameKey, override val timestampMs: Long,
    override val repetitions: Int, override val repetition: RepetitionCompleted?,
    override val feedback: Feedback, override val trackingValid: Boolean,
    val phase: SquatPhase, val depth: DepthStatus, val side: BodySide?, val metrics: SquatMetrics?
) : ExerciseAnalysisResult {
    override val presentation get() = AnalysisPresentation(
        phaseKey = phase.name,
        phaseLabel = phase.name.lowercase().replace('_', ' '),
        ready = phase != SquatPhase.NOT_READY,
        detail = when (depth) {
            DepthStatus.UNKNOWN -> null
            DepthStatus.ABOVE_TARGET -> "Bend: above target"
            DepthStatus.TARGET_REACHED -> "Target bend reached"
        }
    )
}

/** Engineering defaults, not calibrated biomechanical or medical limits. */
data class SquatConfig(
    val standingEntry: Double = 165.0, val standingExit: Double = 155.0,
    val standingHip: Double = 150.0, val descent: Double = 150.0,
    val bottomEntry: Double = 100.0, val bottomExit: Double = 112.0,
    val readinessMs: Long = 300, val phaseMs: Long = 80, val finalStandingMs: Long = 200,
    val minimumCycleMs: Long = 600, val maximumGapMs: Long = 300,
    val filterMs: Double = 80.0, val maximumSpeed: Double = 600.0,
    val maximumSegmentChange: Double = .35, val chainVisibility: Float = .65f,
    val presence: Float = .5f, val framingVisibility: Float = .5f, val edgeMargin: Float = .02f,
    val minimumSegment: Double = .025, val shoulderRatio: Double = .75,
    val hipRatio: Double = .55, val orientationMs: Long = 300,
    val goodDepthAngle: Double = 90.0
) {
    init {
        require(bottomEntry > 0 && bottomEntry < bottomExit && bottomExit < descent &&
            descent <= standingExit && standingExit < standingEntry && standingEntry <= 180)
        require(standingHip in 0.0..180.0 && filterMs > 0 && maximumSpeed > 0)
        require(listOf(filterMs, maximumSpeed, minimumSegment, shoulderRatio, hipRatio).all { it.isFinite() && it > 0 })
        require(listOf(readinessMs, phaseMs, finalStandingMs, minimumCycleMs, maximumGapMs, orientationMs).all { it > 0 })
        require(maximumSegmentChange in 0.0..1.0 && edgeMargin in 0f.. .25f && minimumSegment > 0)
        require(listOf(chainVisibility, presence, framingVisibility).all { it in 0f..1f })
        require(goodDepthAngle.isFinite() && goodDepthAngle in 1.0..bottomEntry)
    }
}

/** Single-owner pure state machine. Invalid observations cancel motion, never accepted reps. */
class SquatAnalyzer(
    private val config: SquatConfig = SquatConfig(),
    private val diagnostic: ((String) -> Unit)? = null
) : ExerciseAnalyzer {
    private var phase = SquatPhase.NOT_READY
    private var repetitions = 0
    private var side: BodySide? = null
    private var previous: PoseFrame? = null
    private var rawKnee: Double? = null
    private var filtered: Double? = null
    private var lengths: List<Double>? = null
    private var candidate: String? = null
    private var candidateAt = 0L
    private var candidateSamples = 0
    private var attemptAt = 0L
    private var minimum = 180.0
    private var targetReached = false
    private var broadAt: Long? = null
    private var countedAt: Long? = null
    private var shallowAt: Long? = null
    private val segmentTrace = if (diagnostic != null) SegmentLengthTrace() else null

    override fun reset() {
        repetitions = 0
        previous = null
        segmentTrace?.reset()
        invalidateTracking(DiscontinuityReason.EPOCH)
    }

    override fun invalidateTracking(reason: DiscontinuityReason) {
        diagnostic?.invoke("invalidate reason=$reason phase=$phase side=$side previous=${previous?.timestampMs} " +
            "attempt=${phase in listOf(SquatPhase.DESCENDING, SquatPhase.BOTTOM, SquatPhase.ASCENDING)}")
        phase = SquatPhase.NOT_READY
        side = null
        rawKnee = null
        filtered = null
        lengths = null
        candidate = null
        candidateSamples = 0
        targetReached = false
        minimum = 180.0
        attemptAt = 0
        countedAt = null
        shallowAt = null
        broadAt = null
        // Keep the high-water mark: loss must not make an old observation acceptable again.
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

    private fun transition(next: SquatPhase) { phase = next; candidate = null; candidateSamples = 0 }

    override fun processFrame(pose: PoseFrame): SquatAnalysisResult {
        val old = previous
        val segmentDetails = segmentTrace?.observe(pose)
        val phaseBefore = phase
        var dt: Long? = null
        var rawAngle: Double? = null
        var smoothAngle: Double? = null
        var hipAngleForTrace: Double? = null
        var standingForTrace: Boolean? = null
        var bottomForTrace: Boolean? = null
        var diagnosticOutcome: String? = null
        var landmarkTrace: String? = null
        var geometryTrace: String? = null
        var jumpTrace: String? = null
        fun result(feedback: Feedback, valid: Boolean = false, metrics: SquatMetrics? = null,
                   event: RepetitionCompleted? = null, failure: String = "none"): SquatAnalysisResult {
            val analysis = SquatAnalysisResult(
                pose.key, pose.timestampMs, repetitions, event, feedback, valid, phase,
                if (!valid) DepthStatus.UNKNOWN else if (targetReached) DepthStatus.TARGET_REACHED else DepthStatus.ABOVE_TARGET,
                side, metrics
            )
            diagnostic?.invoke("frame time=${pose.timestampMs} seq=${pose.key.sequence} " +
                "from=$phaseBefore to=$phase valid=$valid failure=$failure side=$side " +
                "dt=$dt rawKnee=$rawAngle smoothKnee=$smoothAngle hip=$hipAngleForTrace " +
                "standing=$standingForTrace bottom=$bottomForTrace " +
                "landmarks=$landmarkTrace geometry=$geometryTrace jump=$jumpTrace $segmentDetails " +
                "candidate=${candidate ?: "none"} samples=$candidateSamples " +
                "candidateMs=${if (candidate == null) 0 else pose.timestampMs - candidateAt} " +
                "feedback=$feedback event=${event?.ordinal} assessment=${event?.assessment?.quality} " +
                "diagnosticAttempt=$diagnosticOutcome count=$repetitions")
            return analysis
        }
        fun invalid(feedback: Feedback, failure: String): SquatAnalysisResult {
            if (phaseBefore in listOf(SquatPhase.DESCENDING, SquatPhase.BOTTOM, SquatPhase.ASCENDING)) {
                diagnosticOutcome = "TRACKING_INTERRUPTED"
            }
            invalidateTracking(DiscontinuityReason.TRACKING)
            return result(feedback, failure = failure)
        }
        val sameEpoch = old == null || old.key.let {
            it.sessionId == pose.key.sessionId && it.cameraEpoch == pose.key.cameraEpoch && it.analysisEpoch == pose.key.analysisEpoch
        }
        if (!sameEpoch) return invalid(Feedback.UNCERTAIN, "epochMismatch") // Owner must reset for a new epoch.
        if (old != null && (pose.timestampMs <= old.timestampMs || pose.key.sequence <= old.key.sequence)) {
            return invalid(Feedback.UNCERTAIN, "nonMonotonic")
        }
        previous = pose
        val frameDt = old?.let { pose.timestampMs - it.timestampMs }
        dt = frameDt
        if (frameDt != null && frameDt > config.maximumGapMs) invalidateTracking(DiscontinuityReason.GAP)
        if (pose.timestampMs < 0 || pose.imageWidth <= 0 || pose.imageHeight <= 0)
            return invalid(Feedback.UNCERTAIN, "invalidFrame")
        if (pose.landmarks.isEmpty()) return invalid(Feedback.NO_POSE, "noPose")
        fun landmark(i: Int) = pose.landmarks.getOrNull(i)
        fun confident(i: Int, threshold: Float = config.chainVisibility) =
            PoseGeometry.confident(landmark(i), threshold, config.presence)
        fun chain(s: BodySide) = listOf(s.shoulder, s.hip, s.knee, s.ankle)
        fun point(i: Int) = PoseGeometry.point(pose.landmarks[i], pose.imageWidth, pose.imageHeight)!!
        fun lowConfidence(indices: List<Int>, threshold: Float = config.chainVisibility) =
            indices.filterNot { confident(it, threshold) }.joinToString(",") { i ->
                val l = landmark(i)
                "$i(v=${l?.visibility},p=${l?.presence})"
            }
        val chosen = side ?: BodySide.entries.filter { s -> chain(s).all { confident(it) } }
            .maxByOrNull { s -> chain(s).minOf { landmark(it)!!.visibility!! } }
            ?: return invalid(Feedback.UNCERTAIN,
                "noConfidentLeg left=[${lowConfidence(chain(BodySide.LEFT))}] right=[${lowConfidence(chain(BodySide.RIGHT))}]")
        if (!chain(chosen).all { confident(it) })
            return invalid(Feedback.UNCERTAIN, "lowConfidence side=$chosen indices=[${lowConfidence(chain(chosen))}]")
        if (!chain(chosen).all { PoseGeometry.inside(landmark(it)!!, config.edgeMargin) })
            return invalid(Feedback.MOVE_BACK, "legOutOfFrame side=$chosen")
        if (!confident(0, config.framingVisibility) || !PoseGeometry.inside(landmark(0)!!, config.edgeMargin) ||
            listOf(chosen.heel, chosen.toe).none { confident(it, config.framingVisibility) && PoseGeometry.inside(landmark(it)!!, config.edgeMargin) }
        ) return invalid(Feedback.FRAMING,
            "headOrFootFraming head=${landmark(0)?.visibility} foot=[${lowConfidence(listOf(chosen.heel, chosen.toe), config.framingVisibility)}]")
        val shoulder = point(chosen.shoulder)
        val hip = point(chosen.hip)
        val knee = point(chosen.knee)
        val ankle = point(chosen.ankle)
        val minLength = config.minimumSegment * min(pose.imageWidth, pose.imageHeight)
        val raw = PoseGeometry.angle(hip, knee, ankle, minLength)
            ?: return invalid(Feedback.UNCERTAIN, "invalidKneeGeometry")
        rawAngle = raw
        val hipAngle = PoseGeometry.angle(shoulder, hip, knee, minLength)
            ?: return invalid(Feedback.UNCERTAIN, "invalidHipGeometry")
        hipAngleForTrace = hipAngle
        val currentLengths = listOf(PoseGeometry.distance(shoulder, hip), PoseGeometry.distance(hip, knee), PoseGeometry.distance(knee, ankle))
        if (diagnostic != null) {
            fun short(value: Double) = round(value * 1000.0) / 1000.0
            fun joint(name: String, index: Int): String {
                val landmark = pose.landmarks[index]
                return "$name=(${short(landmark.x.toDouble())},${short(landmark.y.toDouble())}," +
                    "v=${landmark.visibility},p=${landmark.presence})"
            }
            landmarkTrace = listOf(
                joint("shoulder", chosen.shoulder), joint("hip", chosen.hip),
                joint("knee", chosen.knee), joint("ankle", chosen.ankle)
            ).joinToString(";")
            val names = listOf("torso", "thigh", "shin")
            val changes = lengths?.zip(currentLengths)?.map { (before, current) -> abs(current - before) / before }
            val speed = if (frameDt != null && frameDt in 1..config.maximumGapMs && rawKnee != null)
                abs(raw - rawKnee!!) * 1000 / frameDt else null
            geometryTrace = names.indices.joinToString(";") { index ->
                "${names[index]}Px=${short(currentLengths[index])}" +
                    ",change=${changes?.get(index)?.let { short(it) }}"
            } + ";kneeSpeedDegPerSec=${speed?.let { short(it) }}"
            val speedJump = speed != null && speed > config.maximumSpeed
            val segmentJump = changes?.any { it > config.maximumSegmentChange } == true
            if (speedJump || segmentJump) {
                jumpTrace = buildList {
                    if (speedJump) add("kneeSpeed")
                    if (segmentJump) add("segmentLength")
                }.joinToString("+")
            }
        }
        if (listOf(11, 12, 23, 24).all { confident(it) }) {
            val torso = (PoseGeometry.distance(point(11), point(23)) + PoseGeometry.distance(point(12), point(24))) / 2
            val broad = torso > minLength && PoseGeometry.distance(point(11), point(12)) / torso > config.shoulderRatio &&
                PoseGeometry.distance(point(23), point(24)) / torso > config.hipRatio
            if (broad) {
                val since = broadAt ?: pose.timestampMs
                invalidateTracking(DiscontinuityReason.TRACKING)
                broadAt = since
                return result(if (pose.timestampMs - since >= config.orientationMs) Feedback.SIDEWAYS else Feedback.UNCERTAIN,
                    failure = "frontFacing")
            }
        }
        broadAt = null
        if (frameDt != null && frameDt in 1..config.maximumGapMs &&
            (rawKnee?.let { abs(raw - it) * 1000 / frameDt > config.maximumSpeed } == true ||
                lengths?.zip(currentLengths)?.any { (a, b) -> abs(b - a) / a > config.maximumSegmentChange } == true)
        ) return invalid(Feedback.UNCERTAIN, "geometryJump")
        side = chosen
        val angle = filtered?.let { it + (1 - exp(-frameDt!!.toDouble() / config.filterMs)) * (raw - it) } ?: raw
        smoothAngle = angle
        filtered = angle
        rawKnee = raw
        lengths = currentLengths
        val now = pose.timestampMs
        val standing = raw >= config.standingEntry && angle >= config.standingEntry && hipAngle >= config.standingHip &&
            shoulder.y < hip.y && hip.y < ankle.y
        val bottom = raw <= config.bottomEntry && angle <= config.bottomEntry
        standingForTrace = standing
        bottomForTrace = bottom
        var event: RepetitionCompleted? = null
        if (phase in listOf(SquatPhase.DESCENDING, SquatPhase.BOTTOM, SquatPhase.ASCENDING)) minimum = min(minimum, angle)
        when (phase) {
            SquatPhase.NOT_READY -> if (confirm("ready", standing, now, config.readinessMs)) transition(SquatPhase.STANDING)
            SquatPhase.STANDING -> {
                if (angle < config.standingExit) countedAt = null
                if (confirm("down", raw <= config.descent && angle <= config.descent, now, config.phaseMs)) {
                    attemptAt = candidateAt
                    minimum = angle
                    shallowAt = null
                    transition(SquatPhase.DESCENDING)
                }
            }
            SquatPhase.DESCENDING -> {
                if (confirm("bottom", bottom, now, config.phaseMs)) {
                    targetReached = true
                    transition(SquatPhase.BOTTOM)
                } else if (confirm("partial", standing, now, config.finalStandingMs)) {
                    shallowAt = now
                    diagnosticOutcome = "INCOMPLETE"
                    transition(SquatPhase.STANDING)
                }
            }
            SquatPhase.BOTTOM -> if (confirm("up", raw >= config.bottomExit && angle >= config.bottomExit && angle >= minimum + 12, now, config.phaseMs)) transition(SquatPhase.ASCENDING)
            SquatPhase.ASCENDING -> {
                if (confirm("top", standing, now, config.finalStandingMs)) {
                    if (now - attemptAt >= config.minimumCycleMs) {
                        repetitions++
                        event = RepetitionCompleted(repetitions, now,
                            if (minimum <= config.goodDepthAngle)
                                RepAssessment(RepFormQuality.GOOD, "Depth target reached")
                            else RepAssessment(RepFormQuality.NEEDS_ATTENTION, "Try lowering a little farther"))
                        targetReached = false
                        countedAt = now
                        transition(SquatPhase.STANDING)
                    } else {
                        diagnosticOutcome = "TOO_FAST"
                        invalidateTracking(DiscontinuityReason.TRACKING)
                    }
                } else if (confirm("bottomAgain", bottom, now, config.phaseMs)) transition(SquatPhase.BOTTOM)
            }
        }
        val feedback = when (phase) {
            SquatPhase.NOT_READY -> Feedback.STAND
            SquatPhase.STANDING -> when {
                countedAt?.let { now - it < 700 } == true -> Feedback.COUNTED
                shallowAt?.let { now - it < 1500 } == true -> Feedback.SHALLOW
                else -> Feedback.LOWER
            }
            SquatPhase.DESCENDING -> Feedback.LOWER
            SquatPhase.BOTTOM, SquatPhase.ASCENDING -> Feedback.RETURN
        }
        return result(feedback, true, SquatMetrics(angle, hipAngle), event)
    }
}
