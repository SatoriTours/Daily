# News Module Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce news ViewModel complexity by extracting pure state-transition helpers while preserving all unified news and remote news behavior.

**Architecture:** Keep repositories and services untouched. Add small package-local helper files beside the existing ViewModels, then update the ViewModels to call those helpers for cache/selection/detail/loading state transitions. Tests exercise the extracted pure helpers and source-level boundaries so API calls, cache keys, database writes, UI text, and navigation remain unchanged.

**Tech Stack:** Kotlin, AndroidX ViewModel, StateFlow, Kotlin coroutines, existing Daily Satori KMP repositories/services, Kotlin unit tests.

**Workspace Note:** Project instructions forbid git worktrees and commits unless explicitly requested. Execute in the current workspace and do not include commit steps.

---

## File Structure

- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSourceArticleState.kt`
  - Pure helper functions for unified-news remote source selection, cache checks, and source-article loading state.
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSourceArticleStateTest.kt`
  - Unit tests for unified-news source selection and cache helpers.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
  - Replace inline source-selection/cache state logic with helpers; keep service/repository calls unchanged.
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsLocalArticleBackTest.kt`
  - Update source-level guardrails to look for the new helper calls instead of inline implementation details.
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsDetailState.kt`
  - Pure helper functions for clearing selected source detail and starting a detail request.
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsDetailStateTest.kt`
  - Unit tests for detail-state clearing and navigation-target preservation.
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsLoadState.kt`
  - Pure helper functions for page-load start, success, refresh token, and failure state.
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsLoadStateTest.kt`
  - Unit tests for remote-news loading, refresh, append, and failure state transitions.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt`
  - Replace repeated paging state update blocks with helpers; keep service calls unchanged.

---

### Task 1: Extract Unified News Source Article State Helpers

**Files:**
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSourceArticleStateTest.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsSourceArticleState.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsLocalArticleBackTest.kt`

- [ ] **Step 1: Write the failing helper tests**

Create `UnifiedNewsSourceArticleStateTest.kt` with this complete content:

```kotlin
package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.service.remotenews.RemoteArticle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnifiedNewsSourceArticleStateTest {
    @Test
    fun removedRemoteSourceResetsSelectionToSummary() {
        val selection = UnifiedNewsSourceSelection.RemoteSource(id = 7L, name = "旧来源")
        val sources = listOf(UnifiedNewsRemoteSourceOption(id = 8L, name = "新来源"))

        assertTrue(shouldResetUnifiedNewsSourceSelection(selection, sources))
        assertEquals(UnifiedNewsSourceSelection.Summary, resolvedUnifiedNewsSourceSelection(selection, sources))
    }

    @Test
    fun existingRemoteSourceKeepsSelection() {
        val selection = UnifiedNewsSourceSelection.RemoteSource(id = 7L, name = "来源")
        val sources = listOf(UnifiedNewsRemoteSourceOption(id = 7L, name = "来源"))

        assertFalse(shouldResetUnifiedNewsSourceSelection(selection, sources))
        assertEquals(selection, resolvedUnifiedNewsSourceSelection(selection, sources))
    }

    @Test
    fun summaryAndLocalSelectionsNeverResetForRemoteSourceList() {
        val sources = emptyList<UnifiedNewsRemoteSourceOption>()

        assertFalse(shouldResetUnifiedNewsSourceSelection(UnifiedNewsSourceSelection.Summary, sources))
        assertFalse(shouldResetUnifiedNewsSourceSelection(UnifiedNewsSourceSelection.LocalArticles, sources))
    }

    @Test
    fun cacheChecksUseSourceIdAndSummaryDate() {
        val cachedArticle = RemoteArticle(id = 1L, title = "已缓存")
        val state = UnifiedNewsState(
            sourceArticlesByCacheKey = mapOf(sourceArticleCacheKey(7L, "2026-05-31") to listOf(cachedArticle)),
        )

        assertTrue(hasUnifiedNewsSourceArticlesCache(state, sourceId = 7L, summaryDate = "2026-05-31"))
        assertFalse(hasUnifiedNewsSourceArticlesCache(state, sourceId = 7L, summaryDate = "2026-06-01"))
        assertEquals(listOf(cachedArticle), cachedUnifiedNewsSourceArticles(state, sourceId = 7L, summaryDate = "2026-05-31"))
        assertEquals(emptyList(), cachedUnifiedNewsSourceArticles(state, sourceId = 8L, summaryDate = "2026-05-31"))
    }

    @Test
    fun sourceArticleLoadingStateTransitionsPreserveCachedArticles() {
        val cachedArticle = RemoteArticle(id = 1L, title = "已缓存")
        val state = UnifiedNewsState(
            sourceArticlesByCacheKey = mapOf(sourceArticleCacheKey(7L, "2026-05-31") to listOf(cachedArticle)),
            sourceArticlesError = "旧错误",
        )

        val loading = state.withUnifiedNewsSourceArticlesLoading(sourceId = 7L)
        assertEquals(7L, loading.sourceArticlesLoadingSourceId)
        assertNull(loading.sourceArticlesError)
        assertEquals(state.sourceArticlesByCacheKey, loading.sourceArticlesByCacheKey)

        val failed = loading.withUnifiedNewsSourceArticlesFailure("新错误")
        assertNull(failed.sourceArticlesLoadingSourceId)
        assertEquals("新错误", failed.sourceArticlesError)
        assertEquals(state.sourceArticlesByCacheKey, failed.sourceArticlesByCacheKey)
    }
}
```

- [ ] **Step 2: Run the focused helper test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsSourceArticleStateTest
```

