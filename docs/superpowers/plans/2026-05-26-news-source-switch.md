# News Source Switch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add source switching to the unified news page so users can browse today's articles from each enabled remote news source.

**Architecture:** Keep the feature inside `UnifiedNewsViewModel` and `UnifiedNewsScreen`. Add a selected-mode state, load enabled remote sources, cache fetched source articles by source ID, and reuse `RemoteArticleDetailScreen` for article detail and favorite behavior.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Koin ViewModel, SQLDelight repositories, Kotlin coroutines.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`: source selection state, enabled source loading, source article fetch/cache/refresh, and source-aware article detail opening.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`: source chips row, source article list content, loading/error/empty states, and article click wiring.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsLocalArticleBackTest.kt`: lightweight source checks for the new view model and UI contracts.
- No database files change.

## Task 1: Add Source Selection State And ViewModel Loading

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsLocalArticleBackTest.kt`

- [ ] **Step 1: Write the failing contract test**

Add `import kotlin.test.assertFalse`, then append this test method to `UnifiedNewsLocalArticleBackTest`:

```kotlin
@Test
fun unifiedNewsViewModelKeepsSourceArticleStateSeparateFromSummaryErrors() {
    val source = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()

    assertTrue(source.contains("sealed class UnifiedNewsSourceSelection"))
    assertTrue(source.contains("data object Summary : UnifiedNewsSourceSelection()"))
    assertTrue(source.contains("data class RemoteSource"))
    assertTrue(source.contains("data class UnifiedNewsRemoteSourceOption(val id: Long, val name: String)"))
    assertTrue(source.contains("remoteSources: List<UnifiedNewsRemoteSourceOption> = emptyList()"))
    assertFalse(source.contains("remoteSources: List<Remote_news_source> = emptyList()"))
    assertTrue(source.contains("sourceArticlesBySourceId: Map<Long, List<RemoteArticle>> = emptyMap()"))
    assertTrue(source.contains("sourceArticlesLoadingSourceId: Long? = null"))
    assertTrue(source.contains("sourceArticlesError: String? = null"))
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsLocalArticleBackTest`

Expected: FAIL because `UnifiedNewsSourceSelection` and source article state do not exist.

- [ ] **Step 3: Add the minimal state model**

Add this sealed class after `UnifiedNewsPage`:

```kotlin
sealed class UnifiedNewsSourceSelection {
    data object Summary : UnifiedNewsSourceSelection()
    data class RemoteSource(val id: Long, val name: String) : UnifiedNewsSourceSelection()
}

data class UnifiedNewsRemoteSourceOption(val id: Long, val name: String)
```

Add these properties to `UnifiedNewsState`:

```kotlin
val sourceSelection: UnifiedNewsSourceSelection = UnifiedNewsSourceSelection.Summary,
val remoteSources: List<UnifiedNewsRemoteSourceOption> = emptyList(),
val sourceArticlesBySourceId: Map<Long, List<RemoteArticle>> = emptyMap(),
val sourceArticlesLoadingSourceId: Long? = null,
val sourceArticlesError: String? = null,
```

In `loadInitial()`, before updating state from `displaySummaries`, load enabled sources and reset a deleted selected source:

```kotlin
val remoteSources = remoteNewsSourceRepo.getEnabled().map { source -> UnifiedNewsRemoteSourceOption(source.id, source.name) }
```

Then include this in the `_state.update` copy block:

```kotlin
val currentSelection = it.sourceSelection
val nextSelection = if (
    currentSelection is UnifiedNewsSourceSelection.RemoteSource &&
    remoteSources.none { source -> source.id == currentSelection.id }
) {
    UnifiedNewsSourceSelection.Summary
} else {
    currentSelection
}
```

And set these fields in the same copy:

```kotlin
sourceSelection = nextSelection,
remoteSources = remoteSources,
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsLocalArticleBackTest`

Expected: PASS.

