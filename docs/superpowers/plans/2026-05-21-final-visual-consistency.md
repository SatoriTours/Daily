# Final Visual Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the visual-system convergence so the AI input is vertically centered and news, diary, reading, and AI content use one coherent typography/card rhythm.

**Architecture:** Fix the root causes in shared style surfaces first: typography tokens, Markdown presets, input layout, and main content card defaults. Then align the four primary tab content components to those shared contracts and guard against regressions with source-contract tests.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Kotlin unit/source-contract tests, Gradle Android build, ADB wireless install.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/Typography.kt`: normalize core typography tokens around `15/24` standard body and `16/26` long-form body.
- Modify `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`: derive Markdown presets from app typography roles; keep only card and reading visual modes.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt`: separate input container height from text-field content and center placeholder/text vertically.
- Modify `app/src/main/kotlin/com/dailysatori/ui/component/card/CustomCard.kt`: keep as shared main content card style.
- Modify `app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt`: use shared card padding rhythm and card Markdown preset.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt`: align card chrome/title/metadata/body with the shared main-card language.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt`: align user/assistant text rhythm and nested reference card typography.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt`: align manual bullet text with card Markdown body.
- Modify tests under `app/src/test/kotlin/com/dailysatori/` and `app/src/test/kotlin/com/dailysatori/ui/` to lock the final contracts.

## Task 1: Typography And Markdown Contracts

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/Typography.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/theme/LiquidDarkThemeTest.kt`
- Add: `app/src/test/kotlin/com/dailysatori/ui/theme/PolishTypographyUsageTest.kt`

- [ ] **Step 1: Add failing typography contract tests**

In `LiquidDarkThemeTest.typographyUsesOneSansSerifFamilyAcrossContentAndUi`, add assertions:

```kotlin
assertEquals(15.sp, AppTypography.bodyMedium.fontSize)
assertEquals(24.sp, AppTypography.bodyMedium.lineHeight)
assertEquals(16.sp, AppTypography.bodyLarge.fontSize)
assertEquals(26.sp, AppTypography.bodyLarge.lineHeight)
```

In `LiquidDarkThemeTest.markdownScaleMatchesUnifiedAppTypography`, replace hardcoded body-size assertions with source-contract assertions:

```kotlin
assertTrue(source.contains("private fun cardTextStyle"))
assertTrue(source.contains("MaterialTheme.typography.bodyMedium"))
assertTrue(source.contains("private fun readingTextStyle"))
assertTrue(source.contains("MaterialTheme.typography.bodyLarge"))
assertTrue(source.contains("fun summaryTypography(): MarkdownTypography = cardTypography()"))
assertTrue(source.contains("fun compactTypography(): MarkdownTypography = cardTypography()"))
assertTrue(source.contains("fontFamily = UiFontFamily"))
assertFalse(source.contains("bodySize ="))
assertFalse(source.contains("bodyLine ="))
```

Create `app/src/test/kotlin/com/dailysatori/ui/theme/PolishTypographyUsageTest.kt`:

```kotlin
package com.dailysatori.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolishTypographyUsageTest {
    @Test
    fun polishSensitiveScreensDoNotHardcodeFontSizes() {
        val files = listOf(
            "src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt",
            "src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt",
            "src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt",
            "src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt",
            "src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt",
            "src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt",
        )

        files.forEach { path ->
            val source = File(path).readText()
            assertFalse(source.contains("fontSize ="), path)
            assertFalse(source.contains(".sp"), path)
            assertTrue(source.contains("MaterialTheme.typography") || source.contains("MarkdownStyles"), path)
        }
    }
}
```