Expected: FAIL because `shouldResetUnifiedNewsSourceSelection`, `resolvedUnifiedNewsSourceSelection`, `hasUnifiedNewsSourceArticlesCache`, `cachedUnifiedNewsSourceArticles`, `withUnifiedNewsSourceArticlesLoading`, and `withUnifiedNewsSourceArticlesFailure` do not exist.

- [ ] **Step 3: Create the helper file**

Create `UnifiedNewsSourceArticleState.kt` with this complete content:

```kotlin
package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.service.remotenews.RemoteArticle

internal fun shouldResetUnifiedNewsSourceSelection(
    selection: UnifiedNewsSourceSelection,
    remoteSources: List<UnifiedNewsRemoteSourceOption>,
): Boolean = selection is UnifiedNewsSourceSelection.RemoteSource && remoteSources.none { it.id == selection.id }

internal fun resolvedUnifiedNewsSourceSelection(
    selection: UnifiedNewsSourceSelection,
    remoteSources: List<UnifiedNewsRemoteSourceOption>,
): UnifiedNewsSourceSelection = if (shouldResetUnifiedNewsSourceSelection(selection, remoteSources)) {
    UnifiedNewsSourceSelection.Summary
} else {
    selection
}

internal fun hasUnifiedNewsSourceArticlesCache(
    state: UnifiedNewsState,
    sourceId: Long,
    summaryDate: String,
): Boolean = state.sourceArticlesByCacheKey.containsKey(sourceArticleCacheKey(sourceId, summaryDate))

internal fun cachedUnifiedNewsSourceArticles(
    state: UnifiedNewsState,
    sourceId: Long,
    summaryDate: String,
): List<RemoteArticle> = state.sourceArticlesByCacheKey[sourceArticleCacheKey(sourceId, summaryDate)].orEmpty()

internal fun UnifiedNewsState.withUnifiedNewsSourceArticlesLoading(sourceId: Long): UnifiedNewsState = copy(
    sourceArticlesLoadingSourceId = sourceId,
    sourceArticlesError = null,
)

internal fun UnifiedNewsState.withUnifiedNewsSourceArticlesFailure(message: String): UnifiedNewsState = copy(
    sourceArticlesLoadingSourceId = null,
    sourceArticlesError = message,
)

internal fun UnifiedNewsState.withUnifiedNewsSourceArticlesLoaded(
    sourceId: Long,
    summaryDate: String,
    articles: List<RemoteArticle>,
): UnifiedNewsState = copy(
    sourceArticlesByCacheKey = sourceArticlesByCacheKey + (sourceArticleCacheKey(sourceId, summaryDate) to articles),
    sourceArticlesLoadingSourceId = null,
    sourceArticlesError = null,
)

internal fun UnifiedNewsState.withUnifiedNewsSourceArticleRequestInvalidated(): UnifiedNewsState = copy(
    sourceArticlesLoadingSourceId = null,
    sourceArticlesError = null,
)
```

- [ ] **Step 4: Update `UnifiedNewsViewModel.loadInitial()` selection reset logic**

Replace this block:

