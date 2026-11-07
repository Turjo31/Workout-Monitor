package com.kuet.gymtest.analysis

import com.kuet.gymtest.session.SetAttemptCounts
import com.kuet.gymtest.session.SetFormCounts
import com.kuet.gymtest.session.WorkoutSessionController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class LateralRaiseAnalyzerTest {
    private var sequence = 0L
    private var time = 0L
    private val blank = PoseLandmark(.5f, .5f, 0f, 0f, 0f)

    private fun frame(leftAngle: Double, rightAngle: Double = leftAngle,
                      visible: Boolean = true, frontView: Boolean = true,
                      bentElbows: Boolean = false, stepMs: Long = 50): PoseFrame {
        sequence++
        time += stepMs
        val landmarks = MutableList(33) { blank }
        val shoulderWidth = if (frontView) .20 else .04
        val confidence = if (visible) .95f else .1f
        fun put(index: Int, x: Double, y: Double) {
            landmarks[index] = PoseLandmark(x.toFloat(), y.toFloat(), 0f, confidence, confidence)
        }
        fun arm(shoulder: Int, elbow: Int, wrist: Int, hip: Int, x: Double, direction: Double, angle: Double) {
            val radians = Math.toRadians(angle)
            val elbowX = x + direction * .18 * sin(radians)
            val elbowY = .28 + .18 * cos(radians)
            val wristX = if (bentElbows) elbowX else elbowX + direction * .16 * sin(radians)
            val wristY = if (bentElbows) elbowY + .16 else elbowY + .16 * cos(radians)
            put(shoulder, x, .28)
            put(elbow, elbowX, elbowY)
            put(wrist, wristX, wristY)
            put(hip, x, .62)
        }
        arm(11, 13, 15, 23, .5 + shoulderWidth / 2, 1.0, leftAngle)
        arm(12, 14, 16, 24, .5 - shoulderWidth / 2, -1.0, rightAngle)
        return PoseFrame(FrameKey("lateral", 1, 1, sequence), time, 1000, 1000, landmarks)
    }

    private fun ready(analyzer: LateralRaiseAnalyzer) = List(4) { analyzer.processFrame(frame(10.0)) }
    private fun cycle(analyzer: LateralRaiseAnalyzer, stepMs: Long = 50) =
        listOf(45.0, 60.0, 80.0, 90.0, 95.0, 95.0, 95.0, 95.0,
            70.0, 50.0, 25.0, 10.0, 10.0, 10.0, 10.0)
            .map { analyzer.processFrame(frame(it, stepMs = stepMs)) }

    @Test fun completeBilateralCycleCountsExactlyOnceWithGoodForm() {
        val analyzer = LateralRaiseAnalyzer()
        assertEquals(LateralRaisePhase.LOWERED, ready(analyzer).last().phase)
        val results = cycle(analyzer)
        assertEquals(1, results.count { it.repetition != null })
        assertEquals(1, results.last().repetitions)
        assertEquals(RepFormQuality.GOOD, results.first { it.repetition != null }.repetition!!.assessment!!.quality)
        assertEquals(LateralRaisePhase.LOWERED, results.last().phase)
    }

    @Test fun repeatedAndDifferentCadenceCyclesDoNotDuplicate() {
        listOf(25L, 50L, 100L).forEach { step ->
            sequence = 0; time = 0
            val analyzer = LateralRaiseAnalyzer()
            ready(analyzer)
            val results = cycle(analyzer, step) + cycle(analyzer, step) + cycle(analyzer, step)
            assertEquals("step=$step", 3, results.count { it.repetition != null })
            assertEquals("step=$step", 3, results.last().repetitions)
        }
    }

    @Test fun oneArmOnlyAndShallowPartialReturnDoNotCount() {
        val analyzer = LateralRaiseAnalyzer()
        ready(analyzer)
        val oneArm = List(8) { analyzer.processFrame(frame(95.0, 10.0)) }
        assertTrue(oneArm.all { it.repetition == null })
        val returnDown = List(5) { analyzer.processFrame(frame(10.0)) }
        assertEquals(1, returnDown.count { it.uncountedAttempt == UncountedAttempt.INCOMPLETE })
        assertEquals(0, returnDown.last().repetitions)
    }

    @Test fun visibleFullCycleWithUnevenArmsCountsButNeedsAttention() {
        val analyzer = LateralRaiseAnalyzer()
        ready(analyzer)
        val up = listOf(45.0, 60.0, 80.0, 95.0, 110.0, 110.0, 110.0, 110.0)
            .map { analyzer.processFrame(frame(it, if (it >= 80.0) 75.0 else it)) }
        assertEquals(LateralRaisePhase.RAISED, up.last().phase)
        val down = listOf(70.0, 50.0, 25.0, 10.0, 10.0, 10.0, 10.0)
            .map { analyzer.processFrame(frame(it)) }
        val event = down.first { it.repetition != null }
        assertEquals(RepFormQuality.NEEDS_ATTENTION, event.repetition!!.assessment!!.quality)
        assertEquals(LateralRaiseFeedback.COUNTED_FORM, event.feedback)
    }

    @Test fun bentElbowsAtTheTopAreFormFeedbackNotARejectedRep() {
        val analyzer = LateralRaiseAnalyzer()
        ready(analyzer)
        listOf(45.0, 60.0, 80.0, 90.0, 95.0, 95.0, 95.0, 95.0)
            .forEach { analyzer.processFrame(frame(it, bentElbows = it >= 80.0)) }
        val results = listOf(70.0, 50.0, 25.0, 10.0, 10.0, 10.0, 10.0)
            .map { analyzer.processFrame(frame(it)) }
        assertEquals(RepFormQuality.NEEDS_ATTENTION,
            results.first { it.repetition != null }.repetition!!.assessment!!.quality)
    }

    @Test fun missingWristCannotFinishAnActiveRaise() {
        val analyzer = LateralRaiseAnalyzer()
        ready(analyzer)
        assertTrue(analyzer.processFrame(frame(60.0)).attemptInProgress)
        val missing = frame(90.0).let { pose ->
            pose.copy(landmarks = pose.landmarks.toMutableList().apply { this[15] = blank })
        }
        val invalid = analyzer.processFrame(missing)
        assertFalse(invalid.trackingValid)
        assertEquals(UncountedAttempt.TRACKING_INTERRUPTED, invalid.uncountedAttempt)
        assertTrue(List(5) { analyzer.processFrame(frame(95.0)) }.all { it.repetition == null })
    }

    @Test fun lostTrackingMidRaiseIsReportedOnceAndRequiresNewLoweredStart() {
        val analyzer = LateralRaiseAnalyzer()
        ready(analyzer)
        val active = analyzer.processFrame(frame(60.0))
        assertTrue(active.attemptInProgress)
        val lost = analyzer.processFrame(frame(80.0, visible = false))
        assertEquals(UncountedAttempt.TRACKING_INTERRUPTED, lost.uncountedAttempt)
        assertFalse(lost.attemptInProgress)
        assertNull(analyzer.processFrame(frame(90.0, visible = false)).uncountedAttempt)
        assertTrue(List(5) { analyzer.processFrame(frame(95.0)) }.all { it.repetition == null })
    }

    @Test fun sideViewFramingGapAndResetCannotFabricateARep() {
        val analyzer = LateralRaiseAnalyzer()
        assertEquals(LateralRaiseFeedback.FRONT_VIEW,
            analyzer.processFrame(frame(10.0, frontView = false)).feedback)
        ready(analyzer)
        analyzer.processFrame(frame(60.0))
        val gap = analyzer.processFrame(frame(95.0, stepMs = 400))
        assertEquals(UncountedAttempt.TRACKING_INTERRUPTED, gap.uncountedAttempt)
        analyzer.reset()
        assertEquals(0, analyzer.processFrame(frame(10.0)).repetitions)
    }

    @Test fun sessionSummarySeparatesCountedFormAndUncountedAttempts() {
        val analyzer = LateralRaiseAnalyzer()
        val controller = WorkoutSessionController("Lateral Raise", 1, 2).apply { bindCamera(1); start() }
        fun accept(frame: PoseFrame): LateralRaiseAnalysisResult {
            val key = frame.key.copy(sessionId = controller.state.sessionId,
                cameraEpoch = controller.state.cameraEpoch, analysisEpoch = controller.state.analysisEpoch)
            return analyzer.processFrame(frame.copy(key = key)).also { controller.accept(it) }
        }
        List(4) { accept(frame(10.0)) }
        listOf(45.0, 60.0, 80.0, 90.0, 95.0, 95.0, 95.0, 95.0,
            70.0, 50.0, 25.0, 10.0, 10.0, 10.0, 10.0).forEach { accept(frame(it)) }
        listOf(60.0, 10.0, 10.0, 10.0, 10.0).forEach { accept(frame(it)) }
        val summary = controller.summary(time)
        assertEquals(1, summary.totalReps)
        assertEquals(SetFormCounts(good = 1), summary.formBySet.single())
        assertEquals(SetAttemptCounts(incomplete = 1), summary.attemptsBySet.single())
    }
}
