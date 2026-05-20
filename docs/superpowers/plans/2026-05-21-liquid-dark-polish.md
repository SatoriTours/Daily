# Liquid Dark Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish the installed Liquid Dark UI so bottom tab icons stay centered, content borders are subtler, and Markdown typography feels unified across news, diary, reading, and AI.

**Architecture:** Make a focused theme/component pass instead of screen-by-screen visual tweaks. The bottom bar becomes icon-only inside the existing floating capsule, border brightness is reduced through existing Material color tokens/usages, and Markdown presets converge on one content scale.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Kotlin source-contract tests, Gradle Android build, ADB wireless install.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`: remove visible bottom tab labels from `NavigationBarItem` and keep accessible content descriptions.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/home/HomeIaTest.kt`: assert icon-only compact navigation and no selected label rendering.
- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/Color.kt`: lower the normal outline variant brightness so blue borders are not overused.
- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`: unify reading, summary, compact, and card presets around a consistent content scale.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`: tone down news card/skeleton borders.
- Modify `app/src/test/kotlin/com/dailysatori/ui/theme/LiquidDarkThemeTest.kt`: assert unified Markdown scale and lower outline variant.
- Modify `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`: assert subtle news borders and updated Markdown spacing contracts.

## Task 1: Icon-Only Bottom Navigation

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/home/HomeIaTest.kt`

- [ ] **Step 1: Update the failing source contract test**

In `HomeIaTest.bottomBarUsesCompactFloatingLiquidGlassStyle`, replace the label assertion with icon-only assertions:

```kotlin
assertFalse(source.contains("label = { Text(tab.label"))
assertTrue(source.contains("label = null"))
assertTrue(source.contains("alwaysShowLabel = false"))
assertTrue(source.contains("contentDescription = tab.label"))
```

Keep existing assertions for height, icon size, rounded capsule, safe-area padding, indicator color, inset consumption, and `alwaysShowLabel = false`.

- [ ] **Step 2: Run the failing home test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.home.HomeIaTest
```

Expected: FAIL because `HomeScreen.kt` still renders the selected label and uses `contentDescription = null`.

- [ ] **Step 3: Remove visible labels from `HomeScreen.kt`**

Change the `NavigationBarItem` block in `HomeScreen.kt` from:

```kotlin
Icon(
    if (selectedIndex == index) tab.selectedIcon else tab.unselectedIcon,
    contentDescription = null,
    modifier = Modifier.size(HomeBottomBarIconSize),
)
```

to:

```kotlin
Icon(
    if (selectedIndex == index) tab.selectedIcon else tab.unselectedIcon,
    contentDescription = tab.label,
    modifier = Modifier.size(HomeBottomBarIconSize),
)
```

Change:

```kotlin
label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
```

to:

```kotlin
label = null,
```

Keep `alwaysShowLabel = false`.

- [ ] **Step 4: Run the home test and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.home.HomeIaTest
./gradlew :app:compileDebugKotlin
```

