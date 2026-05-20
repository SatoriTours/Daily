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
        assertTrue(source.contains("liquidOutlineHigh = Color(0x33475569)"))
    }

    @Test
    fun themeDefaultsToLiquidDarkAndUsesDarkSystemBarIcons() {
        val source = File("src/main/kotlin/com/dailysatori/ui/theme/Theme.kt").readText()
        val darkScheme = source.substringAfter("private val DarkColorScheme").substringBefore("@Composable")

        assertTrue(source.contains("onPrimary = Color(0xFF03121D)"))
        assertTrue(source.contains("onSecondary = Color(0xFF140A2A)"))
        assertTrue(darkScheme.contains("onSecondary = Color(0xFF140A2A)"))
        assertFalse(darkScheme.contains("onSecondary = AppColors.onLiquid"))
        assertTrue(source.contains("darkTheme: Boolean = true"))
        assertTrue(source.contains("primary = AppColors.sapphire"))
        assertTrue(source.contains("background = AppColors.liquidBackground"))
        assertTrue(source.contains("surface = AppColors.liquidSurface"))
        assertTrue(source.contains("isAppearanceLightStatusBars = false"))
        assertTrue(source.contains("isAppearanceLightNavigationBars = false"))
    }

    @Test
    fun directDialogsAndSheetsUseLiquidDarkTreatment() {
        val dialogSources = listOf(
            File("src/main/kotlin/com/dailysatori/DailySatoriApp.kt").readText(),
            File("src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt").readText(),
            File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText(),
            File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText(),
            File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListScreen.kt").readText(),
            File("src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreScreen.kt").readText(),
            File("src/main/kotlin/com/dailysatori/ui/feature/aiconfig/AiConfigScreen.kt").readText(),
        )
        val sheetSources = listOf(
            File("src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt").readText(),
            File("src/main/kotlin/com/dailysatori/ui/feature/aichat/MemorySearchSheet.kt").readText(),
            File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailSheet.kt").readText(),
        )

        dialogSources.forEach { source ->
            assertTrue(source.contains("shape = RoundedCornerShape(Radius.xl)"))
            assertTrue(source.contains("containerColor = MaterialTheme.colorScheme.surfaceContainer"))
            assertTrue(source.contains("tonalElevation = 0.dp"))
            assertTrue(source.contains("titleContentColor = MaterialTheme.colorScheme.onSurface"))
            assertTrue(source.contains("textContentColor = MaterialTheme.colorScheme.onSurfaceVariant"))
        }
        sheetSources.forEach { source ->
            assertTrue(source.contains("containerColor = MaterialTheme.colorScheme.surfaceContainer"))
            assertTrue(source.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
            assertTrue(source.contains("shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl)"))
            assertTrue(source.contains("tonalElevation = 0.dp"))
        }
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

        assertTrue(source.contains("bodySize = 15"))
        assertTrue(source.contains("bodyLine = 24"))
        assertFalse(source.contains("bodySize = 16"))
        assertFalse(source.contains("bodyLine = 27"))
        assertTrue(source.contains("fun cardTypography(): MarkdownTypography = summaryTypography()"))
        assertTrue(source.contains("fun cardPadding(): MarkdownPadding = summaryPadding()"))
        assertTrue(source.contains("fun remoteArticleTypography(): MarkdownTypography = summaryTypography()"))
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

    @Test
    fun sharedComponentsUseLiquidDarkSurfaces() {
        val card = File("src/main/kotlin/com/dailysatori/ui/component/card/CustomCard.kt").readText()
        val dialog = File("src/main/kotlin/com/dailysatori/ui/component/dialog/ConfirmDialog.kt").readText()
        val search = File("src/main/kotlin/com/dailysatori/ui/component/input/SearchBar.kt").readText()
        val topBar = File("src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt").readText()

        assertTrue(card.contains("shape = RoundedCornerShape(Radius.l)"))
        assertTrue(card.contains("MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)"))
        assertTrue(card.contains("BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline)"))
        assertTrue(card.contains("CardDefaults.cardElevation(defaultElevation = 0.dp)"))
        assertTrue(dialog.contains("shape = RoundedCornerShape(Radius.xl)"))
        assertTrue(dialog.contains("containerColor = MaterialTheme.colorScheme.surfaceContainer"))
        assertTrue(dialog.contains("tonalElevation = 0.dp"))
        assertTrue(dialog.contains("iconContentColor = MaterialTheme.colorScheme.primary"))
        assertTrue(dialog.contains("titleContentColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(dialog.contains("textContentColor = MaterialTheme.colorScheme.onSurfaceVariant"))
        assertTrue(search.contains("height(Height.searchBar)"))
        assertTrue(search.contains("focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)"))
        assertTrue(search.contains("unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)"))
        assertTrue(search.contains("disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)"))
        assertTrue(search.contains("focusedIndicatorColor = Color.Transparent"))
        assertTrue(search.contains("unfocusedIndicatorColor = Color.Transparent"))
        assertTrue(search.contains("disabledIndicatorColor = Color.Transparent"))
        assertTrue(search.contains("focusedTextColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(search.contains("unfocusedTextColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(topBar.contains("containerColor = MaterialTheme.colorScheme.background"))
        assertTrue(topBar.contains("scrolledContainerColor = MaterialTheme.colorScheme.background"))
    }
}
