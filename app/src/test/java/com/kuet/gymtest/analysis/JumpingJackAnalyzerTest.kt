package com.kuet.gymtest.analysis

import com.kuet.gymtest.WorkoutHistoryCodec
import com.kuet.gymtest.WorkoutRecord
import com.kuet.gymtest.session.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Articulated fixture with fixed limb lengths; arm and leg motion are independently controllable. */
private fun jackFrame(time: Long, leftArm: Double = 0.0, rightArm: Double = leftArm,
                      leftLeg: Double = 0.0, rightLeg: Double = leftLeg,
                      hipOffset: Double = .05, legReach: Double = .28,
                      elbowBend: Double = 0.0): PoseFrame {
    val points = MutableList(33) { PoseLandmark(.5f, .5f, 0f, 0f, 0f) }
    fun put(index: Int, x: Double, y: Double) { points[index] = PoseLandmark(x.toFloat(), y.toFloat(), 0f, .95f, .95f) }
    put(0, .5, .27)
    for ((offset, side) in BodySide.entries.withIndex()) {
        val sign = if (offset == 0) -1 else 1
        val sx = .5 + sign * .08
        val hx = .5 + sign * hipOffset
        val arm = Math.toRadians(if (offset == 0) leftArm else rightArm)
        val leg = Math.toRadians(if (offset == 0) leftLeg else rightLeg)
        put(side.shoulder, sx, .4)
        val ex = sx + sign * .12 * sin(arm)
        val ey = .4 + .12 * cos(arm)
        val bend = Math.toRadians(elbowBend * (if (offset == 0) leftArm else rightArm) / 170.0)
        val forearm = arm - bend
        put(13 + offset, ex, ey)
        put(15 + offset, ex + sign * .12 * sin(forearm), ey + .12 * cos(forearm))
        put(side.hip, hx, .6)
        put(side.knee, hx + sign * legReach / 2 * sin(leg), .6 + legReach / 2 * cos(leg))
        val ax = hx + sign * legReach * sin(leg)
        val ay = .6 + legReach * cos(leg)
        put(side.ankle, ax, ay)
        put(side.heel, ax, ay + .02)
        put(side.toe, ax + sign * .02, ay + .02)
    }
    return PoseFrame(FrameKey("jack", 1, 1, time), time, 1000, 1000, points)
}

class JumpingJackAnalyzerTest {
    private val compactLegFrame: (Long, Double, Double, Double, Double) -> PoseFrame = { t, la, ra, ll, rl ->
        jackFrame(t, la, ra, ll, rl, hipOffset = .025, legReach = .22)
    }

    private class Stream(
        val analyzer: JumpingJackAnalyzer = JumpingJackAnalyzer(),
        private val intervals: List<Long> = listOf(33),
        var transform: (PoseFrame) -> PoseFrame = { it },
        private val makeFrame: (Long, Double, Double, Double, Double) -> PoseFrame = { t, la, ra, ll, rl ->
            jackFrame(t, la, ra, ll, rl)
        },
        val observer: (JumpingJackAnalysisResult) -> Unit = {}
    ) {
        var time = 0L
        private var index = 0
        private var angles = listOf(0.0, 0.0, 0.0, 0.0)
        val results = mutableListOf<JumpingJackAnalysisResult>()
        val count get() = results.last().repetitions
        fun send(frame: PoseFrame) {
            val result = analyzer.processFrame(transform(frame))
            results += result
            observer(result)
        }
        fun move(arm: Double, leg: Double, duration: Long = 500, rightArm: Double = arm, rightLeg: Double = leg) {
            val from = angles
            val target = listOf(arm, rightArm, leg, rightLeg)
            var elapsed = 0L
            while (elapsed < duration) {
                val step = intervals[index++ % intervals.size]
                time += step
                elapsed += step
                val fraction = (elapsed.toDouble() / duration).coerceAtMost(1.0)
                angles = from.zip(target).map { (a, b) -> a + (b - a) * fraction }
                send(makeFrame(time, angles[0], angles[1], angles[2], angles[3]))
            }
        }
        fun ready() = move(0.0, 0.0, 650)
        fun cycle(duration: Long = 500) {
            move(170.0, 35.0, duration)
            move(170.0, 35.0, 250)
            move(0.0, 0.0, duration)
            move(0.0, 0.0, 350)
        }
    }

