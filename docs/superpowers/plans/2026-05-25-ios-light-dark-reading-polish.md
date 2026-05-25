# iOS Light/Dark Reading Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh Daily Satori with system-following iOS-style light/dark themes and more comfortable long-form reading pages.

**Architecture:** Make token-centered changes first: colors, theme selection, typography, Markdown rhythm, then small detail-page spacing adjustments. Existing feature screens continue using `MaterialTheme` and shared `MarkdownStyles` so the app changes globally without navigation rewrites.

**Tech Stack:** Kotlin, Android Jetpack Compose Material 3, Compose Markdown renderer, JVM unit tests with `kotlin.test`, Gradle Android build.

---

## Commit Policy

The repository instructions say commits only happen when explicitly requested by the user. This plan uses verification checkpoints instead of commit steps. If the user later asks for commits, commit after each completed task with the task-specific files only.

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/Color.kt`: replace the current liquid-dark-only app token set with iOS light/dark token constants while preserving existing public names used across the app.
- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/Theme.kt`: map full Material 3 light/dark color schemes and default `DailySatoriTheme` to `isSystemInDarkTheme()`.
- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/Typography.kt`: tune global typography toward Apple-style hierarchy and make `bodyLarge` the comfortable reading base.
- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`: increase reading padding and ensure reading typography uses `ContentFontFamily` and comfortable line height.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt`: increase detail content horizontal padding through theme tokens.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`: soften remote article Markdown surface and increase detail content padding.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteDigestDetailScreen.kt`: increase digest page reading margins and keep Markdown on the reading preset.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsDetailScreen.kt`: increase crayfish news detail reading margins.
- Modify `app/src/test/kotlin/com/dailysatori/ui/theme/LiquidDarkThemeTest.kt`: update outdated liquid-dark assertions into adaptive iOS light/dark theme assertions.
- Create `app/src/test/kotlin/com/dailysatori/ui/theme/ReadingComfortThemeTest.kt`: cover reading typography, Markdown padding, and detail page use of comfortable reading margins.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailLayoutTest.kt`: update reader-card surface expectation if needed.
- Modify `docs/04-style-guide.md`: document adaptive light/dark mode and the updated reading typography guidance.

## Task 1: Lock Theme Requirements With Tests

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/theme/LiquidDarkThemeTest.kt`
- Create: `app/src/test/kotlin/com/dailysatori/ui/theme/ReadingComfortThemeTest.kt`

- [ ] **Step 1: Replace outdated liquid-dark theme tests with adaptive iOS theme tests**

Replace `app/src/test/kotlin/com/dailysatori/ui/theme/LiquidDarkThemeTest.kt` with:

```kotlin
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
        assertTrue(darkScheme.contains("background = AppColors.iosDarkBackground"))
        assertTrue(darkScheme.contains("surface = AppColors.iosDarkSurface"))
        assertTrue(darkScheme.contains("onSurface = AppColors.iosDarkOnSurface"))
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
```

- [ ] **Step 2: Add reading comfort tests**

Create `app/src/test/kotlin/com/dailysatori/ui/theme/ReadingComfortThemeTest.kt`:

```kotlin
package com.dailysatori.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadingComfortThemeTest {
    @Test
    fun markdownReadingPresetUsesComfortableContentRhythm() {
        val source = File("src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt").readText()

        assertTrue(source.contains("block = 14.dp"))
        assertTrue(source.contains("list = 10.dp"))
        assertTrue(source.contains("listItemBottom = 8.dp"))
        assertTrue(source.contains("indentList = 24.dp"))
        assertTrue(source.contains("codeBlock = PaddingValues(14.dp)"))
        assertTrue(source.contains("private fun readingTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = ContentFontFamily)"))
        assertTrue(source.contains("private fun headingStyle(style: TextStyle): TextStyle = style.copy(fontFamily = ContentFontFamily)"))
        assertFalse(source.contains("private fun readingTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = UiFontFamily)"))
    }

    @Test
    fun detailScreensUseComfortableReadingMargins() {
        val article = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val remoteArticle = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()
        val remoteDigest = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteDigestDetailScreen.kt").readText()
        val crayfish = File("src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsDetailScreen.kt").readText()

        assertTrue(article.contains("horizontal = Spacing.l, vertical = Spacing.s"))
        assertTrue(remoteArticle.contains("horizontal = Spacing.l, vertical = Spacing.s"))
        assertTrue(remoteDigest.contains("contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m)"))
        assertTrue(crayfish.contains("contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m)"))
    }
}
```

