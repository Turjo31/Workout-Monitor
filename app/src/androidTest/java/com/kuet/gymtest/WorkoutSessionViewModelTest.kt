package com.kuet.gymtest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kuet.gymtest.analysis.*
import com.kuet.gymtest.session.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import kotlin.math.cos
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class WorkoutSessionViewModelTest {
    @Test fun realWorkerCountsOnlyAfterStartAndRestoresPausedLedger() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val saved = SavedStateHandle()
        val worker = Executors.newSingleThreadExecutor()
        val vm = WorkoutSessionViewModel(saved, worker, Handler(Looper.getMainLooper()))
        val store = ViewModelStore().apply { put("test-session", vm) }
        try {
            instrumentation.runOnMainSync { vm.open("Bodyweight Squat", 2, 2); vm.bindCamera(1) }
            assertEquals(SessionStatus.SETUP, vm.state.value.progress!!.status)
            instrumentation.runOnMainSync { vm.start() }
            var time = 0L
            var angle = 178.0
            fun send(to: Double, duration: Long) {
                val from = angle
                val samples = (duration / 66).toInt()
                repeat(samples) { i ->
                    time += 66
                    angle = from + (to - from) * (i + 1) / samples
                    val bend = Math.toRadians(180 - angle)
                    val hipX = (.42 + .18 * sin(bend)).toFloat()
                    val hipY = (.6 - .18 * cos(bend)).toFloat()
                    val points = MutableList(33) { PoseLandmark(.5f, .5f, 0f, .95f) }
                    points[0] = PoseLandmark(.5f, .12f, 0f, .95f)
                    for (s in BodySide.entries) {
                        points[s.shoulder] = PoseLandmark(hipX, hipY - .18f, 0f, .95f)
                        points[s.hip] = PoseLandmark(hipX, hipY, 0f, .95f)
                        points[s.knee] = PoseLandmark(.42f, .6f, 0f, .95f)
                        points[s.ankle] = PoseLandmark(.42f, .82f, 0f, .95f)
                        points[s.heel] = PoseLandmark(.42f, .85f, 0f, .95f)
                    }
                    val frame = PoseFrame(vm.key(1, time), time, 1000, 1000, points)
                    vm.pose(frame)
                    worker.submit { }.get(3, TimeUnit.SECONDS)
                    assertEquals(frame.key, vm.state.value.analysis?.key)
                }
            }
            send(178.0, 700); send(85.0, 1000); send(85.0, 400); send(178.0, 1000); send(178.0, 700)
            assertEquals(1, vm.state.value.progress!!.totalReps)
            send(85.0, 1000); send(85.0, 400)
            val oldKey = vm.key(1, time + 66)
            instrumentation.runOnMainSync { vm.pause(); vm.resume() }
            vm.pose(PoseFrame(oldKey, time + 66, 1000, 1000, emptyList()))
            send(178.0, 1000); send(178.0, 700)
            assertEquals(1, vm.state.value.progress!!.totalReps)
            instrumentation.runOnMainSync { vm.pause() }
            val restored = WorkoutProgressCodec.decode(saved.get<ArrayList<Any>>("progress"))!!
            assertEquals(1, restored.totalReps); assertEquals(SessionStatus.PAUSED, restored.status)
            instrumentation.runOnMainSync { vm.reset() }
            assertEquals(0, vm.state.value.progress!!.totalReps)
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }

    @Test fun queuedOldFrameCannotCommitAcrossPauseResumeOrReset() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val worker = Executors.newSingleThreadExecutor()
        val vm = WorkoutSessionViewModel(SavedStateHandle(), worker, Handler(Looper.getMainLooper()))
        val store = ViewModelStore().apply { put("race-session", vm) }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            instrumentation.runOnMainSync { vm.open("Bodyweight Squat", 2, 2); vm.bindCamera(1); vm.start() }
            worker.execute { entered.countDown(); release.await(3, TimeUnit.SECONDS) }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val old = PoseFrame(vm.key(1, 1), 100, 100, 100, emptyList())
            vm.pose(old)
            instrumentation.runOnMainSync { vm.pause(); vm.resume(); vm.reset(); vm.start() }
            release.countDown()
            worker.submit { }.get(3, TimeUnit.SECONDS)
            assertNull(vm.state.value.analysis)
            assertEquals(0, vm.state.value.progress!!.totalReps)
            assertNotEquals(old.key.sessionId, vm.state.value.progress!!.sessionId)
        } finally { release.countDown(); instrumentation.runOnMainSync { store.clear() } }
    }

    @Test fun watchdogClosesEpochBeforeQueuedObservationCanPublish() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val worker = Executors.newSingleThreadExecutor()
        val vm = WorkoutSessionViewModel(SavedStateHandle(), worker, Handler(Looper.getMainLooper()))
        val store = ViewModelStore().apply { put("watchdog-session", vm) }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            instrumentation.runOnMainSync { vm.open("Bodyweight Squat", 2, 2); vm.bindCamera(1); vm.start() }
            worker.execute { entered.countDown(); release.await(3, TimeUnit.SECONDS) }
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val old = PoseFrame(vm.key(1, 1), 100, 100, 100, emptyList())
            vm.pose(old)
            instrumentation.runOnMainSync { vm.trackingLost() }
            assertEquals(SessionStatus.RUNNING, vm.state.value.progress!!.status)
            assertNotEquals(old.key.analysisEpoch, vm.state.value.progress!!.analysisEpoch)
            release.countDown()
            worker.submit { }.get(3, TimeUnit.SECONDS)
            assertNull(vm.state.value.analysis)
            assertEquals(0, vm.state.value.progress!!.totalReps)
        } finally { release.countDown(); instrumentation.runOnMainSync { store.clear() } }
    }
}