    @Test fun combinedHipAnkleAngleMatchesUpwardVectorGeometry() {
        val analyzer = JumpingJackAnalyzer()
        val closed = analyzer.processFrame(jackFrame(33))
        assertEquals(0.0, closed.metrics!!.legSpreadAngle, 0.01)
        val open = JumpingJackAnalyzer().processFrame(jackFrame(66, 0.0, 0.0, 20.0, 20.0))
        assertEquals(40.0, open.metrics!!.legSpreadAngle, 0.5)
        // Parallel legs shifted in the same direction must still have a near-zero angle.
        val shifted = jackFrame(99).copy(landmarks = jackFrame(99).landmarks.toMutableList().also {
            for (index in listOf(27, 28)) it[index] = it[index].copy(x = it[index].x + .06f)
        })
        val parallel = JumpingJackAnalyzer().processFrame(shifted)
        assertEquals(0.0, parallel.metrics!!.legSpreadAngle, 0.01)
    }

    @Test fun newMeasurementsAreLoggedAndOnlyLegAndHandsBlockOpen() {
        val trace = mutableListOf<String>()
        val s = Stream(analyzer = JumpingJackAnalyzer(diagnostic = trace::add))
        s.ready()
        s.move(170.0, 10.0)
        assertEquals(JumpingJackPhase.OPENING, s.results.last().phase)
        assertTrue(trace.any { it.contains("legSpreadAngle=") && it.contains("leftWristHeight=") &&
            it.contains("rightWristHeight=") && it.contains("openBlockers=legSpread") })
        s.move(170.0, 35.0)
        s.move(170.0, 35.0, 250)
        assertEquals(JumpingJackPhase.OPEN, s.results.last().phase)
    }

    @Test fun fullCyclesCountOnceAtMultipleAndIrregularRates() {
        for (intervals in listOf(listOf(16L), listOf(33L), listOf(66L), listOf(17L, 41L, 29L, 63L))) {
            val s = Stream(intervals = intervals)
            s.ready()
            s.cycle(); s.cycle()
            assertEquals("intervals=$intervals", 2, s.count)
            assertEquals(listOf(1, 2), s.results.mapNotNull { it.repetition?.ordinal })
            s.results.filter { it.repetition != null }.forEach {
                assertTrue(it.trackingValid)
                assertEquals(it.timestampMs, it.repetition!!.timestampMs)
                assertEquals(JumpingJackPhase.CLOSED, it.phase)
            }
        }
    }

    @Test fun loggedPhoneLikeAnkleRatioCompletesOneBilateralCycle() {
        val stream = Stream(intervals = listOf(80L, 81L, 121L), makeFrame = compactLegFrame)
        stream.ready()
        stream.move(170.0, 20.0)
        stream.move(170.0, 20.0, 250)
        assertEquals(JumpingJackPhase.OPEN, stream.results.last().phase)
        assertTrue(stream.results.last().metrics!!.legSpreadAngle in 35.0..45.0)
        stream.move(0.0, 0.0)
        stream.ready()
        assertEquals(1, stream.count)
        assertEquals(1, stream.results.count { it.repetition != null })
    }

    @Test fun bentButRaisedArmsCompleteOneBilateralCycleAtPhoneFrameRate() {
        val stream = Stream(intervals = listOf(80L, 81L, 121L), makeFrame = { t, la, ra, ll, rl ->
            jackFrame(t, la, ra, ll, rl, elbowBend = 70.0)
        })
        stream.ready()
        stream.cycle()
        assertEquals(1, stream.count)
        assertEquals(1, stream.results.count { it.repetition != null })
    }

    @Test fun optionalFaceAndFootDetailsDoNotBlockBilateralMovement() {
        val stream = Stream(transform = { frame ->
            frame.copy(landmarks = frame.landmarks.mapIndexed { index, landmark ->
                if (index in listOf(0, 29, 30, 31, 32)) landmark.copy(visibility = null, presence = null)
                else landmark
            })
        })
        stream.ready()
        stream.cycle()
        assertEquals(1, stream.count)
        assertEquals(1, stream.results.count { it.repetition != null })
    }

