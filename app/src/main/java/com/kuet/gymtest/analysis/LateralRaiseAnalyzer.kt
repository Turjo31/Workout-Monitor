package com.kuet.gymtest.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

enum class LateralRaiseFeedback(override val text: String, override val priority: Int) : AnalysisFeedback {
    NO_POSE("Pose unclear - face the camera", 1),
    FRAMING("Keep both shoulders, arms, hands and hips in view", 2),
    FRONT_VIEW("Face the camera with both arms visible", 2),
    UNCERTAIN("Arm tracking unclear - adjust the camera", 3),
    READY("Start with both arms lowered at your sides", 4),
    RAISE("Raise both arms out to shoulder height", 4),
    LOWER("Lower both arms with control", 4),
    COUNTED("Repetition counted", 5),
    COUNTED_FORM("Repetition counted - arm position needs attention", 5);

    override val key get() = "lateralraise.$name"
}

enum class LateralRaisePhase { NOT_READY, LOWERED, RAISING, RAISED, LOWERING }

data class LateralRaiseMetrics(val leftAngle: Double, val rightAngle: Double)

data class LateralRaiseAnalysisResult(
    override val key: FrameKey, override val timestampMs: Long,
    override val repetitions: Int, override val repetition: RepetitionCompleted?,
    override val feedback: LateralRaiseFeedback, override val trackingValid: Boolean,
    val phase: LateralRaisePhase, val metrics: LateralRaiseMetrics?,
    override val uncountedAttempt: UncountedAttempt? = null
) : ExerciseAnalysisResult {
    override val attemptInProgress get() = phase in listOf(
        LateralRaisePhase.RAISING, LateralRaisePhase.RAISED, LateralRaisePhase.LOWERING)
    override val presentation get() = AnalysisPresentation(
        "lateralraise.${phase.name}", phase.name.lowercase().replace('_', ' '),
        phase != LateralRaisePhase.NOT_READY,
        if (trackingValid) "Both arms" else null
    )
}

/** Front-view 2D starting values; neither load nor clinically correct technique is inferred. */
data class LateralRaiseConfig(
    val loweredAngle: Double = 28.0, val leaveLoweredAngle: Double = 40.0,
    val raisedAngle: Double = 70.0, val leaveRaisedAngle: Double = 55.0,
    val maximumTargetAngle: Double = 115.0, val maximumArmDifference: Double = 22.0,
    val minimumElbowAngle: Double = 125.0,
    val readinessMs: Long = 100, val raisedMs: Long = 35, val loweredMs: Long = 45,
    val minimumCycleMs: Long = 250, val maximumGapMs: Long = 300,
    val filterMs: Double = 45.0, val minimumSegment: Double = .025,
    val minimumFrontWidth: Double = .45, val maximumShoulderSlope: Double = .35,
    val visibility: Float = .65f, val presence: Float = .5f, val edgeMargin: Float = .02f
) {
    init {
        require(listOf(loweredAngle, leaveLoweredAngle, raisedAngle, leaveRaisedAngle,
            maximumTargetAngle, maximumArmDifference, minimumElbowAngle).all { it.isFinite() && it in 0.0..180.0 })
        require(loweredAngle < leaveLoweredAngle && leaveLoweredAngle < leaveRaisedAngle &&
            leaveRaisedAngle < raisedAngle && raisedAngle < maximumTargetAngle)
        require(listOf(readinessMs, raisedMs, loweredMs, minimumCycleMs, maximumGapMs).all { it > 0 })
        require(listOf(filterMs, minimumSegment, minimumFrontWidth, maximumShoulderSlope)
            .all { it.isFinite() && it > 0 })
        require(listOf(visibility, presence, edgeMargin).all { it.isFinite() && it in 0f..1f })
    }
}

