package com.dailysatori.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiaryThemeTest {
    @Test
    fun bothThemesKeepBodyLabelsButtonsAndQuotesReadable() {
        listOf(lightColorScheme(), darkColorScheme()).forEach { base ->
            val scheme = diaryColorScheme(base)
            listOf(
                scheme.onBackground to scheme.background,
                scheme.onSurface to scheme.surface,
                scheme.onSurfaceVariant to scheme.surface,
                scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.primary to scheme.background,
                scheme.tertiary to scheme.surface,
                scheme.onTertiaryContainer to scheme.tertiaryContainer,
            ).forEach { (text, background) ->
                assertTrue(contrast(text, background) >= 4.5, "Insufficient contrast: $text on $background")
            }
            assertEquals(base.error, scheme.error)
            assertEquals(base.onErrorContainer, scheme.onErrorContainer)
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
