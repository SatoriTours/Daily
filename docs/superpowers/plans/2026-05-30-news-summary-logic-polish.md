# News Summary Logic Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the news summary page refresh, source loading, detail opening, and failure handling predictable without redesigning the UI.

**Architecture:** Keep the existing `UnifiedNewsViewModel` state shape and Compose screens, adding only small guards and fallback helpers. The work is split into remote-source refresh correctness, detail-opening resilience, readable article fallbacks, scoped messages, and verification.

**Tech Stack:** Kotlin, Android Jetpack Compose, Koin ViewModels, Kotlin coroutines, existing source-level Kotlin unit tests, Gradle Android plugin.

---

## File Structure

- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
  - Owns contextual refresh routing, remote-source article requests, citation/detail opening, and favorite state lookup for news-summary flows.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
  - Owns the small UI feedback for source loading/errors and refresh action wiring. Keep layout unchanged.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`
  - Owns readable fallback text for remote article details opened from summary remote sources or digests.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt`
  - Apply the same favorite-lookup resilience used by summary detail opening because `RemoteArticleDetailScreen` is shared.
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
  - Add source-level regression tests for summary refresh routing, remote-source duplicate/stale guards, scoped errors, and summary detail opening behavior.
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsUiBehaviorTest.kt`
  - Add source-level regression tests for shared remote article detail fallbacks and favorite lookup resilience.

## Task 1: Contextual Refresh Guards For Remote Sources

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`

- [ ] **Step 1: Write the failing refresh routing and duplicate guard tests**

Add these tests near the existing unified remote-source tests in `UnifiedNewsBehaviorTest`:

```kotlin
@Test
fun unifiedNewsRefreshSelectedSourceRoutesByCurrentSelectionOnly() {
    val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
    val refreshBody = viewModel.substringAfter("fun refreshSelectedSource()").substringBefore("private fun incrementLocalArticleRefreshRequest")

    assertTrue(refreshBody.contains("UnifiedNewsSourceSelection.Summary -> regenerateCurrentWindow()"))
    assertTrue(refreshBody.contains("is UnifiedNewsSourceSelection.RemoteSource -> refreshSelectedRemoteSource()"))
    assertTrue(refreshBody.contains("UnifiedNewsSourceSelection.LocalArticles -> incrementLocalArticleRefreshRequest()"))
    assertFalse(refreshBody.contains("generateDaily"))
    assertFalse(refreshBody.contains("fetchTopArticlesToday"))
}

@Test
fun unifiedRemoteSourceRefreshIgnoresDuplicateRequestForSameSource() {
    val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
    val fetchBody = viewModel.substringAfter("private fun fetchSourceArticles(sourceId: Long, force: Boolean)").substringBefore("private fun parseRemoteNewsSourceRouteKey")

    assertTrue(fetchBody.contains("sourceArticlesLoadingSourceId == sourceId"))
    assertTrue(fetchBody.contains("return"))
    assertTrue(fetchBody.contains("fetchTopArticlesToday"))
}

@Test
fun unifiedRemoteSourceSwitchInvalidatesLateSourceResponses() {
    val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
    val selectBody = viewModel.substringAfter("fun selectRemoteSource").substringBefore("fun selectLocalArticlesSource")
    val requestHelpers = viewModel.substringAfter("private fun ifLatestSourceArticleRequest").substringBefore("private fun UnifiedNewsState.clearSelectedSourceDetail")

    assertTrue(selectBody.contains("invalidateSourceArticleRequest()"))
    assertTrue(requestHelpers.contains("sourceArticleRequestToken.incrementAndGet()"))
    assertTrue(requestHelpers.contains("if (token == sourceArticleRequestToken.get())"))
}
```

