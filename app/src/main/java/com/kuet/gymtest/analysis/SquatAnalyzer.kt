package com.kuet.gymtest.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

enum class SquatPhase { NOT_READY, STANDING, DESCENDING, BOTTOM, ASCENDING }
enum class DepthStatus { UNKNOWN, ABOVE_TARGET, TARGET_REACHED }
data class SquatMetrics(val knee: Double, val hip: Double)
data class SquatAnalysisResult(
    override val key: FrameKey, override val timestampMs: Long,
    override val repetitions: Int, override val repetition: RepetitionCompleted?,
    override val feedback: Feedback, override val trackingValid: Boolean,
    val phase: SquatPhase, val depth: DepthStatus, val side: BodySide?, val metrics: SquatMetrics?
) : ExerciseAnalysisResult

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
    val hipRatio: Double = .55, val orientationMs: Long = 300
) {
    init {
        require(bottomEntry > 0 && bottomEntry < bottomExit && bottomExit < descent &&
            descent <= standingExit && standingExit < standingEntry && standingEntry <= 180)
        require(standingHip in 0.0..180.0 && filterMs > 0 && maximumSpeed > 0)
        require(listOf(filterMs, maximumSpeed, minimumSegment, shoulderRatio, hipRatio).all { it.isFinite() && it > 0 })
        require(listOf(readinessMs, phaseMs, finalStandingMs, minimumCycleMs, maximumGapMs, orientationMs).all { it > 0 })
        require(maximumSegmentChange in 0.0..1.0 && edgeMargin in 0f.. .25f && minimumSegment > 0)
        require(listOf(chainVisibility, presence, framingVisibility).all { it in 0f..1f })
    }
}

/** Single-owner pure state machine. Invalid observations cancel motion, never accepted reps. */
class SquatAnalyzer(private val config: SquatConfig = SquatConfig()) : ExerciseAnalyzer {
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

    override fun reset() {
        repetitions = 0
        previous = null
        invalidateTracking(DiscontinuityReason.EPOCH)
    }

    override fun invalidateTracking(reason: DiscontinuityReason) {
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
        fun result(feedback: Feedback, valid: Boolean = false, metrics: SquatMetrics? = null,
                   event: RepetitionCompleted? = null) = SquatAnalysisResult(
            pose.key, pose.timestampMs, repetitions, event, feedback, valid, phase,
            if (!valid) DepthStatus.UNKNOWN else if (targetReached) DepthStatus.TARGET_REACHED else DepthStatus.ABOVE_TARGET,
            side, metrics
        )
        fun invalid(feedback: Feedback): SquatAnalysisResult {
            invalidateTracking(DiscontinuityReason.TRACKING)
            return result(feedback)
        }
        val sameEpoch = old == null || old.key.let {
            it.sessionId == pose.key.sessionId && it.cameraEpoch == pose.key.cameraEpoch && it.analysisEpoch == pose.key.analysisEpoch
        }
        if (!sameEpoch) return invalid(Feedback.UNCERTAIN) // Owner must reset for a new epoch.
        if (old != null && (pose.timestampMs <= old.timestampMs || pose.key.sequence <= old.key.sequence)) {
            return invalid(Feedback.UNCERTAIN)
        }
        previous = pose
        val dt = old?.let { pose.timestampMs - it.timestampMs }
        if (dt != null && dt > config.maximumGapMs) invalidateTracking(DiscontinuityReason.GAP)
        if (pose.timestampMs < 0 || pose.imageWidth <= 0 || pose.imageHeight <= 0) return invalid(Feedback.UNCERTAIN)
        if (pose.landmarks.isEmpty()) return invalid(Feedback.NO_POSE)
        fun landmark(i: Int) = pose.landmarks.getOrNull(i)
        fun confident(i: Int, threshold: Float = config.chainVisibility) =
            PoseGeometry.confident(landmark(i), threshold, config.presence)
        fun chain(s: BodySide) = listOf(s.shoulder, s.hip, s.knee, s.ankle)
        fun point(i: Int) = PoseGeometry.point(pose.landmarks[i], pose.imageWidth, pose.imageHeight)!!
        val chosen = side ?: BodySide.entries.filter { s -> chain(s).all { confident(it) } }
            .maxByOrNull { s -> chain(s).minOf { landmark(it)!!.visibility!! } }
            ?: return invalid(Feedback.UNCERTAIN)
        if (!chain(chosen).all { confident(it) }) return invalid(Feedback.UNCERTAIN)
        if (!chain(chosen).all { PoseGeometry.inside(landmark(it)!!, config.edgeMargin) }) return invalid(Feedback.MOVE_BACK)
        if (!confident(0, config.framingVisibility) || !PoseGeometry.inside(landmark(0)!!, config.edgeMargin) ||
            listOf(chosen.heel, chosen.toe).none { confident(it, config.framingVisibility) && PoseGeometry.inside(landmark(it)!!, config.edgeMargin) }
        ) return invalid(Feedback.FRAMING)
        val shoulder = point(chosen.shoulder)
        val hip = point(chosen.hip)
        val knee = point(chosen.knee)
        val ankle = point(chosen.ankle)
        val minLength = config.minimumSegment * min(pose.imageWidth, pose.imageHeight)
        val raw = PoseGeometry.angle(hip, knee, ankle, minLength) ?: return invalid(Feedback.UNCERTAIN)
        val hipAngle = PoseGeometry.angle(shoulder, hip, knee, minLength) ?: return invalid(Feedback.UNCERTAIN)
        val currentLengths = listOf(PoseGeometry.distance(shoulder, hip), PoseGeometry.distance(hip, knee), PoseGeometry.distance(knee, ankle))
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
            (rawKnee?.let { abs(raw - it) * 1000 / dt > config.maximumSpeed } == true ||
                lengths?.zip(currentLengths)?.any { (a, b) -> abs(b - a) / a > config.maximumSegmentChange } == true)
        ) return invalid(Feedback.UNCERTAIN)
        side = chosen
        val angle = filtered?.let { it + (1 - exp(-dt!!.toDouble() / config.filterMs)) * (raw - it) } ?: raw
        filtered = angle
        rawKnee = raw
        lengths = currentLengths
        val now = pose.timestampMs
        val standing = raw >= config.standingEntry && angle >= config.standingEntry && hipAngle >= config.standingHip &&
            shoulder.y < hip.y && hip.y < ankle.y
        val bottom = raw <= config.bottomEntry && angle <= config.bottomEntry
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
                    transition(SquatPhase.STANDING)
                }
            }
            SquatPhase.BOTTOM -> if (confirm("up", raw >= config.bottomExit && angle >= config.bottomExit && angle >= minimum + 12, now, config.phaseMs)) transition(SquatPhase.ASCENDING)
            SquatPhase.ASCENDING -> {
                if (confirm("top", standing, now, config.finalStandingMs)) {
                    if (now - attemptAt >= config.minimumCycleMs) {
                        repetitions++
                        event = RepetitionCompleted(repetitions, now)
                        targetReached = false
                        countedAt = now
                        transition(SquatPhase.STANDING)
                    } else invalidateTracking(DiscontinuityReason.TRACKING)
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
