# New Articles Indicator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve article-list reading position when returning from background while showing a clear prompt when new articles were inserted above.

**Architecture:** Add small pure helpers for counting new leading article ids and deciding when to show the indicator. `ArticlesViewModel` tracks the last visible top article id when the app backgrounds and exposes a one-shot count on resume; `ArticleListScreen` shows a floating Material button that scrolls to top and clears the indicator.

**Tech Stack:** Kotlin, Jetpack Compose, lifecycle events, existing ArticleRepository flow.

---

### Task 1: Add Pure Policy Helpers

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/AppUrlIntakeTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/AppUrlIntake.kt`

- [ ] Add failing tests for counting new leading IDs and indicator visibility.
- [ ] Run focused unit test and confirm unresolved references.
- [ ] Add `countNewLeadingArticles()` and `shouldShowNewArticlesIndicator()`.
- [ ] Rerun focused unit test.

### Task 2: Track Background/Foreground State In ViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticlesViewModel.kt`

- [ ] Add `newArticlesAboveCount` to `ArticlesState`.
- [ ] Add `rememberVisibleTopArticle(articleId: Long?)`, `checkNewArticlesAbove(firstVisibleArticleId: Long?)`, and `clearNewArticlesIndicator()`.
- [ ] On foreground check, count articles before the remembered top article id and expose the count only if the user is not already at top.

### Task 3: Wire UI Lifecycle And Floating Indicator

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListScreen.kt`

- [ ] Observe `ON_STOP` to record the current first visible article id.
- [ ] Observe `ON_RESUME` to ask the ViewModel to check for new articles above the current visible item.
- [ ] Show a floating button near the top: `上方有 N 篇新文章`.
- [ ] On click, animate to top and clear the indicator.
- [ ] If the list is already at top, clear the indicator.

### Task 4: Verify

**Files:**
- No edits.

- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.dailysatori.AppUrlIntakeTest`.
- [ ] Run `./gradlew :app:compileDebugKotlin`.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`.
- [ ] Run `adb shell am start -n com.dailysatori/.MainActivity`.