- [ ] **Step 2: Run the targeted tests and confirm failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsRefreshSelectedSourceRoutesByCurrentSelectionOnly" --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedRemoteSourceRefreshIgnoresDuplicateRequestForSameSource" --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedRemoteSourceSwitchInvalidatesLateSourceResponses"
```

Expected: at least `unifiedRemoteSourceRefreshIgnoresDuplicateRequestForSameSource` fails because `fetchSourceArticles` does not yet guard `sourceArticlesLoadingSourceId == sourceId`.

- [ ] **Step 3: Add the minimal duplicate request guard**

In `UnifiedNewsViewModel.kt`, update the start of `fetchSourceArticles` to this exact form:

```kotlin
private fun fetchSourceArticles(sourceId: Long, force: Boolean) {
    val cacheKey = sourceArticleCacheKey(sourceId, dailyUnifiedNewsWindowFor().summaryDate)
    val current = _state.value
    if (current.sourceArticlesLoadingSourceId == sourceId) return
    if (!force && current.sourceArticlesByCacheKey.containsKey(cacheKey)) return
    val token = beginSourceArticleRequest(sourceId)
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val source = remoteNewsSourceRepo.getById(sourceId)
```

Leave the rest of the function body unchanged.

- [ ] **Step 4: Run the targeted tests and confirm pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsRefreshSelectedSourceRoutesByCurrentSelectionOnly" --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedRemoteSourceRefreshIgnoresDuplicateRequestForSameSource" --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedRemoteSourceSwitchInvalidatesLateSourceResponses"
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt
git commit -m "fix: guard duplicate source refreshes"
```

## Task 2: Scoped Remote-Source Refresh Errors

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`

- [ ] **Step 1: Write failing scoped error tests**

Add these tests to `UnifiedNewsBehaviorTest`:

```kotlin
@Test
fun unifiedRemoteSourceRefreshFailureUsesScopedSourceError() {
    val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
    val fetchBody = viewModel.substringAfter("private fun fetchSourceArticles(sourceId: Long, force: Boolean)").substringBefore("private fun parseRemoteNewsSourceRouteKey")

    assertTrue(fetchBody.contains("sourceArticlesError = result.message"))
    assertTrue(fetchBody.contains("sourceArticlesError = config.message"))
    assertTrue(fetchBody.contains("sourceArticlesError = \"来源文章加载失败，请稍后重试\""))
    assertFalse(fetchBody.contains("error = result.message"))
    assertFalse(fetchBody.contains("error = config.message"))
}

@Test
fun unifiedRemoteSourceRefreshFailureKeepsCachedArticlesVisible() {
    val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
    val contentBody = screen.substringAfter("private fun UnifiedNewsSourceArticleContent").substringBefore("private fun UnifiedNewsSourceArticleList")
    val listBody = screen.substringAfter("private fun UnifiedNewsSourceArticleList").substringBefore("private fun UnifiedNewsSourceArticleMessage")

    assertTrue(contentBody.contains("state.sourceArticlesError != null && articles.isEmpty()"))
    assertTrue(listBody.contains("刷新失败，正在显示上次结果"))
    assertTrue(listBody.contains("sourceArticlesError"))
}
```

- [ ] **Step 2: Run the targeted tests and confirm current behavior**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedRemoteSourceRefreshFailureUsesScopedSourceError" --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedRemoteSourceRefreshFailureKeepsCachedArticlesVisible"
```

Expected: PASS if the current implementation already scopes source errors. If either test fails, continue to Step 3.

- [ ] **Step 3: Ensure source errors stay scoped in ViewModel**

In `UnifiedNewsViewModel.kt`, the failure branches inside `fetchSourceArticles` must use this exact pattern:

```kotlin
is RemoteNewsResult.Failure -> ifLatestSourceArticleRequest(token) { state ->
    state.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = result.message)
}
```

For config failure, use:

```kotlin
is RemoteNewsResult.Failure -> ifLatestSourceArticleRequest(token) { state ->
    state.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = config.message)
}
```

For unexpected exceptions, use:

```kotlin
ifLatestSourceArticleRequest(token) { state ->
    state.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = "来源文章加载失败，请稍后重试")
}
```

- [ ] **Step 4: Ensure cached source articles remain visible with non-blocking error UI**

In `UnifiedNewsScreen.kt`, keep this structure in `UnifiedNewsSourceArticleContent`:

```kotlin
when {
    isLoading && articles.isEmpty() -> LoadingIndicator()
    state.sourceArticlesError != null && articles.isEmpty() -> UnifiedNewsSourceArticleMessage(
        title = state.sourceArticlesError,
        actionLabel = "刷新",
        onAction = viewModel::refreshSelectedRemoteSource,
    )
    articles.isEmpty() -> UnifiedNewsSourceArticleMessage(
        title = "这个来源今天还没有新闻",
        actionLabel = "刷新",
        onAction = viewModel::refreshSelectedRemoteSource,
    )
    else -> UnifiedNewsSourceArticleList(
        selection = selection,
        articles = articles,
        isLoading = isLoading,
        sourceArticlesError = state.sourceArticlesError,
        viewModel = viewModel,
    )
}
```

Keep the non-blocking list warning in `UnifiedNewsSourceArticleList`:

```kotlin
if (sourceArticlesError != null) item {
    Surface(shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            text = "刷新失败，正在显示上次结果：$sourceArticlesError",
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 5: Run the targeted tests and confirm pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedRemoteSourceRefreshFailureUsesScopedSourceError" --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedRemoteSourceRefreshFailureKeepsCachedArticlesVisible"
```

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt
git commit -m "fix: keep source refresh errors scoped"
```

## Task 3: Resilient Remote Article Detail Opening

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsUiBehaviorTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt`

- [ ] **Step 1: Write failing favorite lookup resilience tests**

Add this test to `UnifiedNewsBehaviorTest`:

```kotlin
@Test
fun unifiedSourceArticleOpensEvenWhenFavoriteLookupFails() {
    val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
    val openBody = viewModel.substringAfter("fun openSourceArticle(article: RemoteArticle)").substringBefore("fun toggleSelectedRemoteArticleFavorite")

    assertTrue(openBody.contains("selectedRemoteArticle = article"))
    assertTrue(openBody.contains("runCatching { articleRepo.findLocalArticleForRemote(article) }.getOrNull()"))
    assertTrue(openBody.contains("selectedRemoteArticleIsFavorite = local?.is_favorite == 1L"))
    assertFalse(openBody.contains("catch (_: Exception)"))
}
```

Add this test to `RemoteNewsUiBehaviorTest`:

```kotlin
@Test
fun remoteArticleOpensEvenWhenFavoriteLookupFails() {
    val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt").readText()
    val openBody = viewModel.substringAfter("fun openArticle(article: RemoteArticle)").substringBefore("fun toggleSelectedArticleFavorite")

    assertTrue(openBody.contains("selectedArticle = article"))
    assertTrue(openBody.contains("runCatching { articleRepo.findLocalArticleForRemote(article) }.getOrNull()"))
    assertTrue(openBody.contains("selectedArticleIsFavorite = local?.is_favorite == 1L"))
}
```

- [ ] **Step 2: Run the targeted tests and confirm failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedSourceArticleOpensEvenWhenFavoriteLookupFails" --tests "com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest.remoteArticleOpensEvenWhenFavoriteLookupFails"
```

Expected: FAIL because both ViewModels currently call `articleRepo.findLocalArticleForRemote(article)` directly.

- [ ] **Step 3: Make summary remote-source article opening resilient**

Replace `openSourceArticle(article: RemoteArticle)` in `UnifiedNewsViewModel.kt` with:

```kotlin
fun openSourceArticle(article: RemoteArticle) {
    viewModelScope.launch(Dispatchers.IO) {
        _state.update {
            it.clearSelectedSourceDetail().copy(
                selectedRemoteArticle = article,
                selectedRemoteArticleLocalId = null,
                selectedRemoteArticleIsFavorite = false,
                isLoading = true,
                error = null,
            )
        }
        val local = runCatching { articleRepo.findLocalArticleForRemote(article) }.getOrNull()
        _state.update { state ->
            if (state.selectedRemoteArticle?.id == article.id) {
                state.copy(
                    selectedRemoteArticleLocalId = local?.id,
                    selectedRemoteArticleIsFavorite = local?.is_favorite == 1L,
                    isLoading = false,
                )
            } else {
                state
            }
        }
    }
}
```

- [ ] **Step 4: Make standalone remote article opening resilient because it shares the detail screen**

Replace `openArticle(article: RemoteArticle)` in `RemoteNewsViewModel.kt` with:

```kotlin
fun openArticle(article: RemoteArticle) {
    viewModelScope.launch(Dispatchers.IO) {
        _state.update {
            it.copy(
                selectedArticle = article,
                selectedArticleLocalId = null,
                selectedArticleIsFavorite = false,
                isLoading = true,
                detailError = null,
            )
        }
        val local = runCatching { articleRepo.findLocalArticleForRemote(article) }.getOrNull()
        _state.update { state ->
            if (state.selectedArticle?.id == article.id) {
                state.copy(
                    selectedArticleLocalId = local?.id,
                    selectedArticleIsFavorite = local?.is_favorite == 1L,
                    isLoading = false,
                )
            } else {
                state
            }
        }
    }
}
```

- [ ] **Step 5: Run targeted tests and confirm pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedSourceArticleOpensEvenWhenFavoriteLookupFails" --tests "com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest.remoteArticleOpensEvenWhenFavoriteLookupFails"
```

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsUiBehaviorTest.kt app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt
git commit -m "fix: keep remote article detail readable on lookup failure"
```

## Task 4: Readable Remote Article Detail Fallbacks

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsUiBehaviorTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`

- [ ] **Step 1: Write failing fallback tests**

Add these tests to `RemoteNewsUiBehaviorTest`:

```kotlin
@Test
fun remoteArticleDetailUsesReadableTitleFallback() {
    val source = File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt").readText()

    assertTrue(source.contains("private fun remoteArticleDisplayTitle(article: RemoteArticle): String"))
    assertTrue(source.contains("remoteArticleDisplayTitle(article)"))
    assertTrue(source.contains("article.title?.trim()?.takeIf { it.isNotBlank() }"))
    assertTrue(source.contains("article.summary?.trim()?.takeIf { it.isNotBlank() }?.take(48)"))
    assertTrue(source.contains("\"未命名远程文章\""))
}

@Test
fun remoteArticleDetailShowsExplicitOriginalFallback() {
    assertEquals(
        "暂无原文内容，请刷新当前来源后重试。",
        remoteArticleDetailPageContent(page = 1, summary = null, viewpoints = emptyList(), original = null),
    )
}

@Test
fun remoteArticleDetailShowsExplicitSummaryFallback() {
    assertEquals(
        "暂无摘要内容，请刷新当前来源后重试。",
        remoteArticleDetailPageContent(page = 0, summary = null, viewpoints = emptyList(), original = null),
    )
}
```

- [ ] **Step 2: Run the targeted tests and confirm failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest.remoteArticleDetailUsesReadableTitleFallback" --tests "com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest.remoteArticleDetailShowsExplicitOriginalFallback" --tests "com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest.remoteArticleDetailShowsExplicitSummaryFallback"
```

Expected: FAIL because the title helper does not exist and current fallbacks are shorter generic text.

- [ ] **Step 3: Use display title fallback in the detail screen**

In `RemoteArticleDetailScreen.kt`, change the app bar and header calls:

```kotlin
AppScaffold(
    title = article.domain ?: article.feedName ?: "文章",
    onBack = onBack,
    actions = { RemoteArticleDetailActions(article, isFavorite, showFavoriteAction, onFavoriteClick) },
) { modifier ->
```

Keep the app bar source title as-is, but replace the header title call in `RemoteArticleDetailPage` with:

```kotlin
MagazineArticleHeader(remoteArticleDisplayTitle(article), remoteArticleMetaChips(article), intro = null)
```

Add this helper near `remoteArticleMetaChips`:

```kotlin
private fun remoteArticleDisplayTitle(article: RemoteArticle): String = listOfNotNull(
    article.title?.trim()?.takeIf { it.isNotBlank() },
    article.summary?.trim()?.takeIf { it.isNotBlank() }?.take(48),
    article.feedName?.trim()?.takeIf { it.isNotBlank() },
    article.domain?.trim()?.takeIf { it.isNotBlank() },
).firstOrNull() ?: "未命名远程文章"
```

- [ ] **Step 4: Make summary and original page fallbacks explicit**

In `RemoteArticleDetailScreen.kt`, replace fallback strings in the page content helpers:

```kotlin
private fun remoteArticleSummaryPageContent(summary: String?, viewpoints: List<String>): String {
    val summaryContent = summary?.trim()?.takeIf { it.isNotBlank() }
    val viewpointContent = viewpoints
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n") { "- $it" }

    return listOfNotNull(
        summaryContent,
        viewpointContent.takeIf { it.isNotBlank() }?.let { "## 关键观点\n\n$it" },
    ).joinToString(separator = "\n\n").ifBlank { "暂无摘要内容，请刷新当前来源后重试。" }
}

private fun remoteArticleOriginalPageContent(original: String?, imageUrls: List<String>): String {
    val content = original?.trim()?.takeIf { it.isNotBlank() } ?: "暂无原文内容，请刷新当前来源后重试。"
    if (content == "暂无原文内容，请刷新当前来源后重试。" || imageUrls.isEmpty() || !content.hasRemoteImagePlaceholder()) return content
    return normalizeArticleMarkdownImages(content, imageUrls)
}
```

- [ ] **Step 5: Run the targeted tests and confirm pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest.remoteArticleDetailUsesReadableTitleFallback" --tests "com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest.remoteArticleDetailShowsExplicitOriginalFallback" --tests "com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest.remoteArticleDetailShowsExplicitSummaryFallback"
```

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsUiBehaviorTest.kt app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt
git commit -m "fix: add remote article detail fallbacks"
```

## Task 5: Summary Generation Failure State Check

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`

- [ ] **Step 1: Write the summary generation state regression test**

Add this test to `UnifiedNewsBehaviorTest`:

```kotlin
@Test
fun unifiedSummaryRegenerationAlwaysStopsGeneratingState() {
    val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
    val regenerateBody = viewModel.substringAfter("fun regenerateCurrentWindow()").substringBefore("private fun manualRefreshMessage")

    assertTrue(regenerateBody.contains("isRegenerating = true"))
    assertTrue(regenerateBody.contains("isRegenerating = false"))
    assertTrue(regenerateBody.contains("regeneratingSummaryDate = null"))
    assertTrue(regenerateBody.contains("manualRefreshMessage = manualRefreshMessage(result)"))
    assertTrue(regenerateBody.contains("error = result.message?.takeIf { !result.success }"))
    assertTrue(regenerateBody.contains("catch (e: CancellationException)"))
    assertTrue(regenerateBody.contains("throw e"))
    assertTrue(regenerateBody.contains("新闻汇总重新生成失败，请稍后重试"))
}
```

- [ ] **Step 2: Run the targeted test and confirm current behavior**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedSummaryRegenerationAlwaysStopsGeneratingState"
```

Expected: PASS if the current implementation already satisfies the failure-state guarantee. If it fails, continue to Step 3.

- [ ] **Step 3: Ensure summary generation clears loading state on result and exception**

In `UnifiedNewsViewModel.kt`, keep `regenerateCurrentWindow()` structured like this:

```kotlin
fun regenerateCurrentWindow() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val today = dailyUnifiedNewsWindowFor()
            _state.update {
                it.copy(
                    isRegenerating = true,
                    regeneratingSummaryDate = today.summaryDate,
                    manualRefreshMessage = null,
                    error = null,
                    page = UnifiedNewsPage.SUMMARY,
                )
            }
            val result = summaryService.generateDaily(
                force = true,
                ignoreSourceTimeFilter = isDebugBuild,
            )
            _state.update {
                it.copy(
                    isRegenerating = false,
                    regeneratingSummaryDate = null,
                    summaryRefreshCompletedToken = it.summaryRefreshCompletedToken + 1,
                    manualRefreshMessage = manualRefreshMessage(result),
                    error = result.message?.takeIf { !result.success },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(isRegenerating = false, regeneratingSummaryDate = null, error = "新闻汇总重新生成失败，请稍后重试") }
        }
    }
}
```

- [ ] **Step 4: Run the targeted test and confirm pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.UnifiedNewsBehaviorTest.unifiedSummaryRegenerationAlwaysStopsGeneratingState"
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt
git commit -m "test: cover summary refresh failure state"
```

