package com.kuet.gymtest.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class PushUpAnalyzerTest {
    private val stepMs = 33L
    private var seq = 0L

    private fun lm(p: Pair<Double, Double>) = PoseLandmark(p.first.toFloat(), p.second.toFloat(), 0f, .95f, .95f)

    /**
     * Left-side plank (or upright body when [upright]). The hand stays planted and the shoulder moves with
     * [angle], the elbow angle in degrees. With [elbowOnly] the shoulder is fixed and only the forearm moves.
     */
    private fun frame(angle: Double, upright: Boolean = false, elbowOnly: Boolean = false): PoseFrame {
        seq++
        val len = .15
        val r = Math.toRadians(angle)
        val s: Pair<Double, Double>
        val e: Pair<Double, Double>
        val w: Pair<Double, Double>
        if (elbowOnly) {
            s = Pair(.3, .4)
            e = Pair(.35, .55)
            val ux = -.05
            val uy = -.15
            val n = hypot(ux, uy)
            w = Pair(e.first + len * (ux * cos(r) - uy * sin(r)) / n, e.second + len * (ux * sin(r) + uy * cos(r)) / n)
        } else {
            w = if (upright) Pair(.5, .45) else Pair(.3, .55)
            val d = 2 * len * sin(r / 2)
            s = Pair(w.first, w.second - d)
            e = Pair(w.first + len * cos(r / 2), w.second - d / 2)
        }
        val h = if (upright) Pair(s.first, s.second + .3) else Pair(s.first + .25, s.second + .05)
        val a = if (upright) Pair(s.first, s.second + .6) else Pair(s.first + .5, s.second + .1)
        val blank = PoseLandmark(.5f, .5f, 0f, 0f, 0f)
        val l = MutableList(33) { blank }
        l[11] = lm(s); l[13] = lm(e); l[15] = lm(w); l[23] = lm(h); l[27] = lm(a)
        return PoseFrame(FrameKey("test", 1, 1, seq), seq * stepMs, 1000, 1000, l)
    }

    private fun emptyFrame(): PoseFrame {
        seq++
        return PoseFrame(FrameKey("test", 1, 1, seq), seq * stepMs, 1000, 1000, emptyList())
    }

    private fun ramp(from: Double, to: Double, per: Double = 6.0): List<Double> {
        val n = ceil(abs(to - from) / per).toInt()
        return (1..n).map { from + (to - from) * it / n }
    }

    private fun lead() = List(12) { 170.0 }
    private fun rep(depth: Double = 80.0) =
        ramp(170.0, depth) + List(10) { depth } + ramp(depth, 170.0) + List(12) { 170.0 }

    private fun run(a: PushUpAnalyzer, angles: List<Double>) = angles.map { a.processFrame(frame(it)) }

    @Test fun oneFullRepCountsExactlyOnce() {
        val results = run(PushUpAnalyzer(), lead() + rep())
        assertEquals(1, results.last().repetitions)
        assertEquals(1, results.count { it.repetition != null })
        assertEquals(1, results.first { it.repetition != null }.repetition!!.ordinal)
    }

    @Test fun threeRepsCountThree() {
        val results = run(PushUpAnalyzer(), lead() + rep() + rep() + rep())
        assertEquals(3, results.last().repetitions)
        assertEquals(3, results.count { it.repetition != null })
    }

    @Test fun partialRepIsNotCountedAndGivesShallowFeedback() {
        val angles = lead() + ramp(170.0, 120.0) + List(6) { 120.0 } + ramp(120.0, 170.0) + List(12) { 170.0 }
        val results = run(PushUpAnalyzer(), angles)
        assertEquals(0, results.last().repetitions)
        assertTrue(results.any { it.feedback == Feedback.PUSH_SHALLOW })
    }

    @Test fun jitterAroundTopNeverCounts() {
        val angles = lead() + List(40) { if (it % 2 == 0) 170.0 else 150.0 }
        assertEquals(0, run(PushUpAnalyzer(), angles).last().repetitions)
    }

    @Test fun elbowOnlyMotionNeverCounts() {
        val a = PushUpAnalyzer()
        val results = (lead() + rep() + rep()).map { a.processFrame(frame(it, elbowOnly = true)) }
        assertEquals(0, results.last().repetitions)
    }

    @Test fun uprightBodyNeverBecomesReady() {
        val a = PushUpAnalyzer()
        val results = (1..30).map { a.processFrame(frame(170.0, upright = true)) }
        assertTrue(results.all { it.phase == PushUpPhase.NOT_READY && it.repetitions == 0 })
        assertEquals(Feedback.PUSH_READY, results.last().feedback)
    }

    @Test fun trackingLossKeepsCountAndRequiresReacquire() {
        val a = PushUpAnalyzer()
        run(a, lead() + rep())
        val lost = a.processFrame(emptyFrame())
        assertFalse(lost.trackingValid)
        assertEquals(Feedback.NO_POSE, lost.feedback)
        assertEquals(1, lost.repetitions)
        assertEquals(PushUpPhase.NOT_READY, lost.phase)
        assertEquals(2, run(a, lead() + rep()).last().repetitions)
    }

    @Test fun resetClearsCount() {
        val a = PushUpAnalyzer()
        run(a, lead() + rep())
        a.reset()
        assertEquals(0, a.processFrame(frame(170.0)).repetitions)
    }

    @Test fun invalidConfigIsRejected() {
        try {
            PushUpConfig(topEntry = 150.0, topExit = 155.0)
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }
}