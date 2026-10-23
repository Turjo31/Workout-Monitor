package com.kuet.gymtest.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kuet.gymtest.ui.theme.Ivory
import com.kuet.gymtest.ui.theme.GlassHighlight
import dev.chrisbanes.haze.*

internal const val GLASS_BACKGROUND = "workout-glass-background"
internal val LocalGlassBackdrop = staticCompositionLocalOf<HazeState?> { null }

/**
 * Captures only the decorative backdrop. A card never captures itself or its
 * foreground text. Camera screens deliberately use their existing dark overlay
 * instead of sampling the unrelated application backdrop through the preview.
 */
@Composable
fun GlassBackdropProvider(content: @Composable () -> Unit) {
    // Respect Haze's platform safety defaults (including the Android 12
    // RenderNode invalidation fallback) rather than forcing blur everywhere.
    val state = rememberHazeState()
    CompositionLocalProvider(LocalGlassBackdrop provides state, content = content)
}

@Composable
@OptIn(ExperimentalHazeApi::class)
internal fun Modifier.glassBackdrop(fill: Color): Modifier {
    val state = LocalGlassBackdrop.current ?: return this
    val style = remember(fill) {
        HazeStyle(
            backgroundColor = Ivory,
            blurRadius = 22.dp,
            noiseFactor = 0.025f,
            tints = listOf(
                HazeTint(fill.copy(alpha = fill.alpha * 0.40f)),
                HazeTint(Brush.linearGradient(
                    listOf(GlassHighlight, GlassHighlight.copy(alpha = 0f))
                ))
            ),
            fallbackTint = HazeTint(fill.copy(alpha = fill.alpha * .40f))
        )
    }
    return hazeEffect(state = state, style = style) {
        canDrawArea = { it.key == GLASS_BACKGROUND }
        inputScale = HazeInputScale.Auto
    }
}
