package com.dailysatori.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val UiFontFamily = FontFamily.SansSerif

val ContentFontFamily = UiFontFamily
val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 37.sp, letterSpacing = (-0.4).sp),
    displaySmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 33.sp, letterSpacing = (-0.3).sp),
    headlineLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = (-0.1).sp),
    headlineSmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontFamily = ContentFontFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.sp),
)