    @Test fun bentElbowLandmarksDoNotGateOpenWhenBothHandsAreUp() {
        val trace = mutableListOf<String>()
        val stream = Stream(analyzer = JumpingJackAnalyzer(diagnostic = trace::add), transform = { frame ->
            val rightShoulder = frame.landmarks[12]
            if (frame.landmarks[14].y < rightShoulder.y - .08f) {
                val adjusted = frame.landmarks.toMutableList()
                adjusted[16] = adjusted[16].copy(x = .48f, y = .27f)
                frame.copy(landmarks = adjusted)
            } else frame
        })
        stream.ready()
        stream.move(170.0, 35.0)
        stream.move(170.0, 35.0, 250)
        assertEquals(JumpingJackPhase.OPEN, stream.results.last().phase)
        assertTrue(trace.any { it.contains("to=OPEN") && it.contains("openBlockers=none") })
        stream.move(0.0, 0.0)
        stream.ready()
        assertEquals(1, stream.count)
    }

    @Test fun traceRecordsSingleEventForValidCompleteCycle() {
        val trace = mutableListOf<String>()
        val stream = Stream(analyzer = JumpingJackAnalyzer(diagnostic = trace::add))
        stream.ready()
        stream.cycle()
        assertEquals(1, stream.count)
        assertEquals(1, trace.count { it.startsWith("frame ") && it.contains("event=1") })
        assertTrue(trace.any { it.contains("from=OPENING to=OPEN") })
        assertTrue(trace.any { it.contains("from=OPEN to=CLOSING") })
    }

    @Test fun kneeTrackingSpikeDoesNotBlockLegAngleCycle() {
        val s = Stream(intervals = listOf(84L), transform = { frame ->
            frame.copy(landmarks = frame.landmarks.toMutableList().also {
                it[25] = it[25].copy(x = it[25].x - .20f)
            })
        })
        s.ready()
        s.cycle()
        assertEquals(1, s.count)
        assertTrue(s.results.all { it.trackingValid })
    }

    @Test fun ankleCrossingDoesNotResetTrackingByItself() {
        val s = Stream(intervals = listOf(84L))
        s.ready()
        s.move(170.0, 35.0)
        s.move(170.0, 35.0, 250)
        repeat(3) {
            s.time += 84
            val frame = jackFrame(s.time, 170.0, 170.0, 35.0, 35.0)
            s.send(frame.copy(landmarks = frame.landmarks.toMutableList().also {
                it[27] = it[27].copy(x = .51f)
            }))
            assertTrue(s.results.last().trackingValid)
            assertNotEquals(JumpingJackPhase.NOT_READY, s.results.last().phase)
        }
        s.move(0.0, 0.0)
        s.ready()
        assertEquals(1, s.count)
    }

    @Test fun traceSeparatesConfidenceLossFromFrameGap() {
        val trace = mutableListOf<String>()
        val stream = Stream(analyzer = JumpingJackAnalyzer(diagnostic = trace::add))
        stream.ready()
        stream.time += 33
        val frame = jackFrame(stream.time)
        stream.send(frame.copy(landmarks = frame.landmarks.toMutableList().also {
            it[16] = it[16].copy(visibility = .4f)
        }))
        assertTrue(trace.any { it.contains("failure=landmark 16 low confidence") && it.contains("16:0.4/") })
        stream.time += 300
        stream.send(jackFrame(stream.time))
        assertTrue(trace.any { it.contains("failure=gap 300ms > 250ms") })
        assertEquals(JumpingJackPhase.NOT_READY, stream.results.last().phase)
    }

    @Test fun moderateOpenVWithBothFeetApartCompletesOneCycle() {
        val stream = Stream()
        stream.ready()
        stream.move(125.0, 20.0)
        stream.move(125.0, 20.0, 250)
        assertEquals(JumpingJackPhase.OPEN, stream.results.last().phase)
        stream.move(0.0, 0.0)
        stream.ready()
        assertEquals(1, stream.count)
        assertEquals(1, stream.results.count { it.repetition != null })
    }

    @Test fun fastSlowAndLongHoldsCountWithoutDuplicates() {
        for (duration in listOf(200L, 1500L)) {
            val s = Stream()
            s.ready(); s.cycle(duration)
            s.move(0.0, 0.0, 2000)
            assertEquals("duration=$duration phases=${s.results.groupingBy { it.phase }.eachCount()}", 1, s.count)
            s.move(170.0, 35.0); s.move(170.0, 35.0, 3000)
            assertEquals(1, s.count)
            s.move(0.0, 0.0); s.ready()
            assertEquals(2, s.count)
        }
    }

