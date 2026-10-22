package com.example.mediapipetest

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

class PoseLandmarkerHelper(
    private val context: Context,
    private val onResults: (List<NormalizedLandmark>) -> Unit
) {

    private var poseLandmarker: PoseLandmarker? = null

    fun setup() {
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
            .setResultListener { result, _ ->
                val poseCount = result.landmarks().size
                Log.d(
                    "MediaPipePose",
                    "Pose detected! Poses: $poseCount"
                )

                if (result.landmarks().isNotEmpty()) {
                    onResults(result.landmarks()[0])
                }
            }
            .setErrorListener { error ->
                Log.e(
                    "MediaPipePose",
                    "MediaPipe error: ${error.message}"
                )
            }
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(
            context,
            options
        )
    }

    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
    }
}
