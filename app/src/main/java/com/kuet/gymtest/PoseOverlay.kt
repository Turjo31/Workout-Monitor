package com.kuet.gymtest

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.kuet.gymtest.ui.theme.LightGreen
import kotlin.math.max

private const val MIN_VISIBILITY = 0.5f

@Composable
fun PoseOverlay(pose: PoseResult?, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val result = pose ?: return@Canvas
        if (result.landmarks.isEmpty()) return@Canvas

        // Matches PreviewView.ScaleType.FILL_CENTER
        val scale = max(size.width / result.imageWidth, size.height / result.imageHeight)
        val dx = (size.width - result.imageWidth * scale) / 2f
        val dy = (size.height - result.imageHeight * scale) / 2f

        fun point(l: PoseLandmark) = Offset(
            l.x * result.imageWidth * scale + dx,
            l.y * result.imageHeight * scale + dy
        )

        POSE_CONNECTIONS.forEach { (a, b) ->
            val la = result.landmarks.getOrNull(a)
            val lb = result.landmarks.getOrNull(b)
            if (la != null && lb != null &&
                la.visibility > MIN_VISIBILITY && lb.visibility > MIN_VISIBILITY
            ) {
                drawLine(
                    color = LightGreen,
                    start = point(la),
                    end = point(lb),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round
                )
            }
        }

        POSE_CONNECTIONS.flatMap { listOf(it.first, it.second) }.distinct().forEach { i ->
            val l = result.landmarks.getOrNull(i)
            if (l != null && l.visibility > MIN_VISIBILITY) {
                drawCircle(color = Color.White, radius = 9f, center = point(l))
            }
        }
    }
}