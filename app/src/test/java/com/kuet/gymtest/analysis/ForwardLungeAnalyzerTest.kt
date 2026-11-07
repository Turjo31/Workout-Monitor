package com.kuet.gymtest.analysis

import com.kuet.gymtest.session.SetAttemptCounts
import com.kuet.gymtest.session.SetFormCounts
import com.kuet.gymtest.session.WorkoutSessionController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardLungeAnalyzerTest {
    private var sequence = 0L
    private var time = 0L
    private val blank = PoseLandmark(.5f, .5f, 0f, 0f, 0f)

    private fun frame(progress: Double, visible: Boolean = true, frontView: Boolean = false,
                      shallow: Boolean = false, attention: Boolean = false,
                      stepMs: Long = 50, missingRightKnee: Boolean = false,
                      noFootStep: Boolean = false, travelScale: Double = 1.0,
                      footProgress: Double = progress): PoseFrame {
        sequence++
        time += stepMs
        val marks = MutableList(33) { blank }
        val confidence = if (visible) .95f else .1f
        fun put(i: Int, x: Double, y: Double) {
            marks[i] = PoseLandmark(x.toFloat(), y.toFloat(), 0f, confidence, confidence)
        }
        val halfWidth = if (frontView) .115 else .01
        val leftHip = .5 - halfWidth
        val rightHip = .5 + halfWidth
        put(11, leftHip, .20); put(12, rightHip, .20)
        put(23, leftHip, .48); put(24, rightHip, .48)
        val kneeTarget = if (shallow) .65 else if (attention) .72 else .75
        val kneeY = if (shallow) .68 else if (attention) .61 else .59
        put(25, .475 + (kneeTarget - .475) * progress, .67 + (kneeY - .67) * progress)
        put(26, .525 + (.37 - .525) * progress, .67)
        put(27, if (noFootStep) .46 else .46 + (.75 - .46) * footProgress * travelScale, .87)
        put(28, if (noFootStep) .54 else .54 + (.26 - .54) * footProgress * travelScale, .87)
        if (missingRightKnee) marks[26] = blank
        return PoseFrame(FrameKey("lunge", 1, 1, sequence), time, 1000, 1000, marks)
    }

    private fun ready(a: ForwardLungeAnalyzer) = List(4) { a.processFrame(frame(0.0)) }
    private fun cycle(a: ForwardLungeAnalyzer, stepMs: Long = 50) =
        listOf(.3, .6, .9, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
            .8, .5, .2, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0).map { a.processFrame(frame(it, stepMs = stepMs)) }

    @Test fun completeSideViewCycleCountsOneGoodRep() {
        val a = ForwardLungeAnalyzer()
        assertEquals(ForwardLungePhase.STANDING, ready(a).last().phase)
        val results = cycle(a)
        assertEquals(1, results.count { it.repetition != null })
        assertEquals(RepFormQuality.GOOD, results.first { it.repetition != null }.repetition!!.assessment!!.quality)
        assertEquals(1, results.last().repetitions)
    }

    @Test fun occludedRearKneeDoesNotResetVisibleLegCycle() {
        val a = ForwardLungeAnalyzer()
        List(4) { a.processFrame(frame(0.0, missingRightKnee = true)) }
        val results = listOf(.3, .6, .9, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
            .8, .5, .2, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
            .map { a.processFrame(frame(it, missingRightKnee = true)) }
        assertTrue(results.all { it.trackingValid })
        assertEquals(1, results.count { it.repetition != null })
    }

    @Test fun kneeBendingWithoutFootStepCannotCountAsLunge() {
        val a = ForwardLungeAnalyzer()
        ready(a)
        val results = listOf(.3, .6, .9, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
            .8, .5, .2, 0.0, 0.0, 0.0, 0.0).map { a.processFrame(frame(it, noFootStep = true)) }
        assertTrue(results.all { it.repetition == null })
        assertEquals(0, results.last().repetitions)
    }

    @Test fun phoneLikeFootTravelCanReachBottomButShortMotionCannot() {
        val full = ForwardLungeAnalyzer()
        ready(full)
        val motion = listOf(.3, .6, .9, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
            .8, .5, .2, 0.0, 0.0, 0.0, 0.0)
        val fullResults = motion.map { full.processFrame(frame(it, travelScale = .68)) }
        assertEquals(1, fullResults.count { it.repetition != null })
        val short = ForwardLungeAnalyzer()
        ready(short)
        val shortResults = motion.map { short.processFrame(frame(it, travelScale = .48)) }
        assertTrue(shortResults.all { it.repetition == null })
    }

    @Test fun confirmedStepStillCountsWhenHipRelativeTravelShrinksDuringDescent() {
        val a = ForwardLungeAnalyzer()
        List(4) { a.processFrame(frame(0.0, missingRightKnee = true)) }
        val movement = List(4) { 0.0 to .65 } +
            listOf(.3 to .55, .7 to .45) + List(6) { 1.0 to .35 } +
            listOf(.7 to .35, .3 to .20) + List(5) { 0.0 to 0.0 }
        val results = movement.map { (knee, foot) ->
            a.processFrame(frame(knee, footProgress = foot, missingRightKnee = true))
        }
        assertTrue(results.any { it.phase == ForwardLungePhase.BOTTOM })
        assertEquals(1, results.count { it.repetition != null })
        assertEquals(RepFormQuality.GOOD, results.first { it.repetition != null }.repetition!!.assessment!!.quality)
    }

    @Test fun returningFootBeforeKneeBendDoesNotCountAsLunge() {
        val a = ForwardLungeAnalyzer()
        List(4) { a.processFrame(frame(0.0, missingRightKnee = true)) }
        val movement = List(4) { 0.0 to .65 } + List(6) { 1.0 to 0.0 } +
            List(5) { 0.0 to 0.0 }
        val results = movement.map { (knee, foot) ->
            a.processFrame(frame(knee, footProgress = foot, missingRightKnee = true))
        }
        assertTrue(results.all { it.phase != ForwardLungePhase.BOTTOM && it.repetition == null })
        assertEquals(0, results.last().repetitions)
    }

    @Test fun repeatedAndDifferentCadencesDoNotDuplicate() {
        listOf(25L, 50L, 100L).forEach { step ->
            sequence = 0; time = 0
            val a = ForwardLungeAnalyzer()
            ready(a)
            val results = cycle(a, step) + cycle(a, step) + cycle(a, step)
            assertEquals("step=$step", 3, results.count { it.repetition != null })
        }
    }

    @Test fun shallowStepReturnsAsIncompleteAttempt() {
        val a = ForwardLungeAnalyzer()
        ready(a)
        val results = List(7) { a.processFrame(frame(.7, shallow = true)) } +
            List(5) { a.processFrame(frame(0.0)) }
        assertTrue(results.all { it.repetition == null })
        assertEquals(1, results.count { it.uncountedAttempt == UncountedAttempt.INCOMPLETE })
    }

    @Test fun completeModerateDepthStillCountsWithFormAttention() {
        val a = ForwardLungeAnalyzer()
        ready(a)
        val results = listOf(.3, .6, .9, 1.0, 1.0, 1.0, 1.0, 1.0,
            .8, .5, .2, 0.0, 0.0, 0.0, 0.0).map { a.processFrame(frame(it, attention = true)) }
        val event = results.first { it.repetition != null }
        assertEquals(RepFormQuality.NEEDS_ATTENTION, event.repetition!!.assessment!!.quality)
        assertEquals(ForwardLungeFeedback.COUNTED_FORM, event.feedback)
    }

    @Test fun trackingLossInterruptsOnceAndRequiresNewStandingStart() {
        val a = ForwardLungeAnalyzer()
        ready(a)
        assertTrue(a.processFrame(frame(.9)).attemptInProgress)
        val lost = a.processFrame(frame(1.0, visible = false))
        assertFalse(lost.trackingValid)
        assertEquals(UncountedAttempt.TRACKING_INTERRUPTED, lost.uncountedAttempt)
        assertNull(a.processFrame(frame(1.0, visible = false)).uncountedAttempt)
        assertTrue(List(5) { a.processFrame(frame(1.0)) }.all { it.repetition == null })
    }

    @Test fun frontViewGapAndResetCannotFabricateRep() {
        val a = ForwardLungeAnalyzer()
        assertEquals(ForwardLungeFeedback.SIDE_VIEW, a.processFrame(frame(0.0, frontView = true)).feedback)
        ready(a)
        a.processFrame(frame(.9))
        assertEquals(UncountedAttempt.TRACKING_INTERRUPTED,
            a.processFrame(frame(1.0, stepMs = 400)).uncountedAttempt)
        a.reset()
        assertEquals(0, a.processFrame(frame(0.0)).repetitions)
    }

    @Test fun sessionSummarySeparatesCountedAndIncompleteAttempts() {
        val a = ForwardLungeAnalyzer()
        val controller = WorkoutSessionController("Forward Lunges", 1, 2).apply { bindCamera(1); start() }
        fun accept(frame: PoseFrame): ForwardLungeAnalysisResult {
            val key = frame.key.copy(sessionId = controller.state.sessionId,
                cameraEpoch = controller.state.cameraEpoch, analysisEpoch = controller.state.analysisEpoch)
            return a.processFrame(frame.copy(key = key)).also { controller.accept(it) }
        }
        List(4) { accept(frame(0.0)) }
        listOf(.3, .6, .9, 1.0, 1.0, 1.0, 1.0, 1.0,
            .8, .5, .2, 0.0, 0.0, 0.0, 0.0).forEach { accept(frame(it)) }
        List(7) { accept(frame(.7, shallow = true)) }
        List(5) { accept(frame(0.0)) }
        val summary = controller.summary(time)
        assertEquals(1, summary.totalReps)
        assertEquals(SetFormCounts(good = 1), summary.formBySet.single())
        assertEquals(SetAttemptCounts(incomplete = 1), summary.attemptsBySet.single())
    }

    @Test fun traceShowsGateBlockersAndTrackingFailureWithoutChangingCounts() {
        val lines = mutableListOf<String>()
        val a = ForwardLungeAnalyzer(diagnostic = lines::add)
        ready(a)
        a.processFrame(frame(.6))
        a.processFrame(frame(1.0, visible = false))
        assertTrue(lines.any { "standingBlockers=" in it && "bottomBlockers=" in it && "raw=" in it })
        assertTrue(lines.any { "sideRatio=" in it && "candidateMs=" in it })
        assertTrue(lines.any { "torso landmark low confidence indices=" in it })
        assertEquals(0, a.processFrame(frame(0.0)).repetitions)
    }
}
