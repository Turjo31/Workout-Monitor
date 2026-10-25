package com.kuet.gymtest

import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.hardware.display.DisplayManager
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.camera.view.transform.CoordinateTransform
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kuet.gymtest.analysis.FrameKey
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class PoseCameraStatus(val ready: Boolean = false, val message: String = "Starting camera…", val lens: Int = CameraSelector.LENS_FACING_FRONT)
private val cameraEpochs = AtomicLong()

@Composable
fun PoseCamera(
    modifier: Modifier = Modifier, onPose: (PoseResult) -> Unit = {},
    lens: Int = CameraSelector.LENS_FACING_FRONT, retry: Int = 0,
    frameKey: (Long, Long) -> FrameKey = { epoch, sequence -> FrameKey("preview", epoch, 0, sequence) },
    onBinding: (Long) -> Unit = {}, onStatus: (PoseCameraStatus) -> Unit = {},
    onTrackingLost: () -> Unit = {}, onInterrupted: () -> Unit = {}
) {
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val displayManager = remember(context) { context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager }
    var rotation by remember { mutableIntStateOf(displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.rotation ?: 0) }
    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val interrupted by rememberUpdatedState(onInterrupted)
    DisposableEffect(displayManager) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) = Unit
            override fun onDisplayRemoved(id: Int) = Unit
            override fun onDisplayChanged(id: Int) {
                val next = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.rotation ?: 0
                if (next != rotation) { interrupted(); rotation = next }
            }
        }
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { displayManager.unregisterDisplayListener(listener) }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { interrupted(); foreground = false }
            if (event == Lifecycle.Event.ON_START) foreground = true
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Box(modifier.onSizeChanged { if (size != it && size != IntSize.Zero) interrupted(); size = it }) {
        if (foreground && size.width > 0 && size.height > 0) key(lens, retry, size, rotation) {
            MountedPoseCamera(lens, frameKey, onPose, onBinding, onStatus, onTrackingLost, onInterrupted)
        }
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
@Composable
private fun MountedPoseCamera(
    lens: Int, frameKey: (Long, Long) -> FrameKey, onPose: (PoseResult) -> Unit,
    onBinding: (Long) -> Unit, onStatus: (PoseCameraStatus) -> Unit,
    onTrackingLost: () -> Unit, onInterrupted: () -> Unit
) {
    val context = LocalContext.current
    val main = remember(context) { ContextCompat.getMainExecutor(context) }
    val view = remember { PreviewView(context).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    } }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val active = remember { AtomicBoolean(true) }
    val failed = remember { AtomicBoolean(false) }
    val epoch = remember { cameraEpochs.incrementAndGet() }
    val sequence = remember { AtomicLong() }
    val detector = remember { AtomicReference<PoseDetector?>(null) }
    var pose by remember { mutableStateOf<PoseResult?>(null) }
    var actualLens by remember { mutableIntStateOf(lens) }
    var lastResult by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var lossReported by remember { mutableStateOf(false) }
    val keyCallback by rememberUpdatedState(frameKey)
    val poseCallback by rememberUpdatedState(onPose)
    val statusCallback by rememberUpdatedState(onStatus)
    val lostCallback by rememberUpdatedState(onTrackingLost)
    val interruptCallback by rememberUpdatedState(onInterrupted)
    val fail: (String) -> Unit = remember {
        { message ->
            main.execute {
                if (active.get() && failed.compareAndSet(false, true)) {
                    pose = null
                    interruptCallback()
                    statusCallback(PoseCameraStatus(false, message, actualLens))
                    worker.execute { detector.get()?.close() }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onBinding(epoch)
        statusCallback(PoseCameraStatus(false, "Starting camera…", lens))
        worker.execute {
            try {
                val created = PoseDetector(context.applicationContext, worker, { result ->
                    main.execute {
                        if (active.get() && !failed.get() && result.frame.key.cameraEpoch == epoch) {
                            if (SystemClock.elapsedRealtime() - result.submittedMs > 500) {
                                lostCallback(); pose = null
                            } else {
                                val target = view.outputTransform
                                if (target == null) { pose = null; lostCallback() }
                                else try {
                                    val points = result.landmarks.flatMap { listOf(it.x * result.imageWidth, it.y * result.imageHeight) }.toFloatArray()
                                    result.modelToRaw.mapPoints(points)
                                    CoordinateTransform(result.sourceTransform, target).mapPoints(points)
                                    val display = points.toList().chunked(2).map { it[0] to it[1] }
                                    val mapped = result.copy(displayPoints = display)
                                    pose = mapped
                                    lastResult = SystemClock.elapsedRealtime()
                                    lossReported = false
                                    val lensName = if (actualLens == CameraSelector.LENS_FACING_FRONT) "Front camera" else "Rear camera"
                                    statusCallback(PoseCameraStatus(true,
                                        lensName + if (actualLens != lens) " · requested camera unavailable" else "", actualLens))
                                    poseCallback(mapped)
                                } catch (error: Exception) { fail("Preview alignment unavailable — retry camera") }
                            }
                        }
                    }
                }, fail)
                // Worker is the only reader/writer used for detection/close.
                detector.set(created)
                if (!active.get() || failed.get()) created.close()
            } catch (error: Exception) { fail("Pose model unavailable: ${error.message ?: "retry camera"}") }
        }
        onDispose {
            active.set(false)
            interruptCallback()
            worker.execute { try { detector.get()?.close() } finally { worker.shutdown() } }
        }
    }
    val analyzer = remember {
        ImageAnalysis.Analyzer { image ->
            if (!active.get() || failed.get()) image.close()
            else detector.get()?.detect(image, keyCallback(epoch, sequence.incrementAndGet())) ?: image.close()
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            if (!failed.get() && SystemClock.elapsedRealtime() - lastResult > 500 && !lossReported) {
                pose = null; lossReported = true; lostCallback()
                statusCallback(PoseCameraStatus(false, "Waiting for camera frames — check the camera or Retry", actualLens))
            }
            if (!failed.get() && detector.get()?.isStalled(SystemClock.elapsedRealtime()) == true) fail("Pose processing stalled — retry camera")
        }
    }
    Box(Modifier.fillMaxSize()) {
        CameraPreview(view, lens, worker, analyzer, Modifier.fillMaxSize(),
            onLens = { actualLens = it }, onError = fail)
        PoseOverlay(pose, Modifier.fillMaxSize())
    }
}
