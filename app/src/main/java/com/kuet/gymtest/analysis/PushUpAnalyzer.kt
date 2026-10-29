package com.kuet.gymtest.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

enum class PushUpPhase { NOT_READY, TOP, DESCENDING, BOTTOM, ASCENDING }
data class PushUpMetrics(val elbow: Double, val bodyLine: Double)
data class PushUpAnalysisResult(
    override val key: FrameKey, override val timestampMs: Long,
    override val repetitions: Int, override val repetition: RepetitionCompleted?,
    override val feedback: Feedback, override val trackingValid: Boolean,
    val phase: PushUpPhase, val side: BodySide?, val metrics: PushUpMetrics?
) : ExerciseAnalysisResult

/** Engineering defaults, not calibrated biomechanical or medical limits. Elbow angle is 2D side-view. */
data class PushUpConfig(
    val topEntry: Double = 160.0, val topExit: Double = 150.0,
    val descent: Double = 140.0,
    val bottomEntry: Double = 90.0, val bottomExit: Double = 105.0,
    val minimumBodyLine: Double = 150.0, val horizontalRatio: Double = .6,
    val readinessMs: Long = 300, val phaseMs: Long = 80, val finalTopMs: Long = 200,
    val minimumCycleMs: Long = 500, val maximumGapMs: Long = 300,
    val filterMs: Double = 80.0, val maximumSpeed: Double = 900.0,
    val maximumSegmentChange: Double = .35, val chainVisibility: Float = .65f,
    val presence: Float = .5f, val edgeMargin: Float = .02f,
    val minimumSegment: Double = .025, val shoulderRatio: Double = .75,
    val hipRatio: Double = .55, val orientationMs: Long = 300,
    val minimumShoulderTravel: Double = .10, val maximumWristTravelRatio: Double = .6
) {
    init {
        require(bottomEntry > 0 && bottomEntry < bottomExit && bottomExit < descent &&
                descent <= topExit && topExit < topEntry && topEntry <= 180)
        require(minimumBodyLine in 0.0..180.0)
        require(listOf(filterMs, maximumSpeed, minimumSegment, shoulderRatio, hipRatio, horizontalRatio,
            minimumShoulderTravel, maximumWristTravelRatio).all { it.isFinite() && it > 0 })
        require(listOf(readinessMs, phaseMs, finalTopMs, minimumCycleMs, maximumGapMs, orientationMs).all { it > 0 })
        require(maximumSegmentChange in 0.0..1.0 && edgeMargin in 0f..0.25f)
        require(listOf(chainVisibility, presence).all { it in 0f..1f })
    }
}

/** Single-owner pure state machine. Invalid observations cancel motion, never accepted reps. */
class PushUpAnalyzer(private val config: PushUpConfig = PushUpConfig()) : ExerciseAnalyzer {
    private var phase = PushUpPhase.NOT_READY
    private var repetitions = 0
    private var side: BodySide? = null
    private var previous: PoseFrame? = null
    private var rawElbow: Double? = null
    private var filtered: Double? = null
    private var lengths: List<Double>? = null
    private var candidate: String? = null
    private var candidateAt = 0L
    private var candidateSamples = 0
    private var attemptAt = 0L
    private var minimum = 180.0
    private var broadAt: Long? = null
    private var countedAt: Long? = null
    private var shallowAt: Long? = null
    private var refShoulder: Point2? = null
    private var refWrist: Point2? = null

    private fun elbowOf(s: BodySide) = if (s == BodySide.LEFT) 13 else 14
    private fun wristOf(s: BodySide) = if (s == BodySide.LEFT) 15 else 16

    override fun reset() {
        repetitions = 0
        previous = null
        invalidateTracking(DiscontinuityReason.EPOCH)
    }

