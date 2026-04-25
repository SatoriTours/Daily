package com.dailysatori.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dailysatori.R

val LatoFontFamily = FontFamily(
    Font(R.font.lato_thin, FontWeight.Thin),
    Font(R.font.lato_light, FontWeight.Light),
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold),
    Font(R.font.lato_black, FontWeight.Black),
    Font(R.font.lato_thin_italic, FontWeight.Thin),
    Font(R.font.lato_light_italic, FontWeight.Light),
    Font(R.font.lato_italic, FontWeight.Normal),
    Font(R.font.lato_bold_italic, FontWeight.Bold),
    Font(R.font.lato_black_italic, FontWeight.Black),
)

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 63.8.sp, letterSpacing = 0.15.sp),
    displayMedium = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.2.sp, letterSpacing = 0.15.sp),
    displaySmall = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 43.9.sp, letterSpacing = 0.15.sp),
    headlineLarge = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.15.sp),
    headlineMedium = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 31.2.sp, letterSpacing = 0.15.sp),
    headlineSmall = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.15.sp),
    titleLarge = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 23.sp, letterSpacing = 0.15.sp),
    titleMedium = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    bodyLarge = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 30.4.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 28.5.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 23.4.sp, letterSpacing = 0.15.sp),
    labelLarge = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    labelMedium = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.15.sp),
    labelSmall = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.15.sp),
)
