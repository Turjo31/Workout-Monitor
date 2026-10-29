package com.kuet.gymtest.analysis

/** Text-only smoothing: never delays tracking invalidation, movement state or count acceptance. */
class FeedbackPresenter(private val minimumDisplayMs: Long = 500) {
    private var shown: Feedback? = null
    private var since = 0L
    private var phase: String? = null
    fun reset() { shown = null; phase = null; since = 0 }
    fun present(result: ExerciseAnalysisResult): Feedback {
        val next = result.feedback
        val old = shown
        val recovery = old != null && old.priority <= 3 && next.priority >= 4 && result.isNotReady
        val actionablePhaseChange = phase != result.phaseName && !result.isNotReady
        if (old == null || next.priority < old.priority ||
            (!recovery && (actionablePhaseChange || result.timestampMs - since >= minimumDisplayMs))) {
            if (shown != next) { shown = next; since = result.timestampMs }
        }
        phase = result.phaseName
        return shown ?: next
    }
}