## Task 6: Final Verification And Device Smoke

**Files:**
- No source files expected unless verification finds a defect.

- [ ] **Step 1: Run all targeted news behavior tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.UnifiedNewsBehaviorTest" --tests "com.dailysatori.ui.feature.remotenews.RemoteNewsUiBehaviorTest"
```

Expected: PASS.

- [ ] **Step 2: Run required compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install debug build to connected device**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

Expected: `BUILD SUCCESSFUL` and install completes.

- [ ] **Step 4: Launch the app**

Run:

```bash
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: Activity starts without command failure.

- [ ] **Step 5: Manual smoke checklist**

On the device:

- Open `新闻汇总`.
- On `汇总`, tap refresh and confirm it shows generation feedback and returns to readable content or a clear empty/failure message.
- Select a remote source and tap refresh twice quickly; confirm the list does not flicker into a broken state.
- While a remote source is loading, switch to another remote source; confirm late results do not replace the newly selected source.
- Open a remote source article; confirm the detail page shows title, summary/original fallback, and favorite button state.
- Open a key point citation; confirm it navigates to local detail or shows a clear unavailable-content error.

- [ ] **Step 6: Inspect git diff before completion**

Run:

```bash
git status --short
git diff --stat
git diff -- app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsUiBehaviorTest.kt
```