- [ ] **Step 2: Run failing typography tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.LiquidDarkThemeTest --tests com.dailysatori.ui.theme.PolishTypographyUsageTest
```

Expected: FAIL before implementation.

- [ ] **Step 3: Normalize `Typography.kt`**

Change body tokens:

```kotlin
bodyLarge = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
bodyMedium = TextStyle(fontFamily = UiFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
```

Keep title and label roles unless tests or compile require small adjustments.

- [ ] **Step 4: Refactor `MarkdownStyles.kt` to derive from typography roles**

Replace integer-scale presets with two visual modes. Keep the public functions but make `summary` and `compact` aliases of card mode:

```kotlin
@Composable
fun readingTypography(): MarkdownTypography = typographyFrom(
    body = readingTextStyle(),
    h1 = MaterialTheme.typography.headlineLarge,
    h2 = MaterialTheme.typography.headlineMedium,
    h3 = MaterialTheme.typography.headlineSmall,
    linkColor = MaterialTheme.colorScheme.primary,
)

@Composable
fun cardTypography(): MarkdownTypography = typographyFrom(
    body = cardTextStyle(),
    h1 = MaterialTheme.typography.titleLarge,
    h2 = MaterialTheme.typography.titleMedium,
    h3 = MaterialTheme.typography.titleSmall,
    linkColor = MaterialTheme.colorScheme.primary,
)

@Composable
fun summaryTypography(): MarkdownTypography = cardTypography()

@Composable
fun compactTypography(): MarkdownTypography = cardTypography()

@Composable
fun typography(): MarkdownTypography = readingTypography()

@Composable
fun remoteArticleTypography(): MarkdownTypography = readingTypography()

@Composable
private fun cardTextStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = UiFontFamily)

@Composable
private fun readingTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = UiFontFamily)
```

Implement `typographyFrom` so paragraph/list/quote/link all use the provided body style, code uses the existing small UI style, and headings use the provided heading styles with font family set to `UiFontFamily`.

- [ ] **Step 5: Run typography tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.LiquidDarkThemeTest --tests com.dailysatori.ui.theme.PolishTypographyUsageTest
./gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/theme/Typography.kt app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt app/src/test/kotlin/com/dailysatori/ui/theme/LiquidDarkThemeTest.kt app/src/test/kotlin/com/dailysatori/ui/theme/PolishTypographyUsageTest.kt
git commit -m "fix: converge typography rhythm"
```

## Task 2: Center AI Chat Input Text

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`

- [ ] **Step 1: Update failing input source test**

In `AiChatUiStateTest.chatInputUsesCompactLiquidGlassSizing`, replace the assertion for direct text-field min height with assertions for a centered decoration container:

```kotlin
assertTrue(source.contains("private val ChatInputContentMinHeight = Height.input"))
assertTrue(source.contains("contentAlignment = Alignment.CenterStart"))
assertTrue(source.contains("Modifier.heightIn(min = ChatInputContentMinHeight)"))
assertFalse(source.contains(".heightIn(min = ChatInputMinHeight)\n                            .padding(contentPadding)"))
```

Keep assertions for button size, circular shape, `minLines = 1`, `maxLines = 3`, and bodyMedium styles.

- [ ] **Step 2: Run failing AI chat test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.aichat.AiChatUiStateTest
```

Expected: FAIL before implementation.

- [ ] **Step 3: Refactor `ChatInputBar.kt` layout**

Change `ChatInputMinHeight` to:

```kotlin
private val ChatInputContentMinHeight = Height.input
```

Use a `Box` in `decorationBox` as the container that owns min height and center alignment:

```kotlin
modifier = Modifier
    .weight(1f)
    .onFocusChanged { isFocused = it.isFocused },
```

```kotlin
decorationBox = { innerTextField ->
    Box(
        modifier = Modifier
            .heightIn(min = ChatInputContentMinHeight)
            .padding(contentPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (inputText.isEmpty()) {
            Text(
                "问我任何问题...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        innerTextField()
    }
},
```

Keep `BasicTextField` `textStyle = MaterialTheme.typography.bodyMedium.copy(color = ...)`, `minLines = 1`, and `maxLines = 3`.