Expected: both PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt app/src/test/kotlin/com/dailysatori/ui/feature/home/HomeIaTest.kt
git commit -m "fix: keep bottom navigation icons centered"
```

## Task 2: Unified Markdown Scale And Subtle News Borders

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/Color.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/theme/LiquidDarkThemeTest.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Update failing theme and unified news tests**

In `LiquidDarkThemeTest.colorTokensUseSapphireLiquidDarkPalette`, add:

```kotlin
assertTrue(source.contains("liquidOutlineHigh = Color(0x33475569)"))
```

In `LiquidDarkThemeTest.markdownScaleMatchesUnifiedAppTypography`, replace the current body scale assertions with:

```kotlin
assertTrue(source.contains("bodySize = 15"))
assertTrue(source.contains("bodyLine = 24"))
assertFalse(source.contains("bodySize = 16"))
assertFalse(source.contains("bodyLine = 27"))
assertTrue(source.contains("fun cardTypography(): MarkdownTypography = summaryTypography()"))
assertTrue(source.contains("fun cardPadding(): MarkdownPadding = summaryPadding()"))
assertTrue(source.contains("fun remoteArticleTypography(): MarkdownTypography = summaryTypography()"))
assertTrue(source.contains("fontFamily = UiFontFamily"))
```

In `UnifiedNewsBehaviorTest.unifiedNewsMarkdownListSpacingIsComfortableForReading`, update expectations to the unified scale:

```kotlin
assertTrue(typographyBody.contains("bodySize = 15"))
assertTrue(typographyBody.contains("bodyLine = 24"))
assertFalse(typographyBody.contains("bodyLine = 27"))
assertFalse(typographyBody.contains("list = TextStyle(\n            fontFamily = LatoFontFamily"))
assertTrue(paddingBody.contains("listItemBottom = 6.dp"))
assertTrue(paddingBody.contains("list = 8.dp"))
```

Add a new test to `UnifiedNewsBehaviorTest`:

```kotlin
@Test
fun unifiedNewsCardsUseSubtleLiquidBorders() {
    val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

    assertFalse(screen.contains("BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outlineVariant),"))
    assertTrue(screen.contains("BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline)"))
    assertTrue(screen.contains("MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)"))
}
```

- [ ] **Step 2: Run failing tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.LiquidDarkThemeTest --tests com.dailysatori.UnifiedNewsBehaviorTest
```

Expected: FAIL because Markdown and border source have not been updated yet.

- [ ] **Step 3: Reduce outline variant brightness in `Color.kt`**

Change:

```kotlin
val liquidOutlineHigh = Color(0x667DD3FC)
```

to:

```kotlin
val liquidOutlineHigh = Color(0x33475569)
```

This makes `outlineVariant` a subtle slate outline instead of a bright sapphire line.

- [ ] **Step 4: Unify Markdown scales in `MarkdownStyles.kt`**

Change `readingTypography`, `summaryTypography`, and `compactTypography` to all use the same body size and line height:

```kotlin
fun readingTypography(): MarkdownTypography = typographyScale(
    bodySize = 15,
    bodyLine = 24,
    h1 = 22,
    h2 = 19,
    h3 = 17,
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
    bodySize = 15,
    bodyLine = 24,
    h1 = 22,
    h2 = 19,
    h3 = 17,
    linkColor = MaterialTheme.colorScheme.primary,
)
```

Change aliases so cards and remote articles use the same summary preset:

```kotlin
fun cardTypography(): MarkdownTypography = summaryTypography()
fun cardPadding(): MarkdownPadding = summaryPadding()
fun remoteArticleTypography(): MarkdownTypography = summaryTypography()
fun remoteArticlePadding(): MarkdownPadding = summaryPadding()
```

Keep `readingPadding` and `summaryPadding` if tests require separate padding, but source contracts should now favor `summaryPadding` for cards and remote article content.

- [ ] **Step 5: Tone down unified news borders**

In `UnifiedNewsScreen.kt`, change the generating skeleton card border from:

```kotlin
border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outlineVariant),
```

to:

```kotlin
border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline),
```

Change the today unified news card border from:

```kotlin
border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
```

to:

```kotlin
border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
```

- [ ] **Step 6: Run tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.LiquidDarkThemeTest --tests com.dailysatori.UnifiedNewsBehaviorTest
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugKotlin
```

Expected: all PASS.

- [ ] **Step 7: Commit**

Run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/theme/Color.kt app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt app/src/test/kotlin/com/dailysatori/ui/theme/LiquidDarkThemeTest.kt app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt
git commit -m "fix: unify content typography and borders"
```

## Task 3: Final Verification And Device Install

**Files:**
- Verify all changed files.

- [ ] **Step 1: Run full unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Run required compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Connect wireless device**

Run:

```bash
adb connect 192.168.2.7:42577
```

Expected: connected or already connected.

- [ ] **Step 4: Install debug build**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

Expected: BUILD SUCCESSFUL and installed on the connected device.

- [ ] **Step 5: Launch app**

Run:

```bash
adb -s 192.168.2.7:42577 shell am start -n com.dailysatori/.MainActivity
```

Expected: activity starts.

- [ ] **Step 6: Final status**

Run:

```bash
git status --short
```

Expected: clean.
