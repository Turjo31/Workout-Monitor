package com.kuet.gymtest

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.kuet.gymtest.ui.theme.TrackingGreen

private const val MIN_VISIBILITY = 0.5f

@Composable
fun PoseOverlay(pose: PoseResult?, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val result = pose ?: return@Canvas
        if (result.landmarks.isEmpty()) return@Canvas

        if (result.displayPoints.size != result.landmarks.size ||
            result.displayPoints.any { !it.first.isFinite() || !it.second.isFinite() }) return@Canvas
        fun point(i: Int) = result.displayPoints[i].let { Offset(it.first, it.second) }

        POSE_CONNECTIONS.forEach { (a, b) ->
            val la = result.landmarks.getOrNull(a)
            val lb = result.landmarks.getOrNull(b)
            if (la != null && lb != null &&
                (la.visibility ?: 0f) > MIN_VISIBILITY && (lb.visibility ?: 0f) > MIN_VISIBILITY
            ) {
                drawLine(
                    color = TrackingGreen,
                    start = point(a),
                    end = point(b),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round
                )
            }
        }

        POSE_CONNECTIONS.flatMap { listOf(it.first, it.second) }.distinct().forEach { i ->
            val l = result.landmarks.getOrNull(i)
            if (l != null && (l.visibility ?: 0f) > MIN_VISIBILITY) {
                drawCircle(color = Color.White, radius = 9f, center = point(i))
            }
        }
    }
}
