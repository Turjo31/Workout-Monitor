package com.kuet.gymtest.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

enum class ShoulderPressPhase { NOT_READY, RACKED, PRESSING, OVERHEAD, LOWERING }

enum class ShoulderPressFeedback(override val text: String, override val priority: Int) : AnalysisFeedback {
    NO_POSE("Pose unclear - face the camera", 1),
    FRAMING("Keep both arms, hands and hips in view", 2),
    FRONT_VIEW("Face the camera with both arms visible", 2),
    UNCERTAIN("Arm tracking unclear - adjust the camera", 3),
    READY("Hold both hands near shoulder height", 4),
    PRESS("Press both hands overhead", 4),
    LOWER("Lower both hands to shoulder height", 4),
    COUNTED("Repetition counted", 5),
    COUNTED_FORM("Repetition counted - arm position needs attention", 5);

    override val key get() = "shoulderpress.$name"
}

data class ShoulderPressMetrics(val leftHeight: Double, val rightHeight: Double,
                                val leftElbowAngle: Double, val rightElbowAngle: Double)

data class ShoulderPressAnalysisResult(
    override val key: FrameKey, override val timestampMs: Long,
    override val repetitions: Int, override val repetition: RepetitionCompleted?,
    override val feedback: ShoulderPressFeedback, override val trackingValid: Boolean,
    val phase: ShoulderPressPhase, val metrics: ShoulderPressMetrics?,
    override val uncountedAttempt: UncountedAttempt? = null
) : ExerciseAnalysisResult {
    override val attemptInProgress get() = phase in listOf(
        ShoulderPressPhase.PRESSING, ShoulderPressPhase.OVERHEAD, ShoulderPressPhase.LOWERING)
    override val presentation get() = AnalysisPresentation(
        "shoulderpress.${phase.name}", phase.name.lowercase().replace('_', ' '),
        phase != ShoulderPressPhase.NOT_READY, if (trackingValid) "Both arms" else null)
}

/** Front-view 2D thresholds, normalized to visible torso length. Not a weight or safety assessment. */
data class ShoulderPressConfig(
    val rackMinimum: Double = -.22, val rackMaximum: Double = .34,
    val leaveRackHeight: Double = .36, val overheadHeight: Double = .82,
    val leaveOverheadHeight: Double = .43, val minimumOverheadElbowAngle: Double = 140.0,
    val maximumArmHeightDifference: Double = .28, val maximumWristOffset: Double = .55,
    val minimumElbowBelowShoulder: Double = .04,
    val readinessMs: Long = 90, val overheadMs: Long = 40, val rackMs: Long = 45,
    val minimumCycleMs: Long = 250, val maximumGapMs: Long = 300,
    val filterMs: Double = 45.0, val minimumSegment: Double = .025,
    val minimumFrontWidth: Double = .45, val maximumShoulderSlope: Double = .35,
    val visibility: Float = .65f, val presence: Float = .5f, val edgeMargin: Float = .02f
) {
    init {
        require(rackMinimum < rackMaximum && rackMaximum < leaveRackHeight &&
            leaveOverheadHeight < overheadHeight && leaveRackHeight < overheadHeight)
        require(listOf(rackMinimum, rackMaximum, leaveRackHeight, overheadHeight,
            leaveOverheadHeight, minimumOverheadElbowAngle, maximumArmHeightDifference,
            maximumWristOffset, minimumElbowBelowShoulder, filterMs, minimumSegment,
            minimumFrontWidth, maximumShoulderSlope).all { it.isFinite() })
        require(listOf(readinessMs, overheadMs, rackMs, minimumCycleMs, maximumGapMs).all { it > 0 })
        require(listOf(visibility, presence, edgeMargin).all { it.isFinite() && it in 0f..1f })
    }
}