## Task 2: Add Source Article Fetching, Cache, Refresh, And Detail Opening

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsLocalArticleBackTest.kt`

- [ ] **Step 1: Write the failing behavior contract test**

Append this test method to `UnifiedNewsLocalArticleBackTest`:

```kotlin
@Test
fun unifiedNewsSourceArticlesUseCurrentRemoteSourceAndCacheResults() {
    val source = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()

    assertTrue(source.contains("fun selectSummarySource()"))
    assertTrue(source.contains("fun selectRemoteSource(source: UnifiedNewsRemoteSourceOption)"))
    assertTrue(source.contains("fun refreshSelectedRemoteSource()"))
    assertTrue(source.contains("fun openSelectedSourceArticle(articleId: Long)"))
    assertTrue(source.contains("sourceArticlesBySourceId.containsKey(source.id)"))
    assertTrue(source.contains("remoteNewsService.fetchTopArticlesToday(config.value, page = 1, limit = 50)"))
    assertTrue(source.contains("openCitationSource(\"remote_article\", articleId, remoteNewsSourceRouteKey(selection.id))"))
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsLocalArticleBackTest`

Expected: FAIL because source article functions do not exist.

- [ ] **Step 3: Add source article actions**

In `UnifiedNewsViewModel.kt`, add this import:

```kotlin
import com.dailysatori.service.unifiednews.remoteNewsSourceRouteKey
```

Add these public methods inside `UnifiedNewsViewModel` after `switchPage`:

```kotlin
fun selectSummarySource() {
    _state.update { it.copy(sourceSelection = UnifiedNewsSourceSelection.Summary, sourceArticlesError = null) }
}

fun selectRemoteSource(source: UnifiedNewsRemoteSourceOption) {
    _state.update {
        it.copy(
            sourceSelection = UnifiedNewsSourceSelection.RemoteSource(source.id, source.name),
            sourceArticlesError = null,
        )
    }
    if (!_state.value.sourceArticlesBySourceId.containsKey(source.id)) fetchSourceArticles(source.id, force = false)
}

fun refreshSelectedRemoteSource() {
    val selection = _state.value.sourceSelection as? UnifiedNewsSourceSelection.RemoteSource ?: return
    fetchSourceArticles(selection.id, force = true)
}

fun openSelectedSourceArticle(articleId: Long) {
    val selection = _state.value.sourceSelection as? UnifiedNewsSourceSelection.RemoteSource ?: return
    openCitationSource("remote_article", articleId, remoteNewsSourceRouteKey(selection.id))
}
```

Add this private method near `openRemoteArticle`:

```kotlin
private fun fetchSourceArticles(sourceId: Long, force: Boolean) {
    if (!force && _state.value.sourceArticlesBySourceId.containsKey(sourceId)) return
    viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(sourceArticlesLoadingSourceId = sourceId, sourceArticlesError = null) }
        val source = remoteNewsSourceRepo.getById(sourceId)
        if (source == null) {
            _state.update { state ->
                state.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = "远程新闻源不存在或已删除")
            }
            return@launch
        }
        when (val config = remoteNewsService.configOrFailure(source.base_url, source.api_token)) {
            is RemoteNewsResult.Success -> when (val result = remoteNewsService.fetchTopArticlesToday(config.value, page = 1, limit = 50)) {
                is RemoteNewsResult.Success -> _state.update { state ->
                    state.copy(
                        sourceArticlesBySourceId = state.sourceArticlesBySourceId + (sourceId to result.value.articles),
                        sourceArticlesLoadingSourceId = null,
                        sourceArticlesError = null,
                    )
                }
                is RemoteNewsResult.Failure -> _state.update { state ->
                    state.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = result.message)
                }
            }
            is RemoteNewsResult.Failure -> _state.update { state ->
                state.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = config.message)
            }
        }
    }
}
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsLocalArticleBackTest`

Expected: PASS.

## Task 3: Add Source Switch Chips And Source Article List UI

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsLocalArticleBackTest.kt`

- [ ] **Step 1: Write the failing UI contract test**

Append this test method to `UnifiedNewsLocalArticleBackTest`:

```kotlin
@Test
fun unifiedNewsScreenRendersSourceSwitcherAndSourceArticleStates() {
    val source = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

    assertTrue(source.contains("UnifiedNewsSourceSwitcher("))
    assertTrue(source.contains("UnifiedNewsSourceArticleContent("))
    assertTrue(source.contains("FilterChip("))
    assertTrue(source.contains("Text(\"汇总\")"))
    assertTrue(source.contains("sourceArticlesLoadingSourceId == selection.id"))
    assertTrue(source.contains("这个来源今天还没有新闻"))
    assertTrue(source.contains("viewModel.openSelectedSourceArticle(article.id)"))
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsLocalArticleBackTest`

Expected: FAIL because the source switcher UI does not exist.

- [ ] **Step 3: Add imports for the UI**

In `UnifiedNewsScreen.kt`, add these imports:

```kotlin
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.ui.feature.remotenews.RemoteArticleSummaryCard
```

- [ ] **Step 4: Wire the switcher into the summary page**

In `UnifiedNewsSummaryPage`, inside the top-level `Column`, add the switcher before the regenerating skeleton:

```kotlin
UnifiedNewsSourceSwitcher(state = state, viewModel = viewModel)
```

Replace the existing `Box(modifier = Modifier.fillMaxWidth().weight(1f)) { ... }` content selection with:

```kotlin
Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
    when (val selection = state.sourceSelection) {
        UnifiedNewsSourceSelection.Summary -> UnifiedNewsSummaryContent(state, viewModel)
        is UnifiedNewsSourceSelection.RemoteSource -> UnifiedNewsSourceArticleContent(state, selection, viewModel)
    }
}
```

