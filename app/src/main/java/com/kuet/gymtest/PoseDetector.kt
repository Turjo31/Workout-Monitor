package com.kuet.gymtest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

data class PoseLandmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float
)

data class PoseResult(
    val landmarks: List<PoseLandmark>,
    val imageWidth: Int,
    val imageHeight: Int
)

/** MediaPipe landmark index pairs forming the skeleton. */
val POSE_CONNECTIONS = listOf(
    11 to 12,
    11 to 13, 13 to 15,
    12 to 14, 14 to 16,
    11 to 23, 12 to 24, 23 to 24,
    23 to 25, 25 to 27,
    24 to 26, 26 to 28
)

class PoseDetector(
    context: Context,
    private val onResult: (PoseResult) -> Unit
) {

    @Volatile
    private var closed = false

    private val landmarker: PoseLandmarker

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result: PoseLandmarkerResult, input: MPImage ->
                handleResult(result, input)
            }
            .setErrorListener { it.printStackTrace() }
            .build()

        landmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun detect(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (closed) {
            imageProxy.close()
            return
        }

        val frameTime = SystemClock.uptimeMillis()

        val bitmap = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmap.copyPixelsFromBuffer(it.planes[0].buffer) }

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (isFrontCamera) {
                postScale(-1f, 1f, imageProxy.width / 2f, imageProxy.height / 2f)
            }
        }

        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )

        landmarker.detectAsync(BitmapImageBuilder(rotated).build(), frameTime)
    }

    private fun handleResult(result: PoseLandmarkerResult, input: MPImage) {
        if (closed) return
        val landmarks = result.landmarks().firstOrNull()?.map {
            PoseLandmark(it.x(), it.y(), it.z(), it.visibility().orElse(0f))
        } ?: emptyList()
        onResult(PoseResult(landmarks, input.width, input.height))
    }

    fun close() {
        closed = true
        landmarker.close()
    }
}