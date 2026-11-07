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

    private fun assessed(c: WorkoutSessionController, ordinal: Int, quality: RepFormQuality) =
        event(c, ordinal).let { it.copy(repetition = it.repetition!!.copy(assessment = RepAssessment(quality))) }

    private fun attempt(c: WorkoutSessionController, sequence: Long, active: Boolean = false,
                        outcome: UncountedAttempt? = null): ExerciseAnalysisResult = object : ExerciseAnalysisResult {
        override val key = FrameKey(c.state.sessionId, c.state.cameraEpoch, c.state.analysisEpoch, sequence)
        override val timestampMs = sequence * 1000
        override val repetitions = 0
        override val repetition: RepetitionCompleted? = null
        override val feedback = Feedback.COUNTED
        override val trackingValid = outcome != UncountedAttempt.TRACKING_INTERRUPTED
        override val presentation = AnalysisPresentation("test", "test", true)
        override val uncountedAttempt = outcome
        override val attemptInProgress = active
    }

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
    @Test fun formCountsFollowAcceptedRepsAndSetsOnly() {
        val c = controller(); c.start()
        val first = assessed(c, 1, RepFormQuality.GOOD)
        assertTrue(c.accept(first)); assertFalse(c.accept(first))
        assertTrue(c.accept(assessed(c, 2, RepFormQuality.NEEDS_ATTENTION)))
        assertEquals(SetFormCounts(1, 1, 0), c.state.formBySet.single())
        c.nextSet()
        assertTrue(c.accept(event(c, 1)))
        val summary = c.summary(100)
        assertEquals(listOf(SetFormCounts(1, 1, 0), SetFormCounts(notAssessed = 1)), summary.formBySet)
        assertEquals(1, summary.goodFormReps)
        assertEquals(1, summary.formIssueReps)
        assertEquals(1, summary.notAssessedReps)
        val restored = WorkoutProgressCodec.decode(WorkoutProgressCodec.encode(c.state))!!
        assertEquals(c.state.formBySet, restored.formBySet)
        c.reset()
        assertEquals(listOf(SetFormCounts()), c.state.formBySet)
    }

    @Test fun legacyProgressDecodesWithUnassessedForm() {
        val values = listOf<Any>("workout-v1", "old", "Bodyweight Squat", 2, 2,
            SessionStatus.PAUSED.name, 4L, 1, 1)
        val decoded = WorkoutProgressCodec.decode(values)!!
        assertEquals(listOf(SetFormCounts(notAssessed = 1)), decoded.formBySet)
        val bad = WorkoutProgressCodec.encode(decoded).apply { this[11] = 99 }
        assertNull(WorkoutProgressCodec.decode(bad))
    }

    @Test fun previousFormProgressDecodesWithZeroUncountedAttempts() {
        val values = listOf<Any>("workout-v2", "old", "Bodyweight Squat", 2, 2,
            SessionStatus.PAUSED.name, 4L, 1, 2, 1, 1, 0)
        val decoded = WorkoutProgressCodec.decode(values)!!
        assertEquals(listOf(SetFormCounts(1, 1, 0)), decoded.formBySet)
        assertEquals(listOf(SetAttemptCounts()), decoded.attemptsBySet)
    }

    @Test fun observedIncompleteAndTrackingInterruptedAttemptsAreSeparateFromReps() {
        val c = controller(); c.start()
        val partial = attempt(c, 1, outcome = UncountedAttempt.INCOMPLETE)
        assertTrue(c.accept(partial)); assertFalse(c.accept(partial))
        assertTrue(c.accept(attempt(c, 2, active = true)))
        c.interruptTracking()
        c.interruptTracking() // No fresh active attempt in the new epoch.
        assertEquals(0, c.state.totalReps)
        assertEquals(SetAttemptCounts(1, 1), c.state.attemptsBySet.single())
        assertEquals(c.state.attemptsBySet,
            WorkoutProgressCodec.decode(WorkoutProgressCodec.encode(c.state))!!.attemptsBySet)
        c.reset()
        assertEquals(listOf(SetAttemptCounts()), c.state.attemptsBySet)
    }

    @Test fun invalidFrameAndWatchdogCannotDoubleReportSameAttempt() {
        val c = controller(); c.start()
        assertTrue(c.accept(attempt(c, 1, active = true)))
        assertTrue(c.accept(attempt(c, 2, outcome = UncountedAttempt.TRACKING_INTERRUPTED)))
        c.interruptTracking()
        assertEquals(SetAttemptCounts(trackingInterrupted = 1), c.state.attemptsBySet.single())
        assertTrue(c.accept(attempt(c, 3, active = true)))
        c.recordTrackingInterruption() // Worker overflow without a new epoch.
        c.recordTrackingInterruption()
        assertEquals(SetAttemptCounts(trackingInterrupted = 2), c.state.attemptsBySet.single())
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
