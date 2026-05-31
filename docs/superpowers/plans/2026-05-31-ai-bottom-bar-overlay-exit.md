# AI Bottom Bar Overlay Exit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make AI exit animation slide only the input row out to the right while normal tab icons remain stable, and remove the bottom-bar border.

**Architecture:** Render normal tab navigation as a stable base layer inside `HomeBottomBarSurface`. Render AI compact input as an `AnimatedVisibility` overlay with right-to-left enter and left-to-right exit.

**Tech Stack:** Kotlin, Jetpack Compose animation APIs, Android app JVM source tests, Gradle.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`: replace `AnimatedContent` swapping with a base `Box` plus `AnimatedVisibility` overlay; remove `border` from bottom bar `Surface`.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`: update source-level animation and border assertions.

## Task 1: Lock Overlay Exit Expectations

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt:634-657`

- [ ] **Step 1: Update animation source test**

In `aiBottomBarUsesDirectionalSlideInsteadOfWeightMorph()`, assert overlay animation and no `AnimatedContent`:

```kotlin
assertTrue(home.contains("AnimatedVisibility("))
assertFalse(home.contains("AnimatedContent("))
assertTrue(home.contains("visible = isAiMode"))
assertTrue(home.contains("enter = homeBottomBarEnterTransition()"))
assertTrue(home.contains("exit = homeBottomBarExitTransition()"))
assertTrue(home.contains("slideInHorizontally(initialOffsetX = { it })"))
assertTrue(home.contains("slideOutHorizontally(targetOffsetX = { it })"))
```

- [ ] **Step 2: Update stable container test for no border**

In `aiExpandedBottomBarKeepsStableOuterContainerDuringSlide()`, replace the border assertion with:

```kotlin
assertFalse(bottomBar.contains("border ="))
```

- [ ] **Step 3: Run focused tests and verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.aichat.AiChatUiStateTest
```

Expected: FAIL because the implementation still uses `AnimatedContent` and has `border =` in the bottom bar surface.

## Task 2: Implement Stable Base Plus AI Overlay

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt:3-55`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt:163-209`

- [ ] **Step 1: Update imports**

Remove:

```kotlin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
```

Add:

```kotlin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
```

- [ ] **Step 2: Remove border from bottom bar surface**

Delete the `border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outlineVariant),` argument from `HomeBottomBarSurface`.

- [ ] **Step 3: Replace `AnimatedContent` with base tabs and overlay input**

Replace the current `AnimatedContent` block with:

```kotlin
Box(modifier = Modifier.fillMaxWidth()) {
    HomeTabNavigationBar(
        selectedIndex = selectedIndex,
        onTabSelected = onTabSelected,
        modifier = Modifier.fillMaxWidth(),
    )
    AnimatedVisibility(
        visible = isAiMode,
        enter = homeBottomBarEnterTransition(),
        exit = homeBottomBarExitTransition(),
        modifier = Modifier.fillMaxWidth(),
        label = "home-bottom-ai-overlay",
    ) {
        AiCompactInputRow(
            aiInputController = aiInputController,
            onHomeClick = onHomeClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

- [ ] **Step 4: Replace transform helper with enter/exit helpers**

Replace `homeBottomBarSlideTransform()` with:

```kotlin
private fun homeBottomBarEnterTransition(): EnterTransition =
    slideInHorizontally(initialOffsetX = { it }) + fadeIn()

private fun homeBottomBarExitTransition(): ExitTransition =
    slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
```

- [ ] **Step 5: Run focused tests and verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.aichat.AiChatUiStateTest
```

Expected: PASS.

## Task 3: Required Verification And Device Deploy

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

- [ ] **Step 3: Connect TCP device**

Run:

```bash
adb connect 192.168.2.4:39701
```

Expected: connected or already connected.

- [ ] **Step 4: Install debug build**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

Expected: BUILD SUCCESSFUL and installed on one device.

- [ ] **Step 5: Launch app**

Run:

```bash
adb -s 192.168.2.4:39701 shell am start -n com.dailysatori/.MainActivity
```

Expected: app activity starts.

## Self-Review

- Spec coverage: overlay exit, stable icons, border removal, keyboard avoidance preservation, and compact input preservation are covered.
- Placeholder scan: no incomplete placeholders remain.
- Type consistency: `homeBottomBarEnterTransition()` returns `EnterTransition`; `homeBottomBarExitTransition()` returns `ExitTransition`; call site uses `AnimatedVisibility`.
