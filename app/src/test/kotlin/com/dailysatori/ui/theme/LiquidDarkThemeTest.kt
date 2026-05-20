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
    fun colorTokensUseSapphireLiquidDarkPalette() {
        val source = File("src/main/kotlin/com/dailysatori/ui/theme/Color.kt").readText()

        assertEquals(Color(0xFF7DD3FC), AppColors.sapphire)
        assertEquals(AppColors.liquidBackground, AppColors.background)
        assertTrue(source.contains("liquidBackground = Color(0xFF050816)"))
        assertTrue(source.contains("liquidSurface = Color(0xFF111827)"))
        assertTrue(source.contains("liquidSurfaceHigh = Color(0xFF1E293B)"))
        assertTrue(source.contains("sapphire = Color(0xFF7DD3FC)"))
        assertTrue(source.contains("onLiquid = Color(0xFFF8FAFC)"))
        assertTrue(source.contains("onLiquidVariant = Color(0xFFCBD5E1)"))
    }

    @Test
    fun themeDefaultsToLiquidDarkAndUsesDarkStatusBarIcons() {
        val source = File("src/main/kotlin/com/dailysatori/ui/theme/Theme.kt").readText()

        assertTrue(source.contains("onPrimary = Color(0xFF03121D)"))
        assertTrue(source.contains("onSecondary = Color(0xFF140A2A)"))
        assertTrue(source.contains("darkTheme: Boolean = true"))
        assertTrue(source.contains("primary = AppColors.sapphire"))
        assertTrue(source.contains("background = AppColors.liquidBackground"))
        assertTrue(source.contains("surface = AppColors.liquidSurface"))
        assertTrue(source.contains("isAppearanceLightStatusBars = false"))
    }

    @Test
    fun typographyUsesOneSansSerifFamilyAcrossContentAndUi() {
        val source = File("src/main/kotlin/com/dailysatori/ui/theme/Typography.kt").readText()

        assertEquals(UiFontFamily, AppTypography.bodyLarge.fontFamily)
        assertEquals(17.sp, AppTypography.bodyLarge.fontSize)
        assertTrue(source.contains("val ContentFontFamily = UiFontFamily"))
        assertFalse(source.contains("val LatoFontFamily"))
        assertTrue(source.contains("displayLarge = TextStyle(fontFamily = UiFontFamily"))
        assertTrue(source.contains("headlineLarge = TextStyle(fontFamily = UiFontFamily"))
        assertTrue(source.contains("bodyLarge = TextStyle(fontFamily = UiFontFamily"))
        assertTrue(source.contains("fontSize = 17.sp, lineHeight = 27.sp"))
    }

    @Test
    fun markdownScaleMatchesUnifiedAppTypography() {
        val source = File("src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt").readText()

        assertTrue(source.contains("bodySize = 16"))
        assertTrue(source.contains("bodyLine = 27"))
        assertTrue(source.contains("bodySize = 15"))
        assertTrue(source.contains("bodyLine = 24"))
        assertTrue(source.contains("fontFamily = UiFontFamily"))
    }

    @Test
    fun sharedSizingUsesCompactPremiumControls() {
        val spacing = File("src/main/kotlin/com/dailysatori/ui/theme/Spacing.kt").readText()
        val shape = File("src/main/kotlin/com/dailysatori/ui/theme/Shape.kt").readText()

        assertTrue(spacing.contains("val input = 46.dp"))
        assertTrue(spacing.contains("val navBar = 52.dp"))
        assertTrue(spacing.contains("val searchBar = 46.dp"))
        assertTrue(spacing.contains("val l = 22.dp"))
        assertTrue(shape.contains("extraLarge = RoundedCornerShape(24.dp)"))
    }
}
