package com.kuet.gymtest.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class BicepsCurlAnalyzerTest {
    private var sequence = 0L
    private var time = 0L
    private val blank = PoseLandmark(.5f, .5f, 0f, 0f, 0f)

    private fun frame(angle: Double, arm: BodySide = BodySide.LEFT,
                      elbowShift: Double = 0.0, bodyShift: Double = 0.0, visible: Boolean = true,
                      stepMs: Long = 50): PoseFrame {
        sequence++
        time += stepMs
        val landmarks = MutableList(33) { blank }
        val shoulderX = (if (arm == BodySide.LEFT) .45 else .55) + bodyShift
        val shoulderY = .25
        val elbowX = shoulderX + elbowShift
        val elbowY = .42
        val radians = Math.toRadians(angle) + atan2(-elbowShift, .17)
        val wristX = elbowX + .16 * sin(radians)
        val wristY = elbowY - .16 * cos(radians)
        val confidence = if (visible) .95f else .1f
        fun landmark(x: Double, y: Double) = PoseLandmark(x.toFloat(), y.toFloat(), 0f, confidence, confidence)
        val elbowIndex = if (arm == BodySide.LEFT) 13 else 14
        val wristIndex = if (arm == BodySide.LEFT) 15 else 16
        landmarks[arm.shoulder] = landmark(shoulderX, shoulderY)
        landmarks[elbowIndex] = landmark(elbowX, elbowY)
        landmarks[wristIndex] = landmark(wristX, wristY)
        landmarks[arm.hip] = landmark(shoulderX, .58)
        return PoseFrame(FrameKey("curl", 1, 1, sequence), time, 1000, 1000, landmarks)
    }

    private fun lead(analyzer: BicepsCurlAnalyzer) = List(5) { analyzer.processFrame(frame(170.0)) }
    private fun cycle(analyzer: BicepsCurlAnalyzer, stepMs: Long = 50): List<BicepsCurlAnalysisResult> =
        (listOf(145.0, 120.0, 95.0, 70.0, 55.0, 55.0, 55.0, 55.0,
            80.0, 105.0, 130.0, 155.0, 170.0, 170.0, 170.0, 170.0))
            .map { analyzer.processFrame(frame(it, stepMs = stepMs)) }

    @Test fun completeCurlCountsOnlyOnReturnToExtended() {
        val analyzer = BicepsCurlAnalyzer()
        assertEquals(BicepsCurlPhase.EXTENDED, lead(analyzer).last().phase)
        val results = cycle(analyzer)
        assertEquals(1, results.last().repetitions)
        assertEquals(1, results.count { it.repetition != null })
        assertEquals(BicepsCurlPhase.EXTENDED, results.last().phase)
        assertEquals(1, results.first { it.repetition != null }.repetition!!.ordinal)
    }

    @Test fun repeatedCyclesDoNotDuplicateCounts() {
        val analyzer = BicepsCurlAnalyzer()
        lead(analyzer)
        val results = cycle(analyzer) + cycle(analyzer) + cycle(analyzer)
        assertEquals(3, results.last().repetitions)
        assertEquals(3, results.count { it.repetition != null })
    }

    @Test fun fasterAndSlowerCadencesEachCountOneCompleteCycle() {
        listOf(25L, 100L).forEach { step ->
            sequence = 0
            time = 0
            val analyzer = BicepsCurlAnalyzer()
            lead(analyzer)
            val results = cycle(analyzer, step)
            assertEquals("step=$step", 1, results.last().repetitions)
            assertEquals("step=$step", 1, results.count { it.repetition != null })
        }
    }

    @Test fun shallowBendAndTopJitterNeverCount() {
        val analyzer = BicepsCurlAnalyzer()
        lead(analyzer)
        val results = (listOf(145.0, 120.0, 110.0, 120.0, 145.0, 170.0, 170.0) +
            List(20) { if (it % 2 == 0) 158.0 else 170.0 }).map { analyzer.processFrame(frame(it)) }
        assertTrue(results.all { it.repetition == null })
        assertEquals(0, results.last().repetitions)
    }

    @Test fun partialCurlReportsOneIncompleteAttempt() {
        val analyzer = BicepsCurlAnalyzer()
        lead(analyzer)
        val results = listOf(130.0, 115.0, 115.0, 145.0, 170.0, 170.0, 170.0)
            .map { analyzer.processFrame(frame(it)) }
        assertEquals(1, results.count { it.uncountedAttempt == UncountedAttempt.INCOMPLETE })
        assertTrue(results.all { it.repetition == null })
        assertEquals(BicepsCurlPhase.EXTENDED, results.last().phase)
    }

    @Test fun trackingLossMidCurlReportsOneInterruptedAttemptButIdleLossDoesNot() {
        val analyzer = BicepsCurlAnalyzer()
        val idle = analyzer.processFrame(frame(170.0, visible = false))
        assertNull(idle.uncountedAttempt)
        lead(analyzer)
        val active = analyzer.processFrame(frame(120.0))
        assertTrue(active.attemptInProgress)
        val lost = analyzer.processFrame(frame(100.0, visible = false))
        assertEquals(UncountedAttempt.TRACKING_INTERRUPTED, lost.uncountedAttempt)
        assertFalse(lost.attemptInProgress)
        assertNull(analyzer.processFrame(frame(100.0, visible = false)).uncountedAttempt)
    }

    @Test fun rightArmCanBeSelectedWithoutLeftArm() {
        val analyzer = BicepsCurlAnalyzer()
        val results = List(5) { analyzer.processFrame(frame(170.0, BodySide.RIGHT)) }
        assertEquals(BodySide.RIGHT, results.last().side)
        assertEquals(BicepsCurlPhase.EXTENDED, results.last().phase)
    }

    @Test fun missingArmCancelsPartialCycleAndNeedsFreshStart() {
        val analyzer = BicepsCurlAnalyzer()
        lead(analyzer)
        listOf(120.0, 70.0, 55.0, 55.0).forEach { analyzer.processFrame(frame(it)) }
        val lost = analyzer.processFrame(frame(55.0, visible = false))
        assertFalse(lost.trackingValid)
        assertEquals(BicepsCurlPhase.NOT_READY, lost.phase)
        val results = List(6) { analyzer.processFrame(frame(170.0)) }
        assertTrue(results.all { it.repetition == null })
        assertEquals(0, results.last().repetitions)
    }

    @Test fun elbowMovementStillCountsCompleteCurlButFlagsForm() {
        val analyzer = BicepsCurlAnalyzer()
        lead(analyzer)
        val angles = listOf(145.0, 120.0, 95.0, 70.0, 55.0, 55.0, 55.0, 55.0,
            80.0, 105.0, 130.0, 155.0, 170.0, 170.0, 170.0, 170.0)
        val results = angles.map { analyzer.processFrame(frame(it, elbowShift = .14)) }
        assertTrue(results.all { it.trackingValid })
        val counted = results.first { it.repetition != null }
        assertEquals(RepFormQuality.NEEDS_ATTENTION, counted.repetition!!.assessment!!.quality)
        assertEquals("Elbow moved", counted.repetition!!.assessment!!.note)
        assertEquals(BicepsCurlFeedback.COUNTED_ELBOW_MOVED, counted.feedback)
        assertEquals(1, results.last().repetitions)
    }

    @Test fun wholeBodyTranslationDoesNotFlagElbowForm() {
        val analyzer = BicepsCurlAnalyzer()
        lead(analyzer)
        val angles = listOf(145.0, 120.0, 95.0, 70.0, 55.0, 55.0, 55.0, 55.0,
            80.0, 105.0, 130.0, 155.0, 170.0, 170.0, 170.0, 170.0)
        val results = angles.map { analyzer.processFrame(frame(it, bodyShift = .08)) }
        assertEquals(RepFormQuality.GOOD, results.first { it.repetition != null }.repetition!!.assessment!!.quality)
    }

    @Test fun swingingStraightArmDoesNotFabricateCurl() {
        val analyzer = BicepsCurlAnalyzer()
        lead(analyzer)
        val results = List(20) { index ->
            analyzer.processFrame(frame(170.0, elbowShift = if (index % 2 == 0) .14 else 0.0))
        }
        assertTrue(results.all { it.repetition == null })
        assertEquals(0, results.last().repetitions)
    }

    @Test fun gapAndResetDiscardPartialState() {
        val analyzer = BicepsCurlAnalyzer()
        lead(analyzer)
        analyzer.processFrame(frame(120.0))
        val gap = analyzer.processFrame(frame(55.0, stepMs = 400))
        assertFalse(gap.trackingValid)
        assertEquals(0, gap.repetitions)
        analyzer.reset()
        assertEquals(0, analyzer.processFrame(frame(170.0)).repetitions)
    }

    @Test fun invalidGeometryNeverCounts() {
        val analyzer = BicepsCurlAnalyzer()
        lead(analyzer)
        val frame = frame(60.0)
        val invalid = frame.copy(landmarks = frame.landmarks.toMutableList().apply {
            this[15] = this[15].copy(x = Float.NaN)
        })
        assertFalse(analyzer.processFrame(invalid).trackingValid)
    }

    @Test fun traceExplainsPhaseAnglesBlockerAndCountEvent() {
        val lines = mutableListOf<String>()
        val analyzer = BicepsCurlAnalyzer(diagnostic = lines::add)
        lead(analyzer)
        cycle(analyzer)
        assertTrue(lines.any { it.contains("from=NOT_READY to=EXTENDED") && it.contains("side=LEFT") })
        assertTrue(lines.any { it.contains("rawElbow=") && it.contains("smoothElbow=") &&
            it.contains("candidate=") && it.contains("dt=") })
        assertTrue(lines.any { it.contains("event=1") })
        analyzer.processFrame(frame(120.0, elbowShift = .14))
        analyzer.processFrame(frame(95.0, elbowShift = .14))
        assertTrue(lines.any { it.contains("relativeElbowTravel=") && it.contains("formIssue=true") })
        analyzer.processFrame(frame(120.0, visible = false))
        assertTrue(lines.any { it.contains("invalidate reason=TRACKING") })
    }
}