Expected: only intended news-summary logic polish files are changed. Existing unrelated dirty files, if any, must not be reverted or staged.

- [ ] **Step 7: Commit verification-only fixes if needed**

If Step 1 through Step 4 required any code fixes, commit only those intended files:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsViewModel.kt app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsUiBehaviorTest.kt
git commit -m "fix: polish news summary logic"
```

Expected: commit succeeds. If no fixes were needed after prior task commits, skip this step.

## Plan Self-Review

Spec coverage:

- Contextual refresh routing is covered by Task 1.
- Duplicate and stale remote-source requests are covered by Task 1.
- Scoped source errors and cached-list behavior are covered by Task 2.
- Detail opening and favorite lookup resilience are covered by Task 3.
- Missing content fallback is covered by Task 4.
- Summary generation failure state is covered by Task 5.
- Required compile, install, launch, and smoke verification are covered by Task 6.

Forbidden-marker scan:

- No incomplete-marker text, vague edge-case instructions, or references to missing functions remain.

Type consistency:

- Plan uses existing `UnifiedNewsState`, `UnifiedNewsSourceSelection`, `RemoteArticle`, `sourceArticlesLoadingSourceId`, `sourceArticlesError`, `selectedRemoteArticle`, `selectedArticle`, and `remoteArticleDetailPageContent` names.
