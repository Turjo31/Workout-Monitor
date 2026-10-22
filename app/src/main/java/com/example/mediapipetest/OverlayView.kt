package com.example.mediapipetest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var landmarks: List<NormalizedLandmark> = emptyList()
    private var imageWidth = 0
    private var imageHeight = 0

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.MAGENTA
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }

    // MediaPipe Pose connections
    private val connections = listOf(
        11 to 12, // Shoulders
        11 to 13, 13 to 15, // Left arm
        12 to 14, 14 to 16, // Right arm
        11 to 23, 12 to 24, // Torso
        23 to 24, // Hips
        23 to 25, 25 to 27, // Left leg
        27 to 29, 29 to 31,
        27 to 31,
        24 to 26, 26 to 28, // Right leg
        28 to 30, 30 to 32,
        28 to 32,
        0 to 1, 1 to 2, 2 to 3, 3 to 7,
        0 to 4, 4 to 5, 5 to 6, 6 to 8,
        9 to 10
    )

    fun setLandmarks(
        newLandmarks: List<NormalizedLandmark>,
        sourceWidth: Int,
        sourceHeight: Int
    ) {
        landmarks = newLandmarks
        imageWidth = sourceWidth
        imageHeight = sourceHeight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (landmarks.isEmpty() ||
            imageWidth == 0 ||
            imageHeight == 0
        ) return

        // Match PreviewView's FILL_CENTER scaling
        val scale = maxOf(
            width.toFloat() / imageWidth,
            height.toFloat() / imageHeight
        )

        val offsetX = (width - imageWidth * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f

        fun point(index: Int): Pair<Float, Float> {
            val landmark = landmarks[index]

            return Pair(
                offsetX + landmark.x() * imageWidth * scale,
                offsetY + landmark.y() * imageHeight * scale
            )
        }

        // Draw skeleton connections
        for ((start, end) in connections) {
            if (start >= landmarks.size ||
                end >= landmarks.size
            ) continue

            val (x1, y1) = point(start)
            val (x2, y2) = point(end)

            canvas.drawLine(x1, y1, x2, y2, linePaint)
        }

        // Draw joints
        for (i in landmarks.indices) {
            val (x, y) = point(i)
            canvas.drawCircle(x, y, 7f, pointPaint)
        }
    }
}