    @Test fun bothHandsAndLegSpreadAreRequiredButOneSidedLegStyleIsAllowed() {
        val incomplete = listOf(listOf(170.0, 170.0, 0.0, 0.0),
            listOf(0.0, 0.0, 35.0, 35.0),
            listOf(170.0, 0.0, 35.0, 35.0),
            listOf(170.0, 170.0, 10.0, 10.0))
        for (t in incomplete) {
            val s = Stream(); s.ready()
            s.move(t[0], t[2], rightArm = t[1], rightLeg = t[3])
            s.move(t[0], t[2], rightArm = t[1], rightLeg = t[3])
            s.move(0.0, 0.0); s.ready()
            assertEquals("target=$t", 0, s.count)
        }
        val varied = Stream(); varied.ready()
        varied.move(170.0, 35.0, rightLeg = 0.0)
        varied.move(170.0, 35.0, 250, rightLeg = 0.0)
        varied.move(0.0, 0.0); varied.ready()
        assertEquals(1, varied.count)
    }

    @Test fun mismatchedEndpointsAndStartingOpenDoNotCount() {
        val s = Stream()
        s.move(170.0, 35.0); s.move(170.0, 35.0)
        s.move(0.0, 0.0); s.ready()
        assertEquals(0, s.count)
        s.move(170.0, 0.0); s.move(170.0, 0.0)
        s.move(0.0, 35.0); s.move(0.0, 35.0)
        s.move(0.0, 0.0); s.ready()
        assertEquals(0, s.count)
        s.cycle(); assertEquals(1, s.count)
    }

    @Test fun closingRequiresBothArmsAndFeetAndReopeningDoesNotDuplicate() {
        val s = Stream(); s.ready()
        s.move(170.0, 35.0); s.move(170.0, 35.0)
        s.move(0.0, 35.0); s.move(0.0, 35.0)
        assertEquals(0, s.count)
        s.move(170.0, 35.0); s.move(170.0, 35.0)
        s.move(170.0, 0.0); s.move(170.0, 0.0)
        assertEquals(0, s.count)
        s.move(0.0, 0.0); s.ready()
        assertEquals(1, s.count)
    }

    @Test fun shortLegAngleThresholdCrossingsCannotConfirmOpen() {
        val s = Stream(); s.ready()
        repeat(10) {
            s.move(170.0, 13.0, 33)
            s.move(170.0, 10.0, 33)
        }
        s.move(0.0, 0.0); s.ready()
        assertEquals(0, s.count)
        s.cycle()
        assertEquals(1, s.count)
    }

    @Test fun legAngleJitterAndIncompleteClosureDoNotInventEndpoints() {
        val s = Stream(makeFrame = compactLegFrame); s.ready()
        repeat(12) {
            s.move(170.0, 13.0, 33)
            s.move(170.0, 10.0, 33)
        }
        s.move(0.0, 0.0); s.ready(); assertEquals(0, s.count)
        s.move(170.0, 20.0)
        s.move(170.0, 20.0, 250)
        s.move(0.0, 8.0)
        s.move(0.0, 8.0, 300)
        assertEquals(0, s.count)
        s.ready()
        assertEquals(1, s.count)
    }

    @Test fun everyInterruptionCancelsPartialButPreservesAcceptedCount() {
        for (reason in DiscontinuityReason.entries) {
            val s = Stream(); s.ready(); s.cycle()
            s.move(170.0, 35.0); s.move(170.0, 35.0)
            s.analyzer.invalidateTracking(reason)
            s.move(0.0, 0.0); s.ready()
            assertEquals("reason=$reason", 1, s.count)
            s.cycle(); assertEquals(2, s.count)
        }
    }

    @Test fun longGapsAndSustainedMissingLandmarksCancelPartialMotion() {
        for (gap in listOf(false, true)) {
            val s = Stream(); s.ready(); s.cycle()
            s.move(170.0, 35.0); s.move(170.0, 35.0)
            if (gap) {
                s.time += 400
                s.send(jackFrame(s.time, 170.0, 170.0, 35.0, 35.0))
            } else repeat(3) {
                s.time += 33
                s.send(jackFrame(s.time).copy(landmarks = emptyList()))
            }
            assertEquals(JumpingJackPhase.NOT_READY, s.results.last().phase)
            s.move(0.0, 0.0); s.ready(); assertEquals(1, s.count)
            s.cycle(); assertEquals(2, s.count)
        }
    }

