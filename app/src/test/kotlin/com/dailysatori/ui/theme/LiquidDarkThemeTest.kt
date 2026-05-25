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
    fun colorTokensExposeIosLightAndDarkPalettes() {
        val source = File("src/main/kotlin/com/dailysatori/ui/theme/Color.kt").readText()

        assertEquals(Color(0xFF007AFF), AppColors.primary)
        assertEquals(Color(0xFF34C759), AppColors.success)
        assertEquals(Color(0xFFFF3B30), AppColors.error)
        assertTrue(source.contains("iosLightBackground = Color(0xFFF5F5F7)"))
        assertTrue(source.contains("iosLightSurface = Color(0xFFFFFFFF)"))
        assertTrue(source.contains("iosLightOnSurface = Color(0xFF1D1D1F)"))
        assertTrue(source.contains("iosLightOnSurfaceVariant = Color(0xFF6E6E73)"))
        assertTrue(source.contains("iosDarkBackground = Color(0xFF000000)"))
        assertTrue(source.contains("iosDarkSurface = Color(0xFF1C1C1E)"))
        assertTrue(source.contains("iosDarkOnSurface = Color(0xFFF5F5F7)"))
        assertTrue(source.contains("iosDarkOnSurfaceVariant = Color(0xFFA1A1A6)"))
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
        assertTrue(lightScheme.contains("background = AppColors.iosLightBackground"))
        assertTrue(lightScheme.contains("surface = AppColors.iosLightSurface"))
        assertTrue(lightScheme.contains("onSurface = AppColors.iosLightOnSurface"))
        assertTrue(lightScheme.contains("tertiary = AppColors.iosLightTertiaryRole"))
        assertTrue(lightScheme.contains("onTertiary = AppColors.iosLightOnTertiaryRole"))
        assertTrue(lightScheme.contains("errorContainer = AppColors.iosLightErrorContainer"))
        assertTrue(lightScheme.contains("onErrorContainer = AppColors.iosLightOnErrorContainer"))
        assertTrue(darkScheme.contains("background = AppColors.iosDarkBackground"))
        assertTrue(darkScheme.contains("surface = AppColors.iosDarkSurface"))
        assertTrue(darkScheme.contains("onSurface = AppColors.iosDarkOnSurface"))
        assertTrue(darkScheme.contains("tertiary = AppColors.iosDarkTertiaryRole"))
        assertTrue(darkScheme.contains("onTertiary = AppColors.iosDarkOnTertiaryRole"))
        assertTrue(darkScheme.contains("errorContainer = AppColors.iosDarkErrorContainer"))
        assertTrue(darkScheme.contains("onErrorContainer = AppColors.iosDarkOnErrorContainer"))
    }

    @Test
    fun typographyUsesComfortableReadingScale() {
        val source = File("src/main/kotlin/com/dailysatori/ui/theme/Typography.kt").readText()

        assertEquals(17.sp, AppTypography.bodyLarge.fontSize)
        assertEquals(30.sp, AppTypography.bodyLarge.lineHeight)
        assertEquals((-0.2).sp, AppTypography.headlineLarge.letterSpacing)
        assertTrue(source.contains("val ContentFontFamily = UiFontFamily"))
        assertTrue(source.contains("bodyLarge = TextStyle(fontFamily = ContentFontFamily"))
        assertTrue(source.contains("headlineLarge = TextStyle(fontFamily = UiFontFamily"))
    }

    @Test
    fun sharedComponentsStayTokenBased() {
        val card = File("src/main/kotlin/com/dailysatori/ui/component/card/CustomCard.kt").readText()
        val dialog = File("src/main/kotlin/com/dailysatori/ui/component/dialog/ConfirmDialog.kt").readText()
        val search = File("src/main/kotlin/com/dailysatori/ui/component/input/SearchBar.kt").readText()
        val topBar = File("src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt").readText()

        assertTrue(card.contains("MaterialTheme.colorScheme.surfaceContainer"))
        assertTrue(card.contains("MaterialTheme.colorScheme.outline"))
        assertTrue(dialog.contains("containerColor = MaterialTheme.colorScheme.surfaceContainer"))
        assertTrue(search.contains("focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer"))
        assertTrue(topBar.contains("containerColor = MaterialTheme.colorScheme.background"))
    }
}
