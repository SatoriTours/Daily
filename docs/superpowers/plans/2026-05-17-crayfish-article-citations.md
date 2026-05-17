# Crayfish Article Citations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make unified news claims cite individual crayfish articles, so tapping a citation opens the corresponding article detail, while reducing feed jank and removing the first-card top gap.

**Architecture:** Extend the existing crayfish API client with article list/detail endpoints and store article routing in the existing `unified_news_source.source_filename` field. Reuse the current citation click flow by decoding article route keys in `UnifiedNewsViewModel`; keep schema unchanged.

**Tech Stack:** Kotlin Multiplatform shared services, Ktor client, kotlinx.serialization, SQLDelight existing schema, Jetpack Compose UI, Android unit tests.

---

### Task 1: Crayfish Article API Models And Service

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/crayfishnews/CrayfishNewsService.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write failing test**

Add a source inspection test requiring `CrayfishArticleListResponse`, `CrayfishArticle`, `fetchArticleList`, and `fetchArticle`.

- [ ] **Step 2: Run red test**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.crayfishNewsServiceSupportsArticleListAndDetailEndpoints --no-configuration-cache`

Expected: FAIL because models and methods do not exist.

- [ ] **Step 3: Implement models and methods**

Add serializable models for article lists and article details. Add service methods hitting `/news/{category}/{date}/articles` and `/news/{category}/{date}/articles/{id}`.

### Task 2: Unified News Collects Article-Level Crayfish Sources

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write failing test**

Require unified collection to call `fetchArticleList`, build `sourceFilename = crayfishArticleRouteKey(...)`, and use article title/url/content.

- [ ] **Step 2: Run red test**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsCollectsCrayfishArticleLevelSources --no-configuration-cache`

Expected: FAIL.

- [ ] **Step 3: Implement article source collection**

For each matching date item, call article list, map each article to a `UnifiedNewsSourceItem`, and use a route key like `general/2026-05-16/articles/ai-大事件-1`.

### Task 3: Citation Routing Opens Crayfish Article Detail

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write failing test**

Require `CrayfishArticle`, `crayfishArticleRouteKey`, `parseCrayfishArticleRouteKey`, and `fetchArticle` in ViewModel routing.

- [ ] **Step 2: Run red test**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsCitationRoutingOpensCrayfishArticleDetails --no-configuration-cache`

Expected: FAIL.

- [ ] **Step 3: Implement route parsing and article detail loading**

Add a navigation target for article route keys and fetch article detail via the new service method.

### Task 4: Feed Jank And Top Gap Fixes

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write failing tests**

Require `visibleSummaryLimit: Int = 1`, `visibleSummaryLimit + 1`, no unconditional `item { Column(...) }` before summaries, and `rememberMarkdownState` in `CitationText`.

- [ ] **Step 2: Run red tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsFeedAvoidsTopGapAndLoadsOneAtATime --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsMarkdownUsesRememberedAsyncState --no-configuration-cache`

Expected: FAIL.

- [ ] **Step 3: Implement fixes**

Set paging size to 1. Only render status item when needed. Use remembered async Markdown state.

### Task 5: Verification

**Files:**
- No new files.

- [ ] **Step 1: Run focused tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Build/install/launch**

Run: `./gradlew :app:compileDebugKotlin --no-configuration-cache && ./gradlew :app:assembleDebug --no-configuration-cache && JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug --no-configuration-cache && adb shell am start -n com.dailysatori/.MainActivity`

Expected: all commands succeed.
