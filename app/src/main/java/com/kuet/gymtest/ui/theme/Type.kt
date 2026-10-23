package com.kuet.gymtest.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
    displaySmall = Base.displaySmall.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = Base.titleLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = Base.titleMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = Base.titleSmall.copy(fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Nunito, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Nunito, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = Base.bodySmall.copy(fontFamily = Nunito, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = Base.labelLarge.copy(fontFamily = Nunito),
    labelMedium = Base.labelMedium.copy(fontFamily = Nunito),
    labelSmall = Base.labelSmall.copy(fontFamily = Nunito)
)
