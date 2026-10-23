package com.kuet.gymtest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SoftNeutralColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = ActionGreen,
    onPrimaryContainer = Color(0xFF1F3B28),
    secondary = Color(0xFF655C4E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE4D6),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFF835626),
    onTertiary = Color.White,
    tertiaryContainer = Peach,
    onTertiaryContainer = Color(0xFF4F3219),
    background = Ivory,
    onBackground = Ink,
    surface = IvoryLight,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAE3D8),
    onSurfaceVariant = MutedInk,
    surfaceContainerLowest = IvoryLight,
    surfaceContainerLow = Color(0xFFF5F0E8),
    surfaceContainer = Color(0xFFF0E9DE),
    surfaceContainerHigh = Color(0xFFECE4D7),
    surfaceContainerHighest = Color(0xFFE6DDCE),
    surfaceTint = Color.Transparent,
    outline = WarmOutline,
    outlineVariant = Color(0xFFDCD3C6),
    error = SoftError,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF6D211B)
)

@Composable
fun GymTestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SoftNeutralColors,
        typography = AppTypography,
        shapes = WorkoutMaterialShapes,
        content = content
    )
}
