package com.kuet.gymtest.analysis

/** Text-only smoothing: never delays tracking invalidation, movement state or count acceptance. */
class FeedbackPresenter(private val minimumDisplayMs: Long = 500) {
    private var shown: AnalysisFeedback? = null
    private var since = 0L
    private var phase: String? = null
    fun reset() { shown = null; phase = null; since = 0 }
    fun present(result: ExerciseAnalysisResult): AnalysisFeedback {
        val next = result.feedback
        val old = shown
        val presentation = result.presentation
        val recovery = old != null && old.priority <= 3 && next.priority >= 4 && !presentation.ready
        val actionablePhaseChange = phase != presentation.phaseKey && presentation.ready
        if (old == null || next.priority < old.priority ||
            (!recovery && (actionablePhaseChange || result.timestampMs - since >= minimumDisplayMs))) {
            // A strategy may allocate a new message object per frame. Hold by content, not identity.
            if (old?.key != next.key || old.text != next.text || old.priority != next.priority) {
                shown = next; since = result.timestampMs
            }
        }
        phase = presentation.phaseKey
        return shown ?: next
    }
}
