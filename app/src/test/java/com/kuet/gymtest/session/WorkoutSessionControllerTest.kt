package com.kuet.gymtest.session

import com.kuet.gymtest.analysis.*
import org.junit.Assert.*
import org.junit.Test

class WorkoutSessionControllerTest {
    private fun controller() = WorkoutSessionController("Bodyweight Squat", 2, 2).apply { bindCamera(1) }
    private fun event(c: WorkoutSessionController, ordinal: Int, sequence: Long = ordinal.toLong()) = SquatAnalysisResult(
        FrameKey(c.state.sessionId, c.state.cameraEpoch, c.state.analysisEpoch, sequence), sequence * 1000,
        ordinal, RepetitionCompleted(ordinal, sequence * 1000), Feedback.COUNTED, true,
        SquatPhase.STANDING, DepthStatus.ABOVE_TARGET, BodySide.LEFT, null)

    @Test fun explicitStartPauseResumeAndOldEpochRejection() {
        val c = controller(); assertFalse(c.accept(event(c, 1)))
        c.start(); assertTrue(c.accept(event(c, 1)))
        val old = event(c, 2); c.pause(); assertFalse(c.accept(old))
        c.resume(); assertFalse(c.accept(old)); assertEquals(1, c.state.totalReps)
        assertTrue(c.accept(event(c, 1))); assertEquals(SessionStatus.BETWEEN_SETS, c.state.status)
    }
    @Test fun manualSetsFreezeAndDeduplicate() {
        val c = controller(); c.start(); val first = event(c, 1)
        c.accept(first); assertFalse(c.accept(first)); c.accept(event(c, 2))
        assertEquals(listOf(2), c.state.perSet); assertFalse(c.accept(event(c, 3)))
        c.nextSet(); assertEquals(listOf(2, 0), c.state.perSet)
        c.accept(event(c, 1)); c.accept(event(c, 2))
        assertEquals(SessionStatus.COMPLETE, c.state.status)
        c.nextSet(); assertEquals(listOf(2, 2), c.state.perSet)
        assertTrue(c.finish(123)!!.fullyCompleted); assertNull(c.finish(124))
    }
    @Test fun resetClearsEntireLedgerAndChangesSessionIdentity() {
        val c = controller(); c.start(); c.accept(event(c, 1)); val old = event(c, 2)
        c.reset(); assertEquals(0, c.state.totalReps); assertEquals(SessionStatus.SETUP, c.state.status)
        c.start(); assertFalse(c.accept(old)); assertEquals(0, c.state.totalReps)
    }
    @Test fun emptyAndPartialFinishAreAccurate() {
        assertNull(controller().finish(10))
        val c = controller(); c.start(); c.accept(event(c, 1)); c.accept(event(c, 2)); c.nextSet(); c.accept(event(c, 1))
        val summary = c.finish(99)!!
        assertEquals(listOf(2, 1), summary.perSet); assertEquals(3, summary.totalReps)
        assertEquals(1, summary.completedSets); assertFalse(summary.fullyCompleted)
    }
    @Test fun restorationPreservesLedgerButNeverRunningMotion() {
        val c = controller(); c.start(); c.accept(event(c, 1)); val old = event(c, 2)
        val p = WorkoutProgressCodec.decode(WorkoutProgressCodec.encode(c.state))!!
        assertEquals(SessionStatus.PAUSED, p.status)
        val restored = WorkoutSessionController("Bodyweight Squat", 2, 2, p)
        assertEquals(1, restored.state.totalReps); restored.resume(); assertFalse(restored.accept(old))
        assertNull(WorkoutProgressCodec.decode(listOf("bad")))
    }
    @Test fun cameraSwitchAndNewSessionCannotCompleteAnOldRep() {
        val c = controller(); c.start(); val old = event(c, 1)
        c.bindCamera(2); assertEquals(SessionStatus.PAUSED, c.state.status)
        c.resume(); assertFalse(c.accept(old)); assertFalse(controller().accept(old))
    }

    @Test fun watchdogInvalidatesPendingCompletionWithoutPausingOrLosingProgress() {
        val c = controller(); c.start(); c.accept(event(c, 1))
        val queued = event(c, 2)
        c.interruptTracking()
        assertEquals(SessionStatus.RUNNING, c.state.status)
        assertEquals(1, c.state.totalReps)
        assertFalse(c.accept(queued))
        assertTrue(c.accept(event(c, 1)))
        assertEquals(2, c.state.totalReps)
    }

    @Test fun pauseAtBottomRequiresANewObservedSquatCycle() {
        val c = controller()
        val analyzer = SquatAnalyzer()
        var time = 0L
        var angle = 178.0
        fun move(to: Double, duration: Long) {
            val start = angle
            val samples = (duration / 33).toInt()
            repeat(samples) { i ->
                time += 33
                angle = start + (to - start) * (i + 1) / samples
                val p = c.state
                val pose = squatFrame(time, angle).copy(key = FrameKey(p.sessionId, p.cameraEpoch, p.analysisEpoch, time))
                if (c.accepts(pose.key)) c.accept(analyzer.processFrame(pose))
            }
        }
        c.start()
        move(178.0, 700); move(85.0, 1000); move(85.0, 400)
        c.pause(); analyzer.reset()
        move(178.0, 1000)
        assertEquals(0, c.state.totalReps)
        c.resume(); analyzer.reset()
        move(178.0, 700)
        assertEquals(0, c.state.totalReps)
        move(85.0, 1000); move(85.0, 400); move(178.0, 1000); move(178.0, 700)
        assertEquals(1, c.state.totalReps)
    }
}
