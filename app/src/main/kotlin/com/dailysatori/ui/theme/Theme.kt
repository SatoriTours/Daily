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
    primary = AppColors.iosLightPrimaryRole,
    onPrimary = AppColors.iosLightOnPrimaryRole,
    primaryContainer = AppColors.iosLightPrimaryContainer,
    onPrimaryContainer = AppColors.iosLightOnPrimaryContainer,
    secondary = AppColors.iosLightSecondaryRole,
    onSecondary = AppColors.iosLightOnSecondaryRole,
    secondaryContainer = AppColors.iosLightSecondaryContainer,
    onSecondaryContainer = AppColors.iosLightOnSecondaryContainer,
    tertiary = AppColors.iosLightTertiaryRole,
    onTertiary = AppColors.iosLightOnTertiaryRole,
    tertiaryContainer = AppColors.iosLightTertiaryContainer,
    background = AppColors.iosLightBackground,
    onBackground = AppColors.iosLightOnSurface,
    surface = AppColors.iosLightSurface,
    onSurface = AppColors.iosLightOnSurface,
    surfaceVariant = AppColors.iosLightSurfaceHigh,
    onSurfaceVariant = AppColors.iosLightOnSurfaceVariant,
    outline = AppColors.iosLightOutline,
    outlineVariant = AppColors.iosLightOutlineHigh,
    error = AppColors.iosRed,
    errorContainer = AppColors.iosLightErrorContainer,
    onErrorContainer = AppColors.iosLightOnErrorContainer,
    surfaceContainerLow = AppColors.iosLightSurfaceContainerLow,
    surfaceContainer = AppColors.iosLightSurfaceHigh,
    surfaceContainerHighest = AppColors.iosLightSurfaceHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.iosDarkPrimaryRole,
    onPrimary = AppColors.iosDarkOnPrimaryRole,
    primaryContainer = AppColors.iosDarkPrimaryContainer,
    onPrimaryContainer = AppColors.iosDarkOnPrimaryContainer,
    secondary = AppColors.iosDarkSecondaryRole,
    onSecondary = AppColors.iosDarkOnSecondaryRole,
    secondaryContainer = AppColors.iosDarkSecondaryContainer,
    onSecondaryContainer = AppColors.iosDarkOnSecondaryContainer,
    tertiary = AppColors.iosDarkTertiaryRole,
    onTertiary = AppColors.iosDarkOnTertiaryRole,
    tertiaryContainer = AppColors.iosDarkTertiaryContainer,
    background = AppColors.iosDarkBackground,
    onBackground = AppColors.iosDarkOnSurface,
    surface = AppColors.iosDarkSurface,
    onSurface = AppColors.iosDarkOnSurface,
    surfaceVariant = AppColors.iosDarkSurfaceHigh,
    onSurfaceVariant = AppColors.iosDarkOnSurfaceVariant,
    outline = AppColors.iosDarkOutline,
    outlineVariant = AppColors.iosDarkOutlineHigh,
    error = AppColors.iosDarkErrorRole,
    errorContainer = AppColors.iosDarkErrorContainer,
    onErrorContainer = AppColors.iosDarkOnErrorContainer,
    surfaceContainerLow = AppColors.iosDarkSurfaceContainerLow,
    surfaceContainer = AppColors.iosDarkSurfaceHigh,
    surfaceContainerHighest = AppColors.iosDarkSurfaceHighest,
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
