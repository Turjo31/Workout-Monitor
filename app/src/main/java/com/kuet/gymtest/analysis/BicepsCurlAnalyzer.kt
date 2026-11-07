package com.kuet.gymtest.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

enum class BicepsCurlFeedback(override val text: String, override val priority: Int) : AnalysisFeedback {
    NO_POSE("Pose unclear — keep your working arm in view", 1),
    FRAMING("Keep your shoulder, elbow, and hand in view", 2),
    POSITION("Stand sideways with your upper arm close to your body", 2),
    UNCERTAIN("Arm tracking unclear — adjust the camera", 3),
    READY("Start with your working arm lowered", 4),
    CURL("Curl the weight toward your shoulder", 4),
    LOWER("Lower the weight under control", 4),
    COUNTED("Repetition counted", 5),
    COUNTED_ELBOW_MOVED("Repetition counted — elbow moved", 5);

    override val key get() = "bicepscurl.$name"
}

enum class BicepsCurlPhase { NOT_READY, EXTENDED, CURLING, CONTRACTED, LOWERING }

data class BicepsCurlAnalysisResult(
    override val key: FrameKey, override val timestampMs: Long,
    override val repetitions: Int, override val repetition: RepetitionCompleted?,
    override val feedback: BicepsCurlFeedback, override val trackingValid: Boolean,
    val phase: BicepsCurlPhase, val side: BodySide?, val elbowAngle: Double?,
    override val uncountedAttempt: UncountedAttempt? = null
) : ExerciseAnalysisResult {
    override val attemptInProgress get() = phase in listOf(
        BicepsCurlPhase.CURLING, BicepsCurlPhase.CONTRACTED, BicepsCurlPhase.LOWERING)
    override val presentation get() = AnalysisPresentation(
        "bicepscurl.${phase.name}", phase.name.lowercase().replace('_', ' '),
        phase != BicepsCurlPhase.NOT_READY,
        if (trackingValid) side?.name?.lowercase()?.replaceFirstChar { it.uppercase() }?.let { "$it arm" } else null
    )
}

/** Provisional 2D side-view limits, not calibrated exercise or medical guidance. */
data class BicepsCurlConfig(
    val extended: Double = 155.0, val leaveExtended: Double = 140.0,
    val contracted: Double = 75.0, val leaveContracted: Double = 95.0,
    val readinessMs: Long = 100, val phaseMs: Long = 35, val finishMs: Long = 45,
    val minimumCycleMs: Long = 250, val maximumGapMs: Long = 300,
    val filterMs: Double = 45.0, val minimumSegment: Double = .025,
    val maximumSegmentChange: Double = .35, val formElbowTravel: Double = .45,
    val maximumTorsoTilt: Double = .70,
    val visibility: Float = .65f, val presence: Float = .5f, val edgeMargin: Float = .02f
) {
    init {
        require(listOf(extended, leaveExtended, contracted, leaveContracted)
            .all { it.isFinite() && it in 0.0..180.0 })
        require(contracted < leaveContracted && leaveContracted < leaveExtended && leaveExtended < extended)
        require(listOf(readinessMs, phaseMs, finishMs, minimumCycleMs, maximumGapMs).all { it > 0 })
        require(listOf(filterMs, minimumSegment, formElbowTravel,
            maximumTorsoTilt).all { it.isFinite() && it > 0 })
        require(maximumSegmentChange.isFinite() && maximumSegmentChange in 0.0..1.0)
        require(listOf(visibility, presence, edgeMargin).all { it.isFinite() && it in 0f..1f })
    }
}