    override fun invalidateTracking(reason: DiscontinuityReason) {
        phase = PushUpPhase.NOT_READY
        side = null
        rawElbow = null
        filtered = null
        lengths = null
        candidate = null
        candidateSamples = 0
        minimum = 180.0
        attemptAt = 0
        countedAt = null
        shallowAt = null
        broadAt = null
        refShoulder = null
        refWrist = null
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

    private fun transition(next: PushUpPhase) { phase = next; candidate = null; candidateSamples = 0 }

    override fun processFrame(pose: PoseFrame): PushUpAnalysisResult {
        val old = previous
        fun result(feedback: Feedback, valid: Boolean = false, metrics: PushUpMetrics? = null,
                   event: RepetitionCompleted? = null) = PushUpAnalysisResult(
            pose.key, pose.timestampMs, repetitions, event, feedback, valid, phase, side, metrics
        )
        fun invalid(feedback: Feedback): PushUpAnalysisResult {
            invalidateTracking(DiscontinuityReason.TRACKING)
            return result(feedback)
        }
        val sameEpoch = old == null || old.key.let {
            it.sessionId == pose.key.sessionId && it.cameraEpoch == pose.key.cameraEpoch && it.analysisEpoch == pose.key.analysisEpoch
        }
        if (!sameEpoch) return invalid(Feedback.UNCERTAIN)
        if (old != null && (pose.timestampMs <= old.timestampMs || pose.key.sequence <= old.key.sequence)) {
            return invalid(Feedback.UNCERTAIN)
        }
        previous = pose
        val dt = old?.let { pose.timestampMs - it.timestampMs }
        if (dt != null && dt > config.maximumGapMs) invalidateTracking(DiscontinuityReason.GAP)
        if (pose.timestampMs < 0 || pose.imageWidth <= 0 || pose.imageHeight <= 0) return invalid(Feedback.UNCERTAIN)
        if (pose.landmarks.isEmpty()) return invalid(Feedback.NO_POSE)
        fun landmark(i: Int) = pose.landmarks.getOrNull(i)
        fun confident(i: Int) = PoseGeometry.confident(landmark(i), config.chainVisibility, config.presence)
        fun chain(s: BodySide) = listOf(s.shoulder, elbowOf(s), wristOf(s), s.hip, s.ankle)
        fun point(i: Int) = PoseGeometry.point(pose.landmarks[i], pose.imageWidth, pose.imageHeight)!!
        val chosen = side ?: BodySide.entries.filter { s -> chain(s).all { confident(it) } }
            .maxByOrNull { s -> chain(s).minOf { landmark(it)!!.visibility!! } }
        ?: return invalid(Feedback.UNCERTAIN)
        if (!chain(chosen).all { confident(it) }) return invalid(Feedback.UNCERTAIN)
        if (!chain(chosen).all { PoseGeometry.inside(landmark(it)!!, config.edgeMargin) }) return invalid(Feedback.MOVE_BACK)
        val shoulder = point(chosen.shoulder)
        val elbow = point(elbowOf(chosen))
        val wrist = point(wristOf(chosen))
        val hip = point(chosen.hip)
        val ankle = point(chosen.ankle)
        val minLength = config.minimumSegment * min(pose.imageWidth, pose.imageHeight)
        val raw = PoseGeometry.angle(shoulder, elbow, wrist, minLength) ?: return invalid(Feedback.UNCERTAIN)
        val bodyLine = PoseGeometry.angle(shoulder, hip, ankle, minLength) ?: return invalid(Feedback.UNCERTAIN)
        val currentLengths = listOf(PoseGeometry.distance(shoulder, elbow), PoseGeometry.distance(elbow, wrist),
            PoseGeometry.distance(shoulder, hip), PoseGeometry.distance(hip, ankle))
        if (listOf(11, 12, 23, 24).all { confident(it) }) {
            val torso = (PoseGeometry.distance(point(11), point(23)) + PoseGeometry.distance(point(12), point(24))) / 2
            val broad = torso > minLength && PoseGeometry.distance(point(11), point(12)) / torso > config.shoulderRatio &&
                    PoseGeometry.distance(point(23), point(24)) / torso > config.hipRatio
            if (broad) {
                val since = broadAt ?: pose.timestampMs
                invalidateTracking(DiscontinuityReason.TRACKING)
                broadAt = since
                return result(if (pose.timestampMs - since >= config.orientationMs) Feedback.SIDEWAYS else Feedback.UNCERTAIN)
            }
        }
        broadAt = null
        if (dt != null && dt in 1..config.maximumGapMs &&
            (rawElbow?.let { abs(raw - it) * 1000 / dt > config.maximumSpeed } == true ||
                    lengths?.zip(currentLengths)?.any { (a, b) -> abs(b - a) / a > config.maximumSegmentChange } == true)
        ) return invalid(Feedback.UNCERTAIN)
        side = chosen
        val last = filtered
        val angle = if (last != null && dt != null) last + (1 - exp(-dt.toDouble() / config.filterMs)) * (raw - last) else raw
        filtered = angle
        rawElbow = raw
        lengths = currentLengths
        val now = pose.timestampMs
        val plank = bodyLine >= config.minimumBodyLine &&
                abs(ankle.y - shoulder.y) <= config.horizontalRatio * abs(ankle.x - shoulder.x)
        val top = raw >= config.topEntry && angle >= config.topEntry && plank
        // A real push-up moves the shoulder while the hand stays planted; elbow-only motion does the opposite.
        if (top) { refShoulder = shoulder; refWrist = wrist }
        val armLength = currentLengths[0] + currentLengths[1]
        val shoulderTravel = refShoulder?.let { PoseGeometry.distance(shoulder, it) } ?: 0.0
        val wristTravel = refWrist?.let { PoseGeometry.distance(wrist, it) } ?: Double.MAX_VALUE
        val bodyMoved = shoulderTravel >= config.minimumShoulderTravel * armLength &&
                wristTravel <= config.maximumWristTravelRatio * shoulderTravel
        val bottom = raw <= config.bottomEntry && angle <= config.bottomEntry && bodyMoved
        var event: RepetitionCompleted? = null
        if (phase in listOf(PushUpPhase.DESCENDING, PushUpPhase.BOTTOM, PushUpPhase.ASCENDING)) minimum = min(minimum, angle)
        when (phase) {
            PushUpPhase.NOT_READY -> if (confirm("ready", top, now, config.readinessMs)) transition(PushUpPhase.TOP)
            PushUpPhase.TOP -> {
                if (angle < config.topExit) countedAt = null
                if (confirm("down", raw <= config.descent && angle <= config.descent, now, config.phaseMs)) {
                    attemptAt = candidateAt
                    minimum = angle
                    shallowAt = null
                    transition(PushUpPhase.DESCENDING)
                }
            }
            PushUpPhase.DESCENDING -> {
                if (confirm("bottom", bottom, now, config.phaseMs)) transition(PushUpPhase.BOTTOM)
                else if (confirm("partial", top, now, config.finalTopMs)) {
                    shallowAt = now
                    transition(PushUpPhase.TOP)
                }
            }
            PushUpPhase.BOTTOM -> if (confirm("up", raw >= config.bottomExit && angle >= config.bottomExit && angle >= minimum + 12, now, config.phaseMs)) transition(PushUpPhase.ASCENDING)
            PushUpPhase.ASCENDING -> {
                if (confirm("top", top, now, config.finalTopMs)) {
                    if (now - attemptAt >= config.minimumCycleMs) {
                        repetitions++
                        event = RepetitionCompleted(repetitions, now)
                        countedAt = now
                        transition(PushUpPhase.TOP)
                    } else invalidateTracking(DiscontinuityReason.TRACKING)
                } else if (confirm("bottomAgain", bottom, now, config.phaseMs)) transition(PushUpPhase.BOTTOM)
            }
        }
        val feedback = when (phase) {
            PushUpPhase.NOT_READY -> Feedback.PUSH_READY
            PushUpPhase.TOP -> when {
                countedAt?.let { now - it < 700 } == true -> Feedback.COUNTED
                shallowAt?.let { now - it < 1500 } == true -> Feedback.PUSH_SHALLOW
                else -> Feedback.PUSH_LOWER
            }
            PushUpPhase.DESCENDING -> Feedback.PUSH_LOWER
            PushUpPhase.BOTTOM, PushUpPhase.ASCENDING -> Feedback.PUSH_UP
        }
        return result(feedback, true, PushUpMetrics(angle, bodyLine), event)
    }
}