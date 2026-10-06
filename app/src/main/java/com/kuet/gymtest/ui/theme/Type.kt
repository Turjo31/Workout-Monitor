package com.kuet.gymtest.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.kuet.gymtest.R

val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_medium, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold)
)

private val Base = Typography()

val AppTypography = Typography(
    displayLarge = Base.displayLarge.copy(fontFamily = Nunito),
    displayMedium = Base.displayMedium.copy(fontFamily = Nunito),
    displaySmall = Base.displaySmall.copy(fontFamily = Nunito),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Nunito),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Nunito),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Nunito),
    titleLarge = Base.titleLarge.copy(fontFamily = Nunito),
    titleMedium = Base.titleMedium.copy(fontFamily = Nunito),
    titleSmall = Base.titleSmall.copy(fontFamily = Nunito),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Nunito),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Nunito),
    bodySmall = Base.bodySmall.copy(fontFamily = Nunito),
    labelLarge = Base.labelLarge.copy(fontFamily = Nunito),
    labelMedium = Base.labelMedium.copy(fontFamily = Nunito),
    labelSmall = Base.labelSmall.copy(fontFamily = Nunito)
)