    @Test fun invalidObservationsNeverCreateEventsAndSustainedLossResets() {
        val corruptions: List<(PoseFrame) -> PoseFrame> = listOf(
            { it.copy(landmarks = it.landmarks.take(20)) },
            { it.copy(imageWidth = 0) },
            { f -> f.copy(landmarks = f.landmarks.mapIndexed { i, l -> if (i == 15) l.copy(x = Float.NaN) else l }) },
            { f -> f.copy(landmarks = f.landmarks.mapIndexed { i, l -> if (i == 28) l.copy(visibility = .1f) else l }) },
            { f -> f.copy(landmarks = f.landmarks.mapIndexed { i, l -> if (i == 16) l.copy(x = .999f) else l }) },
            { f -> f.copy(landmarks = f.landmarks.mapIndexed { i, l -> if (i == 15) l.copy(presence = Float.NaN) else l }) },
            { f -> f.copy(landmarks = f.landmarks.map { it.copy(presence = Float.POSITIVE_INFINITY) }) }
        )
        for ((index, corrupt) in corruptions.withIndex()) {
            val s = Stream(); s.ready(); s.move(170.0, 35.0); s.move(170.0, 35.0)
            repeat(3) {
                s.time += 33
                s.send(corrupt(jackFrame(s.time, 170.0, 170.0, 35.0, 35.0)))
                assertFalse("corruption=$index", s.results.last().trackingValid)
                assertNull(s.results.last().repetition)
            }
            assertEquals(JumpingJackPhase.NOT_READY, s.results.last().phase)
            s.move(0.0, 0.0); s.ready(); assertEquals(0, s.count)
            s.cycle(); assertEquals("recovery=$index", 1, s.count)
        }
    }

    @Test fun timestampsSequencesAndEpochsFailClosedUntilProperRecovery() {
        val changes: List<(PoseFrame) -> PoseFrame> = listOf(
            { it.copy(timestampMs = it.timestampMs - 33) },
            { it.copy(timestampMs = -1) },
            { it.copy(key = it.key.copy(sequence = it.key.sequence - 33)) },
            { it.copy(key = it.key.copy(cameraEpoch = 2)) },
            { it.copy(key = it.key.copy(analysisEpoch = 2)) },
            { it.copy(key = it.key.copy(sessionId = "other")) }
        )
        for (change in changes) {
            val s = Stream(); s.ready(); s.move(170.0, 35.0); s.move(170.0, 35.0)
            s.time += 33
            s.send(change(jackFrame(s.time)))
            assertFalse(s.results.last().trackingValid)
            s.move(0.0, 0.0); s.ready(); assertEquals(0, s.count)
            s.cycle(); assertEquals(1, s.count)
        }
        val a = JumpingJackAnalyzer()
        a.processFrame(jackFrame(100))
        a.invalidateTracking(DiscontinuityReason.TRACKING)
        assertFalse(a.processFrame(jackFrame(100)).trackingValid)
        a.reset()
        assertTrue(a.processFrame(jackFrame(1).copy(key = FrameKey("new", 2, 2, 1))).trackingValid)
    }

    @Test fun reflectionAspectRatioScaleTranslationAndOptionalPresencePreserveCounts() {
        val transforms: List<(PoseFrame) -> PoseFrame> = listOf(
            { f -> f.copy(landmarks = f.landmarks.map { it.copy(x = 1 - it.x) }) },
            { f -> f.copy(imageWidth = 1600, landmarks = f.landmarks.map { it.copy(x = it.x / 1.6f) }) },
            { f -> f.copy(imageHeight = 1500, landmarks = f.landmarks.map { it.copy(y = it.y / 1.5f) }) },
            { f -> f.copy(landmarks = f.landmarks.map { it.copy(x = it.x * .8f + .1f, y = it.y * .8f + .05f) }) },
            { f -> f.copy(landmarks = f.landmarks.map { it.copy(presence = null) }) }
        )
        for (transform in transforms) {
            val s = Stream(transform = transform); s.ready(); s.cycle()
            assertEquals(1, s.count)
        }
    }

    @Test fun missingRequiredGeometryCancelsReadinessWithoutFalseEvents() {
        val s = Stream(); s.ready()
        s.time += 33
        val frame = jackFrame(s.time)
        s.send(frame.copy(landmarks = frame.landmarks.toMutableList().also {
            it[27] = it[23]
        }))
        assertFalse(s.results.last().trackingValid)
        assertNull(s.results.last().repetition)
        s.move(0.0, 0.0); s.cycle()
        assertEquals(1, s.count)
    }

