# AI Input Bar Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish the AI assistant compact bottom input so it uses a short one-line placeholder, does not auto-focus, and stays above the Android keyboard.

**Architecture:** Keep the existing shared `ChatInputField` component and add a small compact-mode parameter for the home-owned bottom bar. Apply IME padding where the bottom bar is actually rendered: `HomeBottomBarSurface`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android IME insets, existing JVM source-text tests.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt`: shorten placeholder and add compact single-line behavior to `ChatInputField`.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`: apply `imePadding()` to the home bottom bar and use compact mode for the AI input.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`: update behavior tests and add source checks for compact mode and IME placement.

## Task 1: Lock Placeholder And Compact-Mode Expectations

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt:347-349`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt:602-624`

- [ ] **Step 1: Update the placeholder test**

Change the expectation in `chatInputOffersEditorialQuickPrompts()` to:

```kotlin
assertEquals("问点什么", chatInputPlaceholderText())
```

- [ ] **Step 2: Add source checks for IME handling and compact mode**

Update `homeBottomBarRemainsVisibleOnAiTab()` to read `HomeScreen.kt` and verify `imePadding()` is applied in the home bottom bar:

```kotlin
@Test
fun homeBottomBarRemainsVisibleOnAiTab() {
    val home = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()
    val bottomBar = home.substringAfter("private fun HomeBottomBarSurface(").substringBefore("@Composable\nprivate fun AiCompactInputRow(")

    assertTrue(chatInputUsesImePadding())
    assertTrue(homeBottomBarVisibleForTab(AI_CHAT_TAB_INDEX))
    assertTrue(homeBottomBarVisibleForTab(TODAY_TAB_INDEX))
    assertTrue(bottomBar.contains(".imePadding()"))
}
```

- [ ] **Step 3: Add source checks for compact input mode**

In `aiTabUsesSharedMorphingHomeBottomBar()`, after the existing `assertTrue(input.contains("fun ChatInputField("))`, add:

```kotlin
assertTrue(input.contains("compact: Boolean = false"))
assertTrue(input.contains("singleLine = compact"))
assertTrue(home.contains("compact = true"))
```

- [ ] **Step 4: Run the focused test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.aichat.AiChatUiStateTest
```

Expected: FAIL because the placeholder is still long, `HomeBottomBarSurface` does not apply `.imePadding()`, and `ChatInputField` does not yet expose `compact`.

## Task 2: Implement Compact Input Behavior

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt:58`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt:101-157`

- [ ] **Step 1: Shorten placeholder**

Change:

```kotlin
fun chatInputPlaceholderText(): String = "继续追问今天的新闻、日记或文章..."
```

to:

```kotlin
fun chatInputPlaceholderText(): String = "问点什么"
```

- [ ] **Step 2: Add compact parameter to `ChatInputField`**

Change the function signature to:

```kotlin
fun ChatInputField(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
)
```

- [ ] **Step 3: Make compact mode single-line**

In the `BasicTextField`, replace:

```kotlin
minLines = 1,
maxLines = 3,
```

with:

```kotlin
singleLine = compact,
minLines = 1,
maxLines = if (compact) 1 else 3,
```

- [ ] **Step 4: Run the focused test and verify the remaining failures**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.aichat.AiChatUiStateTest
```

Expected: placeholder and compact source checks pass; the IME padding check still fails until Task 3 is implemented.

## Task 3: Apply IME Padding To The Home Bottom Bar

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt:1-60`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt:177-179`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt:231-238`

- [ ] **Step 1: Add the `imePadding` import**

Add this import near the other `androidx.compose.foundation.layout` imports:

```kotlin
import androidx.compose.foundation.layout.imePadding
```

- [ ] **Step 2: Apply IME padding to `HomeBottomBarSurface`**

Change the `Surface` modifier in `HomeBottomBarSurface` from:

```kotlin
modifier = Modifier.navigationBarsPadding()
    .padding(horizontal = Spacing.m, vertical = Spacing.s),
```

to:

```kotlin
modifier = Modifier
    .navigationBarsPadding()
    .imePadding()
    .padding(horizontal = Spacing.m, vertical = Spacing.s),
```

- [ ] **Step 3: Enable compact mode for the AI bottom input**

In `AiCompactInputRow`, change the `ChatInputField` call to include compact mode:

```kotlin
ChatInputField(
    inputText = aiInputController?.inputText.orEmpty(),
    onInputChange = aiInputController?.onInputChange ?: {},
    onSend = aiInputController?.onSend ?: {},
    onStop = aiInputController?.onStop ?: {},
    isProcessing = aiInputController?.isProcessing ?: false,
    modifier = Modifier.weight(1f),
    compact = true,
)
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.aichat.AiChatUiStateTest
```

Expected: PASS.

## Task 4: Required Project Verification And Device Deploy

**Files:**
- No code edits.

- [ ] **Step 1: Compile Kotlin**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Assemble debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install debug build to connected Android device**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

Expected: BUILD SUCCESSFUL. If no device is connected, report the exact Gradle/ADB error instead of claiming deployment succeeded.

- [ ] **Step 4: Launch the app**

Run:

```bash
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: Android reports the activity start command completed. If no device is connected, report the exact ADB error.

## Self-Review

- Spec coverage: placeholder shortening is covered in Tasks 1 and 2; no auto-focus is preserved by avoiding focus request changes; keyboard avoidance is covered in Task 3; typing stability is covered by compact single-line mode.
- Placeholder scan: no TBD/TODO/fill-in placeholders remain.
- Type consistency: `compact: Boolean = false` is introduced in `ChatInputField` and called as `compact = true` from `HomeScreen.kt`.
