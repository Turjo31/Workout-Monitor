package com.kuet.gymtest.analysis

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

fun squatFrame(time: Long, angle: Double = 178.0, epoch: Long = 1): PoseFrame {
    val bend = Math.toRadians(180 - angle)
    val hipX = .42 + .18 * sin(bend)
    val hipY = .60 - .18 * cos(bend)
    val points = MutableList(33) { PoseLandmark(.5f, .5f, 0f, .95f, .95f) }
    points[0] = PoseLandmark(.5f, .12f, 0f, .95f)
    for (s in BodySide.entries) {
        points[s.shoulder] = PoseLandmark(hipX.toFloat(), (hipY - .18).toFloat(), 0f, .95f)
        points[s.hip] = PoseLandmark(hipX.toFloat(), hipY.toFloat(), 0f, .95f)
        points[s.knee] = PoseLandmark(.42f, .60f, 0f, .95f)
        points[s.ankle] = PoseLandmark(.42f, .82f, 0f, .95f)
        points[s.heel] = PoseLandmark(.42f, .85f, 0f, .95f)
        points[s.toe] = PoseLandmark(.45f, .85f, 0f, .95f)
    }
    return PoseFrame(FrameKey("session", 1, epoch, time), time, 1000, 1000, points)
}

class SquatAnalyzerTest {
    @Test fun irregularIntervalsAndLongBottomHoldCountOnce() {
        val analyzer = SquatAnalyzer()
        var time = 0L
        var index = 0
        var last = analyzer.processFrame(squatFrame(time))
        val intervals = listOf(17L, 41L, 29L, 63L, 22L)
        while (time < 6500) {
            time += intervals[index++ % intervals.size]
            val angle = when {
                time < 700 -> 178.0
                time < 1700 -> 178.0 - 93 * (time - 700) / 1000.0
                time < 4700 -> 85.0
                time < 5700 -> 85.0 + 93 * (time - 4700) / 1000.0
                else -> 178.0
            }
            last = analyzer.processFrame(squatFrame(time, angle))
        }
        assertEquals(1, last.repetitions)
    }

    @Test fun lockedSideLossCannotSwitchLegsToCompleteARep() {
        val s = Stream(); s.move(178.0, 700); s.move(85.0, 1000); s.move(85.0, 400)
        val locked = s.results.last().side!!
        val lost = squatFrame(s.time + s.step, 85.0).let { frame ->
            frame.copy(landmarks = frame.landmarks.mapIndexed { index, landmark ->
                if (index == locked.knee) landmark.copy(visibility = .1f) else landmark
            })
        }
        assertEquals(SquatPhase.NOT_READY, s.analyzer.processFrame(lost).phase)
        s.time += s.step
        s.move(178.0, 1000); s.move(178.0, 700)
        assertEquals(0, s.count)
        s.cycle(); assertEquals(1, s.count)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteConfigurationIsRejected() { SquatConfig(filterMs = Double.POSITIVE_INFINITY) }

    private class Stream(val analyzer: SquatAnalyzer = SquatAnalyzer(), val step: Long = 33) {
        var time = 0L
        var angle = 178.0
        val results = mutableListOf<SquatAnalysisResult>()
        fun move(to: Double, duration: Long) {
            val from = angle
            val samples = (duration / step).toInt().coerceAtLeast(2)
            repeat(samples) { i ->
                time += step
                angle = from + (to - from) * (i + 1) / samples
                results += analyzer.processFrame(squatFrame(time, angle))
            }
        }
        fun cycle(depth: Double = 85.0) {
            move(178.0, 700); move(depth, 1000); move(depth, 400); move(178.0, 1000); move(178.0, 700)
        }
        fun loss() { time += step; results += analyzer.processFrame(squatFrame(time, angle).copy(landmarks = emptyList())) }
        val count get() = results.last().repetitions
    }

    @Test fun completeCycleCountsOnceAtDifferentRates() {
        for (step in listOf(16L, 33L, 66L)) {
            val s = Stream(step = step); s.cycle()
            assertEquals("step=$step", 1, s.count)
            assertEquals(1, s.results.count { it.repetition != null })
            s.cycle(); assertEquals(2, s.count)
        }
    }
    @Test fun incompleteAndStartingAtBottomNeverCount() {
        val s = Stream(); s.cycle(125.0); assertEquals(0, s.count)
        val b = Stream(); b.move(85.0, 1000); b.move(85.0, 500); b.move(178.0, 1000); b.move(178.0, 700)
        assertEquals(0, b.count)
    }
    @Test fun noisyBottomAndStandingDoNotDuplicate() {
        val s = Stream(); s.move(178.0, 700); s.move(85.0, 1000)
        repeat(8) { s.move(99.0, 120); s.move(92.0, 120) }
        s.move(178.0, 1000); s.move(178.0, 700)
        repeat(8) { s.move(166.0, 120); s.move(178.0, 120) }
        assertEquals(1, s.count)
    }
    @Test fun missingLandmarksCancelPartialAndPreserveCompleted() {
        val s = Stream(); s.cycle(); s.move(85.0, 1000); s.move(85.0, 400); s.loss()
        s.move(178.0, 1000); s.move(178.0, 700); assertEquals(1, s.count)
        s.cycle(); assertEquals(2, s.count)
    }
    @Test fun gapAndExplicitWatchdogRequireStandingAgain() {
        for (watchdog in listOf(false, true)) {
            val s = Stream(); s.move(178.0, 700); s.move(85.0, 1000); s.move(85.0, 400)
            if (watchdog) s.analyzer.invalidateTracking(DiscontinuityReason.TRACKING) else s.time += 500
            s.move(178.0, 1000); s.move(178.0, 700); assertEquals(0, s.count)
        }
    }
    @Test fun resetClearsCountsAndNewSessionHasNoMovement() {
        val s = Stream(); s.cycle(); s.analyzer.reset(); s.move(178.0, 700)
        assertEquals(0, s.count); s.cycle(); assertEquals(1, s.count)
        val fresh = Stream(); fresh.move(178.0, 700); assertEquals(0, fresh.count)
    }
    @Test fun invalidConfidenceGeometryAndOldFramesCannotCount() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f)) {
            val a = SquatAnalyzer()
            val p = squatFrame(100).let { f -> f.copy(landmarks = f.landmarks.map { it.copy(visibility = bad) }) }
            assertFalse(a.processFrame(p).trackingValid)
        }
        val a = SquatAnalyzer(); a.processFrame(squatFrame(100))
        assertFalse(a.processFrame(squatFrame(100)).trackingValid)
        assertFalse(a.processFrame(squatFrame(90)).trackingValid)
        assertFalse(a.processFrame(squatFrame(200, epoch = 2)).trackingValid)
    }
    @Test fun reflectedAndNonSquareFramesHaveSameMeasurements() {
        val p = squatFrame(100, 120.0)
        val a = SquatAnalyzer().processFrame(p)
        val mirrored = p.copy(landmarks = p.landmarks.map { it.copy(x = 1 - it.x) })
        assertEquals(a.metrics!!.knee, SquatAnalyzer().processFrame(mirrored).metrics!!.knee, .001)
        val wide = p.copy(imageWidth = 2000, landmarks = p.landmarks.map { it.copy(x = it.x / 2) })
        assertEquals(a.metrics.knee, SquatAnalyzer().processFrame(wide).metrics!!.knee, .001)
    }
}