Move the old summary `when { state.isLoading ... }` block into a new composable:

```kotlin
@Composable
private fun UnifiedNewsSummaryContent(state: UnifiedNewsState, viewModel: UnifiedNewsViewModel) {
    val visibleSummaries = if (state.isRegenerating) {
        state.summaries.filter { summary -> summary.summary_date != state.regeneratingSummaryDate }
    } else {
        state.summaries
    }
    when {
        state.isLoading -> LoadingIndicator()
        visibleSummaries.isEmpty() -> EmptyState(
            icon = Icons.AutoMirrored.Filled.Article,
            title = "暂无新闻汇总",
            subtitle = "点击右上角生成/更新当日新闻",
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            items(visibleSummaries, key = { it.id }) { summary ->
                TodayUnifiedNewsCard(
                    summary = summary,
                    sources = state.sourcesBySummaryId[summary.id].orEmpty(),
                    onCitationClick = viewModel::openCitation,
                )
            }
        }
    }
}
```

- [ ] **Step 5: Add source switcher and source article content composables**

Add these composables before `UnifiedNewsRefreshMessage`:

```kotlin
@Composable
private fun UnifiedNewsSourceSwitcher(state: UnifiedNewsState, viewModel: UnifiedNewsViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        FilterChip(
            selected = state.sourceSelection is UnifiedNewsSourceSelection.Summary,
            onClick = viewModel::selectSummarySource,
            label = { Text("汇总") },
        )
        state.remoteSources.forEach { source ->
            FilterChip(
                selected = (state.sourceSelection as? UnifiedNewsSourceSelection.RemoteSource)?.id == source.id,
                onClick = { viewModel.selectRemoteSource(source) },
                label = { Text(source.name) },
            )
        }
    }
}

@Composable
private fun UnifiedNewsSourceArticleContent(
    state: UnifiedNewsState,
    selection: UnifiedNewsSourceSelection.RemoteSource,
    viewModel: UnifiedNewsViewModel,
) {
    val articles = state.sourceArticlesBySourceId[selection.id].orEmpty()
    val isLoading = state.sourceArticlesLoadingSourceId == selection.id
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
        else -> UnifiedNewsSourceArticleList(selection.name, articles, isLoading, viewModel)
    }
}

@Composable
private fun UnifiedNewsSourceArticleList(
    sourceName: String,
    articles: List<RemoteArticle>,
    isLoading: Boolean,
    viewModel: UnifiedNewsViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text("$sourceName · 今日文章", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("共 ${articles.size} 篇", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(articles, key = { it.id }) { article ->
            RemoteArticleSummaryCard(article) { viewModel.openSelectedSourceArticle(article.id) }
        }
        if (isLoading) item {
            Box(modifier = Modifier.fillMaxWidth().padding(Spacing.s), contentAlignment = Alignment.Center) {
                Text("刷新中...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UnifiedNewsSourceArticleMessage(title: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}
```

- [ ] **Step 6: Run the focused test and verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsLocalArticleBackTest`

Expected: PASS.

## Task 4: Compile And Manual Verification

**Files:**
- Modify only if compile errors identify necessary fixes in `UnifiedNewsViewModel.kt` or `UnifiedNewsScreen.kt`.

- [ ] **Step 1: Run app compile**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run debug unit tests for touched behavior**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsLocalArticleBackTest`

Expected: BUILD SUCCESSFUL and all tests pass.

- [ ] **Step 3: Install and launch if a device is connected**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: BUILD SUCCESSFUL if an Android device/emulator is connected. If no device is connected, record the Gradle/ADB error.

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: app launches if a device/emulator is connected.

- [ ] **Step 4: Manual QA checklist**

Verify these flows in the app:

```text
1. Open 新闻汇总: 汇总 Chip is selected and existing summary cards still render.
2. Tap an enabled remote source Chip: lower content shows 来源名 · 今日文章.
3. Tap another source and back: previously loaded source articles appear without an immediate duplicate loading screen.
4. Tap a source article: RemoteArticleDetailScreen opens.
5. In article detail, favorite toggle works using the existing action.
6. Return to 汇总: summary error/message state is not polluted by source article errors.
```

## Self-Review

- Spec coverage: the plan adds source chips, source article list, independent loading/error state, source-aware article detail opening, no database changes, and compile/manual verification.
- Placeholder scan: no `TBD`, `TODO`, or unspecified implementation steps remain.
- Type consistency: `UnifiedNewsSourceSelection`, `sourceArticlesBySourceId`, `sourceArticlesLoadingSourceId`, `sourceArticlesError`, and `openSelectedSourceArticle` names are consistent across tests, view model, and UI.
- Developer constraint: do not run `git commit` unless the user explicitly asks for a commit.
