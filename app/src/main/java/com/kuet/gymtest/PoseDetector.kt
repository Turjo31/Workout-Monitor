package com.kuet.gymtest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.kuet.gymtest.analysis.FrameClock
import com.kuet.gymtest.analysis.FrameKey
import com.kuet.gymtest.analysis.PoseFrame
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

typealias PoseLandmark = com.kuet.gymtest.analysis.PoseLandmark

@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
data class PoseResult(
    val frame: PoseFrame, val sourceTransform: OutputTransform,
    val modelToRaw: Matrix, val submittedMs: Long,
    val displayPoints: List<Pair<Float, Float>> = emptyList()
) {
    val landmarks get() = frame.landmarks
    val imageWidth get() = frame.imageWidth
    val imageHeight get() = frame.imageHeight
}

val POSE_CONNECTIONS = listOf(11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
    11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27, 24 to 26, 26 to 28)

/** Native calls and image ownership are serialized on worker. One inference in flight. */
@androidx.annotation.OptIn(markerClass = [androidx.camera.view.TransformExperimental::class])
class PoseDetector(
    context: Context, private val worker: Executor,
    private val onResult: (PoseResult) -> Unit, private val onError: (String) -> Unit
) {
    private data class Pending(val key: FrameKey, val time: Long, val submitted: Long,
        val image: MPImage, val bitmap: Bitmap, val source: OutputTransform, val inverse: Matrix)
    private val pending = AtomicReference<Pending?>(null)
    private val clock = FrameClock()
    @Volatile private var closed = false
    private val landmarker = PoseLandmarker.createFromOptions(context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build())
            .setRunningMode(RunningMode.LIVE_STREAM).setNumPoses(1)
            .setMinPoseDetectionConfidence(.5f).setMinPosePresenceConfidence(.5f).setMinTrackingConfidence(.5f)
            .setResultListener { result, input ->
                val slot = pending.get()
                if (!closed && slot != null && result.timestampMs() == slot.time) {
                    val landmarks = result.landmarks().firstOrNull()?.map {
                        PoseLandmark(it.x(), it.y(), it.z(), it.visibility().orElse(null), it.presence().orElse(null))
                    }.orEmpty()
                    val pose = PoseResult(PoseFrame(slot.key, slot.time, input.width, input.height, landmarks),
                        slot.source, Matrix(slot.inverse), slot.submitted)
                    worker.execute {
                        if (!closed) {
                            releasePending()
                            if (SystemClock.elapsedRealtime() - slot.submitted <= 500) onResult(pose)
                            else onError("Pose processing delayed — retry camera")
                        }
                    }
                }
            }
            .setErrorListener { error -> if (!closed) onError("Pose model error: ${error.message ?: "retry camera"}") }
            .build())

    fun detect(proxy: ImageProxy, key: FrameKey) {
        if (closed || pending.get() != null) { proxy.close(); return }
        var model: Bitmap? = null
        try {
            val received = SystemClock.elapsedRealtime()
            val timestamp = clock.timestamp(proxy.imageInfo.timestamp, received) ?: return
            val crop = android.graphics.Rect(proxy.cropRect)
            val rotation = proxy.imageInfo.rotationDegrees
            val source = ImageProxyTransformFactory().getOutputTransform(proxy)
            val plane = proxy.planes[0]
            val converted = CameraFrameConversion.convert(plane.buffer, plane.rowStride, plane.pixelStride, crop, rotation)
            model = converted.bitmap
            val input = BitmapImageBuilder(model).build()
            pending.set(Pending(key, timestamp, received, input, model, source, converted.modelToRaw))
            landmarker.detectAsync(input, timestamp)
        } catch (error: Exception) {
            if (pending.get() == null) model?.recycle()
            onError("Camera analysis failed: ${error.message ?: "retry camera"}")
        } finally { proxy.close() }
    }

    fun isStalled(now: Long) = pending.get()?.let { now - it.submitted > 500 } == true
    private fun releasePending() { pending.getAndSet(null)?.let { it.image.close(); it.bitmap.recycle() } }
    fun close() {
        if (closed) return
        closed = true
        try { landmarker.close() } finally { releasePending() }
    }
}