/** One rep is a confidently observed bilateral lowered -> raised -> lowered cycle. */
class LateralRaiseAnalyzer(
    private val config: LateralRaiseConfig = LateralRaiseConfig(),
    private val diagnostic: ((String) -> Unit)? = null
) : ExerciseAnalyzer {
    private var phase = LateralRaisePhase.NOT_READY
    private var repetitions = 0
    private var previous: PoseFrame? = null
    private var filtered: LateralRaiseMetrics? = null
    private var candidate: String? = null
    private var candidateAt = 0L
    private var candidateSamples = 0
    private var attemptAt = 0L
    private var formIssue = false
    private var countedAt: Long? = null
    private var countedFeedback = LateralRaiseFeedback.COUNTED

    override fun reset() {
        repetitions = 0
        previous = null
        invalidateTracking(DiscontinuityReason.EPOCH)
    }

    override fun invalidateTracking(reason: DiscontinuityReason) {
        diagnostic?.invoke("invalidate reason=$reason phase=$phase previous=${previous?.timestampMs}")
        phase = LateralRaisePhase.NOT_READY
        filtered = null
        candidate = null
        candidateSamples = 0
        attemptAt = 0
        formIssue = false
        countedAt = null
        countedFeedback = LateralRaiseFeedback.COUNTED
    }

    private fun transition(next: LateralRaisePhase) {
        phase = next
        candidate = null
        candidateSamples = 0
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

    override fun processFrame(pose: PoseFrame): LateralRaiseAnalysisResult {
        val before = phase
        var failure: String? = null
        var rawTrace: LateralRaiseMetrics? = null
        var smoothTrace: LateralRaiseMetrics? = null
        var endedAttempt: UncountedAttempt? = null
        var dtTrace: Long? = null
        fun result(feedback: LateralRaiseFeedback, valid: Boolean = false,
                   metrics: LateralRaiseMetrics? = null, event: RepetitionCompleted? = null): LateralRaiseAnalysisResult {
            diagnostic?.invoke("frame time=${pose.timestampMs} seq=${pose.key.sequence} from=$before to=$phase " +
                "valid=$valid failure=${failure ?: "none"} raw=$rawTrace smooth=$smoothTrace " +
                "formIssue=$formIssue candidate=${candidate ?: "none"} samples=$candidateSamples " +
                "dt=$dtTrace feedback=$feedback event=${event?.ordinal} uncounted=$endedAttempt")
            return LateralRaiseAnalysisResult(pose.key, pose.timestampMs, repetitions, event,
                feedback, valid, phase, metrics, endedAttempt)
        }
        fun invalid(feedback: LateralRaiseFeedback, reason: String): LateralRaiseAnalysisResult {
            failure = reason
            if (phase in listOf(LateralRaisePhase.RAISING, LateralRaisePhase.RAISED, LateralRaisePhase.LOWERING))
                endedAttempt = UncountedAttempt.TRACKING_INTERRUPTED
            invalidateTracking(DiscontinuityReason.TRACKING)
            return result(feedback)
        }
        val old = previous
        if (pose.timestampMs < 0 || pose.key.sequence < 0 || pose.imageWidth <= 0 || pose.imageHeight <= 0)
            return invalid(LateralRaiseFeedback.UNCERTAIN, "invalid frame metadata")
        if (old != null && (old.key.sessionId != pose.key.sessionId || old.key.cameraEpoch != pose.key.cameraEpoch ||
                old.key.analysisEpoch != pose.key.analysisEpoch || pose.key.sequence <= old.key.sequence ||
                pose.timestampMs <= old.timestampMs))
            return invalid(LateralRaiseFeedback.UNCERTAIN, "key or timestamp discontinuity")
        previous = pose
        val dt = old?.let { pose.timestampMs - it.timestampMs }
        dtTrace = dt
        if (dt != null && dt > config.maximumGapMs) return invalid(LateralRaiseFeedback.UNCERTAIN, "frame gap")
        if (pose.landmarks.isEmpty()) return invalid(LateralRaiseFeedback.NO_POSE, "no pose")
        val required = listOf(11, 12, 13, 14, 15, 16, 23, 24)
        fun confident(i: Int) = PoseGeometry.confident(pose.landmarks.getOrNull(i), config.visibility, config.presence)
        if (!required.all(::confident)) return invalid(LateralRaiseFeedback.UNCERTAIN, "arm landmark low confidence")
        if (!required.all { PoseGeometry.inside(pose.landmarks[it], config.edgeMargin) })
            return invalid(LateralRaiseFeedback.FRAMING, "arm landmark outside frame")
        fun point(i: Int) = PoseGeometry.point(pose.landmarks[i], pose.imageWidth, pose.imageHeight)!!
        val leftShoulder = point(11)
        val rightShoulder = point(12)
        val leftElbow = point(13)
        val rightElbow = point(14)
        val leftHip = point(23)
        val rightHip = point(24)
        val minimum = config.minimumSegment * min(pose.imageWidth, pose.imageHeight)
        val torso = (PoseGeometry.distance(leftShoulder, leftHip) +
            PoseGeometry.distance(rightShoulder, rightHip)) / 2
        val shoulderWidth = PoseGeometry.distance(leftShoulder, rightShoulder)
        if (torso < minimum || shoulderWidth < minimum ||
            shoulderWidth / torso < config.minimumFrontWidth ||
            abs(leftShoulder.y - rightShoulder.y) / shoulderWidth > config.maximumShoulderSlope)
            return invalid(LateralRaiseFeedback.FRONT_VIEW, "front view or torso geometry")
        val left = PoseGeometry.angle(leftHip, leftShoulder, leftElbow, minimum)
            ?: return invalid(LateralRaiseFeedback.UNCERTAIN, "left shoulder angle invalid")
        val right = PoseGeometry.angle(rightHip, rightShoulder, rightElbow, minimum)
            ?: return invalid(LateralRaiseFeedback.UNCERTAIN, "right shoulder angle invalid")
        val raw = LateralRaiseMetrics(left, right)
        rawTrace = raw
        val alpha = if (dt == null || filtered == null) 1.0 else 1 - exp(-dt.toDouble() / config.filterMs)
        val metrics = filtered?.let { LateralRaiseMetrics(
            it.leftAngle + alpha * (raw.leftAngle - it.leftAngle),
            it.rightAngle + alpha * (raw.rightAngle - it.rightAngle)) } ?: raw
        filtered = metrics
        smoothTrace = metrics
        val now = pose.timestampMs
        val lowered = raw.leftAngle <= config.loweredAngle && raw.rightAngle <= config.loweredAngle &&
            metrics.leftAngle <= config.loweredAngle && metrics.rightAngle <= config.loweredAngle
        val centerX = (leftShoulder.x + rightShoulder.x) / 2
        val raised = raw.leftAngle >= config.raisedAngle && raw.rightAngle >= config.raisedAngle &&
            metrics.leftAngle >= config.raisedAngle && metrics.rightAngle >= config.raisedAngle &&
            abs(leftElbow.x - centerX) > abs(leftShoulder.x - centerX) + minimum &&
            abs(rightElbow.x - centerX) > abs(rightShoulder.x - centerX) + minimum
        val leavesLowered = metrics.leftAngle >= config.leaveLoweredAngle ||
            metrics.rightAngle >= config.leaveLoweredAngle
        val leavesRaised = metrics.leftAngle <= config.leaveRaisedAngle ||
            metrics.rightAngle <= config.leaveRaisedAngle
        // Form is assessed at the visible high position, not used to reject a completed cycle.
        val leftElbowAngle = PoseGeometry.angle(leftShoulder, leftElbow, point(15), minimum)
        val rightElbowAngle = PoseGeometry.angle(rightShoulder, rightElbow, point(16), minimum)
        if (phase in listOf(LateralRaisePhase.RAISING, LateralRaisePhase.RAISED, LateralRaisePhase.LOWERING) && raised &&
            (abs(metrics.leftAngle - metrics.rightAngle) > config.maximumArmDifference ||
                metrics.leftAngle > config.maximumTargetAngle || metrics.rightAngle > config.maximumTargetAngle ||
                leftElbowAngle == null || rightElbowAngle == null ||
                leftElbowAngle < config.minimumElbowAngle || rightElbowAngle < config.minimumElbowAngle))
            formIssue = true
        var event: RepetitionCompleted? = null
        when (phase) {
            LateralRaisePhase.NOT_READY -> if (confirm("ready", lowered, now, config.readinessMs))
                transition(LateralRaisePhase.LOWERED)
            LateralRaisePhase.LOWERED -> if (leavesLowered) {
                attemptAt = now
                formIssue = false
                countedAt = null
                transition(LateralRaisePhase.RAISING)
            }
            LateralRaisePhase.RAISING -> {
                if (confirm("raised", raised, now, config.raisedMs)) transition(LateralRaisePhase.RAISED)
                else if (confirm("partial", lowered, now, config.loweredMs)) {
                    endedAttempt = UncountedAttempt.INCOMPLETE
                    transition(LateralRaisePhase.LOWERED)
                }
            }
            LateralRaisePhase.RAISED -> if (leavesRaised) transition(LateralRaisePhase.LOWERING)
            LateralRaisePhase.LOWERING -> {
                if (confirm("lowered", lowered, now, config.loweredMs)) {
                    if (now - attemptAt >= config.minimumCycleMs) {
                        repetitions++
                        val assessment = if (formIssue)
                            RepAssessment(RepFormQuality.NEEDS_ATTENTION, "Arm position needs attention")
                        else RepAssessment(RepFormQuality.GOOD)
                        event = RepetitionCompleted(repetitions, now, assessment)
                        countedAt = now
                        countedFeedback = if (formIssue) LateralRaiseFeedback.COUNTED_FORM
                            else LateralRaiseFeedback.COUNTED
                        transition(LateralRaisePhase.LOWERED)
                    } else {
                        endedAttempt = UncountedAttempt.INCOMPLETE
                        invalidateTracking(DiscontinuityReason.TRACKING)
                    }
                } else if (confirm("raisedAgain", raised, now, config.raisedMs))
                    transition(LateralRaisePhase.RAISED)
            }
        }
        val feedback = when (phase) {
            LateralRaisePhase.NOT_READY -> LateralRaiseFeedback.READY
            LateralRaisePhase.LOWERED -> if (countedAt?.let { now - it < 700 } == true)
                countedFeedback else LateralRaiseFeedback.RAISE
            LateralRaisePhase.RAISING -> LateralRaiseFeedback.RAISE
            LateralRaisePhase.RAISED, LateralRaisePhase.LOWERING -> LateralRaiseFeedback.LOWER
        }
        return result(feedback, true, metrics, event)
    }
}
