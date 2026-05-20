package com.dailysatori.ui.theme

import android.app.Activity
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
    primary = AppColors.primary,
    onPrimary = Color(0xFF03121D),
    secondary = AppColors.secondary,
    onSecondary = Color(0xFF140A2A),
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
    primary = AppColors.sapphire,
    onPrimary = Color(0xFF03121D),
    secondary = AppColors.secondary,
    onSecondary = Color(0xFF140A2A),
    secondaryContainer = AppColors.secondaryContainer,
    onSecondaryContainer = AppColors.onSecondaryContainer,
    tertiaryContainer = AppColors.tertiaryContainer,
    background = AppColors.liquidBackground,
    onBackground = AppColors.onLiquid,
    surface = AppColors.liquidSurface,
    onSurface = AppColors.onLiquid,
    surfaceVariant = AppColors.liquidSurfaceHigh,
    onSurfaceVariant = AppColors.onLiquidVariant,
    outline = AppColors.liquidOutline,
    outlineVariant = AppColors.liquidOutlineHigh,
    error = AppColors.error,
    surfaceContainer = AppColors.liquidSurfaceHigh,
    surfaceContainerHighest = AppColors.liquidSurfaceHighest,
)

@Composable
fun DailySatoriTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