- [ ] **Step 3: Run tests and confirm failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.theme.LiquidDarkThemeTest' --tests 'com.dailysatori.ui.theme.ReadingComfortThemeTest'
```

Expected: FAIL because the production theme still uses the old liquid-dark palette, `DailySatoriTheme` defaults to `true`, `bodyLarge` is still `16.sp / 26.sp`, and reading padding still uses the old values.

## Task 2: Implement Adaptive iOS Color Schemes

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/Color.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/Theme.kt`

- [ ] **Step 1: Replace app color tokens**

Replace `app/src/main/kotlin/com/dailysatori/ui/theme/Color.kt` with:

```kotlin
package com.dailysatori.ui.theme

import androidx.compose.ui.graphics.Color

object AppColors {
    val iosBlue = Color(0xFF007AFF)
    val iosBlueDark = Color(0xFF0A84FF)
    val iosGreen = Color(0xFF34C759)
    val iosRed = Color(0xFFFF3B30)
    val iosOrange = Color(0xFFFF9500)
    val iosPurple = Color(0xFFAF52DE)

    val iosLightBackground = Color(0xFFF5F5F7)
    val iosLightSurface = Color(0xFFFFFFFF)
    val iosLightSurfaceHigh = Color(0xFFEFEFF4)
    val iosLightSurfaceHighest = Color(0xFFE5E5EA)
    val iosLightOnSurface = Color(0xFF1D1D1F)
    val iosLightOnSurfaceVariant = Color(0xFF6E6E73)
    val iosLightOutline = Color(0x1F3C3C43)
    val iosLightOutlineHigh = Color(0x333C3C43)

    val iosDarkBackground = Color(0xFF000000)
    val iosDarkSurface = Color(0xFF1C1C1E)
    val iosDarkSurfaceHigh = Color(0xFF2C2C2E)
    val iosDarkSurfaceHighest = Color(0xFF3A3A3C)
    val iosDarkOnSurface = Color(0xFFF5F5F7)
    val iosDarkOnSurfaceVariant = Color(0xFFA1A1A6)
    val iosDarkOutline = Color(0x33FFFFFF)
    val iosDarkOutlineHigh = Color(0x4DFFFFFF)

    val primary = iosBlue
    val primaryLight = iosBlueDark
    val background = iosDarkBackground
    val surface = iosDarkSurface
    val surfaceContainer = iosDarkSurfaceHigh
    val surfaceContainerHighest = iosDarkSurfaceHighest
    val onBackground = iosDarkOnSurface
    val onSurface = iosDarkOnSurface
    val onSurfaceVariant = iosDarkOnSurfaceVariant
    val outline = iosDarkOutline
    val outlineVariant = iosDarkOutlineHigh

    val success = iosGreen
    val error = iosRed
    val warning = iosOrange
    val info = iosBlue

    val secondary = iosPurple
    val secondaryContainer = Color(0xFFE9D7F5)
    val onSecondaryContainer = Color(0xFF351047)
    val tertiaryContainer = Color(0xFFD8ECFF)

    val tagColors = listOf(
        Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFF5856D6),
        Color(0xFF34C759), Color(0xFFFF9500), Color(0xFFFF2D55),
        Color(0xFF5AC8FA), Color(0xFFBF5AF2), Color(0xFF64D2FF),
        Color(0xFFFF9F0A),
    )
}
```

- [ ] **Step 2: Map Material schemes and system mode**

Replace `app/src/main/kotlin/com/dailysatori/ui/theme/Theme.kt` with:

```kotlin
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
    primary = AppColors.iosBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8ECFF),
    onPrimaryContainer = Color(0xFF003A66),
    secondary = AppColors.iosPurple,
    onSecondary = Color.White,
    secondaryContainer = AppColors.secondaryContainer,
    onSecondaryContainer = AppColors.onSecondaryContainer,
    tertiaryContainer = AppColors.tertiaryContainer,
    background = AppColors.iosLightBackground,
    onBackground = AppColors.iosLightOnSurface,
    surface = AppColors.iosLightSurface,
    onSurface = AppColors.iosLightOnSurface,
    surfaceVariant = AppColors.iosLightSurfaceHigh,
    onSurfaceVariant = AppColors.iosLightOnSurfaceVariant,
    outline = AppColors.iosLightOutline,
    outlineVariant = AppColors.iosLightOutlineHigh,
    error = AppColors.iosRed,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = AppColors.iosLightSurfaceHigh,
    surfaceContainerHighest = AppColors.iosLightSurfaceHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.iosBlueDark,
    onPrimary = Color(0xFF001F33),
    primaryContainer = Color(0xFF073A5C),
    onPrimaryContainer = Color(0xFFD8ECFF),
    secondary = Color(0xFFBF5AF2),
    onSecondary = Color(0xFF250033),
    secondaryContainer = Color(0xFF351047),
    onSecondaryContainer = Color(0xFFF4D9FF),
    tertiaryContainer = Color(0xFF12324A),
    background = AppColors.iosDarkBackground,
    onBackground = AppColors.iosDarkOnSurface,
    surface = AppColors.iosDarkSurface,
    onSurface = AppColors.iosDarkOnSurface,
    surfaceVariant = AppColors.iosDarkSurfaceHigh,
    onSurfaceVariant = AppColors.iosDarkOnSurfaceVariant,
    outline = AppColors.iosDarkOutline,
    outlineVariant = AppColors.iosDarkOutlineHigh,
    error = Color(0xFFFF453A),
    surfaceContainerLow = Color(0xFF111113),
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
```