    @Test fun resetAndFactoryInstancesHaveIndependentCounts() {
        val definition = ExerciseMonitorRegistry.find("Jumping Jacks")!!
        val s = Stream(analyzer = definition.createAnalyzer() as JumpingJackAnalyzer)
        s.ready(); s.cycle(); assertEquals(1, s.count)
        val fresh = Stream(analyzer = definition.createAnalyzer() as JumpingJackAnalyzer)
        fresh.ready(); assertEquals(0, fresh.count)
        s.analyzer.reset(); s.ready(); assertEquals(0, s.count)
        s.cycle(); assertEquals(1, s.results.last { it.repetition != null }.repetition!!.ordinal)
    }

    @Test fun minimumCycleAndInvalidConfigAreEnforced() {
        val s = Stream(JumpingJackAnalyzer(JumpingJackConfig(minimumCycleMs = 5000)))
        s.ready(); s.cycle(); assertEquals(0, s.count)
        assertTrue(s.results.any { it.phase == JumpingJackPhase.OPEN })
        val constructors = listOf<() -> JumpingJackConfig>(
            { JumpingJackConfig(filterMs = Double.NaN) }, { JumpingJackConfig(openLegAngle = Double.POSITIVE_INFINITY) },
            { JumpingJackConfig(closedLegExitAngle = 5.0) }, { JumpingJackConfig(openLegExitAngle = 30.0) },
            { JumpingJackConfig(maximumGapMs = 0) }, { JumpingJackConfig(visibility = Float.NaN) },
            { JumpingJackConfig(maximumTransientTrackingMs = 0) },
            { JumpingJackConfig(maximumTransientTrackingFrames = 0) },
            { JumpingJackConfig(minimumSegment = 0.0) }, { JumpingJackConfig(openWristHeight = .01) }
        )
        for (create in constructors) {
            try { create(); fail("Expected invalid configuration to be rejected") }
            catch (expected: IllegalArgumentException) { /* Expected. */ }
        }
    }

    @Test fun realEventsUseManualSetsAndPartialMeasuredHistory() {
        val controller = WorkoutSessionController("Jumping Jacks", 2, 2)
        controller.start()
        val s = Stream(transform = { frame ->
            val p = controller.state
            frame.copy(key = FrameKey(p.sessionId, p.cameraEpoch, p.analysisEpoch, frame.timestampMs))
        }, observer = { controller.accept(it) })
        s.ready(); s.cycle(); s.cycle()
        assertEquals(SessionStatus.BETWEEN_SETS, controller.state.status)
        s.cycle(); assertEquals(listOf(2), controller.state.perSet)
        controller.nextSet(); s.analyzer.reset(); s.ready(); s.cycle()
        val record = WorkoutRecord.measured(controller.finish(10000)!!)
        assertEquals(listOf(2, 1), record.perSetReps)
        assertEquals(3, record.totalReps)
        assertFalse(record.fullyCompleted!!)
        assertEquals(listOf(record), WorkoutHistoryCodec.decode(WorkoutHistoryCodec.encode(listOf(record))))
        assertNull(controller.finish(10001))
    }

    @Test fun controllerPauseCameraResetAndRestorationRejectOldMotion() {
        for (camera in listOf(false, true)) {
            val controller = WorkoutSessionController("Jumping Jacks", 1, 2)
            controller.start()
            val s = Stream(transform = { f ->
                val p = controller.state
                f.copy(key = FrameKey(p.sessionId, p.cameraEpoch, p.analysisEpoch, f.timestampMs))
            }, observer = { controller.accept(it) })
            s.ready(); s.cycle()
            val stale = s.results.last { it.repetition != null }
            s.move(170.0, 35.0); s.move(170.0, 35.0)
            if (camera) controller.bindCamera(2) else controller.pause()
            assertFalse(controller.accept(stale))
            controller.resume(); s.analyzer.reset()
            assertFalse(controller.accept(stale))
            s.move(0.0, 0.0); s.ready()
            assertEquals(1, controller.state.totalReps)
            val restored = WorkoutSessionController("Jumping Jacks", 1, 2,
                WorkoutProgressCodec.decode(WorkoutProgressCodec.encode(controller.state)))
            assertEquals(SessionStatus.PAUSED, restored.state.status)
            assertEquals(1, restored.state.totalReps)
            s.cycle(); assertEquals(SessionStatus.COMPLETE, controller.state.status)
            assertTrue(controller.summary(1).fullyCompleted)
            controller.reset(); s.analyzer.reset()
            assertEquals(0, controller.state.totalReps)
            assertNull(controller.finish(2))
        }
    }
}
