# Daily Unified News Single Summary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert unified news into one phone-local-date summary that merges remote digest articles, crayfish articles, and local favorites, then overwrites the same daily summary on regeneration.

**Architecture:** Keep the existing SQLDelight tables and reuse `window_key = "daily"` as the canonical row key for today's summary. Add focused daily-window helpers, change source collection to article-level remote digest articles only, and update the UI to show one daily summary with obvious tappable source items. Preserve existing detail-routing code where possible.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Jetpack Compose, Koin ViewModel, WorkManager, kotlin.test/JUnit via Gradle.

---

## File Structure

- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsWindow.kt`: add daily key/date-window helpers while keeping existing scheduled-window helpers compilable.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`: generate the daily row, collect remote digest articles, collect crayfish local-date articles, collect local favorites, and save via the existing repository.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/data/repository/UnifiedNewsSummaryRepository.kt`: add daily lookup helpers if needed, but keep save semantics based on `(summary_date, window_key)`.
- Modify `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`: add query helpers for latest daily summary and daily successful summary if needed; do not add a schema migration unless table shape changes.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`: load today's daily summary, regenerate today's daily row, and keep detail routing.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`: replace history-card timeline with a single daily summary layout and obvious source item cards.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsContentFormat.kt`: add source label helpers and keep citation parsing for internal routing.
- Modify `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`: add regression tests for daily overwrite, source collection semantics, UI source cards, and daily date behavior.

---

### Task 1: Add Canonical Daily Window Helpers

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsWindow.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write the failing test**

Add this import if missing:

```kotlin
import com.dailysatori.service.unifiednews.dailyUnifiedNewsWindowFor
```

Add this test to `UnifiedNewsBehaviorTest`:

```kotlin
@Test
fun dailyUnifiedNewsWindowUsesPhoneLocalDateAndDailyKey() {
    val window = dailyUnifiedNewsWindowFor(
        now = Instant.parse("2026-05-16T18:30:00Z"),
        timeZone = TimeZone.of("Asia/Shanghai"),
    )

    assertEquals("2026-05-17", window.summaryDate)
    assertEquals("daily", window.key.value)
    assertEquals(Instant.parse("2026-05-16T16:00:00Z").toEpochMilliseconds(), window.startMs)
    assertEquals(Instant.parse("2026-05-17T15:59:59.999Z").toEpochMilliseconds(), window.endMs)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.dailyUnifiedNewsWindowUsesPhoneLocalDateAndDailyKey --no-configuration-cache
```

Expected: compile failure because `dailyUnifiedNewsWindowFor` is unresolved or `daily` key is unsupported.

- [ ] **Step 3: Write minimal implementation**

In `UnifiedNewsWindow.kt`, add a daily enum entry and helper. Keep existing entries unchanged.

```kotlin
enum class UnifiedNewsWindowKey(val value: String) {
    DAILY("daily"),
    W0800("0800"),
    W1330("1330"),
    W1800("1800"),
    W2100("2100"),
    FINAL("final"),
}

fun dailyUnifiedNewsWindowFor(
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): UnifiedNewsWindow {
    val date = now.toLocalDateTime(timeZone).date
    val start = date.atStartOfDayIn(timeZone)
    val end = date.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone).minus(1.milliseconds)
    return UnifiedNewsWindow(
        key = UnifiedNewsWindowKey.DAILY,
        summaryDate = date.toString(),
        startMs = start.toEpochMilliseconds(),
        endMs = end.toEpochMilliseconds(),
    )
}
```

Ensure the file imports these symbols:

