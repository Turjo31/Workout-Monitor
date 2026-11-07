package com.kuet.gymtest.analysis

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

enum class ForwardLungePhase { NOT_READY, STANDING, STEPPING, BOTTOM, RETURNING }

enum class ForwardLungeFeedback(override val text: String, override val priority: Int) : AnalysisFeedback {
    NO_POSE("Pose unclear - stand sideways in view", 1),
    FRAMING("Keep one complete leg, hips and shoulders in view", 2),
    SIDE_VIEW("Stand sideways so both legs are visible", 2),
    UNCERTAIN("Leg tracking unclear - adjust the camera", 3),
    READY("Stand tall with feet together", 4),
    STEP("Step forward and lower into a lunge", 4),
    RETURN("Push back to standing", 4),
    COUNTED("Repetition counted", 5),
    COUNTED_FORM("Repetition counted - depth needs attention", 5);

    override val key get() = "forwardlunge.$name"
}

data class ForwardLungeMetrics(val knee: Double, val footTravel: Double, val side: BodySide)

data class ForwardLungeAnalysisResult(
    override val key: FrameKey, override val timestampMs: Long,
    override val repetitions: Int, override val repetition: RepetitionCompleted?,
    override val feedback: ForwardLungeFeedback, override val trackingValid: Boolean,
    val phase: ForwardLungePhase, val metrics: ForwardLungeMetrics?,
    override val uncountedAttempt: UncountedAttempt? = null
) : ExerciseAnalysisResult {
    override val attemptInProgress get() = phase in listOf(
        ForwardLungePhase.STEPPING, ForwardLungePhase.BOTTOM, ForwardLungePhase.RETURNING)
    override val presentation get() = AnalysisPresentation(
        "forwardlunge.${phase.name}", phase.name.lowercase().replace('_', ' '),
        phase != ForwardLungePhase.NOT_READY,
        metrics?.let { "Knee bend: ${it.knee.toInt()}°" })
}

/** Provisional side-view 2D thresholds; step direction and medical form are not inferred. */
data class ForwardLungeConfig(
    val standingKnee: Double = 155.0, val leaveStandingKnee: Double = 145.0,
    val bottomKnee: Double = 135.0, val goodDepthKnee: Double = 115.0,
    val standingFootSeparation: Double = .45, val stepFootSeparation: Double = .60,
    val minimumBottomFootTravel: Double = .20,
    val readinessMs: Long = 100, val bottomMs: Long = 55, val returnMs: Long = 70,
    val minimumCycleMs: Long = 350, val maximumGapMs: Long = 300,
    val filterMs: Double = 50.0, val minimumSegment: Double = .025,
    val maximumShoulderWidthRatio: Double = .55,
    val visibility: Float = .65f, val presence: Float = .5f, val edgeMargin: Float = .02f
) {
    init {
        require(0 < goodDepthKnee && goodDepthKnee < bottomKnee &&
            bottomKnee < leaveStandingKnee && leaveStandingKnee < standingKnee && standingKnee <= 180)
        require(minimumBottomFootTravel > 0 && minimumBottomFootTravel < standingFootSeparation &&
            stepFootSeparation > standingFootSeparation)
        require(listOf(filterMs, minimumSegment, maximumShoulderWidthRatio).all { it.isFinite() && it > 0 })
        require(listOf(readinessMs, bottomMs, returnMs, minimumCycleMs, maximumGapMs).all { it > 0 })
        require(listOf(visibility, presence, edgeMargin).all { it.isFinite() && it in 0f..1f })
    }
}

