# iOS Liquid Dark Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh Daily Satori into a premium iOS-inspired Liquid Dark app with unified typography, compact bottom tabs, consistent inputs/dialogs, and a refined Sapphire Ring launcher icon.

**Architecture:** Apply the redesign through the shared Compose theme and reusable UI components first, then update the specific bottom navigation, AI input bar, and launcher resources. Keep screen-specific edits minimal and only touch screens that currently bypass shared styling.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android adaptive icon XML/vector resources, Kotlin source-level tests, Gradle Android build.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/Color.kt`: define Liquid Dark palette tokens used by Material color schemes.
- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/Theme.kt`: make the app default to the dark Liquid palette and update system bar icon contrast.
- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/Typography.kt`: remove the visual split between news/editorial content and UI by moving all typography roles to the same sans-serif family and consistent scale.
- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`: align Markdown reading, summary, and compact scales with the unified app typography.
- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/Spacing.kt`: tune shared heights and icon sizes for compact iOS-style controls.
- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/Shape.kt`: align Material shapes with the premium rounded surface system.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`: replace the tall Material bottom navigation presentation with a compact floating glass capsule.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt`: slim the AI input bar and align it with shared input sizing.
- Modify `app/src/main/kotlin/com/dailysatori/ui/component/dialog/ConfirmDialog.kt`: standardize dialog shape, color, and text treatment.
- Modify `app/src/main/kotlin/com/dailysatori/ui/component/input/SearchBar.kt`: align search input sizing and dark glass treatment.
- Modify `app/src/main/kotlin/com/dailysatori/ui/component/card/CustomCard.kt`: update default card surface/border/radius for the dark system.
- Modify `app/src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt`: update top bar colors to blend with the dark system.
- Modify `app/src/main/res/drawable/ic_launcher_background.xml`: implement the Sapphire Ring dark glass background.
- Modify `app/src/main/res/drawable/ic_launcher_foreground.xml`: implement the metallic ring, inner shadow layers, sapphire focus, and center cap.
- Modify `app/src/main/res/drawable/ic_launcher_monochrome.xml`: keep a clean monochrome launcher shape aligned with the new ring mark.
- Add `app/src/test/kotlin/com/dailysatori/ui/theme/LiquidDarkThemeTest.kt`: source-level assertions for the theme, typography, Markdown, and shared sizing contracts.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/home/HomeIaTest.kt`: assert the home bottom bar uses compact floating glass constants.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`: assert the AI input bar remains IME-safe and exposes compact layout constants.
- Add `app/src/test/kotlin/com/dailysatori/ui/icon/LauncherIconSourceTest.kt`: source-level assertions for the Sapphire Ring icon structure.

