package com.dailysatori.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = AppColors.paperLightPrimaryRole,
    onPrimary = AppColors.paperLightOnPrimaryRole,
    primaryContainer = AppColors.paperLightPrimaryContainer,
    onPrimaryContainer = AppColors.paperLightOnPrimaryContainer,
    secondary = AppColors.paperLightSecondaryRole,
    onSecondary = AppColors.paperLightOnSecondaryRole,
    secondaryContainer = AppColors.paperLightSecondaryContainer,
    onSecondaryContainer = AppColors.paperLightOnSecondaryContainer,
    tertiary = AppColors.paperLightTertiaryRole,
    onTertiary = AppColors.paperLightOnTertiaryRole,
    tertiaryContainer = AppColors.paperLightTertiaryContainer,
    onTertiaryContainer = AppColors.paperLightOnTertiaryContainer,
    background = AppColors.paperLightBackground,
    onBackground = AppColors.paperLightOnSurface,
    surface = AppColors.paperLightSurface,
    onSurface = AppColors.paperLightOnSurface,
    surfaceVariant = AppColors.paperLightSurfaceContainer,
    onSurfaceVariant = AppColors.paperLightOnSurfaceVariant,
    outline = AppColors.paperLightOutline,
    outlineVariant = AppColors.paperLightOutlineHigh,
    error = AppColors.editorialVermilion,
    errorContainer = AppColors.paperLightErrorContainer,
    onErrorContainer = AppColors.paperLightOnErrorContainer,
    surfaceContainerLow = AppColors.paperLightSurfaceLow,
    surfaceContainer = AppColors.paperLightSurfaceContainer,
    surfaceContainerHigh = AppColors.paperLightSurfaceHigh,
    surfaceContainerHighest = AppColors.paperLightSurfaceHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.obsidianDarkPrimaryRole,
    onPrimary = AppColors.obsidianDarkOnPrimaryRole,
    primaryContainer = AppColors.obsidianDarkPrimaryContainer,
    onPrimaryContainer = AppColors.obsidianDarkOnPrimaryContainer,
    secondary = AppColors.obsidianDarkSecondaryRole,
    onSecondary = AppColors.obsidianDarkOnSecondaryRole,
    secondaryContainer = AppColors.obsidianDarkSecondaryContainer,
    onSecondaryContainer = AppColors.obsidianDarkOnSecondaryContainer,
    tertiary = AppColors.obsidianDarkTertiaryRole,
    onTertiary = AppColors.obsidianDarkOnTertiaryRole,
    tertiaryContainer = AppColors.obsidianDarkTertiaryContainer,
    onTertiaryContainer = AppColors.obsidianDarkOnTertiaryContainer,
    background = AppColors.obsidianDarkBackground,
    onBackground = AppColors.obsidianDarkOnSurface,
    surface = AppColors.obsidianDarkSurface,
    onSurface = AppColors.obsidianDarkOnSurface,
    surfaceVariant = AppColors.obsidianDarkSurfaceContainer,
    onSurfaceVariant = AppColors.obsidianDarkOnSurfaceVariant,
    outline = AppColors.obsidianDarkOutline,
    outlineVariant = AppColors.obsidianDarkOutlineHigh,
    error = AppColors.obsidianDarkErrorRole,
    errorContainer = AppColors.obsidianDarkErrorContainer,
    onErrorContainer = AppColors.obsidianDarkOnErrorContainer,
    surfaceContainerLow = AppColors.obsidianDarkSurfaceLow,
    surfaceContainer = AppColors.obsidianDarkSurfaceContainer,
    surfaceContainerHigh = AppColors.obsidianDarkSurfaceHigh,
    surfaceContainerHighest = AppColors.obsidianDarkSurfaceHighest,
)

@Composable
fun DailySatoriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
