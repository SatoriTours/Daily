package com.dailysatori.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle

private object DiaryColors {
    val lightBackground = Color(0xFFFAF7F1)
    val lightSurface = Color(0xFFF2EBE0)
    val lightSurfaceHigh = Color(0xFFE9E0D4)
    val lightText = Color(0xFF292621)
    val lightSecondaryText = Color(0xFF70685F)
    val lightOutline = Color(0xFFD8CFC2)
    val lightPrimary = Color(0xFF2B6299)
    val lightAction = Color(0xFFE0EAF4)
    val lightQuote = Color(0xFF82551F)
    val lightMood = Color(0xFFF5E7D2)
    val darkBackground = Color(0xFF1D1C1A)
    val darkSurface = Color(0xFF2A2723)
    val darkSurfaceHigh = Color(0xFF35312B)
    val darkText = Color(0xFFECE5D8)
    val darkSecondaryText = Color(0xFFB8B0A5)
    val darkOutline = Color(0xFF484239)
    val darkPrimary = Color(0xFF93BDDF)
    val darkAction = Color(0xFF2C3B48)
    val darkActionText = Color(0xFFA9CCE8)
    val darkQuote = Color(0xFFD5AF77)
    val darkMood = Color(0xFF47351F)
}

internal fun diaryColorScheme(base: ColorScheme): ColorScheme {
    val dark = base.background.luminance() < 0.5f
    val background = if (dark) DiaryColors.darkBackground else DiaryColors.lightBackground
    val surface = if (dark) DiaryColors.darkSurface else DiaryColors.lightSurface
    val text = if (dark) DiaryColors.darkText else DiaryColors.lightText
    val primary = if (dark) DiaryColors.darkPrimary else DiaryColors.lightPrimary
    val action = if (dark) DiaryColors.darkAction else DiaryColors.lightAction
    val actionText = if (dark) DiaryColors.darkActionText else primary
    val quote = if (dark) DiaryColors.darkQuote else DiaryColors.lightQuote
    val outline = if (dark) DiaryColors.darkOutline else DiaryColors.lightOutline
    return base.copy(
        background = background, onBackground = text, surface = surface, onSurface = text,
        surfaceVariant = surface, onSurfaceVariant = if (dark) DiaryColors.darkSecondaryText else DiaryColors.lightSecondaryText,
        surfaceContainerLowest = background, surfaceContainerLow = background, surfaceContainer = surface,
        surfaceContainerHigh = if (dark) DiaryColors.darkSurfaceHigh else DiaryColors.lightSurfaceHigh,
        surfaceContainerHighest = if (dark) DiaryColors.darkSurfaceHigh else DiaryColors.lightSurfaceHigh,
        primary = primary, primaryContainer = action, onPrimaryContainer = actionText,
        secondary = primary, secondaryContainer = action, onSecondaryContainer = actionText,
        tertiary = quote, tertiaryContainer = if (dark) DiaryColors.darkMood else DiaryColors.lightMood,
        onTertiaryContainer = quote, outline = outline, outlineVariant = outline,
        surfaceTint = primary,
    )
}

@Composable
fun DiaryTheme(enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val typography = MaterialTheme.typography
    MaterialTheme(
        colorScheme = diaryColorScheme(MaterialTheme.colorScheme),
        typography = typography.copy(
            displayMedium = typography.displayMedium.copy(fontFamily = FontFamily.Serif),
            headlineLarge = typography.headlineLarge.copy(fontFamily = FontFamily.Serif),
            headlineMedium = typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
            headlineSmall = typography.headlineSmall.copy(fontFamily = FontFamily.Serif),
            titleLarge = typography.titleLarge.copy(fontFamily = FontFamily.Serif),
        ),
        content = content,
    )
}

object DiaryStyles {
    @Composable
    fun quoteTypography() = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic,
    )
}
