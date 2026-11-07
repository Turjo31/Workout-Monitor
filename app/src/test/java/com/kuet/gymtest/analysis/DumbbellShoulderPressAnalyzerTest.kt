package com.kuet.gymtest.analysis

import com.kuet.gymtest.session.SetAttemptCounts
import com.kuet.gymtest.session.SetFormCounts
import com.kuet.gymtest.session.WorkoutSessionController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DumbbellShoulderPressAnalyzerTest {
    private var sequence = 0L
    private var time = 0L
    private val blank = PoseLandmark(.5f, .5f, 0f, 0f, 0f)

    private fun frame(left: Double, right: Double = left, visible: Boolean = true,
                      frontView: Boolean = true, bentElbows: Boolean = false,
                      stepMs: Long = 50): PoseFrame {
        sequence++
        time += stepMs
        val marks = MutableList(33) { blank }
        val confidence = if (visible) .95f else .1f
        val width = if (frontView) .20 else .025
        fun put(i: Int, x: Double, y: Double) {
            marks[i] = PoseLandmark(x.toFloat(), y.toFloat(), 0f, confidence, confidence)
        }
        fun arm(shoulder: Int, elbow: Int, wrist: Int, hip: Int,
                x: Double, direction: Double, progress: Double) {
            put(shoulder, x, .48)
            val elbowOffset = if (bentElbows && progress >= .85) .17 else .12 - .08 * progress
            put(elbow, x + direction * elbowOffset, .57 - .26 * progress)
            put(wrist, x + direction * (.16 - .12 * progress), .46 - .32 * progress)
            put(hip, x, .78)
        }
        arm(11, 13, 15, 23, .5 + width / 2, 1.0, left)
        arm(12, 14, 16, 24, .5 - width / 2, -1.0, right)
        return PoseFrame(FrameKey("press", 1, 1, sequence), time, 1000, 1000, marks)
    }

    private fun ready(a: DumbbellShoulderPressAnalyzer) = List(4) { a.processFrame(frame(0.0)) }
    private fun cycle(a: DumbbellShoulderPressAnalyzer, stepMs: Long = 50) =
        listOf(.35, .60, .85, 1.0, 1.0, 1.0, 1.0, 1.0,
            .8, .5, .2, 0.0, 0.0, 0.0, 0.0).map { a.processFrame(frame(it, stepMs = stepMs)) }

    @Test fun completeBilateralPressCountsOnceWithGoodForm() {
        val a = DumbbellShoulderPressAnalyzer()
        assertEquals(ShoulderPressPhase.RACKED, ready(a).last().phase)
        val results = cycle(a)
        assertEquals(1, results.count { it.repetition != null })
        assertEquals(1, results.last().repetitions)
        assertEquals(RepFormQuality.GOOD, results.first { it.repetition != null }.repetition!!.assessment!!.quality)
    }

    @Test fun repeatedCadencesCountWithoutDuplicates() {
        listOf(25L, 50L, 100L).forEach { step ->
            sequence = 0; time = 0
            val a = DumbbellShoulderPressAnalyzer()
            ready(a)
            val results = cycle(a, step) + cycle(a, step) + cycle(a, step)
            assertEquals("step=$step", 3, results.count { it.repetition != null })
        }
    }

    @Test fun oneArmOnlyAndPartialPressDoNotCount() {
        val a = DumbbellShoulderPressAnalyzer()
        ready(a)
        assertTrue(List(8) { a.processFrame(frame(1.0, 0.0)) }.all { it.repetition == null })
        val returned = List(5) { a.processFrame(frame(0.0)) }
        assertEquals(1, returned.count { it.uncountedAttempt == UncountedAttempt.INCOMPLETE })
    }

    @Test fun visibleBentElbowsAtTopCountWithFormAttention() {
        val a = DumbbellShoulderPressAnalyzer()
        ready(a)
        listOf(.35, .60, .85, 1.0, 1.0, 1.0, 1.0, 1.0).forEach {
            a.processFrame(frame(it, bentElbows = it >= .85))
        }
        val results = listOf(.8, .5, .2, 0.0, 0.0, 0.0, 0.0).map { a.processFrame(frame(it)) }
        val counted = results.first { it.repetition != null }
        assertEquals(RepFormQuality.NEEDS_ATTENTION, counted.repetition!!.assessment!!.quality)
        assertEquals(ShoulderPressFeedback.COUNTED_FORM, counted.feedback)
    }

    @Test fun trackingLossInterruptsAttemptAndRequiresFreshRack() {
        val a = DumbbellShoulderPressAnalyzer()
        ready(a)
        assertTrue(a.processFrame(frame(.6)).attemptInProgress)
        val lost = a.processFrame(frame(.9, visible = false))
        assertFalse(lost.trackingValid)
        assertEquals(UncountedAttempt.TRACKING_INTERRUPTED, lost.uncountedAttempt)
        assertNull(a.processFrame(frame(.9, visible = false)).uncountedAttempt)
        assertTrue(List(5) { a.processFrame(frame(1.0)) }.all { it.repetition == null })
    }

    @Test fun invalidViewGapAndResetCannotFabricateRep() {
        val a = DumbbellShoulderPressAnalyzer()
        assertEquals(ShoulderPressFeedback.FRONT_VIEW, a.processFrame(frame(0.0, frontView = false)).feedback)
        ready(a)
        a.processFrame(frame(.6))
        assertEquals(UncountedAttempt.TRACKING_INTERRUPTED,
            a.processFrame(frame(1.0, stepMs = 400)).uncountedAttempt)
        a.reset()
        assertEquals(0, a.processFrame(frame(0.0)).repetitions)
    }

    @Test fun sessionSummaryIncludesCountedAndIncompleteAttempt() {
        val a = DumbbellShoulderPressAnalyzer()
        val controller = WorkoutSessionController("Dumbbell Shoulder Press", 1, 2)
            .apply { bindCamera(1); start() }
        fun accept(frame: PoseFrame): ShoulderPressAnalysisResult {
            val key = frame.key.copy(sessionId = controller.state.sessionId,
                cameraEpoch = controller.state.cameraEpoch, analysisEpoch = controller.state.analysisEpoch)
            return a.processFrame(frame.copy(key = key)).also { controller.accept(it) }
        }
        List(4) { accept(frame(0.0)) }
        listOf(.35, .60, .85, 1.0, 1.0, 1.0, 1.0, 1.0,
            .8, .5, .2, 0.0, 0.0, 0.0, 0.0).forEach { accept(frame(it)) }
        listOf(.6, 0.0, 0.0, 0.0, 0.0).forEach { accept(frame(it)) }
        val summary = controller.summary(time)
        assertEquals(1, summary.totalReps)
        assertEquals(SetFormCounts(good = 1), summary.formBySet.single())
        assertEquals(SetAttemptCounts(incomplete = 1), summary.attemptsBySet.single())
    }
}