```kotlin
                    val currentState = _state.value
                    val currentSelection = currentState.sourceSelection
                    val shouldResetSelection = currentSelection is UnifiedNewsSourceSelection.RemoteSource &&
                        remoteSources.none { source -> source.id == currentSelection.id }
                    if (shouldResetSelection) invalidateSourceArticleRequest()
                    val nextSelection = if (shouldResetSelection) UnifiedNewsSourceSelection.Summary else currentSelection
```

With:

```kotlin
                    val currentState = _state.value
                    val currentSelection = currentState.sourceSelection
                    if (shouldResetUnifiedNewsSourceSelection(currentSelection, remoteSources)) {
                        invalidateSourceArticleRequest()
                    }
                    val nextSelection = resolvedUnifiedNewsSourceSelection(currentSelection, remoteSources)
```

- [ ] **Step 5: Update `UnifiedNewsViewModel.selectRemoteSource()` cache check**

Replace this block:

```kotlin
        val cacheKey = sourceArticleCacheKey(source.id, dailyUnifiedNewsWindowFor().summaryDate)
        val sourceArticlesCached = _state.value.sourceArticlesByCacheKey.containsKey(cacheKey)
```

With:

```kotlin
        val summaryDate = dailyUnifiedNewsWindowFor().summaryDate
        val sourceArticlesCached = hasUnifiedNewsSourceArticlesCache(_state.value, source.id, summaryDate)
```

- [ ] **Step 6: Update source article request state helpers in `UnifiedNewsViewModel`**

Replace these state updates:

```kotlin
state.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = "远程新闻源不存在或已删除")
```

```kotlin
state.copy(
    sourceArticlesByCacheKey = state.sourceArticlesByCacheKey + (cacheKey to result.value.articles),
    sourceArticlesLoadingSourceId = null,
    sourceArticlesError = null,
)
```

```kotlin
state.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = result.message)
```

```kotlin
state.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = config.message)
```

```kotlin
state.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = "来源文章加载失败，请稍后重试")
```

With:

```kotlin
state.withUnifiedNewsSourceArticlesFailure("远程新闻源不存在或已删除")
```

```kotlin
state.withUnifiedNewsSourceArticlesLoaded(sourceId, cacheKey.summaryDate, result.value.articles)
```

```kotlin
state.withUnifiedNewsSourceArticlesFailure(result.message)
```

```kotlin
state.withUnifiedNewsSourceArticlesFailure(config.message)
```

```kotlin
state.withUnifiedNewsSourceArticlesFailure("来源文章加载失败，请稍后重试")
```

- [ ] **Step 7: Update request invalidation/loading helpers in `UnifiedNewsViewModel`**

Replace this update in `invalidateSourceArticleRequest()`:

```kotlin
            _state.update { it.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = null) }
```

With:

```kotlin
            _state.update { it.withUnifiedNewsSourceArticleRequestInvalidated() }
```

Replace this update in `beginSourceArticleRequest(sourceId: Long)`:

```kotlin
            _state.update { it.copy(sourceArticlesLoadingSourceId = sourceId, sourceArticlesError = null) }
```

With:

```kotlin
            _state.update { it.withUnifiedNewsSourceArticlesLoading(sourceId) }
```

- [ ] **Step 8: Update source-level guardrails in `UnifiedNewsLocalArticleBackTest.kt`**

In `unifiedNewsSourceArticlesUseCurrentRemoteSourceAndCacheResults`, replace assertions that depend on inline implementation details with helper-call assertions:

```kotlin
        assertTrue(source.contains("hasUnifiedNewsSourceArticlesCache(_state.value, source.id, summaryDate)"))
        assertTrue(source.contains("state.withUnifiedNewsSourceArticlesLoaded(sourceId, cacheKey.summaryDate, result.value.articles)"))
        assertTrue(source.contains("state.withUnifiedNewsSourceArticlesFailure(\"来源文章加载失败，请稍后重试\")"))
        assertTrue(source.contains("_state.update { it.withUnifiedNewsSourceArticleRequestInvalidated() }"))
        assertTrue(source.contains("_state.update { it.withUnifiedNewsSourceArticlesLoading(sourceId) }"))
        assertTrue(source.contains("shouldResetUnifiedNewsSourceSelection(currentSelection, remoteSources)"))
        assertTrue(source.contains("resolvedUnifiedNewsSourceSelection(currentSelection, remoteSources)"))
```

Remove these old assertions from the same test:

```kotlin
        assertTrue(source.contains("sourceArticlesByCacheKey.containsKey(cacheKey)"))
        assertTrue(source.contains("sourceArticlesByCacheKey = state.sourceArticlesByCacheKey + (cacheKey to result.value.articles)"))
        assertTrue(source.contains("sourceArticlesError = \"来源文章加载失败，请稍后重试\""))
        assertTrue(source.contains("sourceArticlesLoadingSourceId = null"))
        assertTrue(source.contains("val shouldResetSelection = currentSelection is UnifiedNewsSourceSelection.RemoteSource"))
        assertTrue(source.contains("if (shouldResetSelection) invalidateSourceArticleRequest()"))
```

- [ ] **Step 9: Run focused tests and verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsSourceArticleStateTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsLocalArticleBackTest
```

Expected: BUILD SUCCESSFUL.

---

### Task 2: Extract Unified News Detail State Helpers

**Files:**
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsDetailStateTest.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsDetailState.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`

- [ ] **Step 1: Write the failing detail-state tests**

Create `UnifiedNewsDetailStateTest.kt` with this complete content:

```kotlin
package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.remotenews.RemoteDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class UnifiedNewsDetailStateTest {
    @Test
    fun clearSourceDetailRemovesAllSelectedDetailPayloadsWithoutChangingSourceCache() {
        val article = RemoteArticle(id = 7L, title = "远程文章")
        val state = UnifiedNewsState(
            selectedRemoteDigest = RemoteDigest(id = 9L, date = "2026-05-31"),
            selectedRemoteArticle = article,
            selectedRemoteArticleLocalId = 11L,
            selectedRemoteArticleIsFavorite = true,
            sourceArticlesByCacheKey = mapOf(sourceArticleCacheKey(1L, "2026-05-31") to listOf(article)),
        )

        val cleared = state.withUnifiedNewsSourceDetailCleared()

        assertNull(cleared.selectedRemoteDigest)
        assertNull(cleared.selectedRemoteArticle)
        assertNull(cleared.selectedRemoteArticleLocalId)
        assertFalse(cleared.selectedRemoteArticleIsFavorite)
        assertEquals(state.sourceArticlesByCacheKey, cleared.sourceArticlesByCacheKey)
    }

    @Test
    fun beginDetailRequestClearsPreviousDetailAndSetsNavigationTarget() {
        val target = UnifiedNewsNavigationTarget.RemoteDigest(id = 99L)
        val state = UnifiedNewsState(
            selectedRemoteDigest = RemoteDigest(id = 9L, date = "2026-05-31"),
            error = "旧错误",
        )

        val next = state.withUnifiedNewsDetailRequestStarted(target)

        assertEquals(target, next.navigationTarget)
        assertNull(next.selectedRemoteDigest)
        assertNull(next.error)
    }

    @Test
    fun closeSourceDetailClearsNavigationAndLoading() {
        val state = UnifiedNewsState(
            navigationTarget = UnifiedNewsNavigationTarget.RemoteArticle(id = 1L, remoteSourceId = 2L),
            selectedRemoteArticle = RemoteArticle(id = 1L, title = "远程文章"),
            isLoading = true,
        )

        val closed = state.withUnifiedNewsSourceDetailClosed()

        assertNull(closed.navigationTarget)
        assertNull(closed.selectedRemoteArticle)
        assertFalse(closed.isLoading)
    }
}
```

- [ ] **Step 2: Run the focused detail test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsDetailStateTest
```

Expected: FAIL because `withUnifiedNewsSourceDetailCleared`, `withUnifiedNewsDetailRequestStarted`, and `withUnifiedNewsSourceDetailClosed` do not exist.

- [ ] **Step 3: Create the detail-state helper file**

Create `UnifiedNewsDetailState.kt` with this complete content:

```kotlin
package com.dailysatori.ui.feature.unifiednews

internal fun UnifiedNewsState.withUnifiedNewsSourceDetailCleared(): UnifiedNewsState = copy(
    selectedRemoteDigest = null,
    selectedRemoteArticle = null,
    selectedRemoteArticleLocalId = null,
    selectedRemoteArticleIsFavorite = false,
)

internal fun UnifiedNewsState.withUnifiedNewsDetailRequestStarted(
    target: UnifiedNewsNavigationTarget,
): UnifiedNewsState = withUnifiedNewsSourceDetailCleared().copy(
    navigationTarget = target,
    error = null,
)

internal fun UnifiedNewsState.withUnifiedNewsSourceDetailClosed(): UnifiedNewsState =
    withUnifiedNewsSourceDetailCleared().copy(
        navigationTarget = null,
        isLoading = false,
    )