## Task 1: Theme Palette, Typography, And Markdown Scale

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/Color.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/Theme.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/Typography.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/Spacing.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/Shape.kt`
- Add: `app/src/test/kotlin/com/dailysatori/ui/theme/LiquidDarkThemeTest.kt`

- [ ] **Step 1: Write the failing theme contract test**

Create `app/src/test/kotlin/com/dailysatori/ui/theme/LiquidDarkThemeTest.kt` with:

```kotlin
package com.dailysatori.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LiquidDarkThemeTest {
    @Test
    fun colorTokensUseSapphireLiquidDarkPalette() {
        val source = File("src/main/kotlin/com/dailysatori/ui/theme/Color.kt").readText()

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

        assertTrue(source.contains("darkTheme: Boolean = true"))
        assertTrue(source.contains("primary = AppColors.sapphire"))
        assertTrue(source.contains("background = AppColors.liquidBackground"))
        assertTrue(source.contains("surface = AppColors.liquidSurface"))
        assertTrue(source.contains("isAppearanceLightStatusBars = false"))
    }

    @Test
    fun typographyUsesOneSansSerifFamilyAcrossContentAndUi() {
        val source = File("src/main/kotlin/com/dailysatori/ui/theme/Typography.kt").readText()

        assertTrue(source.contains("val ContentFontFamily = UiFontFamily"))
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
```

- [ ] **Step 2: Run the failing theme contract test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.LiquidDarkThemeTest
```

Expected: FAIL because the Liquid Dark tokens and unified typography changes do not exist yet.

- [ ] **Step 3: Update `Color.kt` with Liquid Dark tokens**

Modify `app/src/main/kotlin/com/dailysatori/ui/theme/Color.kt` so `AppColors` contains these exact dark-system tokens while preserving semantic colors and `tagColors`:

```kotlin
object AppColors {
    val sapphire = Color(0xFF7DD3FC)
    val sapphireSoft = Color(0xFF38BDF8)
    val liquidBackground = Color(0xFF050816)
    val liquidSurface = Color(0xFF111827)
    val liquidSurfaceHigh = Color(0xFF1E293B)
    val liquidSurfaceHighest = Color(0xFF273449)
    val onLiquid = Color(0xFFF8FAFC)
    val onLiquidVariant = Color(0xFFCBD5E1)
    val onLiquidMuted = Color(0xFF94A3B8)
    val liquidOutline = Color(0x33475569)
    val liquidOutlineHigh = Color(0x667DD3FC)

    val primary = sapphire
    val primaryLight = sapphireSoft

    val background = liquidBackground
    val surface = liquidSurface
    val surfaceContainer = liquidSurfaceHigh
    val surfaceContainerHighest = liquidSurfaceHighest

    val onBackground = onLiquid
    val onSurface = onLiquid
    val onSurfaceVariant = onLiquidVariant

    val outline = liquidOutline
    val outlineVariant = liquidOutlineHigh

    val success = Color(0xFF4ADE80)
    val error = Color(0xFFF87171)
    val warning = Color(0xFFFBBF24)
    val info = sapphire

    val secondary = Color(0xFFA78BFA)
    val secondaryContainer = Color(0xFF1E1B4B)
    val onSecondaryContainer = Color(0xFFDCD6FE)
    val tertiaryContainer = Color(0xFF172554)

    val tagColors = listOf(
        Color(0xFF7DD3FC), Color(0xFFA78BFA), Color(0xFF60A5FA),
        Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFFB7185),
        Color(0xFF38BDF8), Color(0xFFC4B5FD), Color(0xFF22D3EE),
        Color(0xFFF472B6),
    )
}
```

- [ ] **Step 4: Update `Theme.kt` to default to the dark Liquid palette**

Modify `app/src/main/kotlin/com/dailysatori/ui/theme/Theme.kt` so `DarkColorScheme` uses `AppColors` tokens and `DailySatoriTheme` defaults to dark:

```kotlin
private val DarkColorScheme = darkColorScheme(
    primary = AppColors.sapphire,
    onPrimary = Color(0xFF03121D),
    secondary = AppColors.secondary,
    onSecondary = AppColors.onLiquid,
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
```

Keep the existing `MaterialTheme(...)` block after this snippet.

- [ ] **Step 5: Update `Typography.kt` to one sans-serif scale**

Modify `app/src/main/kotlin/com/dailysatori/ui/theme/Typography.kt` so the top section and `AppTypography` use this structure:

```kotlin
val UiFontFamily = FontFamily.SansSerif

val ContentFontFamily = UiFontFamily

val LatoFontFamily = UiFontFamily

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp),
    displayMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.3).sp),
    displaySmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp),
    headlineLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 29.sp, letterSpacing = (-0.1).sp),
    headlineSmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 25.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 27.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.sp),
)
```

Remove unused imports for font resources if the compiler reports them unused.

- [ ] **Step 6: Update `MarkdownStyles.kt` typography scales**

Modify `readingTypography`, `summaryTypography`, `compactTypography`, and `contentStyle` in `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`:

```kotlin
fun readingTypography(): MarkdownTypography = typographyScale(
    bodySize = 16,
    bodyLine = 27,
    h1 = 24,
    h2 = 21,
    h3 = 18,
    linkColor = MaterialTheme.colorScheme.primary,
)

@Composable
fun summaryTypography(): MarkdownTypography = typographyScale(
    bodySize = 15,
    bodyLine = 24,
    h1 = 22,
    h2 = 19,
    h3 = 17,
    linkColor = MaterialTheme.colorScheme.primary,
)

@Composable
fun compactTypography(): MarkdownTypography = typographyScale(
    bodySize = 14,
    bodyLine = 21,
    h1 = 18,
    h2 = 16,
    h3 = 15,
    linkColor = MaterialTheme.colorScheme.primary,
)

private fun contentStyle(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    fontStyle: FontStyle = FontStyle.Normal,
    color: Color = Color.Unspecified,
): TextStyle = TextStyle(
    fontFamily = UiFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontStyle = fontStyle,
    color = color,
)
```

- [ ] **Step 7: Update shared sizing and shapes**

Modify `Height` in `Spacing.kt`:

```kotlin
object Height {
    val button = 46.dp
    val buttonSmall = 34.dp
    val input = 46.dp
    val listItem = 54.dp
    val listItemSmall = 46.dp
    val appBar = 54.dp
    val navBar = 52.dp
    val chip = 30.dp
    val searchBar = 46.dp
}
```

Modify `Radius.l` in `Spacing.kt`:

```kotlin
val l = 22.dp
```

Keep `Shape.kt` `extraLarge = RoundedCornerShape(24.dp)` and change `large` only if visual review shows cards need a stronger radius.

- [ ] **Step 8: Run the theme contract test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.LiquidDarkThemeTest
```

Expected: PASS.

- [ ] **Step 9: Commit theme foundation**

Run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/theme app/src/test/kotlin/com/dailysatori/ui/theme/LiquidDarkThemeTest.kt
git commit -m "feat: add liquid dark theme foundation"
```

## Task 2: Compact Floating Bottom Tab Bar

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/home/HomeIaTest.kt`

- [ ] **Step 1: Add failing bottom bar source assertions**

Append this test to `HomeIaTest`:

```kotlin
@Test
fun bottomBarUsesCompactFloatingLiquidGlassStyle() {
    val source = File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()

    assertTrue(source.contains("private val HomeBottomBarHeight = Height.navBar"))
    assertTrue(source.contains("private val HomeBottomBarIconSize = IconSize.l"))
    assertTrue(source.contains("RoundedCornerShape(Radius.circular)"))
    assertTrue(source.contains("BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outlineVariant)"))
    assertTrue(source.contains("Modifier.navigationBarsPadding()"))
    assertTrue(source.contains("label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) }"))
    assertTrue(source.contains("indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)"))
}
```

- [ ] **Step 2: Run the failing home test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.home.HomeIaTest
```

Expected: FAIL because the compact floating bar constants and styling are not present.

- [ ] **Step 3: Add imports and constants in `HomeScreen.kt`**

Add these imports:

```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
```

Add constants below `tabs`:

```kotlin
private val HomeBottomBarHeight = Height.navBar
private val HomeBottomBarIconSize = IconSize.l
```

- [ ] **Step 4: Replace bottom bar content with floating capsule**

In `HomeScreen.kt`, replace the `NavigationBar(...) { ... }` block inside `bottomBar` with:

```kotlin
Surface(
    modifier = Modifier
        .padding(horizontal = Spacing.m, vertical = Spacing.s)
        .navigationBarsPadding(),
    shape = RoundedCornerShape(Radius.circular),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    contentColor = MaterialTheme.colorScheme.onSurface,
    tonalElevation = 0.dp,
    shadowElevation = 10.dp,
    border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outlineVariant),
) {
    NavigationBar(
        modifier = Modifier.height(HomeBottomBarHeight),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        tabs.forEachIndexed { index, tab ->
            NavigationBarItem(
                icon = {
                    Icon(
                        if (selectedIndex == index) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(HomeBottomBarIconSize),
                    )
                },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                selected = selectedIndex == index,
                onClick = { selectedIndex = index },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                ),
            )
        }
    }
}
```

- [ ] **Step 5: Run home tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.home.HomeIaTest
```

Expected: PASS.

- [ ] **Step 6: Commit bottom bar update**

Run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt app/src/test/kotlin/com/dailysatori/ui/feature/home/HomeIaTest.kt
git commit -m "feat: compact home bottom navigation"
```

## Task 3: Shared Components, Dialogs, Search, And AI Input

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/dialog/ConfirmDialog.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/input/SearchBar.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/CustomCard.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`

- [ ] **Step 1: Add failing AI input compactness assertions**

Append this test to `AiChatUiStateTest`:

```kotlin
@Test
fun chatInputUsesCompactLiquidGlassSizing() {
    val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt").readText()

    assertTrue(source.contains("private val ChatInputButtonSize = 34.dp"))
    assertTrue(source.contains("private val ChatInputMinHeight = Height.input"))
    assertTrue(source.contains("RoundedCornerShape(Radius.circular)"))
    assertTrue(source.contains("MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)"))
    assertTrue(source.contains("modifier = Modifier.size(ChatInputButtonSize)"))
    assertTrue(source.contains("minLines = 1"))
    assertTrue(source.contains("maxLines = 3"))
}
```

- [ ] **Step 2: Add failing shared component source assertions**

Add this test to `LiquidDarkThemeTest`:

```kotlin
@Test
fun sharedComponentsUseLiquidDarkSurfaces() {
    val card = File("src/main/kotlin/com/dailysatori/ui/component/card/CustomCard.kt").readText()
    val dialog = File("src/main/kotlin/com/dailysatori/ui/component/dialog/ConfirmDialog.kt").readText()
    val search = File("src/main/kotlin/com/dailysatori/ui/component/input/SearchBar.kt").readText()
    val topBar = File("src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt").readText()

    assertTrue(card.contains("shape = RoundedCornerShape(Radius.l)"))
    assertTrue(card.contains("MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)"))
    assertTrue(dialog.contains("shape = RoundedCornerShape(Radius.xl)"))
    assertTrue(dialog.contains("containerColor = MaterialTheme.colorScheme.surfaceContainer"))
    assertTrue(search.contains("height(Height.searchBar)"))
    assertTrue(search.contains("focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)"))
    assertTrue(topBar.contains("containerColor = MaterialTheme.colorScheme.background"))
}
```

- [ ] **Step 3: Run failing component tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.aichat.AiChatUiStateTest --tests com.dailysatori.ui.theme.LiquidDarkThemeTest
```

Expected: FAIL because component styling has not been updated.

- [ ] **Step 4: Slim `ChatInputBar.kt`**

Add imports:

```kotlin
import androidx.compose.foundation.layout.defaultMinSize
import com.dailysatori.ui.theme.Height
```

Add constants below `enum class ChatInputAction`:

```kotlin
private val ChatInputButtonSize = 34.dp
private val ChatInputMinHeight = Height.input
```

Change the input shape and outer padding:

```kotlin
val inputShape = RoundedCornerShape(Radius.circular)
```

```kotlin
.padding(horizontal = Spacing.m, vertical = Spacing.xs)
```

Change the input surface color and row padding:

```kotlin
color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
```

```kotlin
modifier = Modifier.padding(start = Spacing.s, end = Spacing.xs, top = Spacing.xxs, bottom = Spacing.xxs),
```

Add minimum height to the `TextField` modifier and reduce lines:

```kotlin
modifier = Modifier
    .weight(1f)
    .defaultMinSize(minHeight = ChatInputMinHeight)
    .onFocusChanged { isFocused = it.isFocused },
```

```kotlin
minLines = 1,
maxLines = 3,
```

Change the icon button modifier:

```kotlin
modifier = Modifier.size(ChatInputButtonSize),
```

- [ ] **Step 5: Update `ConfirmDialog.kt` dark styling**

Add imports:

```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.Radius
```

Add these parameters to `AlertDialog`:

```kotlin
shape = RoundedCornerShape(Radius.xl),
containerColor = MaterialTheme.colorScheme.surfaceContainer,
tonalElevation = 0.dp,
iconContentColor = MaterialTheme.colorScheme.primary,
titleContentColor = MaterialTheme.colorScheme.onSurface,
textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
```

If `tonalElevation` requires a `dp` import, add `import androidx.compose.ui.unit.dp`.

- [ ] **Step 6: Update `CustomCard.kt` dark card surface**

Change both clickable and non-clickable card calls to use:

```kotlin
shape = RoundedCornerShape(Radius.l),
colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)),
border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline),
elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
```

- [ ] **Step 7: Update `SearchBar.kt` input surface**

Change `TextFieldDefaults.colors` to:

```kotlin
colors = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
)
```

Add `import androidx.compose.ui.graphics.Color` if missing.

- [ ] **Step 8: Update `AppTopBar.kt` color blending**

Change `TopAppBarDefaults.topAppBarColors` to:

```kotlin
colors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.background,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
),
```

- [ ] **Step 9: Run component tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.aichat.AiChatUiStateTest --tests com.dailysatori.ui.theme.LiquidDarkThemeTest
```

Expected: PASS.

- [ ] **Step 10: Commit shared component refresh**

Run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt app/src/main/kotlin/com/dailysatori/ui/component/dialog/ConfirmDialog.kt app/src/main/kotlin/com/dailysatori/ui/component/input/SearchBar.kt app/src/main/kotlin/com/dailysatori/ui/component/card/CustomCard.kt app/src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt app/src/test/kotlin/com/dailysatori/ui/theme/LiquidDarkThemeTest.kt
git commit -m "feat: standardize liquid dark components"
```

## Task 4: Sapphire Ring Launcher Icon

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_background.xml`
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Modify: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Add: `app/src/test/kotlin/com/dailysatori/ui/icon/LauncherIconSourceTest.kt`

- [ ] **Step 1: Write failing launcher icon source test**

Create `app/src/test/kotlin/com/dailysatori/ui/icon/LauncherIconSourceTest.kt`:

```kotlin
package com.dailysatori.ui.icon

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LauncherIconSourceTest {
    @Test
    fun launcherIconUsesSapphireRingPaletteAndLayers() {
        val background = File("src/main/res/drawable/ic_launcher_background.xml").readText()
        val foreground = File("src/main/res/drawable/ic_launcher_foreground.xml").readText()

        assertTrue(background.contains("#030712"))
        assertTrue(background.contains("#0F172A"))
        assertTrue(background.contains("#1E293B"))
        assertTrue(foreground.contains("#7DD3FC"))
        assertTrue(foreground.contains("#E2E8F0"))
        assertTrue(foreground.contains("#050816"))
        assertTrue(foreground.contains("android:strokeLineCap=\"round\""))
        assertTrue(foreground.contains("android:strokeAlpha=\"0.95\""))
    }

    @Test
    fun monochromeIconKeepsRingShape() {
        val monochrome = File("src/main/res/drawable/ic_launcher_monochrome.xml").readText()

        assertTrue(monochrome.contains("M54,26"))
        assertTrue(monochrome.contains("android:strokeWidth=\"8\""))
        assertTrue(monochrome.contains("android:strokeLineCap=\"round\""))
    }
}
```

- [ ] **Step 2: Run failing launcher icon test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.icon.LauncherIconSourceTest
```

Expected: FAIL because the current launcher icon is teal and does not use Sapphire Ring layers.

- [ ] **Step 3: Replace `ic_launcher_background.xml`**

Use this XML:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#030712"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#0F172A"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#1E293B"
        android:fillAlpha="0.72"
        android:pathData="M0,72c18,-16 38,-24 60,-24s38,9 48,21v39h-108z" />
    <path
        android:fillColor="#7DD3FC"
        android:fillAlpha="0.18"
        android:pathData="M0,0h54c-10,16 -28,27 -54,33z" />
    <path
        android:fillColor="#3B82F6"
        android:fillAlpha="0.18"
        android:pathData="M108,62v46h-50c10,-22 27,-37 50,-46z" />
</vector>
```

- [ ] **Step 4: Replace `ic_launcher_foreground.xml`**

Use this XML layer structure:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="@android:color/transparent"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:pathData="M54,26a28,28 0,1 1,0 56a28,28 0,1 1,0 -56"
        android:strokeColor="#334155"
        android:strokeWidth="10"
        android:strokeAlpha="0.82"
        android:strokeLineCap="round" />
    <path
        android:pathData="M54,26a28,28 0,0 1,27 35"
        android:strokeColor="#E2E8F0"
        android:strokeWidth="10"
        android:strokeAlpha="0.95"
        android:strokeLineCap="round" />
    <path
        android:pathData="M54,26a28,28 0,0 1,12 3"
        android:strokeColor="#7DD3FC"
        android:strokeWidth="10"
        android:strokeAlpha="0.95"
        android:strokeLineCap="round" />
    <path
        android:fillColor="#050816"
        android:pathData="M54,38a16,16 0,1 1,0 32a16,16 0,1 1,0 -32" />
    <path
        android:fillColor="#0F172A"
        android:fillAlpha="0.78"
        android:pathData="M42,47a16,16 0,0 1,28 0c-5,-3 -10,-4 -16,-4s-9,1 -12,4z" />
    <path
        android:pathData="M54,22v26"
        android:strokeColor="#7DD3FC"
        android:strokeWidth="7"
        android:strokeAlpha="0.98"
        android:strokeLineCap="round" />
    <path
        android:pathData="M58,56l17,18"
        android:strokeColor="#CBD5E1"
        android:strokeWidth="7"
        android:strokeAlpha="0.96"
        android:strokeLineCap="round" />
    <path
        android:fillColor="#E2E8F0"
        android:pathData="M54,45a9,9 0,1 1,0 18a9,9 0,1 1,0 -18" />
    <path
        android:fillColor="#94A3B8"
        android:fillAlpha="0.65"
        android:pathData="M48,56a9,9 0,0 0,15 6a9,9 0,0 1,-15 -6z" />
</vector>
```

- [ ] **Step 5: Replace `ic_launcher_monochrome.xml`**

Use this XML:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="@android:color/transparent"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:pathData="M54,26a28,28 0,1 1,0 56a28,28 0,1 1,0 -56"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="8"
        android:strokeLineCap="round" />
    <path
        android:pathData="M54,22v26"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="7"
        android:strokeLineCap="round" />
    <path
        android:pathData="M58,56l17,18"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="7"
        android:strokeLineCap="round" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M54,45a9,9 0,1 1,0 18a9,9 0,1 1,0 -18" />
</vector>
```

- [ ] **Step 6: Run launcher icon test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.icon.LauncherIconSourceTest
```

Expected: PASS.

- [ ] **Step 7: Commit launcher icon update**

Run:

```bash
git add app/src/main/res/drawable/ic_launcher_background.xml app/src/main/res/drawable/ic_launcher_foreground.xml app/src/main/res/drawable/ic_launcher_monochrome.xml app/src/test/kotlin/com/dailysatori/ui/icon/LauncherIconSourceTest.kt
git commit -m "feat: redesign launcher icon"
```

## Task 5: Full Verification And Device Install

**Files:**
- Verify changed files from Tasks 1-4.

- [ ] **Step 1: Run focused unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.LiquidDarkThemeTest --tests com.dailysatori.ui.feature.home.HomeIaTest --tests com.dailysatori.ui.feature.aichat.AiChatUiStateTest --tests com.dailysatori.ui.icon.LauncherIconSourceTest
```

Expected: PASS.

- [ ] **Step 2: Run required Kotlin compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install debug build on connected device**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

Expected: BUILD SUCCESSFUL and app installed.

- [ ] **Step 4: Launch the app**

Run:

```bash
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: Android reports the activity start and Daily Satori opens.

- [ ] **Step 5: Perform visual smoke check**

Check these screens on device:

```text
1. 今日: page background is dark, cards are readable, article/news font is not oversized.
2. 日记: diary cards use the same typography scale and dark card surface.
3. 读书: reading cards and detail content are readable and not too small.
4. AI: input bar is slimmer, send button is compact, keyboard padding still works.
5. Settings or any delete confirmation: dialog uses dark surface and consistent typography.
6. Launcher/home screen: icon shows dark Sapphire Ring, metal ring, blue focus, and center cap clearly.
```

Expected: No screen has black text on dark background, no oversized bottom tab, and no visually bulky AI input.

- [ ] **Step 6: Check git status**

Run:

```bash
git status --short
```

Expected: no uncommitted implementation changes except intentional local artifacts such as `.superpowers/` if present and untracked.
