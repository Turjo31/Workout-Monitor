package com.kuet.gymtest.analysis

import org.junit.Assert.*
import org.junit.Test

class VoiceFeedbackPolicyTest {
    private fun squat(time: Long, phase: SquatPhase, feedback: Feedback = Feedback.LOWER,
                      rep: RepetitionCompleted? = null, epoch: Long = 1) = SquatAnalysisResult(
        FrameKey("session", 1, epoch, time), time, rep?.ordinal ?: 0, rep, feedback, true,
        phase, DepthStatus.ABOVE_TARGET, BodySide.LEFT, SquatMetrics(130.0, 160.0)
    )

    @Test fun readyAndStuckCuesAreDeduplicatedWhileEveryRepIsSpoken() {
        val policy = VoiceFeedbackPolicy()
        assertEquals("Ready. Begin.", policy.observe(squat(100, SquatPhase.STANDING)))
        assertNull(policy.observe(squat(200, SquatPhase.STANDING)))
        assertNull(policy.observe(squat(300, SquatPhase.DESCENDING)))
        assertNull(policy.observe(squat(1600, SquatPhase.DESCENDING)))
        assertEquals("Go a little lower.", policy.observe(squat(1800, SquatPhase.DESCENDING)))
        assertNull(policy.observe(squat(1900, SquatPhase.DESCENDING)))
        assertEquals("1.", policy.observe(squat(2000, SquatPhase.STANDING,
            Feedback.COUNTED, RepetitionCompleted(1, 2000))))
        assertNull(policy.observe(squat(2000, SquatPhase.STANDING,
            Feedback.COUNTED, RepetitionCompleted(1, 2000))))
        assertEquals("2. Try lowering a little farther.", policy.observe(squat(3000, SquatPhase.STANDING,
            Feedback.COUNTED, RepetitionCompleted(2, 3000,
                RepAssessment(RepFormQuality.NEEDS_ATTENTION, "Try lowering a little farther")))))
    }

    @Test fun epochResetAllowsReadyAgainAndDiscardsOldTiming() {
        val policy = VoiceFeedbackPolicy()
        assertEquals("Ready. Begin.", policy.observe(squat(100, SquatPhase.STANDING)))
        assertEquals("Ready. Begin.", policy.observe(squat(50, SquatPhase.STANDING, epoch = 2)))
    }

    @Test fun lateralRaiseAndShoulderPressSpeakRepNumberWithoutArmFormNote() {
        val note = RepAssessment(RepFormQuality.NEEDS_ATTENTION, "Arm position needs attention")
        val key = FrameKey("session", 1, 1, 1)
        val lateral = LateralRaiseAnalysisResult(key, 100, 1,
            RepetitionCompleted(1, 100, note), LateralRaiseFeedback.COUNTED_FORM, true,
            LateralRaisePhase.LOWERED, null)
        val press = ShoulderPressAnalysisResult(key.copy(analysisEpoch = 2), 200, 1,
            RepetitionCompleted(1, 200, note), ShoulderPressFeedback.COUNTED_FORM, true,
            ShoulderPressPhase.RACKED, null)
        val policy = VoiceFeedbackPolicy()
        assertEquals("1.", policy.observe(lateral))
        assertEquals("1.", policy.observe(press))
    }
}
