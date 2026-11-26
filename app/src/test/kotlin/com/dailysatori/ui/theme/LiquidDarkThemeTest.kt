package com.dailysatori.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiquidDarkThemeTest {
    @Test
    fun colorTokensExposeEditorialPaperAndObsidianPalettes() {
        val source = File("src/main/kotlin/com/dailysatori/ui/theme/Color.kt").readText()

        assertEquals(Color(0xFF255B7A), AppColors.primary)
        assertEquals(Color(0xFF2F7A5A), AppColors.success)
        assertEquals(Color(0xFFC94136), AppColors.error)
        assertEquals(Color(0xFFB85D3B), AppColors.secondary)
        assertTrue(source.contains("paperLightBackground = Color(0xFFF7F5F0)"))
        assertTrue(source.contains("paperLightSurface = Color(0xFFFFFDF9)"))
        assertTrue(source.contains("paperLightOnSurface = Color(0xFF1A1917)"))
        assertTrue(source.contains("paperLightOnSurfaceVariant = Color(0xFF6F6A60)"))
        assertTrue(source.contains("obsidianDarkBackground = Color(0xFF111110)"))
        assertTrue(source.contains("obsidianDarkSurface = Color(0xFF1A1917)"))
        assertTrue(source.contains("obsidianDarkOnSurface = Color(0xFFECE9E2)"))
        assertTrue(source.contains("obsidianDarkOnSurfaceVariant = Color(0xFF9A958B)"))
        assertFalse(source.contains("liquidBackground = Color(0xFF050816)"))
    }

    @Test
    fun themeFollowsSystemAndMapsLightAndDarkSchemes() {
        val source = File("src/main/kotlin/com/dailysatori/ui/theme/Theme.kt").readText()
        val lightScheme = source.substringAfter("private val LightColorScheme").substringBefore("private val DarkColorScheme")
        val darkScheme = source.substringAfter("private val DarkColorScheme").substringBefore("@Composable")

        assertTrue(source.contains("import androidx.compose.foundation.isSystemInDarkTheme"))
        assertTrue(source.contains("darkTheme: Boolean = isSystemInDarkTheme()"))
        assertTrue(source.contains("isAppearanceLightStatusBars = !darkTheme"))
        assertTrue(source.contains("isAppearanceLightNavigationBars = !darkTheme"))
        assertTrue(lightScheme.contains("background = AppColors.paperLightBackground"))
        assertTrue(lightScheme.contains("surface = AppColors.paperLightSurface"))
        assertTrue(lightScheme.contains("onSurface = AppColors.paperLightOnSurface"))
        assertTrue(lightScheme.contains("secondary = AppColors.paperLightSecondaryRole"))
        assertTrue(lightScheme.contains("tertiary = AppColors.paperLightTertiaryRole"))
        assertTrue(lightScheme.contains("onTertiary = AppColors.paperLightOnTertiaryRole"))
        assertTrue(lightScheme.contains("errorContainer = AppColors.paperLightErrorContainer"))
        assertTrue(lightScheme.contains("onErrorContainer = AppColors.paperLightOnErrorContainer"))
        assertTrue(darkScheme.contains("background = AppColors.obsidianDarkBackground"))
        assertTrue(darkScheme.contains("surface = AppColors.obsidianDarkSurface"))
        assertTrue(darkScheme.contains("onSurface = AppColors.obsidianDarkOnSurface"))
        assertTrue(darkScheme.contains("secondary = AppColors.obsidianDarkSecondaryRole"))
        assertTrue(darkScheme.contains("tertiary = AppColors.obsidianDarkTertiaryRole"))
        assertTrue(darkScheme.contains("onTertiary = AppColors.obsidianDarkOnTertiaryRole"))
        assertTrue(darkScheme.contains("errorContainer = AppColors.obsidianDarkErrorContainer"))
        assertTrue(darkScheme.contains("onErrorContainer = AppColors.obsidianDarkOnErrorContainer"))
    }

    @Test
    fun typographyUsesEditorialHeadlinesAndComfortableReadingScale() {
        val source = File("src/main/kotlin/com/dailysatori/ui/theme/Typography.kt").readText()

        assertEquals(17.sp, AppTypography.bodyLarge.fontSize)
        assertEquals(30.sp, AppTypography.bodyLarge.lineHeight)
        assertEquals((-0.2).sp, AppTypography.headlineLarge.letterSpacing)
        assertEquals(EditorialFontFamily, AppTypography.headlineLarge.fontFamily)
        assertEquals(ContentFontFamily, AppTypography.bodyLarge.fontFamily)
        assertTrue(source.contains("val ContentFontFamily = UiFontFamily"))
        assertTrue(source.contains("val EditorialFontFamily = FontFamily.Serif"))
        assertTrue(source.contains("bodyLarge = TextStyle(fontFamily = ContentFontFamily"))
        assertTrue(source.contains("headlineLarge = TextStyle(fontFamily = EditorialFontFamily"))
    }

    @Test
    fun sharedComponentsStayTokenBased() {
        val card = File("src/main/kotlin/com/dailysatori/ui/component/card/CustomCard.kt").readText()
        val dialog = File("src/main/kotlin/com/dailysatori/ui/component/dialog/ConfirmDialog.kt").readText()
        val search = File("src/main/kotlin/com/dailysatori/ui/component/input/SearchBar.kt").readText()
        val topBar = File("src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt").readText()

        assertTrue(card.contains("MaterialTheme.colorScheme.surface"))
        assertTrue(card.contains("MaterialTheme.colorScheme.outline"))
        assertTrue(dialog.contains("containerColor = MaterialTheme.colorScheme.surface"))
        assertTrue(dialog.contains(".editorial()"))
        assertTrue(search.contains("focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer"))
        assertTrue(topBar.contains("containerColor = MaterialTheme.colorScheme.background"))
    }
}
