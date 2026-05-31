# AI Bottom Bar Slide Animation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the AI bottom-bar morph animation with a stable right-to-left slide-in and left-to-right slide-out animation.

**Architecture:** Keep `HomeBottomBarSurface` as the single owner of bottom navigation. Replace weight-based layout morphing with `AnimatedContent` using directional horizontal slide transitions between normal tabs and the AI compact input row.

**Tech Stack:** Kotlin, Jetpack Compose animation APIs, Android app JVM source tests, Gradle.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`: remove `updateTransition` weight morphing and render the bottom bar content with directional `AnimatedContent`.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`: update animation behavior source tests.

## Task 1: Lock Slide Animation Expectations

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt:613-655`

- [ ] **Step 1: Replace the morphing animation test**

Replace `aiBottomBarMorphUsesLayoutWidthInsteadOfTransformOnlyScale()` with:

```kotlin
@Test
fun aiBottomBarUsesDirectionalSlideInsteadOfWeightMorph() {
    val home = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()
    val bottomBar = home.substringAfter("private fun HomeBottomBarSurface(").substringBefore("@Composable\nprivate fun AiCompactInputRow(")

    assertTrue(home.contains("AnimatedContent("))
    assertTrue(home.contains("slideInHorizontally(initialOffsetX = { it })"))
    assertTrue(home.contains("slideOutHorizontally(targetOffsetX = { it })"))
    assertTrue(home.contains("selectedIndex = TODAY_TAB_INDEX"))
    assertFalse(bottomBar.contains("home-bottom-ai-input-weight"))
    assertFalse(bottomBar.contains("home-bottom-tabs-weight"))
    assertFalse(bottomBar.contains("Modifier.weight(inputWeight)"))
}
```

- [ ] **Step 2: Update the shared bottom bar test**

In `aiTabUsesSharedMorphingHomeBottomBar()`, keep the existing assertions except replace:

```kotlin
assertTrue(home.contains("updateTransition(targetState = selectedIndex == AI_CHAT_TAB_INDEX"))
```

with:

```kotlin
assertTrue(home.contains("targetState = selectedIndex == AI_CHAT_TAB_INDEX"))
```

- [ ] **Step 3: Update the outer container animation test**

Replace `aiExpandedBottomBarRemovesOuterContainerBackgroundAndBorder()` with:

```kotlin
@Test
fun aiExpandedBottomBarKeepsStableOuterContainerDuringSlide() {
    val home = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()
    val bottomBar = home.substringAfter("private fun HomeBottomBarSurface(").substringBefore("@Composable\nprivate fun AiCompactInputRow(")

    assertTrue(bottomBar.contains("MaterialTheme.colorScheme.surface"))
    assertTrue(bottomBar.contains("BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outlineVariant"))
    assertFalse(bottomBar.contains("home-bottom-container-alpha"))
    assertFalse(bottomBar.contains("copy(alpha = containerAlpha)"))
}
```

- [ ] **Step 4: Run focused tests and verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.aichat.AiChatUiStateTest
```

Expected: FAIL because `HomeScreen.kt` still uses weight morphing and does not yet contain `AnimatedContent` slide transitions.

## Task 2: Implement Directional Slide Transition

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt:3-50`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt:158-208`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt:214-256`

- [ ] **Step 1: Update animation imports**

Remove these imports:

```kotlin
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
```

Add these imports:

```kotlin
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
```

- [ ] **Step 2: Replace `HomeBottomBarSurface` transition state**

Inside `HomeBottomBarSurface`, delete the `updateTransition`, `aiProgress`, `inputWeight`, `tabsWeight`, and `containerAlpha` declarations.

Add:

```kotlin
val isAiMode = selectedIndex == AI_CHAT_TAB_INDEX
```

- [ ] **Step 3: Stabilize the outer bottom container**

In the `Surface`, use fixed surface color and border:

```kotlin
color = MaterialTheme.colorScheme.surface,
contentColor = MaterialTheme.colorScheme.onSurface,
tonalElevation = 0.dp,
shadowElevation = 10.dp,
border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outlineVariant),
```

- [ ] **Step 4: Replace weighted Row content with `AnimatedContent`**

Replace the `Row` body inside `Surface` with:

```kotlin
AnimatedContent(
    targetState = isAiMode,
    transitionSpec = { homeBottomBarSlideTransform() },
    label = "home-bottom-ai-slide",
) { showingAiInput ->
    if (showingAiInput) {
        AiCompactInputRow(
            aiInputController = aiInputController,
            onHomeClick = onHomeClick,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        HomeTabNavigationBar(
            selectedIndex = selectedIndex,
            onTabSelected = onTabSelected,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

- [ ] **Step 5: Add the slide transform helper**

Add this function after `HomeBottomBarSurface`:

```kotlin
private fun homeBottomBarSlideTransform(): ContentTransform =
    (slideInHorizontally(initialOffsetX = { it }) + fadeIn()) togetherWith
        (slideOutHorizontally(targetOffsetX = { it }) + fadeOut()) using
        SizeTransform(clip = true)
```

- [ ] **Step 6: Simplify `AiCompactInputRow` signature and modifier**

Change the signature to:

```kotlin
private fun AiCompactInputRow(
    aiInputController: AiChatInputController?,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Change its `Row` modifier from:

```kotlin
modifier = modifier.alpha(progress),
```

to:

```kotlin
modifier = modifier.height(HomeBottomBarHeight).padding(Spacing.xs),
```

- [ ] **Step 7: Simplify `HomeTabNavigationBar` signature and modifier**

Change the signature to:

```kotlin
private fun HomeTabNavigationBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
)
```

Change the `NavigationBar` modifier from:

```kotlin
modifier = modifier.height(HomeBottomBarHeight).alpha(1f - progress),
```

to:

```kotlin
modifier = modifier.height(HomeBottomBarHeight).padding(Spacing.xs),
```

- [ ] **Step 8: Run focused tests and verify they pass**

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

Expected: `connected to 192.168.2.4:39701` or `already connected to 192.168.2.4:39701`.

- [ ] **Step 4: Install debug build**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

Expected: BUILD SUCCESSFUL and installed on one device.

- [ ] **Step 5: Launch app on TCP device**

Run:

```bash
adb -s 192.168.2.4:39701 shell am start -n com.dailysatori/.MainActivity
```

Expected: `Starting: Intent { cmp=com.dailysatori/.MainActivity }`.

## Self-Review

- Spec coverage: enter/exit slide direction, removal of weight morphing, stable outer container, compact input preservation, and globe navigation are covered.
- Placeholder scan: no incomplete placeholders remain.
- Type consistency: `homeBottomBarSlideTransform()` returns `ContentTransform`; call sites use `AnimatedContent` with `targetState = isAiMode`.