- [ ] **Step 4: Run AI chat test and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.aichat.AiChatUiStateTest
./gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt
git commit -m "fix: center chat input text"
```

## Task 3: Main Tab Card And Metadata Consistency

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt`
- Add: `app/src/test/kotlin/com/dailysatori/ui/theme/MainContentRhythmTest.kt`

- [ ] **Step 1: Add failing main content rhythm test**

Create `app/src/test/kotlin/com/dailysatori/ui/theme/MainContentRhythmTest.kt`:

```kotlin
package com.dailysatori.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainContentRhythmTest {
    @Test
    fun mainCardsUseSharedPaddingAndMarkdownPreset() {
        val diary = File("src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt").readText()
        val viewpoint = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()
        val message = File("src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt").readText()
        val citation = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt").readText()

        assertTrue(diary.contains("Modifier.padding(Spacing.m)"))
        assertTrue(diary.contains("MarkdownStyles.cardTypography()"))
        assertTrue(diary.contains("MarkdownStyles.cardPadding()"))
        assertTrue(viewpoint.contains("shape = RoundedCornerShape(Radius.l)"))
        assertTrue(viewpoint.contains("border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline)"))
        assertTrue(viewpoint.contains("style = MaterialTheme.typography.titleMedium"))
        assertTrue(viewpoint.contains("MarkdownStyles.cardTypography()"))
        assertTrue(message.contains("MarkdownStyles.cardTypography()"))
        assertTrue(citation.contains("style = MaterialTheme.typography.bodyMedium"))
        assertFalse(citation.contains("style = MaterialTheme.typography.bodyLarge"))
    }
}
```

- [ ] **Step 2: Run failing rhythm test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.MainContentRhythmTest
```

Expected: FAIL before implementation.

- [ ] **Step 3: Align `DiaryCard.kt`**

Change the main `Column` padding from:

```kotlin
Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)
```

to:

```kotlin
Modifier.padding(Spacing.m)
```

Change diary Markdown usage from `compactTypography/compactPadding` to `cardTypography/cardPadding`.

- [ ] **Step 4: Align `ViewpointCard.kt`**

Update the card shape to `RoundedCornerShape(Radius.l)`, add a subtle outline border, and align text:

```kotlin
shape = RoundedCornerShape(Radius.l),
border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline),
```

Change the main title style from `headlineSmall` to `titleMedium` and keep `FontWeight.SemiBold` or `Bold` only if visually necessary. Prefer start alignment over centered title unless the existing layout would break.

Change Markdown usage from `compactTypography/compactPadding` to `cardTypography/cardPadding`.

Change passive book metadata from `titleSmall` + primary to `labelSmall` or `bodySmall` + `onSurfaceVariant`.

- [ ] **Step 5: Align `MessageBubble.kt` and `CitationText.kt`**

In `MessageBubble.kt`, change assistant Markdown to `MarkdownStyles.cardTypography()` and `MarkdownStyles.cardPadding()`.

In `CitationText.kt`, change bullet row text style from `MaterialTheme.typography.bodyLarge` to `MaterialTheme.typography.bodyMedium`.

- [ ] **Step 6: Run tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.MainContentRhythmTest --tests com.dailysatori.ui.theme.PolishTypographyUsageTest --tests com.dailysatori.UnifiedNewsBehaviorTest
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt app/src/test/kotlin/com/dailysatori/ui/theme/MainContentRhythmTest.kt
git commit -m "fix: align main content card rhythm"
```

## Task 4: Final Verification And Device Install

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

- [ ] **Step 3: Install and launch on wireless device**

Run:

```bash
adb connect 192.168.2.7:42577
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb -s 192.168.2.7:42577 shell am start -n com.dailysatori/.MainActivity
```

Expected: connected/already connected, install succeeds, activity starts.

- [ ] **Step 4: Final git status**

Run:

```bash
git status --short
```

Expected: clean.
