package com.kuet.gymtest.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

class LungeAnalyzerTest {
    private val stepMs = 33L
    private var seq = 0L
    private val thigh = .17
    private val shin = .18
    private val standingAnkleY = .7999

    private fun lm(p: Pair<Double, Double>, visibility: Float = .95f) =
        PoseLandmark(p.first.toFloat(), p.second.toFloat(), 0f, visibility, .95f)

    /** Two-bone IK: the knee bends toward +x, the direction the person faces. */
    private fun knee(h: Pair<Double, Double>, a: Pair<Double, Double>): Pair<Double, Double> {
        val dx = a.first - h.first
        val dy = a.second - h.second
        val length = hypot(dx, dy)
        val d = length.coerceAtMost(thigh + shin - 1e-6)
        val along = (thigh * thigh - shin * shin + d * d) / (2 * d)
        val height = sqrt(max(0.0, thigh * thigh - along * along))
        val ux = dx / length
        val uy = dy / length
        val p = if (-uy >= uy) Pair(-uy, ux) else Pair(uy, -ux)
        return Pair(h.first + ux * along + p.first * height, h.second + uy * along + p.second * height)
    }

    /**
     * [t] 0 = standing with feet together, 1 = lunge bottom. With [squat] the hips drop but the feet stay together.
     * With [front] the person faces the camera (wide shoulders and hips).
     */
    private fun frame(t: Double, squat: Boolean = false, front: Boolean = false, hiddenKnee: Boolean = false): PoseFrame {
        seq++
        val hipY = .45 + (if (squat) .15 else .17) * t
        val hipA = Pair(if (front) .43 else .5, hipY)
        val hipB = Pair(if (front) .57 else .5, hipY)
        val ankleA = Pair(.5 + (if (squat) 0.0 else .14 * t), standingAnkleY)
        val ankleB = Pair(.5 - (if (squat) 0.0 else .14 * t), standingAnkleY - (if (squat) 0.0 else .06 * t))
        val blank = PoseLandmark(.5f, .5f, 0f, 0f, 0f)
        val l = MutableList(33) { blank }
        l[11] = lm(Pair(if (front) .38 else .5, .28)); l[12] = lm(Pair(if (front) .62 else .5, .28))
        l[23] = lm(hipA); l[24] = lm(hipB)
        l[25] = lm(knee(hipA, ankleA)); l[26] = lm(knee(hipB, ankleB), if (hiddenKnee) .1f else .95f)
        l[27] = lm(ankleA); l[28] = lm(ankleB)
        return PoseFrame(FrameKey("test", 1, 1, seq), seq * stepMs, 1000, 1000, l)
    }

    private fun emptyFrame(): PoseFrame {
        seq++
        return PoseFrame(FrameKey("test", 1, 1, seq), seq * stepMs, 1000, 1000, emptyList())
    }

    private fun ramp(from: Double, to: Double, frames: Int = 36) = (1..frames).map { from + (to - from) * it / frames }
    private fun lead() = List(12) { 0.0 }
    private fun lunge(depth: Double = 1.0) = ramp(0.0, depth) + List(10) { depth } + ramp(depth, 0.0) + List(12) { 0.0 }

    private fun run(a: LungeAnalyzer, path: List<Double>, squat: Boolean = false) =
        path.map { a.processFrame(frame(it, squat = squat)) }

    @Test fun oneFullLungeCountsExactlyOnce() {
        val results = run(LungeAnalyzer(), lead() + lunge())
        assertEquals(1, results.last().repetitions)
        assertEquals(1, results.count { it.repetition != null })
        assertEquals(1, results.first { it.repetition != null }.repetition!!.ordinal)
    }

    @Test fun threeLungesCountThree() {
        val results = run(LungeAnalyzer(), lead() + lunge() + lunge() + lunge())
        assertEquals(3, results.last().repetitions)
        assertEquals(3, results.count { it.repetition != null })
    }

    @Test fun squatWithFeetTogetherNeverCounts() {
        val results = run(LungeAnalyzer(), lead() + lunge(), squat = true)
        assertEquals(0, results.last().repetitions)
    }

    @Test fun shortStepIsNotCountedAndGivesShallowFeedback() {
        val results = run(LungeAnalyzer(), lead() + lunge(.4))
        assertEquals(0, results.last().repetitions)
        assertTrue(results.any { it.feedback == LungeFeedback.SHALLOW })
    }

    @Test fun smallWobbleNeverCounts() {
        val results = run(LungeAnalyzer(), lead() + List(40) { if (it % 2 == 0) 0.0 else .03 })
        assertEquals(0, results.last().repetitions)
    }

    @Test fun readinessNeedsStandingWithFeetTogether() {
        val a = LungeAnalyzer()
        val results = run(a, List(12) { .6 })
        assertTrue(results.all { it.phase == LungePhase.NOT_READY && it.repetitions == 0 })
        assertEquals(LungeFeedback.READY, results.last().feedback)
    }

    @Test fun frontViewGivesSidewaysFeedback() {
        val a = LungeAnalyzer()
        val results = (1..15).map { a.processFrame(frame(0.0, front = true)) }
        assertFalse(results.last().trackingValid)
        assertEquals(LungeFeedback.SIDEWAYS, results.last().feedback)
    }

    @Test fun sustainedHiddenKneeCancelsTracking() {
        val a = LungeAnalyzer()
        run(a, lead())
        val results = (1..6).map { a.processFrame(frame(0.0, hiddenKnee = true)) }
        assertFalse(results.last().trackingValid)
        assertEquals(LungeFeedback.UNCERTAIN, results.last().feedback)
        assertEquals(LungePhase.NOT_READY, results.last().phase)
    }

    @Test fun briefHiddenRearKneeDuringLungeStillCounts() {
        val a = LungeAnalyzer()
        val path = lead() + lunge()
        val hideAt = 12 + 36 + 4 // a few frames into the hold at the bottom
        val results = path.mapIndexed { i, t ->
            a.processFrame(frame(t, hiddenKnee = i in hideAt..hideAt + 1))
        }
        assertEquals(1, results.last().repetitions)
        assertTrue(results.none { it.feedback == LungeFeedback.UNCERTAIN })
    }

    @Test fun trackingLossKeepsCountAndRequiresReacquire() {
        val a = LungeAnalyzer()
        run(a, lead() + lunge())
        val lost = a.processFrame(emptyFrame())
        assertFalse(lost.trackingValid)
        assertEquals(LungeFeedback.NO_POSE, lost.feedback)
        assertEquals(1, lost.repetitions)
        assertEquals(LungePhase.NOT_READY, lost.phase)
        assertEquals(2, run(a, lead() + lunge()).last().repetitions)
    }

    @Test fun resetClearsCount() {
        val a = LungeAnalyzer()
        run(a, lead() + lunge())
        a.reset()
        assertEquals(0, a.processFrame(frame(0.0)).repetitions)
    }

    @Test fun traceReportsFramesWithoutChangingCount() {
        val lines = mutableListOf<String>()
        val results = run(LungeAnalyzer(diagnostic = lines::add), lead() + lunge())
        assertEquals(1, results.last().repetitions)
        assertTrue(lines.any { it.startsWith("frame ") && it.contains("rawSeparation=") && it.contains("to=BOTTOM") })
    }

    @Test fun invalidConfigIsRejected() {
        try {
            LungeConfig(bottomSeparation = .3)
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
        try {
            LungeConfig(bottomEntry = 130.0)
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }
}