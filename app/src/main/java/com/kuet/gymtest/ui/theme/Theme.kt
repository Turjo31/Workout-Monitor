package com.kuet.gymtest.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Blue family: structure (headers, tags, back buttons)
val LightBlue = Color(0xFFD9EDF8)
val DeepBlue = Color(0xFF18506F)

// Green family: actions (buttons, progress)
val LightGreen = Color(0xFFD5F0DF)
val DeepGreen = Color(0xFF17583A)

private val AppColors = lightColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    primaryContainer = LightBlue,
    onPrimaryContainer = DeepBlue,
    secondary = DeepGreen,
    onSecondary = Color.White,
    secondaryContainer = LightGreen,
    onSecondaryContainer = DeepGreen,
    background = Color(0xFFF6FAFB),
    onBackground = Color(0xFF14272F),
    surface = Color.White,
    onSurface = Color(0xFF14272F),
    surfaceVariant = Color(0xFFEDF3F5),
    onSurfaceVariant = Color(0xFF53656C),
    outline = Color(0xFFBCCFD5)
)

@Composable
fun GymTestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        content = content
    )
}