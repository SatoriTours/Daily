package com.dailysatori.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = AppColors.primary,
    onPrimary = Color.White,
    secondary = AppColors.secondary,
    onSecondary = Color.White,
    secondaryContainer = AppColors.secondaryContainer,
    onSecondaryContainer = AppColors.onSecondaryContainer,
    tertiaryContainer = AppColors.tertiaryContainer,
    background = AppColors.background,
    onBackground = AppColors.onBackground,
    surface = AppColors.surface,
    onSurface = AppColors.onSurface,
    surfaceVariant = AppColors.surfaceContainer,
    onSurfaceVariant = AppColors.onSurfaceVariant,
    outline = AppColors.outline,
    outlineVariant = AppColors.outlineVariant,
    error = AppColors.error,
    surfaceContainer = AppColors.surfaceContainer,
    surfaceContainerHighest = AppColors.surfaceContainerHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.primaryLight,
    onPrimary = Color.White,
    secondary = AppColors.secondaryDark,
    secondaryContainer = AppColors.secondaryContainerDark,
    onSecondaryContainer = AppColors.onSecondaryContainerDark,
    tertiaryContainer = AppColors.tertiaryContainerDark,
    background = AppColors.backgroundDark,
    onBackground = AppColors.onBackgroundDark,
    surface = AppColors.surfaceDark,
    onSurface = AppColors.onSurfaceDark,
    surfaceVariant = AppColors.surfaceContainerDark,
    onSurfaceVariant = AppColors.onSurfaceVariantDark,
    outline = AppColors.outlineDark,
    outlineVariant = AppColors.outlineVariantDark,
    error = AppColors.errorDark,
    surfaceContainer = AppColors.surfaceContainerDark,
    surfaceContainerHighest = AppColors.surfaceContainerHighestDark,
)

@Composable
fun DailySatoriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        content = content,
    )
}
