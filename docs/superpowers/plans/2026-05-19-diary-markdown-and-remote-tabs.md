# Diary Markdown And Remote Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix remote article detail ordering, make diary Markdown toolbar act directly on text, and remove the modal sheet side-back click-blocking issue.

**Architecture:** Keep changes local to the Compose UI files. Replace diary `ModalBottomSheet` with a `Dialog` panel so all dismissal paths clear composition state, and extend the existing toolbar callbacks to insert Markdown into the current `TextFieldValue`.

**Tech Stack:** Kotlin, Jetpack Compose Material3, AndroidX activity back handling, Coil, Gradle unit tests.

---

### Task 1: Remote Article Detail Order

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailLayoutTest.kt`

- [ ] **Step 1: Write failing source-order test**

Assert `RemoteArticleCoverImage` appears before `MarkdownTabRow`, `MarkdownTabRow` before `RemoteArticleHeader`, and header before `LazyColumn` inside the pager content.

- [ ] **Step 2: Run focused test**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.remotenews.RemoteArticleDetailLayoutTest`

Expected: FAIL because `MarkdownTabRow` is currently above the image.

- [ ] **Step 3: Move tab row below cover and above header**

Place `MarkdownTabRow` inside the pager page column after the optional cover image and before `RemoteArticleHeader(article)`.

- [ ] **Step 4: Run focused test**

Expected: PASS.

### Task 2: Diary Editor Dialog And Markdown Toolbar

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorToolbar.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheetBehaviorTest.kt`

- [ ] **Step 1: Write failing source tests**

Assert the editor uses `Dialog`, no longer uses `ModalBottomSheet`, all Markdown actions call direct text mutation callbacks, and the old `OutlinedTextField` tag input path is gone.

- [ ] **Step 2: Run focused test**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.diary.DiaryEditorSheetBehaviorTest`

Expected: FAIL because the editor still uses `ModalBottomSheet` and has tag input UI.

- [ ] **Step 3: Replace sheet with dialog panel**

Use `Dialog(onDismissRequest = onDismiss)` with `DialogProperties(usePlatformDefaultWidth = false)`, a full-screen Box aligned bottom center, and a rounded Surface/Column at `fillMaxHeight(0.92f)`.

- [ ] **Step 4: Expand toolbar actions**

Add callbacks and buttons for title, bold, italic, quote, unordered list, ordered list, task item, inline code, divider, link, image, undo, redo, media, save. Remove tag input state and UI.

- [ ] **Step 5: Run focused test**

Expected: PASS.

### Task 3: Verification And Device Install

**Files:**
- No additional files.

- [ ] **Step 1: Run focused tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.diary.DiaryEditorSheetBehaviorTest --tests com.dailysatori.ui.feature.remotenews.RemoteArticleDetailLayoutTest`

Expected: PASS.

- [ ] **Step 2: Run required compile**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Connect, install, and launch**

Run: `adb connect 192.168.2.12:37343`

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: device connects, APK installs, app starts.

---

Self-review: The plan covers the approved UI order, toolbar behavior, and side-back blocker root cause. It contains exact files and commands, and no placeholder implementation steps.
