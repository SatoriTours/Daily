# Book Immersive Reader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the book page into a single-book immersive viewpoint reader with clearer current-book hierarchy and explicit previous/next reading controls.

**Architecture:** Keep existing `BooksScreen` state and sheets, but reorganize the UI hierarchy. Add small pure helper functions for title/progress/action labels and update tests to lock the new interaction structure.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Gradle unit tests.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`: reduce top-level actions, show current book title, add progress strip and bottom reading controls, move management actions into the menu.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/book/BooksScreenUiTextTest.kt`: add contracts for the immersive reader structure and helper labels.

### Task 1: Immersive Reader Structure

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BooksScreenUiTextTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`

- [ ] **Step 1: Write the failing test**

Add tests that expect `booksTopLevelActionCount()` to return `1`, progress helper text, previous/next labels, and source markers for `BookReadingProgressStrip` and `BookReadingNavigationBar`.

- [ ] **Step 2: Run the focused failing test**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BooksScreenUiTextTest`

Expected: FAIL because the immersive reader helpers and structure are not implemented yet.

- [ ] **Step 3: Implement minimal UI restructuring**

Update `BooksScreen` so the selected book drives the top bar title, the top-level toolbar only exposes the overflow menu, management actions move into that menu, and the reading area includes progress and previous/next controls.

- [ ] **Step 4: Run focused test**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BooksScreenUiTextTest`

Expected: PASS.

- [ ] **Step 5: Run required verification**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: BUILD SUCCESSFUL and install succeeds.

Run explicit `adb -s <device> shell am start -n com.dailysatori/.MainActivity` for connected devices.

Expected: Activity starts or receives the intent.

## Self-Review

- Spec coverage: The plan covers reduced toolbar actions, current-book hierarchy, progress strip, previous/next controls, and existing sheet reuse.
- Deferred marker scan: no issues found.
- Type consistency: Helper names are introduced and tested in the same task.
