package com.kuet.gymtest.analysis

/** Pure speech decisions. Each accepted frame may produce at most one short cue. */
class VoiceFeedbackPolicy {
    private var epoch: Triple<String, Long, Long>? = null
    private var lastTime = -1L
    private var wasReady = false
    private var pendingKey: String? = null
    private var pendingSince = 0L
    private var lastCoachingKey: String? = null
    private var lastCoachingAt = -1L
    private var lastRepOrdinal = 0

    fun reset() {
        epoch = null
        lastTime = -1L
        wasReady = false
        pendingKey = null
        lastCoachingKey = null
        lastCoachingAt = -1L
        lastRepOrdinal = 0
    }

    fun observe(result: ExerciseAnalysisResult): String? {
        val frameEpoch = Triple(result.key.sessionId, result.key.cameraEpoch, result.key.analysisEpoch)
        if (epoch != frameEpoch) {
            reset()
            epoch = frameEpoch
        }
        val now = result.timestampMs
        if (now <= lastTime) return null
        lastTime = now

        val rep = result.repetition
        if (rep != null && result.trackingValid && rep.ordinal > lastRepOrdinal) {
            lastRepOrdinal = rep.ordinal
            pendingKey = null
            val note = rep.assessment?.takeIf { it.quality == RepFormQuality.NEEDS_ATTENTION }?.note
            return "${rep.ordinal}." + (if (note.isNullOrBlank()) "" else " $note.")
        }

        val ready = result.trackingValid && result.presentation.ready
        if (ready && !wasReady) {
            wasReady = true
            pendingKey = null
            return "Ready. Begin."
        }
        wasReady = ready

        val key: String
        val cue: String
        val delay: Long
        when {
            !result.trackingValid && result.feedback.priority <= 3 -> {
                key = result.feedback.key
                cue = result.feedback.text
                delay = 1200
            }
            result is SquatAnalysisResult && result.phase == SquatPhase.DESCENDING &&
                result.depth == DepthStatus.ABOVE_TARGET -> {
                key = "squat.lower"
                cue = "Go a little lower."
                delay = 1400
            }
            result is SquatAnalysisResult && result.feedback == Feedback.SHALLOW -> {
                key = result.feedback.key
                cue = "Go lower on the next rep."
                delay = 400
            }
            result is JumpingJackAnalysisResult && result.phase == JumpingJackPhase.OPENING -> {
                key = "jumpingjack.open"
                cue = "Raise both arms and spread your feet."
                delay = 1400
            }
            else -> {
                pendingKey = null
                return null
            }
        }
        if (pendingKey != key) {
            pendingKey = key
            pendingSince = now
            return null
        }
        if (now - pendingSince < delay ||
            (lastCoachingKey == key && now - lastCoachingAt < 6000)) return null
        lastCoachingKey = key
        lastCoachingAt = now
        return cue
    }
}