/** Counts a visible standing -> moving-foot bent-knee stance -> standing cycle. */
class ForwardLungeAnalyzer(
    private val config: ForwardLungeConfig = ForwardLungeConfig(),
    private val diagnostic: ((String) -> Unit)? = null
) : ExerciseAnalyzer {
    private var phase = ForwardLungePhase.NOT_READY
    private var repetitions = 0
    private var previous: PoseFrame? = null
    private var filtered: ForwardLungeMetrics? = null
    private val standingFeet = mutableMapOf<BodySide, Double>()
    private var activeSide: BodySide? = null
    private var filteredSide: BodySide? = null
    private var candidate: String? = null
    private var candidateAt = 0L
    private var candidateSamples = 0
    private var attemptAt = 0L
    private var deepestKnee = 180.0
    private var stepSamples = 0
    private var stepConfirmed = false
    private var countedAt: Long? = null
    private var countedFeedback = ForwardLungeFeedback.COUNTED

    override fun reset() {
        repetitions = 0
        previous = null
        invalidateTracking(DiscontinuityReason.EPOCH)
    }

    override fun invalidateTracking(reason: DiscontinuityReason) {
        diagnostic?.invoke("invalidate reason=$reason phase=$phase previous=${previous?.timestampMs}")
        phase = ForwardLungePhase.NOT_READY
        filtered = null
        standingFeet.clear()
        activeSide = null
        filteredSide = null
        candidate = null
        candidateSamples = 0
        attemptAt = 0
        deepestKnee = 180.0
        stepSamples = 0
        stepConfirmed = false
        countedAt = null
        countedFeedback = ForwardLungeFeedback.COUNTED
    }

    private fun transition(next: ForwardLungePhase) {
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

    override fun processFrame(pose: PoseFrame): ForwardLungeAnalysisResult {
        val before = phase
        var failure: String? = null
        var ended: UncountedAttempt? = null
        var metrics: ForwardLungeMetrics? = null
        var rawMetrics: ForwardLungeMetrics? = null
        var dtTrace: Long? = null
        var sideRatio: Double? = null
        var chosenSide: BodySide? = null
        var standingTrace: Boolean? = null
        var steppingTrace: Boolean? = null
        var bottomTrace: Boolean? = null
        var standingBlockers = "unmeasured"
        var bottomBlockers = "unmeasured"
        fun result(feedback: ForwardLungeFeedback, valid: Boolean = false,
                   event: RepetitionCompleted? = null): ForwardLungeAnalysisResult {
            diagnostic?.invoke("frame time=${pose.timestampMs} seq=${pose.key.sequence} from=$before to=$phase " +
                "valid=$valid failure=${failure ?: "none"} sideRatio=$sideRatio side=$chosenSide " +
                "raw=$rawMetrics smooth=$metrics standing=$standingTrace stepping=$steppingTrace bottom=$bottomTrace " +
                "stepConfirmed=$stepConfirmed stepSamples=$stepSamples " +
                "standingBlockers=$standingBlockers bottomBlockers=$bottomBlockers " +
                "candidate=${candidate ?: "none"} samples=$candidateSamples " +
                "candidateMs=${if (candidate == null) 0 else pose.timestampMs - candidateAt} " +
                "dt=$dtTrace feedback=$feedback event=${event?.ordinal} uncounted=$ended")
            return ForwardLungeAnalysisResult(pose.key, pose.timestampMs, repetitions, event,
                feedback, valid, phase, metrics, ended)
        }
        fun invalid(feedback: ForwardLungeFeedback, reason: String): ForwardLungeAnalysisResult {
            failure = reason
            if (phase in listOf(ForwardLungePhase.STEPPING, ForwardLungePhase.BOTTOM,
                    ForwardLungePhase.RETURNING)) ended = UncountedAttempt.TRACKING_INTERRUPTED
            invalidateTracking(DiscontinuityReason.TRACKING)
            return result(feedback)
        }
        val old = previous
        if (pose.timestampMs < 0 || pose.key.sequence < 0 || pose.imageWidth <= 0 || pose.imageHeight <= 0)
            return invalid(ForwardLungeFeedback.UNCERTAIN, "invalid frame metadata")
        if (old != null && (old.key.sessionId != pose.key.sessionId || old.key.cameraEpoch != pose.key.cameraEpoch ||
                old.key.analysisEpoch != pose.key.analysisEpoch || pose.key.sequence <= old.key.sequence ||
                pose.timestampMs <= old.timestampMs))
            return invalid(ForwardLungeFeedback.UNCERTAIN, "key or timestamp discontinuity")
        previous = pose
        val dt = old?.let { pose.timestampMs - it.timestampMs }
        dtTrace = dt
        if (dt != null && dt > config.maximumGapMs) return invalid(ForwardLungeFeedback.UNCERTAIN, "frame gap")
        if (pose.landmarks.isEmpty()) return invalid(ForwardLungeFeedback.NO_POSE, "no pose")
        val torsoIndices = listOf(11, 12, 23, 24)
        val lowConfidence = torsoIndices.filterNot {
            PoseGeometry.confident(pose.landmarks.getOrNull(it), config.visibility, config.presence) }
        if (lowConfidence.isNotEmpty())
            return invalid(ForwardLungeFeedback.UNCERTAIN, "torso landmark low confidence indices=$lowConfidence")
        val outside = torsoIndices.filterNot { PoseGeometry.inside(pose.landmarks[it], config.edgeMargin) }
        if (outside.isNotEmpty())
            return invalid(ForwardLungeFeedback.FRAMING, "torso landmark outside frame indices=$outside")
        fun point(i: Int) = PoseGeometry.point(pose.landmarks[i], pose.imageWidth, pose.imageHeight)!!
        val ls = point(11); val rs = point(12)
        val lh = point(23); val rh = point(24)
        val minimum = config.minimumSegment * min(pose.imageWidth, pose.imageHeight)
        val torso = (PoseGeometry.distance(ls, lh) + PoseGeometry.distance(rs, rh)) / 2
        val width = PoseGeometry.distance(ls, rs)
        sideRatio = if (torso > 0) width / torso else null
        if (torso < minimum || width / torso > config.maximumShoulderWidthRatio)
            return invalid(ForwardLungeFeedback.SIDE_VIEW, "side view geometry")
        data class Leg(val side: BodySide, val knee: Double, val foot: Double)
        val legs = BodySide.entries.mapNotNull { side ->
            val indices = listOf(side.knee, side.ankle)
            if (indices.any { !PoseGeometry.confident(pose.landmarks.getOrNull(it), config.visibility, config.presence) ||
                    !PoseGeometry.inside(pose.landmarks[it], config.edgeMargin) }) null
            else {
                val hip = if (side == BodySide.LEFT) lh else rh
                PoseGeometry.angle(hip, point(side.knee), point(side.ankle), minimum)?.let {
                    Leg(side, it, (point(side.ankle).x - hip.x) / torso)
                }
            }
        }
        if (legs.isEmpty() || activeSide != null && legs.none { it.side == activeSide }) {
            val missing = BodySide.entries.flatMap { listOf(it.knee, it.ankle) }.filterNot {
                PoseGeometry.confident(pose.landmarks.getOrNull(it), config.visibility, config.presence) }
            return invalid(ForwardLungeFeedback.UNCERTAIN, "tracked leg unavailable; low confidence indices=$missing")
        }
        val selected = when (phase) {
            ForwardLungePhase.NOT_READY -> legs.filter { it.knee >= config.standingKnee &&
                abs(it.foot) <= config.standingFootSeparation }.minByOrNull { abs(it.foot) } ?: legs.first()
            ForwardLungePhase.STANDING -> legs.filter { standingFeet.containsKey(it.side) }
                .maxByOrNull { abs(it.foot - standingFeet.getValue(it.side)) } ?: legs.first()
            else -> legs.first { it.side == activeSide }
        }
        chosenSide = selected.side
        // A foot must move relative to its own hip; whole-body translation is not a step.
        val travel = abs(selected.foot - (standingFeet[selected.side] ?: selected.foot))
        val raw = ForwardLungeMetrics(selected.knee, travel, selected.side)
        rawMetrics = raw
        val alpha = if (dt == null || filtered == null || selected.side != filteredSide) 1.0
            else 1 - exp(-dt.toDouble() / config.filterMs)
        val smooth = filtered?.takeIf { it.side == raw.side }?.let { ForwardLungeMetrics(
            it.knee + alpha * (raw.knee - it.knee),
            it.footTravel + alpha * (raw.footTravel - it.footTravel), raw.side) } ?: raw
        filtered = smooth
        filteredSide = selected.side
        metrics = smooth
        val standing = raw.knee >= config.standingKnee && smooth.knee >= config.standingKnee &&
            raw.footTravel <= config.standingFootSeparation &&
            smooth.footTravel <= config.standingFootSeparation &&
            (standingFeet.containsKey(selected.side) || abs(selected.foot) <= config.standingFootSeparation)
        val stepping = raw.footTravel > config.standingFootSeparation &&
            smooth.footTravel > config.standingFootSeparation
        val stepSample = raw.footTravel >= config.stepFootSeparation &&
            smooth.footTravel >= config.stepFootSeparation
        if (phase == ForwardLungePhase.STEPPING && !stepConfirmed) {
            stepSamples = if (stepSample) stepSamples + 1 else 0
            stepConfirmed = stepSamples >= 2
        }
        // The hip moves toward the planted foot during descent, shrinking hip-relative travel.
        // Remember the confirmed step, but reject a foot that has returned near its baseline.
        val bottom = stepConfirmed &&
            raw.footTravel >= config.minimumBottomFootTravel &&
            smooth.footTravel >= config.minimumBottomFootTravel &&
            raw.knee <= config.bottomKnee && smooth.knee <= config.bottomKnee
        standingTrace = standing
        steppingTrace = stepping
        bottomTrace = bottom
        standingBlockers = listOfNotNull(
            "rawKnee".takeUnless { raw.knee >= config.standingKnee },
            "smoothKnee".takeUnless { smooth.knee >= config.standingKnee },
            "rawTravel".takeUnless { raw.footTravel <= config.standingFootSeparation },
            "smoothTravel".takeUnless { smooth.footTravel <= config.standingFootSeparation },
            "footNotHome".takeUnless { standingFeet.containsKey(selected.side) ||
                abs(selected.foot) <= config.standingFootSeparation }
        ).joinToString(",").ifEmpty { "none" }
        bottomBlockers = listOfNotNull(
            "stepNotConfirmed".takeUnless { stepConfirmed },
            "rawTravelReturned".takeUnless { raw.footTravel >= config.minimumBottomFootTravel },
            "smoothTravelReturned".takeUnless { smooth.footTravel >= config.minimumBottomFootTravel },
            "rawKnee".takeUnless { raw.knee <= config.bottomKnee },
            "smoothKnee".takeUnless { smooth.knee <= config.bottomKnee }
        ).joinToString(",").ifEmpty { "none" }
        val now = pose.timestampMs
        if (phase in listOf(ForwardLungePhase.STEPPING, ForwardLungePhase.BOTTOM,
                ForwardLungePhase.RETURNING) && bottom)
            deepestKnee = min(deepestKnee, raw.knee)
        var event: RepetitionCompleted? = null
        when (phase) {
            ForwardLungePhase.NOT_READY -> if (confirm("ready", standing, now, config.readinessMs)) {
                legs.filter { it.knee >= config.standingKnee &&
                    abs(it.foot) <= config.standingFootSeparation }.forEach { standingFeet[it.side] = it.foot }
                transition(ForwardLungePhase.STANDING)
            }
            ForwardLungePhase.STANDING -> if (stepping) {
                activeSide = selected.side
                attemptAt = now
                deepestKnee = 180.0
                stepSamples = if (stepSample) 1 else 0
                stepConfirmed = false
                countedAt = null
                transition(ForwardLungePhase.STEPPING)
            } else if (standing) {
                legs.filter { it.knee >= config.standingKnee &&
                    abs(it.foot) <= config.standingFootSeparation &&
                    !standingFeet.containsKey(it.side) }.forEach { standingFeet[it.side] = it.foot }
            }
            ForwardLungePhase.STEPPING -> {
                if (confirm("bottom", bottom, now, config.bottomMs)) transition(ForwardLungePhase.BOTTOM)
                else if (confirm("partial", standing, now, config.returnMs)) {
                    ended = UncountedAttempt.INCOMPLETE
                    transition(ForwardLungePhase.STANDING)
                    activeSide = null
                    stepSamples = 0
                    stepConfirmed = false
                }
            }
            ForwardLungePhase.BOTTOM -> if (!bottom) transition(ForwardLungePhase.RETURNING)
            ForwardLungePhase.RETURNING -> {
                if (confirm("stand", standing, now, config.returnMs)) {
                    if (now - attemptAt >= config.minimumCycleMs) {
                        repetitions++
                        val assessment = if (deepestKnee <= config.goodDepthKnee)
                            RepAssessment(RepFormQuality.GOOD)
                        else RepAssessment(RepFormQuality.NEEDS_ATTENTION, "Depth needs attention")
                        event = RepetitionCompleted(repetitions, now, assessment)
                        countedAt = now
                        countedFeedback = if (assessment.quality == RepFormQuality.GOOD)
                            ForwardLungeFeedback.COUNTED else ForwardLungeFeedback.COUNTED_FORM
                        transition(ForwardLungePhase.STANDING)
                        activeSide = null
                        stepSamples = 0
                        stepConfirmed = false
                    } else {
                        ended = UncountedAttempt.INCOMPLETE
                        invalidateTracking(DiscontinuityReason.TRACKING)
                    }
                } else if (confirm("bottomAgain", bottom, now, config.bottomMs))
                    transition(ForwardLungePhase.BOTTOM)
            }
        }
        val feedback = when (phase) {
            ForwardLungePhase.NOT_READY -> ForwardLungeFeedback.READY
            ForwardLungePhase.STANDING -> if (countedAt?.let { now - it < 700 } == true)
                countedFeedback else ForwardLungeFeedback.STEP
            ForwardLungePhase.STEPPING -> ForwardLungeFeedback.STEP
            ForwardLungePhase.BOTTOM, ForwardLungePhase.RETURNING -> ForwardLungeFeedback.RETURN
        }
        return result(feedback, true, event)
    }
}
