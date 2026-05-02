# Article Refresh Stepper And Swipe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add pull-to-refresh to the article list, replace detail refresh text with a visual processing stepper, and support swipe switching between summary and original tabs.

**Architecture:** Keep existing view-model state and add small UI helpers/components in the article feature. Use Material3 `PullToRefreshBox` for list refresh and Compose Foundation `HorizontalPager` for detail tab swiping.

**Tech Stack:** Android Jetpack Compose Material3, Compose Foundation pager, Kotlin, Gradle Android build.

---

### Task 1: Processing Step Model

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleProcessingUiText.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/article/ArticleProcessingUiTextTest.kt`

- [ ] **Step 1: Add failing tests**

Test that known statuses/progress map to step indexes: pending=0, webContentFetched=1, Generating title=2, Generating summary=3, Converting to Markdown=4, Downloading cover image=5, completed=6.

- [ ] **Step 2: Implement step helper**

Add `articleProcessingStepIndex(status: String?, progress: String? = null): Int` and a label list for the seven processing steps.

### Task 2: List Pull-To-Refresh

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticlesViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListScreen.kt`

- [ ] **Step 1: Add refresh state and action**

Add `isRefreshing` to `ArticlesState` and `refreshArticles()` to reload the current list without showing the first-load full-screen spinner.

- [ ] **Step 2: Wrap list in PullToRefreshBox**

Use `PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refreshArticles)` around the list/empty-state content.

### Task 3: Detail Stepper UI

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleProcessingStepper.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt`

- [ ] **Step 1: Track progress in detail state**

Add `processingProgress` to `ArticleDetailState` and update it from `processingStates`.

- [ ] **Step 2: Create stepper composable**

Render all seven steps in a rounded card. Completed steps use `CheckCircle`, current step uses a small `CircularProgressIndicator`, future steps use an outlined circle.

- [ ] **Step 3: Replace content during refresh**

When `state.isRefreshing` is true, keep the top progress bar but render the stepper in the content area instead of old summary/original content.

### Task 4: Swipe Tabs

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt`

- [ ] **Step 1: Add pager state**

Use `rememberPagerState(pageCount = { 2 })` and `HorizontalPager`.

- [ ] **Step 2: Sync tabs and pager**

Tab clicks animate to pages. Swipes update `viewModel.selectTab(page)` through a `LaunchedEffect`.

### Task 5: Verify

**Files:**
- No additional source changes.

- [ ] **Step 1: Run targeted tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.article.ArticleProcessingUiTextTest"`
Expected: PASS.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL and installed on the connected device.

- [ ] **Step 4: Launch**

Run: `adb shell am start -n com.dailysatori/.MainActivity`
Expected: App launches.