- [ ] **Step 3: Run theme tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.theme.LiquidDarkThemeTest'
```

Expected: PASS for color and theme-selection assertions except typography if Task 3 is not implemented yet. If the Gradle test runner executes the whole class, typography assertions may still fail until Task 3.

## Task 3: Tune Global Typography And Markdown Reading Rhythm

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/Typography.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`

- [ ] **Step 1: Update global typography**

Replace `app/src/main/kotlin/com/dailysatori/ui/theme/Typography.kt` with:

```kotlin
package com.dailysatori.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val UiFontFamily = FontFamily.SansSerif

val ContentFontFamily = UiFontFamily

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 37.sp, letterSpacing = (-0.4).sp),
    displaySmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 33.sp, letterSpacing = (-0.3).sp),
    headlineLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = (-0.1).sp),
    headlineSmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontFamily = ContentFontFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.sp),
)
```

- [ ] **Step 2: Update Markdown reading styles**

In `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`, make these exact changes:

```kotlin
fun readingPadding(): MarkdownPadding = markdownPadding(
    block = 14.dp,
    list = 10.dp,
    listItemBottom = 8.dp,
    indentList = 24.dp,
    codeBlock = PaddingValues(14.dp),
    blockQuote = PaddingValues(14.dp),
    blockQuoteText = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
    blockQuoteBar = PaddingValues.Absolute(3.dp, 0.dp, 10.dp, 0.dp),
)
```

Also replace the helper functions at the bottom with:

```kotlin
@Composable
private fun cardTextStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = UiFontFamily)

@Composable
private fun bookTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = ContentFontFamily)

@Composable
private fun readingTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = ContentFontFamily)
```

And replace:

```kotlin
private fun headingStyle(style: TextStyle): TextStyle = style.copy(fontFamily = UiFontFamily)
```

with:

```kotlin
private fun headingStyle(style: TextStyle): TextStyle = style.copy(fontFamily = ContentFontFamily)
```

- [ ] **Step 3: Run theme rhythm tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.theme.LiquidDarkThemeTest' --tests 'com.dailysatori.ui.theme.ReadingComfortThemeTest' --tests 'com.dailysatori.ui.theme.MainContentRhythmTest'
```

Expected: PASS for `LiquidDarkThemeTest` and `ReadingComfortThemeTest`. If `MainContentRhythmTest` fails because it expects the old Markdown helper strings, update only those expectations to `ContentFontFamily` where they refer to reading/book styles.

## Task 4: Apply Comfortable Margins To Detail Pages

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteDigestDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsDetailScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailLayoutTest.kt`

- [ ] **Step 1: Increase article detail content margins**

In `ArticleDetailScreen.kt`, replace:

```kotlin
Box(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
```

with:

```kotlin
Box(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
```

- [ ] **Step 2: Increase remote article content margins and soften surface**

In `RemoteArticleDetailScreen.kt`, replace the LazyColumn item wrapper:

```kotlin
Box(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
```

with:

```kotlin
Box(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
```

In `RemoteArticleMarkdownContent`, replace:

```kotlin
color = MaterialTheme.colorScheme.surfaceContainerLow,
```

with:

```kotlin
color = MaterialTheme.colorScheme.surface,
```

And replace:

```kotlin
Box(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
```

with:

```kotlin
Box(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m)) {
```

- [ ] **Step 3: Increase digest and crayfish detail margins**

In `RemoteDigestDetailScreen.kt`, replace:

```kotlin
contentPadding = PaddingValues(Spacing.m),
```

with:

```kotlin
contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m),
```

In `CrayfishNewsDetailScreen.kt`, replace:

```kotlin
contentPadding = PaddingValues(Spacing.m),
```

with:

```kotlin
contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m),
```

- [ ] **Step 4: Update remote article layout surface test**

In `RemoteArticleDetailLayoutTest.kt`, replace:

```kotlin
assertTrue(screen.contains("MaterialTheme.colorScheme.surfaceContainerLow"))
```

with:

