package com.kuet.gymtest

import com.kuet.gymtest.analysis.*
import com.kuet.gymtest.session.*
import org.junit.Assert.*
import org.junit.Test

/** Test-only strategy outside the analysis package: no production exercise is added. */
class ExerciseExtensionTest {
    private data class Message(override val key: String, override val text: String,
        override val priority: Int) : AnalysisFeedback

    private data class Result(
        override val key: FrameKey, override val timestampMs: Long,
        override val repetitions: Int, override val repetition: RepetitionCompleted?,
        override val feedback: AnalysisFeedback,
        override val trackingValid: Boolean = true,
        override val presentation: AnalysisPresentation = AnalysisPresentation("open", "Open", true, "Test detail")
    ) : ExerciseAnalysisResult

    private class TestAnalyzer : ExerciseAnalyzer {
        private var count = 0
        override fun processFrame(pose: PoseFrame): ExerciseAnalysisResult {
            count++
            return Result(pose.key, pose.timestampMs, count, RepetitionCompleted(count, pose.timestampMs),
                Message("test.counted", "Test repetition", 5))
        }
        override fun reset() { count = 0 }
        override fun invalidateTracking(reason: DiscontinuityReason) = Unit
    }

    @Test fun registryUsesExactNamesAndFreshAnalyzerInstances() {
        val types = mapOf("Bodyweight Squat" to SquatAnalyzer::class.java,
            "Push-up" to PushUpAnalyzer::class.java, "Jumping Jacks" to JumpingJackAnalyzer::class.java,
            "Biceps Curl" to BicepsCurlAnalyzer::class.java)
        types.forEach { (name, type) ->
            val definition = ExerciseMonitorRegistry.find(name)!!
            assertEquals(type, definition.createAnalyzer().javaClass)
            assertNotSame(definition.createAnalyzer(), definition.createAnalyzer())
        }
        listOf("Goblet Squat", "Wall Push-up", "Diamond Push-up", "Jumping Jack", "jumping jacks", "High Knees", "bodyweight squat", "").forEach {
            assertNull(ExerciseMonitorRegistry.find(it))
        }
    }

    @Test fun independentStrategyUsesExistingLedgerAndHistoryWithPartialSets() {
        val definition = ExerciseMonitorDefinition("Test movement", "Test monitoring", "Test setup", ::TestAnalyzer)
        assertFalse(definition.summariesEnabled)
        val analyzer = definition.createAnalyzer()
        val controller = WorkoutSessionController(definition.exerciseName, 2, 2)
        controller.start()
        fun observe(sequence: Long): ExerciseAnalysisResult {
            val p = controller.state
            return analyzer.processFrame(PoseFrame(FrameKey(p.sessionId, p.cameraEpoch, p.analysisEpoch, sequence),
                sequence * 100, 100, 100, emptyList()))
        }
        val first = observe(1)
        assertTrue(controller.accept(first))
        assertFalse(controller.accept(first))
        assertTrue(controller.accept(observe(2)))
        assertEquals(SessionStatus.BETWEEN_SETS, controller.state.status)
        assertFalse(controller.accept(observe(3)))
        controller.nextSet(); analyzer.reset()
        assertTrue(controller.accept(observe(4)))
        val record = WorkoutRecord.measured(controller.finish(1000)!!)
        assertEquals("Test movement", record.exerciseName)
        assertEquals(listOf(2, 1), record.perSetReps)
        assertEquals(3, record.totalReps)
        assertEquals(1, record.completedSets)
        assertEquals(false, record.fullyCompleted)
        assertEquals(listOf(record), WorkoutHistoryCodec.decode(WorkoutHistoryCodec.encode(listOf(record))))
        assertNull(controller.finish(1001))
    }

    @Test fun customPhasesAndMessagesPreservePriorityReadinessAndHoldRules() {
        val presenter = FeedbackPresenter()
        val key = FrameKey("test", 1, 1, 1)
        fun result(time: Long, message: Message, phase: String, ready: Boolean) =
            Result(key, time, 0, null, message, presentation = AnalysisPresentation(phase, phase, ready))
        val open = Message("test.open", "Open arms", 4)
        val close = Message("test.close", "Close arms", 4)
        val lost = Message("test.lost", "Tracking lost", 1)
        assertEquals(open, presenter.present(result(100, open, "open", true)))
        assertEquals(open, presenter.present(result(150, close, "open", true)))
        assertEquals(close, presenter.present(result(200, close, "closed", true)))
        assertEquals(lost, presenter.present(result(210, lost, "acquire", false)))
        assertEquals(lost, presenter.present(result(900, open, "acquire", false)))
        assertEquals(open, presenter.present(result(1000, open, "open", true)))
        presenter.reset()
        assertEquals(close, presenter.present(result(1100, close, "acquire", false)))
    }

    @Test fun recreatedEquivalentFeedbackDoesNotRestartDisplayHold() {
        val presenter = FeedbackPresenter()
        fun message() = object : AnalysisFeedback {
            override val key = "test.open"
            override val text = "Open arms"
            override val priority = 4
        }
        fun result(time: Long, feedback: AnalysisFeedback) = Result(FrameKey("test", 1, 1, time),
            time, 0, null, feedback)
        presenter.present(result(100, message()))
        presenter.present(result(700, message()))
        val next = Message("test.adjust", "Adjust position", 4)
        assertEquals(next, presenter.present(result(750, next)))
    }
}
