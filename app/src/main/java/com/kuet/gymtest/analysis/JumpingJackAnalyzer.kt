package com.kuet.gymtest.analysis

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.min

enum class JumpingJackFeedback(override val text: String, override val priority: Int) : AnalysisFeedback {
    NO_POSE("Pose unclear — adjust the camera or lighting", 1),
    FRAMING("Keep both hands and feet in view", 2),
    FACE_CAMERA("Face the camera with your whole body visible", 2),
    UNCERTAIN("Pose unclear — adjust the camera or lighting", 3),
    READY("Stand with feet together and arms down", 4),
    OPEN("Raise both arms and spread both feet", 4),
    CLOSE("Return arms and feet to the start", 4),
    COUNTED("Repetition counted", 5);

    override val key get() = "jumpingjack.$name"
}

enum class JumpingJackPhase { NOT_READY, CLOSED, OPENING, OPEN, CLOSING }

data class JumpingJackMetrics(
    val legSpreadAngle: Double, val leftWristHeight: Double, val rightWristHeight: Double
)

data class JumpingJackAnalysisResult(
    override val key: FrameKey, override val timestampMs: Long,
    override val repetitions: Int, override val repetition: RepetitionCompleted?,
    override val feedback: JumpingJackFeedback, override val trackingValid: Boolean,
    val phase: JumpingJackPhase, val metrics: JumpingJackMetrics?
) : ExerciseAnalysisResult {
    override val presentation get() = AnalysisPresentation(
        "jumpingjack.${phase.name}", phase.name.lowercase().replace('_', ' '),
        phase != JumpingJackPhase.NOT_READY,
        if (trackingValid && phase in listOf(JumpingJackPhase.OPEN, JumpingJackPhase.CLOSING))
            "Open position reached" else null
    )
}

/** Provisional front-view 2D thresholds; tune from measured combined leg-angle traces. */
data class JumpingJackConfig(
    val closedLegAngle: Double = 12.0, val closedLegExitAngle: Double = 16.0,
    val openLegExitAngle: Double = 19.0, val openLegAngle: Double = 24.0,
    val closedWristHeight: Double = 0.0, val closedWristExitHeight: Double = .02,
    val openWristExitHeight: Double = .03, val openWristHeight: Double = .10,
    val readinessMs: Long = 300, val openMs: Long = 60, val closedMs: Long = 100,
    val minimumCycleMs: Long = 400, val maximumGapMs: Long = 250,
    val maximumTransientTrackingMs: Long = 300, val maximumTransientTrackingFrames: Int = 2,
    val filterMs: Double = 50.0, val visibility: Float = .65f,
    val presence: Float = .5f, val edgeMargin: Float = .02f,
    val minimumSegment: Double = .025
) {
    init {
        require(listOf(closedLegAngle, closedLegExitAngle, openLegExitAngle, openLegAngle)
            .all { it.isFinite() && it in 0.0..180.0 })
        require(closedLegAngle < closedLegExitAngle && closedLegExitAngle < openLegExitAngle &&
            openLegExitAngle < openLegAngle)
        require(listOf(closedWristHeight, closedWristExitHeight, openWristExitHeight, openWristHeight)
            .all { it.isFinite() })
        require(closedWristHeight < closedWristExitHeight &&
            closedWristExitHeight < openWristExitHeight && openWristExitHeight < openWristHeight)
        require(listOf(readinessMs, openMs, closedMs, minimumCycleMs, maximumGapMs).all { it > 0 })
        require(maximumTransientTrackingMs > 0 && maximumTransientTrackingFrames > 0)
        require(filterMs.isFinite() && filterMs > 0 && minimumSegment.isFinite() && minimumSegment > 0)
        require(listOf(visibility, presence).all { it.isFinite() && it in 0f..1f })
        require(edgeMargin.isFinite() && edgeMargin in 0f.. .25f)
    }
}