```kotlin
assertTrue(screen.contains("MaterialTheme.colorScheme.surface"))
```

- [ ] **Step 5: Run detail page tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.theme.ReadingComfortThemeTest' --tests 'com.dailysatori.ui.feature.remotenews.RemoteArticleDetailLayoutTest' --tests 'com.dailysatori.ui.feature.article.ArticleDetailContentTest'
```

Expected: PASS. These tests cover comfortable margins, remote article layout order, and unchanged article content fallback behavior.

## Task 5: Update Style Guide

**Files:**
- Modify: `docs/04-style-guide.md`

- [ ] **Step 1: Update theme guidance**

In `docs/04-style-guide.md`, replace the color examples under `AppColors 扩展色` with:

```markdown
// AppColors 扩展色（位于 Color.kt）
AppColors.primary          // iOS system blue, light-mode default
AppColors.primaryLight     // iOS system blue variant for dark surfaces
AppColors.success          // iOS system green
AppColors.error            // iOS system red
AppColors.warning          // iOS system orange
AppColors.info             // iOS system blue
```

- [ ] **Step 2: Update typography guidance**

In `docs/04-style-guide.md`, update the typography section so the body guidance reads:

```markdown
- `ContentFontFamily`：系统内容字体，用于长文阅读、Markdown 正文、文章/新闻详情、AI 摘要、日记预览等内容型区域。
- `UiFontFamily`：系统 Sans Serif/Roboto，用于导航、按钮、输入框、设置项、标签、时间、来源、状态等界面型文本。
```

And update the common hierarchy examples to:

```markdown
MaterialTheme.typography.headlineLarge // 26sp / 34sp, 内容页大标题
MaterialTheme.typography.headlineSmall // 20sp / 28sp, 内容型区块标题
MaterialTheme.typography.titleMedium   // 17sp / 24sp, UI 标题和 TopBar
MaterialTheme.typography.titleSmall    // 15sp / 21sp, 卡片标题/设置项标题
MaterialTheme.typography.bodyLarge     // 17sp / 30sp, 长文阅读正文
MaterialTheme.typography.bodyMedium    // 15sp / 24sp, 普通 UI 正文
MaterialTheme.typography.bodySmall     // 13sp / 19sp, 元信息/说明
MaterialTheme.typography.labelMedium   // 12sp / 17sp, 标签/Badge
```

- [ ] **Step 3: Run documentation-sensitive tests if present**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.theme.PolishTypographyUsageTest'
```

Expected: PASS. This verifies selected polish-sensitive screens continue using theme typography instead of hardcoded `sp` values.

## Task 6: Full Verification And Device Check

**Files:**
- No code changes unless verification reveals compile errors.

- [ ] **Step 1: Run focused theme and layout tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.theme.LiquidDarkThemeTest' --tests 'com.dailysatori.ui.theme.ReadingComfortThemeTest' --tests 'com.dailysatori.ui.theme.MainContentRhythmTest' --tests 'com.dailysatori.ui.feature.remotenews.RemoteArticleDetailLayoutTest'
```

Expected: PASS.

- [ ] **Step 2: Run project-required compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run project-required debug build**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install to connected device when available**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

Expected when a device is connected: BUILD SUCCESSFUL and the app is installed. If Gradle reports no connected devices, record that device verification was blocked by environment availability.

- [ ] **Step 5: Launch the app when device install succeeds**

Run:

```bash
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: Android starts `com.dailysatori/.MainActivity` without an adb error.

- [ ] **Step 6: Manual visual smoke check**

Inspect these screens in both light and dark modes if the device allows changing system theme:

```text
Article detail: cover collapse still works, tabs still switch, Markdown has comfortable line height.
Remote article detail: header order remains cover -> tabs -> title/meta -> content, reader surface is subtle.
Remote digest detail: summary and sections have larger margins and readable spacing.
Crayfish news detail: Markdown content no longer feels cramped.
Article/news lists: cards remain tappable and colors adapt in both modes.
Settings: rows, dialogs, and sheets remain readable in both modes.
```

Expected: no unreadable low-contrast text, no obvious clipped titles, no horizontal scroll, and no broken navigation.

## Self-Review Notes

- Spec coverage: Task 2 covers adaptive light/dark theme and system selection. Task 3 covers typography and Markdown reading rhythm. Task 4 covers article, remote article, remote digest, and crayfish detail pages. Task 5 covers style guide updates. Task 6 covers compile, build, install, launch, and manual inspection.
- Placeholder scan: this plan contains concrete file paths, exact replacement snippets, exact commands, and expected outcomes.
- Type consistency: all referenced files and symbols already exist except the new `ReadingComfortThemeTest`; new color tokens are defined before `Theme.kt` references them.