/** One rep is a tracked bilateral rack -> overhead -> rack cycle. */
class DumbbellShoulderPressAnalyzer(
    private val config: ShoulderPressConfig = ShoulderPressConfig(),
    private val diagnostic: ((String) -> Unit)? = null
) : ExerciseAnalyzer {
    private var phase = ShoulderPressPhase.NOT_READY
    private var repetitions = 0
    private var previous: PoseFrame? = null
    private var filteredHeights: Pair<Double, Double>? = null
    private var candidate: String? = null
    private var candidateAt = 0L
    private var candidateSamples = 0
    private var attemptAt = 0L
    private var formIssue = false
    private var countedAt: Long? = null
    private var countedFeedback = ShoulderPressFeedback.COUNTED

    override fun reset() {
        repetitions = 0
        previous = null
        invalidateTracking(DiscontinuityReason.EPOCH)
    }

    override fun invalidateTracking(reason: DiscontinuityReason) {
        diagnostic?.invoke("invalidate reason=$reason phase=$phase previous=${previous?.timestampMs}")
        phase = ShoulderPressPhase.NOT_READY
        filteredHeights = null
        candidate = null
        candidateSamples = 0
        attemptAt = 0
        formIssue = false
        countedAt = null
        countedFeedback = ShoulderPressFeedback.COUNTED
    }

    private fun transition(next: ShoulderPressPhase) {
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

    override fun processFrame(pose: PoseFrame): ShoulderPressAnalysisResult {
        val before = phase
        var failure: String? = null
        var endedAttempt: UncountedAttempt? = null
        var metrics: ShoulderPressMetrics? = null
        var dtTrace: Long? = null
        fun result(feedback: ShoulderPressFeedback, valid: Boolean = false,
                   event: RepetitionCompleted? = null): ShoulderPressAnalysisResult {
            diagnostic?.invoke("frame time=${pose.timestampMs} seq=${pose.key.sequence} from=$before to=$phase " +
                "valid=$valid failure=${failure ?: "none"} metrics=$metrics formIssue=$formIssue " +
                "candidate=${candidate ?: "none"} samples=$candidateSamples dt=$dtTrace " +
                "feedback=$feedback event=${event?.ordinal} uncounted=$endedAttempt")
            return ShoulderPressAnalysisResult(pose.key, pose.timestampMs, repetitions, event,
                feedback, valid, phase, metrics, endedAttempt)
        }
        fun invalid(feedback: ShoulderPressFeedback, reason: String): ShoulderPressAnalysisResult {
            failure = reason
            if (phase in listOf(ShoulderPressPhase.PRESSING, ShoulderPressPhase.OVERHEAD,
                    ShoulderPressPhase.LOWERING)) endedAttempt = UncountedAttempt.TRACKING_INTERRUPTED
            invalidateTracking(DiscontinuityReason.TRACKING)
            return result(feedback)
        }
        val old = previous
        if (pose.timestampMs < 0 || pose.key.sequence < 0 || pose.imageWidth <= 0 || pose.imageHeight <= 0)
            return invalid(ShoulderPressFeedback.UNCERTAIN, "invalid frame metadata")
        if (old != null && (old.key.sessionId != pose.key.sessionId || old.key.cameraEpoch != pose.key.cameraEpoch ||
                old.key.analysisEpoch != pose.key.analysisEpoch || pose.key.sequence <= old.key.sequence ||
                pose.timestampMs <= old.timestampMs))
            return invalid(ShoulderPressFeedback.UNCERTAIN, "key or timestamp discontinuity")
        previous = pose
        val dt = old?.let { pose.timestampMs - it.timestampMs }
        dtTrace = dt
        if (dt != null && dt > config.maximumGapMs) return invalid(ShoulderPressFeedback.UNCERTAIN, "frame gap")
        if (pose.landmarks.isEmpty()) return invalid(ShoulderPressFeedback.NO_POSE, "no pose")
        val required = listOf(11, 12, 13, 14, 15, 16, 23, 24)
        if (!required.all { PoseGeometry.confident(pose.landmarks.getOrNull(it), config.visibility, config.presence) })
            return invalid(ShoulderPressFeedback.UNCERTAIN, "arm landmark low confidence")
        if (!required.all { PoseGeometry.inside(pose.landmarks[it], config.edgeMargin) })
            return invalid(ShoulderPressFeedback.FRAMING, "arm landmark outside frame")
        fun point(i: Int) = PoseGeometry.point(pose.landmarks[i], pose.imageWidth, pose.imageHeight)!!
        val ls = point(11); val rs = point(12)
        val le = point(13); val re = point(14)
        val lw = point(15); val rw = point(16)
        val lh = point(23); val rh = point(24)
        val minimum = config.minimumSegment * min(pose.imageWidth, pose.imageHeight)
        val torso = (PoseGeometry.distance(ls, lh) + PoseGeometry.distance(rs, rh)) / 2
        val shoulderWidth = PoseGeometry.distance(ls, rs)
        if (torso < minimum || shoulderWidth < minimum ||
            shoulderWidth / torso < config.minimumFrontWidth ||
            abs(ls.y - rs.y) / shoulderWidth > config.maximumShoulderSlope)
            return invalid(ShoulderPressFeedback.FRONT_VIEW, "front view or torso geometry")
        val leftElbowAngle = PoseGeometry.angle(ls, le, lw, minimum)
            ?: return invalid(ShoulderPressFeedback.UNCERTAIN, "left elbow angle invalid")
        val rightElbowAngle = PoseGeometry.angle(rs, re, rw, minimum)
            ?: return invalid(ShoulderPressFeedback.UNCERTAIN, "right elbow angle invalid")
        val rawLeft = (ls.y - lw.y) / torso
        val rawRight = (rs.y - rw.y) / torso
        val alpha = if (dt == null || filteredHeights == null) 1.0 else 1 - exp(-dt.toDouble() / config.filterMs)
        val heights = filteredHeights?.let { Pair(it.first + alpha * (rawLeft - it.first),
            it.second + alpha * (rawRight - it.second)) } ?: Pair(rawLeft, rawRight)
        filteredHeights = heights
        metrics = ShoulderPressMetrics(heights.first, heights.second, leftElbowAngle, rightElbowAngle)
        val rack = listOf(rawLeft, rawRight, heights.first, heights.second).all {
            it in config.rackMinimum..config.rackMaximum } &&
            (le.y - ls.y) / torso >= config.minimumElbowBelowShoulder &&
            (re.y - rs.y) / torso >= config.minimumElbowBelowShoulder
        val overhead = rawLeft >= config.overheadHeight && rawRight >= config.overheadHeight &&
            heights.first >= config.overheadHeight && heights.second >= config.overheadHeight &&
            abs(lw.x - ls.x) / torso <= config.maximumWristOffset &&
            abs(rw.x - rs.x) / torso <= config.maximumWristOffset
        val leavesRack = heights.first >= config.leaveRackHeight || heights.second >= config.leaveRackHeight
        val leavesOverhead = heights.first <= config.leaveOverheadHeight ||
            heights.second <= config.leaveOverheadHeight
        val now = pose.timestampMs
        if (phase in listOf(ShoulderPressPhase.PRESSING, ShoulderPressPhase.OVERHEAD,
                ShoulderPressPhase.LOWERING) && overhead &&
            (abs(heights.first - heights.second) > config.maximumArmHeightDifference ||
                leftElbowAngle < config.minimumOverheadElbowAngle ||
                rightElbowAngle < config.minimumOverheadElbowAngle)) formIssue = true
        var event: RepetitionCompleted? = null
        when (phase) {
            ShoulderPressPhase.NOT_READY -> if (confirm("ready", rack, now, config.readinessMs))
                transition(ShoulderPressPhase.RACKED)
            ShoulderPressPhase.RACKED -> if (leavesRack) {
                attemptAt = now
                formIssue = false
                countedAt = null
                transition(ShoulderPressPhase.PRESSING)
            }
            ShoulderPressPhase.PRESSING -> {
                if (confirm("overhead", overhead, now, config.overheadMs)) transition(ShoulderPressPhase.OVERHEAD)
                else if (confirm("partial", rack, now, config.rackMs)) {
                    endedAttempt = UncountedAttempt.INCOMPLETE
                    transition(ShoulderPressPhase.RACKED)
                }
            }
            ShoulderPressPhase.OVERHEAD -> if (leavesOverhead) transition(ShoulderPressPhase.LOWERING)
            ShoulderPressPhase.LOWERING -> {
                if (confirm("rack", rack, now, config.rackMs)) {
                    if (now - attemptAt >= config.minimumCycleMs) {
                        repetitions++
                        val assessment = if (formIssue)
                            RepAssessment(RepFormQuality.NEEDS_ATTENTION, "Arm position needs attention")
                        else RepAssessment(RepFormQuality.GOOD)
                        event = RepetitionCompleted(repetitions, now, assessment)
                        countedAt = now
                        countedFeedback = if (formIssue) ShoulderPressFeedback.COUNTED_FORM
                            else ShoulderPressFeedback.COUNTED
                        transition(ShoulderPressPhase.RACKED)
                    } else {
                        endedAttempt = UncountedAttempt.INCOMPLETE
                        invalidateTracking(DiscontinuityReason.TRACKING)
                    }
                } else if (confirm("overheadAgain", overhead, now, config.overheadMs))
                    transition(ShoulderPressPhase.OVERHEAD)
            }
        }
        val feedback = when (phase) {
            ShoulderPressPhase.NOT_READY -> ShoulderPressFeedback.READY
            ShoulderPressPhase.RACKED -> if (countedAt?.let { now - it < 700 } == true)
                countedFeedback else ShoulderPressFeedback.PRESS
            ShoulderPressPhase.PRESSING -> ShoulderPressFeedback.PRESS
            ShoulderPressPhase.OVERHEAD, ShoulderPressPhase.LOWERING -> ShoulderPressFeedback.LOWER
        }
        return result(feedback, true, event)
    }
}
