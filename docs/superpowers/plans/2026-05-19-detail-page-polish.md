# Detail Page Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent diary editor accidental swipe dismissal and make remote article details visually match local article details with cover images and cleaner metadata.

**Architecture:** Keep changes local to the existing Compose screens. Reuse the local article cover collapse helper for remote articles, and keep diary editor dismissal explicit via close/back only.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Coil, Gradle unit tests.

---

### Task 1: Lock Diary Editor Sheet Gesture Dismissal

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheetBehaviorTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.dailysatori.ui.feature.diary

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DiaryEditorSheetBehaviorTest {
    @Test
    fun diaryEditorSheetDisablesSheetGesturesToAvoidScrollDismissal() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()
        val sheetCall = source.substringAfter("ModalBottomSheet(").substringBefore(") {")

        assertTrue(sheetCall.contains("onDismissRequest = {}"))
        assertTrue(source.contains("IconButton(onClick = onDismiss"))
    }
}
```

- [ ] **Step 2: Run test and verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.diary.DiaryEditorSheetBehaviorTest`

Expected: FAIL because implicit dismiss requests still call `onDismiss`.

- [ ] **Step 3: Implement minimal code**

Set `ModalBottomSheet(onDismissRequest = {})` and keep the close button/back handler as explicit exits.

- [ ] **Step 4: Verify green**

Run the same test and confirm PASS.

### Task 2: Remote Article Detail Cover And Compact Header

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailLayoutTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.dailysatori.ui.feature.remotenews

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteArticleDetailLayoutTest {
    @Test
    fun remoteArticleDetailDisplaysCoverUrlWithCollapsibleArticleCover() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()

        assertTrue(source.contains("val coverImage = article.coverUrl"))
        assertTrue(source.contains("RemoteArticleCoverImage("))
        assertTrue(source.contains("articleCoverHeightAfterScroll("))
        assertTrue(source.contains("originalImageUrls = listOfNotNull(article.coverUrl)"))
    }

    @Test
    fun remoteArticleHeaderUsesCompactMetadataInsteadOfHeavyHeroLabel() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()

        assertTrue(source.contains("RemoteArticleHeader("))
        assertFalse(source.contains("阅读详情"))
        assertTrue(source.contains("articleRemoteMetaText(article)"))
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.remotenews.RemoteArticleDetailLayoutTest`

Expected: FAIL because the remote detail still uses the old hero card and has no cover collapse.

- [ ] **Step 3: Implement minimal code**

Use `article.coverUrl` as the detail cover. Add a `RemoteArticleCoverImage` composable with Coil, import `articleCoverMaxHeightDp` and `articleCoverHeightAfterScroll`, and replace `RemoteArticleHeroCard` with compact `RemoteArticleHeader`.

- [ ] **Step 4: Verify green**

Run the remote layout test and confirm PASS.

### Task 3: Final Verification

**Files:**
- No additional files.

- [ ] **Step 1: Run focused tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.diary.DiaryEditorSheetBehaviorTest --tests com.dailysatori.ui.feature.remotenews.RemoteArticleDetailLayoutTest`

Expected: PASS.

- [ ] **Step 2: Run required compile check**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install and launch app**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: install succeeds and app starts.

---

Self-review: The plan covers both requested behaviors, uses exact files and commands, contains no placeholders, and keeps scope limited to existing detail screens.