/** One worker owns this state. Sustained loss cancels the attempt, preserving accepted event ordinals. */
class JumpingJackAnalyzer(
    private val config: JumpingJackConfig = JumpingJackConfig(),
    private val diagnostic: ((String) -> Unit)? = null
) : ExerciseAnalyzer {
    private var phase = JumpingJackPhase.NOT_READY
    private var repetitions = 0
    private var previous: PoseFrame? = null
    private var filtered: JumpingJackMetrics? = null
    private var candidate: String? = null
    private var candidateAt = 0L
    private var candidateSamples = 0
    private var attemptAt = 0L
    private var countedAt: Long? = null
    private var lastValidAt: Long? = null
    private var transientTrackingFrames = 0

    override fun reset() {
        repetitions = 0
        previous = null
        invalidateTracking(DiscontinuityReason.EPOCH)
    }

    override fun invalidateTracking(reason: DiscontinuityReason) {
        diagnostic?.invoke("invalidate reason=$reason phase=$phase previous=${previous?.timestampMs}")
        phase = JumpingJackPhase.NOT_READY
        filtered = null
        candidate = null
        candidateSamples = 0
        attemptAt = 0
        countedAt = null
        lastValidAt = null
        transientTrackingFrames = 0
        // Keep previous: invalidation must not reopen the timestamp/sequence acceptance gate.
    }

    private fun transition(next: JumpingJackPhase) {
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

    override fun processFrame(pose: PoseFrame): JumpingJackAnalysisResult {
        val phaseBefore = phase
        var failure: String? = null
        var rawTrace: JumpingJackMetrics? = null
        var smoothTrace: JumpingJackMetrics? = null
        var elbowTrace = "unmeasured"
        var isClosedTrace: Boolean? = null
        var isOpenTrace: Boolean? = null
        var leavesClosedTrace: Boolean? = null
        var leavesOpenTrace: Boolean? = null
        var openBlockers = "unmeasured"
        var closedBlockers = "unmeasured"
        var frameGapMs: Long? = null
        fun confidences(): String = listOf(11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28)
            .joinToString(",") { index ->
                val point = pose.landmarks.getOrNull(index)
                "$index:${point?.visibility ?: "-"}/${point?.presence ?: "-"}"
            }
        fun result(message: JumpingJackFeedback, valid: Boolean = false,
                   metrics: JumpingJackMetrics? = null, event: RepetitionCompleted? = null): JumpingJackAnalysisResult {
            diagnostic?.invoke("frame time=${pose.timestampMs} seq=${pose.key.sequence} from=$phaseBefore to=$phase " +
                "valid=$valid failure=${failure ?: "none"} raw=$rawTrace smooth=$smoothTrace " +
                "elbows=$elbowTrace " +
                "closed=$isClosedTrace open=$isOpenTrace leavesClosed=$leavesClosedTrace leavesOpen=$leavesOpenTrace " +
                "openBlockers=$openBlockers closedBlockers=$closedBlockers " +
                "candidate=${candidate ?: "none"} samples=$candidateSamples " +
                "candidateMs=${if (candidate == null) 0 else pose.timestampMs - candidateAt} " +
                "dt=$frameGapMs confidence=${confidences()} event=${event?.ordinal}")
            return JumpingJackAnalysisResult(pose.key, pose.timestampMs, repetitions, event, message, valid, phase, metrics)
        }
        fun invalid(message: JumpingJackFeedback, reason: String): JumpingJackAnalysisResult {
            failure = reason
            invalidateTracking(DiscontinuityReason.TRACKING)
            return result(message)
        }
        fun transient(reason: String, message: JumpingJackFeedback = JumpingJackFeedback.UNCERTAIN): JumpingJackAnalysisResult {
            failure = reason
            val lastValid = lastValidAt
            if (phase == JumpingJackPhase.NOT_READY || lastValid == null ||
                transientTrackingFrames >= config.maximumTransientTrackingFrames ||
                pose.timestampMs - lastValid > config.maximumTransientTrackingMs)
                return invalid(message, reason)
            transientTrackingFrames++
            candidate = null
            candidateSamples = 0
            diagnostic?.invoke("hold tracking phase=$phase reason=$reason frames=$transientTrackingFrames " +
                "sinceValidMs=${pose.timestampMs - lastValid}")
            return result(message)
        }
        val old = previous
        if (pose.timestampMs < 0 || pose.key.sequence < 0) return invalid(JumpingJackFeedback.UNCERTAIN, "invalid timestamp/sequence")
        if (old != null && (old.key.sessionId != pose.key.sessionId || old.key.cameraEpoch != pose.key.cameraEpoch ||
                old.key.analysisEpoch != pose.key.analysisEpoch || pose.timestampMs <= old.timestampMs ||
                pose.key.sequence <= old.key.sequence)) return invalid(JumpingJackFeedback.UNCERTAIN, "key or timestamp discontinuity")
        previous = pose
        val dt = old?.let { pose.timestampMs - it.timestampMs }
        frameGapMs = dt
        if (dt != null && dt > config.maximumGapMs) {
            failure = "gap ${dt}ms > ${config.maximumGapMs}ms"
            invalidateTracking(DiscontinuityReason.GAP)
            return result(JumpingJackFeedback.UNCERTAIN)
        }
        if (transientTrackingFrames > 0 && lastValidAt?.let {
                pose.timestampMs - it > config.maximumTransientTrackingMs
            } == true) invalidateTracking(DiscontinuityReason.TRACKING)
        if (pose.imageWidth <= 0 || pose.imageHeight <= 0) return invalid(JumpingJackFeedback.UNCERTAIN, "invalid image size")
        if (pose.landmarks.isEmpty()) return transient("no pose landmarks", JumpingJackFeedback.NO_POSE)
        fun confident(index: Int) = PoseGeometry.confident(pose.landmarks.getOrNull(index), config.visibility, config.presence)
        fun inside(index: Int) = PoseGeometry.inside(pose.landmarks[index], config.edgeMargin)
        val required = listOf(11, 12, 15, 16, 23, 24, 27, 28)
        required.firstOrNull { !confident(it) }?.let { return transient("landmark $it low confidence") }
        required.firstOrNull { !inside(it) }?.let { return transient("landmark $it outside frame", JumpingJackFeedback.FRAMING) }
        fun point(index: Int) = PoseGeometry.point(pose.landmarks[index], pose.imageWidth, pose.imageHeight)!!
        val leftHip = point(23)
        val rightHip = point(24)
        val leftAnkle = point(27)
        val rightAnkle = point(28)
        // Both vectors point upward. This is the angle between the extended hip-to-ankle lines,
        // not two angles against a torso axis and not an ankle-distance ratio.
        val leftVector = Point2(leftHip.x - leftAnkle.x, leftHip.y - leftAnkle.y)
        val rightVector = Point2(rightHip.x - rightAnkle.x, rightHip.y - rightAnkle.y)
        val minLength = config.minimumSegment * min(pose.imageWidth, pose.imageHeight)
        if (PoseGeometry.distance(leftHip, leftAnkle) < minLength ||
            PoseGeometry.distance(rightHip, rightAnkle) < minLength)
            return transient("hip-to-ankle line too short")
        val cross = leftVector.x * rightVector.y - leftVector.y * rightVector.x
        val dot = leftVector.x * rightVector.x + leftVector.y * rightVector.y
        val legSpreadAngle = Math.toDegrees(atan2(abs(cross), dot))
        if (!legSpreadAngle.isFinite()) return transient("leg spread angle invalid")
        val torso = (PoseGeometry.distance(point(11), leftHip) +
            PoseGeometry.distance(point(12), rightHip)) / 2
        if (torso < minLength) return transient("torso too short")
        val raw = JumpingJackMetrics(legSpreadAngle,
            (point(11).y - point(15).y) / torso, (point(12).y - point(16).y) / torso)
        rawTrace = raw
        val measurementDt = lastValidAt?.let { pose.timestampMs - it }
        val prior = filtered
        val alpha = if (measurementDt == null) 1.0 else 1 - exp(-measurementDt.toDouble() / config.filterMs)
        fun smooth(value: Double, before: Double?) = before?.let { it + alpha * (value - it) } ?: value
        val metrics = JumpingJackMetrics(smooth(raw.legSpreadAngle, prior?.legSpreadAngle),
            smooth(raw.leftWristHeight, prior?.leftWristHeight),
            smooth(raw.rightWristHeight, prior?.rightWristHeight))
        smoothTrace = metrics
        filtered = metrics
        lastValidAt = pose.timestampMs
        transientTrackingFrames = 0
        // Elbows are diagnostic only. Their landmarks must not reset or gate a visible cycle.
        elbowTrace = listOf(Triple(11, 13, 15), Triple(12, 14, 16)).joinToString(prefix = "[", postfix = "]") { (s, e, w) ->
            if (confident(e)) PoseGeometry.angle(point(s), point(e), point(w), minLength)?.toString() ?: "-"
            else "-"
        }
        fun closed(m: JumpingJackMetrics) = m.legSpreadAngle <= config.closedLegAngle &&
            m.leftWristHeight <= config.closedWristHeight && m.rightWristHeight <= config.closedWristHeight
        fun open(m: JumpingJackMetrics) = m.legSpreadAngle >= config.openLegAngle &&
            m.leftWristHeight >= config.openWristHeight && m.rightWristHeight >= config.openWristHeight
        val isClosed = closed(metrics)
        val isOpen = open(metrics)
        val leavesClosed = metrics.legSpreadAngle >= config.closedLegExitAngle ||
            metrics.leftWristHeight >= config.closedWristExitHeight ||
            metrics.rightWristHeight >= config.closedWristExitHeight
        val leavesOpen = metrics.legSpreadAngle <= config.openLegExitAngle ||
            metrics.leftWristHeight <= config.openWristExitHeight ||
            metrics.rightWristHeight <= config.openWristExitHeight
        openBlockers = listOfNotNull(
            "legSpread".takeIf { metrics.legSpreadAngle < config.openLegAngle },
            "leftHandUp".takeIf { metrics.leftWristHeight < config.openWristHeight },
            "rightHandUp".takeIf { metrics.rightWristHeight < config.openWristHeight }
        ).joinToString(",").ifEmpty { "none" }
        closedBlockers = listOfNotNull(
            "legSpread".takeIf { metrics.legSpreadAngle > config.closedLegAngle },
            "leftHandDown".takeIf { metrics.leftWristHeight > config.closedWristHeight },
            "rightHandDown".takeIf { metrics.rightWristHeight > config.closedWristHeight }
        ).joinToString(",").ifEmpty { "none" }
        isClosedTrace = isClosed
        isOpenTrace = isOpen
        leavesClosedTrace = leavesClosed
        leavesOpenTrace = leavesOpen
        val now = pose.timestampMs
        var event: RepetitionCompleted? = null
        when (phase) {
            JumpingJackPhase.NOT_READY -> if (confirm("ready", isClosed, now, config.readinessMs)) transition(JumpingJackPhase.CLOSED)
            JumpingJackPhase.CLOSED -> if (leavesClosed) {
                attemptAt = now
                countedAt = null
                transition(JumpingJackPhase.OPENING)
            }
            JumpingJackPhase.OPENING -> {
                if (confirm("open", isOpen, now, config.openMs)) transition(JumpingJackPhase.OPEN)
                else if (confirm("partial", isClosed, now, config.closedMs)) transition(JumpingJackPhase.CLOSED)
            }
            JumpingJackPhase.OPEN -> if (leavesOpen) transition(JumpingJackPhase.CLOSING)
            JumpingJackPhase.CLOSING -> {
                if (confirm("closed", isClosed, now, config.closedMs)) {
                    if (now - attemptAt >= config.minimumCycleMs) {
                        repetitions++
                        event = RepetitionCompleted(repetitions, now)
                        countedAt = now
                        transition(JumpingJackPhase.CLOSED)
                    } else invalidateTracking(DiscontinuityReason.TRACKING)
                } else if (confirm("reopen", isOpen, now, config.openMs)) transition(JumpingJackPhase.OPEN)
            }
        }
        val message = when (phase) {
            JumpingJackPhase.NOT_READY -> JumpingJackFeedback.READY
            JumpingJackPhase.CLOSED -> if (countedAt?.let { now - it < 700 } == true) JumpingJackFeedback.COUNTED else JumpingJackFeedback.OPEN
            JumpingJackPhase.OPENING -> JumpingJackFeedback.OPEN
            JumpingJackPhase.OPEN, JumpingJackPhase.CLOSING -> JumpingJackFeedback.CLOSE
        }
        return result(message, true, metrics, event)
    }
}
