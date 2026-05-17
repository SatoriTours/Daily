# Unified News Card Feed Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make unified news summaries visually distinct as editorial cards and reduce scroll jank by incrementally loading history items.

**Architecture:** Keep the unified news feature in `UnifiedNewsScreen.kt` and `UnifiedNewsViewModel.kt`. UI cards use existing theme primitives (`MaterialTheme`, `Spacing`, `Radius`, `BorderWidth`) and the existing Markdown/citation renderer; pagination is a simple visible-count state in the ViewModel.

**Tech Stack:** Kotlin, Jetpack Compose LazyColumn, Material 3 Card/Surface, existing Android unit tests.

---

### Task 1: Incremental Summary Loading

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`

- [ ] **Step 1: Write failing test**

Add a test requiring `visibleSummaryLimit`, `loadMoreSummaries()`, `take(state.visibleSummaryLimit)`, and `LoadMoreWhenAtEnd`.

- [ ] **Step 2: Run red test**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsHistoryLoadsIncrementally --no-configuration-cache`

Expected: FAIL because these symbols do not exist.

- [ ] **Step 3: Implement minimal pagination**

Add `visibleSummaryLimit: Int = 3` to state and `loadMoreSummaries()` to increase by 3. In the screen, filter success summaries, pass `visibleSummaries.take(state.visibleSummaryLimit)`, and trigger loading when the visible list reaches the bottom.

### Task 2: Editorial Card Styling

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`

- [ ] **Step 1: Write failing test**

Add a test requiring `Card`, `CardDefaults`, `RoundedCornerShape(Radius.l)`, source-count badge text, and `点击正文引用查看原文`.

- [ ] **Step 2: Run red test**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsSummaryCardsUseEditorialSeparation --no-configuration-cache`

Expected: FAIL because summaries are plain `Column`s.

- [ ] **Step 3: Implement card UI**

Wrap each summary in a Material card with rounded corners, outline, surface container color, inner padding, time label, source count badge, Markdown body, and small citation hint.

### Task 3: Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Build and install**

Run: `./gradlew :app:compileDebugKotlin --no-configuration-cache && ./gradlew :app:assembleDebug --no-configuration-cache && JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug --no-configuration-cache && adb shell am start -n com.dailysatori/.MainActivity`

Expected: compile, assemble, install, and launch all succeed.