```kotlin
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.dailyUnifiedNewsWindowUsesPhoneLocalDateAndDailyKey --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 2: Make Remote Collection Use Digest Articles Only

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test:

```kotlin
@Test
fun unifiedNewsCollectsRemoteDigestArticlesNotRemoteDigestSources() {
    val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

    assertTrue(source.contains("collectRemoteDigestArticles"))
    assertFalse(source.contains("collectRemoteArticles(window"))
    assertFalse(source.contains("REMOTE_DIGEST"))
    assertTrue(source.contains("digest.articles.mapNotNull"))
    assertTrue(source.contains("UnifiedNewsSourceType.REMOTE_ARTICLE"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsCollectsRemoteDigestArticlesNotRemoteDigestSources --no-configuration-cache
```

Expected: failure because current service still has digest sources and direct remote article paging.

- [ ] **Step 3: Write minimal implementation**

In `UnifiedNewsSummaryService.kt`:

Replace source collection calls:

```kotlin
addSources(sources, refCounts, collectRemoteDigestArticles(window, warnings, ignoreSourceTimeFilter))
addSources(sources, refCounts, collectCrayfishNews(window, warnings, ignoreSourceTimeFilter))
addSources(sources, refCounts, collectLocalFavorites(window))
```

Delete the `collectRemoteArticles` function.

Rename `collectRemoteDigests` to `collectRemoteDigestArticles` and change the success branch to article-level sources:

```kotlin
private suspend fun collectRemoteDigestArticles(
    window: UnifiedNewsWindow,
    warnings: MutableList<String>,
    ignoreSourceTimeFilter: Boolean,
): List<UnifiedNewsSourceItem> {
    val baseUrl = settingRepo.get(SettingKeys.remoteNewsBaseUrl)
    val token = settingRepo.get(SettingKeys.remoteNewsApiToken)
    if (baseUrl.isNullOrBlank() || token.isNullOrBlank()) return emptyList()
    val config = when (val result = remoteNewsService.configOrFailure(baseUrl, token)) {
        is RemoteNewsResult.Success -> result.value
        is RemoteNewsResult.Failure -> return warnAndEmpty(warnings, result.message)
    }
    val articles = mutableListOf<UnifiedNewsSourceItem>()
    var page: Int? = 1
    repeat(MAX_REMOTE_DIGEST_PAGES) {
        val currentPage = page ?: return articles
        when (val result = remoteNewsService.fetchDigests(config, currentPage, RemoteNewsConfig.digestsPageSize)) {
            is RemoteNewsResult.Success -> {
                result.value.digests.forEach { digest ->
                    articles += digest.articles.mapNotNull { it.toUnifiedSource(window, ignoreSourceTimeFilter) }
                }
                page = result.value.pagination.next
            }
            is RemoteNewsResult.Failure -> {
                warnSourceFailure(warnings, result.message)
                return articles
            }
        }
    }
    return articles.distinctBy { it.sourceId ?: it.sourceUrl ?: it.title }
}
```

Ensure the `RemoteDigest.toUnifiedSource` mapper is removed or left unused only if removing it creates too much churn. Remove `MAX_REMOTE_ARTICLE_PAGES` if it becomes unused.

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsCollectsRemoteDigestArticlesNotRemoteDigestSources --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 3: Generate And Regenerate The Daily Row

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test:

```kotlin
@Test
fun unifiedNewsRegenerationUsesDailyWindowAndOverwritesToday() {
    val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
    val service = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

    assertTrue(viewModel.contains("dailyUnifiedNewsWindowFor"))
    assertTrue(viewModel.contains("summaryService.generateDaily"))
    assertFalse(viewModel.contains("manualRefreshWindowForEnvironment(currentWindow"))
    assertTrue(service.contains("suspend fun generateDaily"))
    assertTrue(service.contains("dailyUnifiedNewsWindowFor"))
    assertTrue(service.contains("window_key = \"daily\"") || service.contains("UnifiedNewsWindowKey.DAILY"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsRegenerationUsesDailyWindowAndOverwritesToday --no-configuration-cache
```

Expected: failure because ViewModel still regenerates selected time window.

- [ ] **Step 3: Write minimal implementation**

In `UnifiedNewsSummaryService.kt`, add a daily wrapper:

```kotlin
suspend fun generateDaily(
    force: Boolean = false,
    ignoreSourceTimeFilter: Boolean = false,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): UnifiedNewsGenerationResult = generate(
    window = dailyUnifiedNewsWindowFor(now, timeZone),
    force = force,
    ignoreSourceTimeFilter = ignoreSourceTimeFilter,
)
```

In `UnifiedNewsViewModel.kt`, add import:

```kotlin
import com.dailysatori.service.unifiednews.dailyUnifiedNewsWindowFor
```

Replace `regenerateCurrentWindow()` body window selection and generate call with daily generation:

```kotlin
fun regenerateCurrentWindow() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            _state.update { it.copy(isRegenerating = true, manualRefreshMessage = null, error = null, page = UnifiedNewsPage.SUMMARY) }
            val result = summaryService.generateDaily(
                force = true,
                ignoreSourceTimeFilter = isDebugBuild,
            )
            _state.update {
                it.copy(
                    isRegenerating = false,
                    manualRefreshMessage = manualRefreshMessage(result),
                    error = result.message?.takeIf { !result.success },
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(isRegenerating = false, error = "新闻汇总重新生成失败，请稍后重试") }
        }
    }
}
```

Delete `latestDueWindow()` if unused. Keep `toWindow()` until load logic is updated in later tasks.

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsRegenerationUsesDailyWindowAndOverwritesToday --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 4: Load Today's Daily Summary Instead Of A Timeline

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/UnifiedNewsSummaryRepository.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test:

```kotlin
@Test
fun unifiedNewsMainLoadFocusesOnTodayDailySummary() {
    val schema = java.io.File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
    val repo = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/UnifiedNewsSummaryRepository.kt").readText()
    val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()

    assertTrue(schema.contains("selectUnifiedNewsSummaryByDate"))
    assertTrue(repo.contains("fun getByDate(summaryDate: String)"))
    assertTrue(viewModel.contains("dailyUnifiedNewsWindowFor"))
    assertTrue(viewModel.contains("summaryRepo.getByDate(today.summaryDate)"))
    assertFalse(viewModel.contains("visibleSummaryLimit"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsMainLoadFocusesOnTodayDailySummary --no-configuration-cache
```

Expected: failure because current load collects all summaries and paginates.

- [ ] **Step 3: Write minimal implementation**

In `DailySatori.sq`, add after `selectUnifiedNewsSummaryByWindow`:

```sql
selectUnifiedNewsSummaryByDate:
SELECT * FROM unified_news_summary
WHERE summary_date = ? AND window_key = 'daily'
LIMIT 1;
```

In `UnifiedNewsSummaryRepository.kt`, add:

```kotlin
fun getByDate(summaryDate: String): Unified_news_summary? =
    q.selectUnifiedNewsSummaryByDate(summaryDate).executeAsOneOrNull()
```

In `UnifiedNewsState`, remove `displaySummaries`, `lastSuccessfulSummaries`, `sourcesBySummaryId`, and `visibleSummaryLimit`. Keep:

```kotlin
val summaries: List<Unified_news_summary> = emptyList(),
val selectedSummary: Unified_news_summary? = null,
val sources: List<Unified_news_source> = emptyList(),
```

Replace `loadInitial()` collection update with a daily-focused update:

```kotlin
summaryRepo.getAll().collect {
    val today = dailyUnifiedNewsWindowFor()
    val summary = summaryRepo.getByDate(today.summaryDate)
    _state.update { state ->
        state.copy(
            summaries = listOfNotNull(summary),
            selectedSummary = summary,
            sources = summary?.let { summaryRepo.getSources(it.id) }.orEmpty(),
            isLoading = false,
        )
    }
}
```

Delete `loadMoreSummaries()`.

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsMainLoadFocusesOnTodayDailySummary --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 5: Replace Timeline UI With Daily Summary And Source Cards

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsContentFormat.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test:

```kotlin
@Test
fun unifiedNewsUiShowsDailySummaryAndObviousSourceCards() {
    val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
    val format = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsContentFormat.kt").readText()

    assertTrue(screen.contains("TodayUnifiedNewsCard"))
    assertTrue(screen.contains("UnifiedNewsSourceCard"))
    assertTrue(screen.contains("查看来源"))
    assertTrue(screen.contains("onCitationClick(source)"))
    assertFalse(screen.contains("加载更多"))
    assertFalse(screen.contains("LoadMoreWhenAtEnd"))
    assertTrue(format.contains("unifiedNewsSourceTypeLabel"))
    assertTrue(format.contains("远程新闻"))
    assertTrue(format.contains("小龙虾"))
    assertTrue(format.contains("本地收藏"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsUiShowsDailySummaryAndObviousSourceCards --no-configuration-cache
```

Expected: failure because current UI still has history/timeline structures and no source cards.

- [ ] **Step 3: Write minimal implementation**

In `UnifiedNewsContentFormat.kt`, add:

```kotlin
fun unifiedNewsSourceTypeLabel(sourceType: String): String = when (sourceType) {
    "remote_article" -> "远程新闻"
    "crayfish_general" -> "小龙虾"
    "crayfish_dji" -> "小龙虾"
    "local_favorite" -> "本地收藏"
    else -> "来源"
}
```

In `UnifiedNewsScreen.kt`, replace `UnifiedNewsContent`, `LoadMoreWhenAtEnd`, `UnifiedNewsSummaryCard`, and `ClickableCitationLines` with these composables:

```kotlin
@Composable
private fun TodayUnifiedNewsCard(
    summary: Unified_news_summary,
    sources: List<Unified_news_source>,
    onCitationClick: (Unified_news_source) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text("今日总结", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(unifiedNewsSummaryTimeLabel(summary.summary_date, summary.window_key), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            summary.error_message?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            summary.source_warnings?.takeIf { it.isNotBlank() }?.let { Text("新闻来源提醒：\n$it", color = MaterialTheme.colorScheme.error) }
            CitationText(content = summary.content.ifBlank { "暂无正文" }, modifier = Modifier.fillMaxWidth()) { citation ->
                sources.firstOrNull { it.ref_key == citation }?.let(onCitationClick)
            }
            if (sources.isNotEmpty()) {
                HorizontalDivider()
                Text("来源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                sources.forEach { source ->
                    UnifiedNewsSourceCard(source = source, onClick = { onCitationClick(source) })
                }
            }
        }
    }
}

@Composable
private fun UnifiedNewsSourceCard(source: Unified_news_source, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(unifiedNewsSourceTypeLabel(source.source_type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("查看来源", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Text(source.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(source.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

Update the summary page content branch to render one card:

```kotlin
val summary = state.selectedSummary
when {
    state.isLoading -> LoadingIndicator()
    summary == null -> EmptyState(
        icon = Icons.AutoMirrored.Filled.Article,
        title = "暂无今日总结",
        subtitle = "点击右上角重新生成今日总结",
    )
    else -> LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.m),
    ) {
        item { TodayUnifiedNewsCard(summary, state.sources, viewModel::openCitation) }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsUiShowsDailySummaryAndObviousSourceCards --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 6: Update Worker To Generate Daily Summary

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/core/worker/UnifiedNewsWorker.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test:

```kotlin
@Test
fun unifiedNewsWorkerWritesDailySummaryRows() {
    val worker = java.io.File("src/main/kotlin/com/dailysatori/core/worker/UnifiedNewsWorker.kt").readText()

    assertTrue(worker.contains("generateDaily"))
    assertFalse(worker.contains("summaryService.generate(window"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsWorkerWritesDailySummaryRows --no-configuration-cache
```

Expected: failure because worker still generates window rows.

- [ ] **Step 3: Write minimal implementation**

In `UnifiedNewsWorker.kt`, replace worker generation calls that pass `window = ...` with:

```kotlin
summaryService.generateDaily(force = true)
```

Keep existing scheduling functions intact for now. They may still determine when the worker runs, but the write target is always today's daily row.

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsWorkerWritesDailySummaryRows --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 7: Full Verification And Device Install

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run focused unified news tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Compile debug Kotlin**

Run:

```bash
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Assemble debug APK**

Run:

```bash
./gradlew :app:assembleDebug --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Install debug APK to connected device**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug --no-configuration-cache
```

Expected: `Installed on 1 device` and `BUILD SUCCESSFUL`.

- [ ] **Step 5: Launch app**

Run:

```bash
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: app launches without crash.

- [ ] **Step 6: Inspect device DB after manual regeneration**

After the user taps `... -> 刷新/重新生成`, run:

```bash
adb exec-out run-as com.dailysatori cat databases/daily_satori.db > /tmp/opencode/daily_satori_daily_summary.db
sqlite3 /tmp/opencode/daily_satori_daily_summary.db "select summary_date, window_key, status, length(content), updated_at from unified_news_summary order by updated_at desc limit 5; select source_type, count(*) from unified_news_source where summary_id=(select id from unified_news_summary where window_key='daily' order by updated_at desc limit 1) group by source_type;"
```

Expected: latest row has `window_key = daily`; same local date has only one daily row; sources include article-level source types such as `remote_article`, `crayfish_general`, `crayfish_dji`, or `local_favorite` depending on available data.

---

## Self-Review Notes

- Spec coverage: source rules are covered by Task 2; persistence/regeneration by Tasks 1, 3, and 4; UI rules by Task 5; worker behavior by Task 6; verification by Task 7.
- No schema migration is planned because no table shape changes are required; `window_key = 'daily'` uses the existing unique key.
- The plan intentionally keeps citation validation internally while making source cards the primary visible interaction.
