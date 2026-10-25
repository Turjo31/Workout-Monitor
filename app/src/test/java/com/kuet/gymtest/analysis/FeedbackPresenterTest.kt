package com.kuet.gymtest.analysis

import org.junit.Assert.*
import org.junit.Test

class FeedbackPresenterTest {
    @Test fun resolvedTrackingProblemAllowsActionableFramingFeedback() {
        val p = FeedbackPresenter()
        p.present(result(100, Feedback.NO_POSE, SquatPhase.NOT_READY))
        assertEquals(Feedback.FRAMING, p.present(result(700, Feedback.FRAMING, SquatPhase.NOT_READY)))
        assertEquals(Feedback.FRAMING, p.present(result(800, Feedback.STAND, SquatPhase.NOT_READY)))
        assertEquals(Feedback.LOWER, p.present(result(1000, Feedback.LOWER, SquatPhase.STANDING)))
    }
    private fun result(time: Long, feedback: Feedback, phase: SquatPhase) = SquatAnalysisResult(
        FrameKey("s", 1, 1, time), time, 0, null, feedback, feedback.priority > 3,
        phase, DepthStatus.UNKNOWN, null, null)
    @Test fun problemsPreemptAndRecoveryWaitsForReadiness() {
        val presenter = FeedbackPresenter()
        presenter.present(result(100, Feedback.LOWER, SquatPhase.STANDING))
        assertEquals(Feedback.NO_POSE, presenter.present(result(110, Feedback.NO_POSE, SquatPhase.NOT_READY)))
        assertEquals(Feedback.NO_POSE, presenter.present(result(700, Feedback.STAND, SquatPhase.NOT_READY)))
        assertEquals(Feedback.LOWER, presenter.present(result(800, Feedback.LOWER, SquatPhase.STANDING)))
    }
    @Test fun phaseGuidancePreemptsHoldAndResetClearsText() {
        val p = FeedbackPresenter()
        p.present(result(100, Feedback.LOWER, SquatPhase.DESCENDING))
        assertEquals(Feedback.RETURN, p.present(result(200, Feedback.RETURN, SquatPhase.BOTTOM)))
        p.reset(); assertEquals(Feedback.STAND, p.present(result(210, Feedback.STAND, SquatPhase.NOT_READY)))
    }
}
