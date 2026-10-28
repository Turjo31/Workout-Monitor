package com.kuet.gymtest.analysis

/** Text-only smoothing: never delays tracking invalidation, movement state or count acceptance. */
class FeedbackPresenter(private val minimumDisplayMs: Long = 500) {
    private var shown: Feedback? = null
    private var since = 0L
    private var phase: SquatPhase? = null
    fun reset() { shown = null; phase = null; since = 0 }
    fun present(result: SquatAnalysisResult): Feedback {
        val next = result.feedback
        val old = shown
        val recovery = old != null && old.priority <= 3 && next.priority >= 4 && result.phase == SquatPhase.NOT_READY
        val actionablePhaseChange = phase != result.phase && result.phase != SquatPhase.NOT_READY
        if (old == null || next.priority < old.priority ||
            (!recovery && (actionablePhaseChange || result.timestampMs - since >= minimumDisplayMs))) {
            if (shown != next) { shown = next; since = result.timestampMs }
        }
        phase = result.phase
        return shown ?: next
    }
}