/** Counts one visible arm only: extended -> contracted -> extended. No weight detection. */
class BicepsCurlAnalyzer(
    private val config: BicepsCurlConfig = BicepsCurlConfig(),
    private val diagnostic: ((String) -> Unit)? = null
) : ExerciseAnalyzer {
    private var phase = BicepsCurlPhase.NOT_READY
    private var repetitions = 0
    private var side: BodySide? = null
    private var previous: PoseFrame? = null
    private var smoothAngle: Double? = null
    private var segmentLengths: Pair<Double, Double>? = null
    private var startElbow: Point2? = null
    private var startShoulder: Point2? = null
    private var startUpperLength: Double? = null
    private var elbowMovedDuringRep = false
    private var candidate: String? = null
    private var candidateAt = 0L
    private var candidateSamples = 0
    private var attemptAt = 0L
    private var countedAt: Long? = null
    private var countedFeedback = BicepsCurlFeedback.COUNTED

    override fun reset() {
        repetitions = 0
        previous = null
        invalidateTracking(DiscontinuityReason.EPOCH)
    }

    override fun invalidateTracking(reason: DiscontinuityReason) {
        diagnostic?.invoke("invalidate reason=$reason phase=$phase side=$side previous=${previous?.timestampMs}")
        phase = BicepsCurlPhase.NOT_READY
        side = null
        smoothAngle = null
        segmentLengths = null
        startElbow = null
        startShoulder = null
        startUpperLength = null
        elbowMovedDuringRep = false
        candidate = null
        candidateSamples = 0
        attemptAt = 0
        countedAt = null
        countedFeedback = BicepsCurlFeedback.COUNTED
        // Preserve the last key/timestamp: a stale frame must not reopen a cancelled attempt.
    }

    private fun transition(next: BicepsCurlPhase) {
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

    override fun processFrame(pose: PoseFrame): BicepsCurlAnalysisResult {
        val phaseBefore = phase
        var failure: String? = null
        var dtTrace: Long? = null
        var chosenSide: BodySide? = null
        var rawTrace: Double? = null
        var smoothTrace: Double? = null
        var upperTrace: Double? = null
        var forearmTrace: Double? = null
        var elbowTravelTrace: Double? = null
        var shoulderTravelTrace: Double? = null
        var relativeElbowTravelTrace: Double? = null
        var extendedTrace: Boolean? = null
        var contractedTrace: Boolean? = null
        var endedAttempt: UncountedAttempt? = null
        fun result(feedback: BicepsCurlFeedback, valid: Boolean = false,
                   angle: Double? = null, event: RepetitionCompleted? = null): BicepsCurlAnalysisResult {
            diagnostic?.invoke("frame time=${pose.timestampMs} seq=${pose.key.sequence} " +
                "from=$phaseBefore to=$phase valid=$valid failure=${failure ?: "none"} " +
                "side=${chosenSide ?: side} rawElbow=$rawTrace smoothElbow=$smoothTrace " +
                "upper=$upperTrace forearm=$forearmTrace elbowTravel=$elbowTravelTrace " +
                "shoulderTravel=$shoulderTravelTrace relativeElbowTravel=$relativeElbowTravelTrace " +
                "formIssue=$elbowMovedDuringRep extended=$extendedTrace contracted=$contractedTrace " +
                "candidate=${candidate ?: "none"} samples=$candidateSamples " +
                "candidateMs=${if (candidate == null) 0 else pose.timestampMs - candidateAt} " +
                "dt=$dtTrace feedback=$feedback event=${event?.ordinal} uncounted=$endedAttempt")
            return BicepsCurlAnalysisResult(pose.key, pose.timestampMs, repetitions, event,
                feedback, valid, phase, side, angle, endedAttempt)
        }
        fun invalid(feedback: BicepsCurlFeedback, reason: String): BicepsCurlAnalysisResult {
            failure = reason
            if (phase in listOf(BicepsCurlPhase.CURLING, BicepsCurlPhase.CONTRACTED, BicepsCurlPhase.LOWERING))
                endedAttempt = UncountedAttempt.TRACKING_INTERRUPTED
            invalidateTracking(DiscontinuityReason.TRACKING)
            return result(feedback)
        }
        val old = previous
        if (pose.timestampMs < 0 || pose.key.sequence < 0 || pose.imageWidth <= 0 || pose.imageHeight <= 0)
            return invalid(BicepsCurlFeedback.UNCERTAIN, "invalid timestamp, sequence or image size")
        if (old != null && (old.key.sessionId != pose.key.sessionId ||
                old.key.cameraEpoch != pose.key.cameraEpoch || old.key.analysisEpoch != pose.key.analysisEpoch ||
                pose.key.sequence <= old.key.sequence || pose.timestampMs <= old.timestampMs))
            return invalid(BicepsCurlFeedback.UNCERTAIN, "key or timestamp discontinuity")
        previous = pose
        val dt = old?.let { pose.timestampMs - it.timestampMs }
        dtTrace = dt
        if (dt != null && dt > config.maximumGapMs) return invalid(BicepsCurlFeedback.UNCERTAIN, "frame gap")
        if (pose.landmarks.isEmpty()) return invalid(BicepsCurlFeedback.NO_POSE, "no pose landmarks")
        fun elbow(s: BodySide) = if (s == BodySide.LEFT) 13 else 14
        fun wrist(s: BodySide) = if (s == BodySide.LEFT) 15 else 16
        fun indices(s: BodySide) = listOf(s.shoulder, elbow(s), wrist(s), s.hip)
        fun confident(i: Int) = PoseGeometry.confident(pose.landmarks.getOrNull(i), config.visibility, config.presence)
        val chosen = side ?: BodySide.entries.filter { s -> indices(s).all(::confident) }
            .maxByOrNull { s -> indices(s).minOf { pose.landmarks[it].visibility!! } }
            ?: return invalid(BicepsCurlFeedback.UNCERTAIN, "no confident arm chain")
        chosenSide = chosen
        if (!indices(chosen).all(::confident)) return invalid(BicepsCurlFeedback.UNCERTAIN, "locked arm low confidence")
        if (!indices(chosen).all { PoseGeometry.inside(pose.landmarks[it], config.edgeMargin) })
            return invalid(BicepsCurlFeedback.FRAMING, "arm landmark outside frame")
        fun point(i: Int) = PoseGeometry.point(pose.landmarks[i], pose.imageWidth, pose.imageHeight)!!
        val shoulder = point(chosen.shoulder)
        val elbow = point(elbow(chosen))
        val wrist = point(wrist(chosen))
        val hip = point(chosen.hip)
        val minimum = config.minimumSegment * min(pose.imageWidth, pose.imageHeight)
        val raw = PoseGeometry.angle(shoulder, elbow, wrist, minimum)
            ?: return invalid(BicepsCurlFeedback.UNCERTAIN, "elbow geometry invalid")
        rawTrace = raw
        val upper = PoseGeometry.distance(shoulder, elbow)
        val forearm = PoseGeometry.distance(elbow, wrist)
        upperTrace = upper
        forearmTrace = forearm
        val torso = PoseGeometry.distance(shoulder, hip)
        if (torso < minimum || abs(shoulder.x - hip.x) > config.maximumTorsoTilt * abs(shoulder.y - hip.y) ||
            elbow.y <= shoulder.y || elbow.y >= hip.y + upper ||
            PoseGeometry.distance(elbow, hip) > torso + upper)
            return invalid(BicepsCurlFeedback.POSITION, "torso or upper-arm position")
        val oldLengths = segmentLengths
        if (oldLengths != null && (abs(upper - oldLengths.first) / oldLengths.first > config.maximumSegmentChange ||
                abs(forearm - oldLengths.second) / oldLengths.second > config.maximumSegmentChange))
            return invalid(BicepsCurlFeedback.UNCERTAIN, "arm segment length changed above limit")
        val anchorElbow = startElbow
        val anchorShoulder = startShoulder
        val elbowTravel = anchorElbow?.let { PoseGeometry.distance(elbow, it) }
        val shoulderTravel = anchorShoulder?.let { PoseGeometry.distance(shoulder, it) }
        elbowTravelTrace = elbowTravel
        shoulderTravelTrace = shoulderTravel
        // Translation of the whole body is not elbow swing. Assess the elbow relative to
        // the shoulder; a visible complete cycle still counts when form needs attention.
        val relativeElbowTravel = if (anchorElbow != null && anchorShoulder != null) {
            val dx = (elbow.x - shoulder.x) - (anchorElbow.x - anchorShoulder.x)
            val dy = (elbow.y - shoulder.y) - (anchorElbow.y - anchorShoulder.y)
            kotlin.math.hypot(dx, dy)
        } else null
        relativeElbowTravelTrace = relativeElbowTravel
        if (phase in listOf(BicepsCurlPhase.CURLING, BicepsCurlPhase.CONTRACTED, BicepsCurlPhase.LOWERING) &&
            relativeElbowTravel != null &&
            relativeElbowTravel > config.formElbowTravel * (startUpperLength ?: upper))
            elbowMovedDuringRep = true
        side = chosen
        segmentLengths = upper to forearm
        val alpha = if (dt == null || smoothAngle == null) 1.0 else 1 - exp(-dt.toDouble() / config.filterMs)
        val angle = smoothAngle?.let { it + alpha * (raw - it) } ?: raw
        smoothAngle = angle
        smoothTrace = angle
        val now = pose.timestampMs
        val isExtended = raw >= config.extended && angle >= config.extended
        val isContracted = raw <= config.contracted && angle <= config.contracted
        extendedTrace = isExtended
        contractedTrace = isContracted
        var event: RepetitionCompleted? = null
        when (phase) {
            BicepsCurlPhase.NOT_READY -> if (confirm("ready", isExtended, now, config.readinessMs)) {
                startElbow = elbow; startShoulder = shoulder; startUpperLength = upper
                elbowMovedDuringRep = false
                transition(BicepsCurlPhase.EXTENDED)
            }
            BicepsCurlPhase.EXTENDED -> if (angle <= config.leaveExtended) {
                attemptAt = now
                elbowMovedDuringRep = relativeElbowTravel != null &&
                    relativeElbowTravel > config.formElbowTravel * (startUpperLength ?: upper)
                transition(BicepsCurlPhase.CURLING)
            }
            BicepsCurlPhase.CURLING -> {
                if (confirm("contracted", isContracted, now, config.phaseMs)) transition(BicepsCurlPhase.CONTRACTED)
                else if (confirm("partial", isExtended, now, config.finishMs)) {
                    endedAttempt = UncountedAttempt.INCOMPLETE
                    startElbow = elbow; startShoulder = shoulder; startUpperLength = upper
                    elbowMovedDuringRep = false
                    transition(BicepsCurlPhase.EXTENDED)
                }
            }
            BicepsCurlPhase.CONTRACTED -> if (angle >= config.leaveContracted) transition(BicepsCurlPhase.LOWERING)
            BicepsCurlPhase.LOWERING -> {
                if (confirm("extended", isExtended, now, config.finishMs)) {
                    if (now - attemptAt >= config.minimumCycleMs) {
                        repetitions++
                        val assessment = if (elbowMovedDuringRep)
                            RepAssessment(RepFormQuality.NEEDS_ATTENTION, "Elbow moved")
                        else RepAssessment(RepFormQuality.GOOD)
                        event = RepetitionCompleted(repetitions, now, assessment)
                        countedAt = now
                        countedFeedback = if (elbowMovedDuringRep)
                            BicepsCurlFeedback.COUNTED_ELBOW_MOVED else BicepsCurlFeedback.COUNTED
                        startElbow = elbow; startShoulder = shoulder; startUpperLength = upper
                        elbowMovedDuringRep = false
                        transition(BicepsCurlPhase.EXTENDED)
                    } else {
                        endedAttempt = UncountedAttempt.INCOMPLETE
                        invalidateTracking(DiscontinuityReason.TRACKING)
                    }
                } else if (confirm("contractedAgain", isContracted, now, config.phaseMs))
                    transition(BicepsCurlPhase.CONTRACTED)
            }
        }
        val feedback = when (phase) {
            BicepsCurlPhase.NOT_READY -> BicepsCurlFeedback.READY
            BicepsCurlPhase.EXTENDED -> if (countedAt?.let { now - it < 700 } == true)
                countedFeedback else BicepsCurlFeedback.CURL
            BicepsCurlPhase.CURLING -> BicepsCurlFeedback.CURL
            BicepsCurlPhase.CONTRACTED, BicepsCurlPhase.LOWERING -> BicepsCurlFeedback.LOWER
        }
        return result(feedback, true, angle, event)
    }
}
