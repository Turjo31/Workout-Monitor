package com.example.mediapipetest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class PoseAnalyzer(
    context: Context,
    private val onLandmarksDetected:
        (List<NormalizedLandmark>, Int, Int) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile
    private var frameWidth = 0

    @Volatile
    private var frameHeight = 0

    private val poseLandmarkerHelper =
        PoseLandmarkerHelper(context) { landmarks ->

            onLandmarksDetected(
                landmarks,
                frameWidth,
                frameHeight
            )
        }

    init {
        poseLandmarkerHelper.setup()
    }

    override fun analyze(imageProxy: ImageProxy) {

        try {

            // Camera frame rotation
            val rotationDegrees =
                imageProxy.imageInfo.rotationDegrees

            // Create Bitmap
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )

            // Copy RGBA CameraX data into Bitmap
            imageProxy.use { image ->

                image.planes[0].buffer.rewind()

                bitmapBuffer.copyPixelsFromBuffer(
                    image.planes[0].buffer
                )
            }

            // Rotate image to correct orientation
            val rotatedBitmap = if (rotationDegrees != 0) {

                val matrix = Matrix().apply {
                    postRotate(
                        rotationDegrees.toFloat()
                    )
                }

                Bitmap.createBitmap(
                    bitmapBuffer,
                    0,
                    0,
                    bitmapBuffer.width,
                    bitmapBuffer.height,
                    matrix,
                    true
                )

            } else {

                bitmapBuffer
            }

            // Save dimensions of the image
            // that MediaPipe actually receives
            frameWidth = rotatedBitmap.width
            frameHeight = rotatedBitmap.height

            // Convert Bitmap → MPImage
            val mpImage =
                BitmapImageBuilder(rotatedBitmap)
                    .build()

            // Send frame to MediaPipe
            poseLandmarkerHelper.detectAsync(
                mpImage,
                SystemClock.uptimeMillis()
            )

        } catch (e: Exception) {

            Log.e(
                "PoseAnalyzer",
                "Error processing frame",
                e
            )
        }
    }

    fun close() {
        poseLandmarkerHelper.close()
    }
}