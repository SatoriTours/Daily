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
    primary = Color(0xFF8AB4F8),
    onPrimary = Color.White,
    secondary = Color(0xFF66BB6A),
    secondaryContainer = Color(0xFF2E3B2E),
    onSecondaryContainer = Color(0xFF81C784),
    tertiaryContainer = Color(0xFF3E2723),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFBDBDBD),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFF9E9E9E),
    outline = Color(0xFF424242),
    outlineVariant = Color(0xFF757575),
    error = Color(0xFFE57373),
    surfaceContainer = Color(0xFF2C2C2C),
    surfaceContainerHighest = Color(0xFF3A3A3A),
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
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