```

- [ ] **Step 4: Replace detail-state logic in `UnifiedNewsViewModel.kt`**

Replace `closeSourceDetail()` body update:

```kotlin
        _state.update {
            it.clearSelectedSourceDetail().copy(navigationTarget = null, isLoading = false)
        }
```

With:

```kotlin
        _state.update { it.withUnifiedNewsSourceDetailClosed() }
```

Replace this update in `openSourceArticle(article: RemoteArticle)`:

```kotlin
                it.clearSelectedSourceDetail().copy(
```

With:

```kotlin
                it.withUnifiedNewsSourceDetailCleared().copy(
```

Replace this update in `beginDetailRequest(target: UnifiedNewsNavigationTarget)`:

```kotlin
        _state.update { it.clearSelectedSourceDetail().copy(navigationTarget = target, error = null) }
```

With:

```kotlin
        _state.update { it.withUnifiedNewsDetailRequestStarted(target) }
```

Delete the private extension at the bottom of `UnifiedNewsViewModel.kt`:

```kotlin
    private fun UnifiedNewsState.clearSelectedSourceDetail(): UnifiedNewsState = copy(
        selectedRemoteDigest = null,
        selectedRemoteArticle = null,
        selectedRemoteArticleLocalId = null,
        selectedRemoteArticleIsFavorite = false,
    )
```

- [ ] **Step 5: Run focused unified-news tests and verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsDetailStateTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsLocalArticleBackTest
```

Expected: BUILD SUCCESSFUL.

---

### Task 3: Extract Remote News Loading State Helpers

**Files:**
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsLoadStateTest.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsLoadState.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt`

- [ ] **Step 1: Write the failing remote-news load-state tests**

Create `RemoteNewsLoadStateTest.kt` with this complete content:

```kotlin
package com.dailysatori.ui.feature.remotenews

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteNewsLoadStateTest {
    @Test
    fun normalPageLoadShowsLoadingAndClearsErrors() {
        val state = RemoteNewsState(error = "旧错误", loadMoreError = "更多错误")

        val next = state.withRemoteNewsPageLoadStarted(refresh = false, append = false)

        assertTrue(next.isLoading)
        assertFalse(next.isRefreshing)
        assertFalse(next.isLoadingMore)
        assertNull(next.error)
        assertNull(next.loadMoreError)
    }

    @Test
    fun refreshLoadUsesRefreshingFlagOnly() {
        val next = RemoteNewsState(refreshCompletedToken = 2).withRemoteNewsPageLoadStarted(refresh = true, append = false)

        assertFalse(next.isLoading)
        assertTrue(next.isRefreshing)
        assertFalse(next.isLoadingMore)
    }

    @Test
    fun appendLoadUsesLoadingMoreFlagOnly() {
        val next = RemoteNewsState().withRemoteNewsPageLoadStarted(refresh = false, append = true)

        assertFalse(next.isLoading)
        assertFalse(next.isRefreshing)
        assertTrue(next.isLoadingMore)
    }

    @Test
    fun refreshCompletionOnlyIncrementsTokenForRefresh() {
        val state = RemoteNewsState(refreshCompletedToken = 5, isLoading = true, isRefreshing = true, isLoadingMore = true)

        val refreshed = state.withRemoteNewsPageLoadFinished(refresh = true)
        assertEquals(6, refreshed.refreshCompletedToken)
        assertFalse(refreshed.isLoading)
        assertFalse(refreshed.isRefreshing)
        assertFalse(refreshed.isLoadingMore)

        val normal = state.withRemoteNewsPageLoadFinished(refresh = false)
        assertEquals(5, normal.refreshCompletedToken)
    }

    @Test
    fun failureUsesListErrorForInitialLoadAndLoadMoreErrorForAppend() {
        val state = RemoteNewsState(error = "保留旧列表错误")

        val initialFailure = state.withRemoteNewsPageLoadFailure("加载失败", append = false)
        assertEquals("加载失败", initialFailure.error)
        assertNull(initialFailure.loadMoreError)
        assertFalse(initialFailure.isLoading)

        val appendFailure = state.withRemoteNewsPageLoadFailure("更多失败", append = true)
        assertEquals("保留旧列表错误", appendFailure.error)
        assertEquals("更多失败", appendFailure.loadMoreError)
        assertFalse(appendFailure.isLoadingMore)
    }
}
```

- [ ] **Step 2: Run the focused remote-news load-state test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.remotenews.RemoteNewsLoadStateTest
```

Expected: FAIL because `withRemoteNewsPageLoadStarted`, `withRemoteNewsPageLoadFinished`, and `withRemoteNewsPageLoadFailure` do not exist.

- [ ] **Step 3: Create `RemoteNewsLoadState.kt`**

Create the helper file with this complete content:

```kotlin
package com.dailysatori.ui.feature.remotenews

internal fun RemoteNewsState.withRemoteNewsPageLoadStarted(
    refresh: Boolean,
    append: Boolean,
): RemoteNewsState = copy(
    isLoading = !refresh && !append,
    isRefreshing = refresh,
    isLoadingMore = append,
    error = null,
    loadMoreError = null,
)

internal fun RemoteNewsState.withRemoteNewsPageLoadFinished(refresh: Boolean): RemoteNewsState = copy(
    isLoading = false,
    isRefreshing = false,
    isLoadingMore = false,
    refreshCompletedToken = if (refresh) refreshCompletedToken + 1 else refreshCompletedToken,
)

internal fun RemoteNewsState.withRemoteNewsPageLoadFailure(
    message: String,
    append: Boolean,
): RemoteNewsState = copy(
    error = if (append) error else message,
    loadMoreError = if (append) message else null,
    isLoading = false,
    isRefreshing = false,
    isLoadingMore = false,
)
```

- [ ] **Step 4: Update `RemoteNewsViewModel.loadPage()`**

Replace this update:

```kotlin
            _state.update { it.copy(isLoading = !refresh && !append, isRefreshing = refresh, isLoadingMore = append, error = null, loadMoreError = null) }
```

With:

```kotlin
            _state.update { it.withRemoteNewsPageLoadStarted(refresh, append) }
```

- [ ] **Step 5: Update success handlers in `RemoteNewsViewModel.kt`**

Replace the `RemoteNewsResult.Success` branch in `loadDigests` with this final code:

```kotlin
            is RemoteNewsResult.Success -> _state.update {
                val loaded = it.withRemoteNewsPageLoadFinished(refresh)
                loaded.copy(
                    digests = if (append) it.digests + result.value.digests else result.value.digests,
                    digestPagination = result.value.pagination,
                )
            }
```

The final `loadArticles` success branch should be:

```kotlin
            is RemoteNewsResult.Success -> _state.update {
                val loaded = it.withRemoteNewsPageLoadFinished(refresh)
                loaded.copy(
                    articles = if (append) it.articles + result.value.articles else result.value.articles,
                    articlePagination = result.value.pagination,
                )
            }
```

The final `loadFeeds` success branch should be:

```kotlin
            is RemoteNewsResult.Success -> _state.update {
                val loaded = it.withRemoteNewsPageLoadFinished(refresh)
                loaded.copy(
                    feeds = if (append) it.feeds + result.value.feeds else result.value.feeds,
                    feedPagination = result.value.pagination,
                )
            }
```

- [ ] **Step 6: Update failure helper in `RemoteNewsViewModel.kt`**

Replace `applyFailure` body:

```kotlin
        _state.update {
            it.copy(error = if (append) it.error else message, loadMoreError = if (append) message else null, isLoading = false, isRefreshing = false, isLoadingMore = false)
        }
```

With:

```kotlin
        _state.update { it.withRemoteNewsPageLoadFailure(message, append) }
```

Delete this now-unused helper:

```kotlin
    private fun RemoteNewsState.nextRefreshCompletedToken(refresh: Boolean): Int =
        if (refresh) refreshCompletedToken + 1 else refreshCompletedToken
```

- [ ] **Step 7: Run focused remote-news tests and verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.remotenews.RemoteNewsLoadStateTest --tests com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest
```

Expected: BUILD SUCCESSFUL.

---

### Task 4: Full Verification And Physical Device Install

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run all app unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run Kotlin compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Build debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install only to the physical phone**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ANDROID_SERIAL=ba5e2328 ./gradlew :app:installDebug
```

Expected: BUILD SUCCESSFUL and installation targets device `ba5e2328`, not `emulator-5554`.

- [ ] **Step 5: Launch the app only on the physical phone**

Run:

```bash
adb -s ba5e2328 shell am start -n com.dailysatori/.MainActivity
```

Expected: `Starting: Intent { cmp=com.dailysatori/.MainActivity }` or equivalent successful launch output.
