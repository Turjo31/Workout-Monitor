package com.kuet.gymtest.session

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.kuet.gymtest.analysis.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

data class WorkoutUiState(val progress: WorkoutProgress? = null, val analysis: SquatAnalysisResult? = null)

/** One Activity-retained owner; no camera, Activity, View or lifecycle references. */
class WorkoutSessionViewModel internal constructor(
    private val saved: SavedStateHandle, private val worker: ExecutorService, private val main: Handler
) : ViewModel() {
    constructor(saved: SavedStateHandle) : this(saved, Executors.newSingleThreadExecutor(), Handler(Looper.getMainLooper()))
    private val lock = Any()
    private val pending = AtomicBoolean(false)
    private val overflow = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val analyzer = SquatAnalyzer()
    private val feedback = FeedbackPresenter()
    private var controller: WorkoutSessionController? = null
    private val mutableState = MutableStateFlow(WorkoutUiState())
    val state = mutableState.asStateFlow()

    fun open(name: String, sets: Int, reps: Int) = synchronized(lock) {
        if (closed.get()) return@synchronized
        if (controller?.state?.exerciseName == name && controller?.state?.status != SessionStatus.FINISHED) return@synchronized
        controller = WorkoutSessionController(name, sets, reps, WorkoutProgressCodec.decode(saved.get<ArrayList<Any>>("progress")))
        worker.execute { analyzer.reset(); feedback.reset() }
        publish()
    }
    private fun publish(analysis: SquatAnalysisResult? = null) {
        val snapshot = WorkoutUiState(controller?.state, analysis)
        mutableState.value = snapshot
        val save = Runnable { if (mutableState.value == snapshot) saved["progress"] = snapshot.progress?.let(WorkoutProgressCodec::encode) }
        if (Looper.myLooper() == Looper.getMainLooper()) save.run() else main.post(save)
    }
    private fun command(action: WorkoutSessionController.() -> Unit) = synchronized(lock) {
        if (closed.get()) return@synchronized
        val before = controller?.state
        controller?.action()
        if (before == controller?.state) return@synchronized
        // Closing/changing the epoch is immediate, before an in-flight analysis can commit.
        worker.execute { analyzer.reset(); feedback.reset() }
        publish()
    }
    fun start() = command { start() }
    fun pause() = command { pause() }
    fun resume() = command { resume() }
    fun reset() = command { reset() }
    fun nextSet() = command { nextSet() }
    fun bindCamera(epoch: Long) = command { bindCamera(epoch) }
    fun key(cameraEpoch: Long, sequence: Long): FrameKey = synchronized(lock) {
        val p = controller?.state
        FrameKey(p?.sessionId.orEmpty(), cameraEpoch, p?.analysisEpoch ?: -1, sequence)
    }
    fun pose(frame: PoseFrame) {
        synchronized(lock) {
            if (closed.get() || controller?.accepts(frame.key) != true) return
            if (!pending.compareAndSet(false, true)) { overflow.set(true); return }
            worker.execute {
                try {
                    if (overflow.getAndSet(false)) analyzer.invalidateTracking(DiscontinuityReason.OVERFLOW)
                    if (!synchronized(lock) { controller?.accepts(frame.key) == true }) return@execute
                    val result = analyzer.processFrame(frame)
                    synchronized(lock) {
                        if (controller?.accept(result) == true) publish(result.copy(feedback = feedback.present(result)))
                    }
                } finally { pending.set(false) }
            }
        }
    }
    // A watchdog/transform loss must invalidate queued and in-flight observations immediately.
    // Keep RUNNING and the accepted ledger, but require standing in a fresh analysis epoch.
    fun trackingLost() = command { interruptTracking() }
    fun finish(timestamp: Long): WorkoutSummary? = synchronized(lock) {
        if (closed.get()) return@synchronized null
        val result = controller?.finish(timestamp)
        worker.execute { analyzer.reset() }
        publish()
        result
    }
    fun discard() = synchronized(lock) {
        if (closed.get()) return@synchronized
        controller = null
        saved.remove<ArrayList<Any>>("progress")
        worker.execute { analyzer.reset() }
        publish()
    }
    override fun onCleared() = synchronized(lock) { closed.set(true); worker.shutdown() }
}
