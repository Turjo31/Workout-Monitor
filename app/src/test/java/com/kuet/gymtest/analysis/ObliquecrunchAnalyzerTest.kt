package com.kuet.gymtest.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ObliqueCrunchAnalyzerTest {
    private val stepMs = 33L
    private var seq = 0L

    private fun lm(x: Double, y: Double, visibility: Float = .95f) =
        PoseLandmark(x.toFloat(), y.toFloat(), 0f, visibility, .95f)

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    /**
     * Front view. [a] and [b] are the crunch progress (0 = knee down, 1 = knee up with the elbow down to it) of the
     * left-body (landmarks 11/13/23/25) and right-body sides. With [elbowsStayHigh] the knee lifts but the elbow does not.
     */
    private fun frame(a: Double, b: Double = 0.0, elbowsStayHigh: Boolean = false, sideView: Boolean = false,
                      hidden: Boolean = false): PoseFrame {
        seq++
        val blank = PoseLandmark(.5f, .5f, 0f, 0f, 0f)
        val l = MutableList(33) { blank }
        l[11] = lm(if (sideView) .5 else .40, .30); l[12] = lm(if (sideView) .5 else .60, .30)
        l[23] = lm(.44, .55); l[24] = lm(.56, .55)
        val elbowT = { t: Double -> if (elbowsStayHigh) 0.0 else t }
        l[13] = lm(lerp(.34, .38, elbowT(a)), lerp(.28, .57, elbowT(a)))
        l[14] = lm(lerp(.66, .62, elbowT(b)), lerp(.28, .57, elbowT(b)), if (hidden) .1f else .95f)
        l[25] = lm(lerp(.45, .38, a), lerp(.78, .60, a)); l[26] = lm(lerp(.55, .62, b), lerp(.78, .60, b))
        return PoseFrame(FrameKey("test", 1, 1, seq), seq * stepMs, 1000, 1000, l)
    }

    private fun emptyFrame(): PoseFrame {
        seq++
        return PoseFrame(FrameKey("test", 1, 1, seq), seq * stepMs, 1000, 1000, emptyList())
    }

    private fun ramp(from: Double, to: Double, frames: Int = 30) = (1..frames).map { lerp(from, to, it.toDouble() / frames) }
    private fun lead() = List(12) { 0.0 }
    private fun crunch(peak: Double = 1.0) = ramp(0.0, peak) + List(10) { peak } + ramp(peak, 0.0) + List(12) { 0.0 }

    private fun run(a: ObliqueCrunchAnalyzer, path: List<Double>, right: Boolean = false) =
        path.map { a.processFrame(if (right) frame(0.0, it) else frame(it)) }

    @Test fun oneCrunchCountsExactlyOnce() {
        val results = run(ObliqueCrunchAnalyzer(), lead() + crunch())
        assertEquals(1, results.last().repetitions)
        assertEquals(1, results.count { it.repetition != null })
        assertEquals(1, results.first { it.repetition != null }.repetition!!.ordinal)
    }

    @Test fun rightSideCountsToo() {
        assertEquals(1, run(ObliqueCrunchAnalyzer(), lead() + crunch(), right = true).last().repetitions)
    }

    @Test fun alternatingSidesCountEach() {
        val a = ObliqueCrunchAnalyzer()
        run(a, lead() + crunch())
        run(a, crunch(), right = true)
        val last = run(a, crunch())
        assertEquals(3, last.last().repetitions)
    }

    @Test fun halfLiftIsNotCountedAndGivesShallowFeedback() {
        val results = run(ObliqueCrunchAnalyzer(), lead() + crunch(.6))
        assertEquals(0, results.last().repetitions)
        assertTrue(results.any { it.feedback == ObliqueCrunchFeedback.SHALLOW })
    }

    @Test fun kneeLiftWithoutElbowCrunchIsNotCounted() {
        val a = ObliqueCrunchAnalyzer()
        val results = (lead() + crunch()).map { a.processFrame(frame(it, elbowsStayHigh = true)) }
        assertEquals(0, results.last().repetitions)
    }

    @Test fun bothKneesRaisedTogetherIsNotCounted() {
        val a = ObliqueCrunchAnalyzer()
        val results = (lead() + crunch()).map { a.processFrame(frame(it, it)) }
        assertEquals(0, results.last().repetitions)
    }

    @Test fun smallWobbleNeverCounts() {
        val results = run(ObliqueCrunchAnalyzer(), lead() + List(40) { if (it % 2 == 0) 0.0 else .1 })
        assertEquals(0, results.last().repetitions)
    }

    @Test fun readinessNeedsBothKneesDown() {
        val results = List(12) { frame(.7) }.map { ObliqueCrunchAnalyzer().processFrame(it) }
        val a = ObliqueCrunchAnalyzer()
        val seen = List(14) { a.processFrame(frame(.7)) }
        assertTrue(seen.all { it.phase == ObliqueCrunchPhase.NOT_READY && it.repetitions == 0 })
        assertEquals(ObliqueCrunchFeedback.READY, seen.last().feedback)
        assertEquals(12, results.size)
    }

    @Test fun sideViewGivesFaceCameraFeedback() {
        val a = ObliqueCrunchAnalyzer()
        val results = (1..15).map { a.processFrame(frame(0.0, sideView = true)) }
        assertFalse(results.last().trackingValid)
        assertEquals(ObliqueCrunchFeedback.FACE_CAMERA, results.last().feedback)
    }

    @Test fun sustainedHiddenElbowCancelsTracking() {
        val a = ObliqueCrunchAnalyzer()
        run(a, lead())
        val results = (1..6).map { a.processFrame(frame(0.0, hidden = true)) }
        assertFalse(results.last().trackingValid)
        assertEquals(ObliqueCrunchFeedback.UNCERTAIN, results.last().feedback)
        assertEquals(ObliqueCrunchPhase.NOT_READY, results.last().phase)
    }

    @Test fun briefHiddenElbowDuringCrunchStillCounts() {
        val a = ObliqueCrunchAnalyzer()
        val hideAt = 12 + 30 + 4
        val results = (lead() + crunch()).mapIndexed { i, t ->
            a.processFrame(frame(0.0, t, hidden = i in hideAt..hideAt + 1))
        }
        assertEquals(1, results.last().repetitions)
        assertTrue(results.none { it.feedback == ObliqueCrunchFeedback.UNCERTAIN })
    }

    @Test fun trackingLossKeepsCountAndRequiresReacquire() {
        val a = ObliqueCrunchAnalyzer()
        run(a, lead() + crunch())
        val lost = a.processFrame(emptyFrame())
        assertFalse(lost.trackingValid)
        assertEquals(ObliqueCrunchFeedback.NO_POSE, lost.feedback)
        assertEquals(1, lost.repetitions)
        assertEquals(ObliqueCrunchPhase.NOT_READY, lost.phase)
        assertEquals(2, run(a, lead() + crunch()).last().repetitions)
    }

    @Test fun resetClearsCount() {
        val a = ObliqueCrunchAnalyzer()
        run(a, lead() + crunch())
        a.reset()
        assertEquals(0, a.processFrame(frame(0.0)).repetitions)
    }

    @Test fun traceReportsFramesWithoutChangingCount() {
        val lines = mutableListOf<String>()
        val results = run(ObliqueCrunchAnalyzer(diagnostic = lines::add), lead() + crunch())
        assertEquals(1, results.last().repetitions)
        assertTrue(lines.any { it.startsWith("frame ") && it.contains("to=CRUNCH") && it.contains("raw=") })
    }

    @Test fun invalidConfigIsRejected() {
        try {
            ObliqueCrunchConfig(peakLift = .6)
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
        try {
            ObliqueCrunchConfig(supportLift = .2)
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
        }
    }
}