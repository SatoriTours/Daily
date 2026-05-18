# Unified News Summary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a unified AI-generated news summary landing page with scheduled natural-day snapshots, persistent clickable citations, and reduced bottom navigation.

**Architecture:** Add an independent unified news module instead of folding this into `RemoteNewsViewModel`. Shared code owns source collection, prompt construction, citation validation, and SQLDelight persistence; Android app code owns WorkManager scheduling, Compose UI, and navigation. Existing remote news, Crayfish news, local article, and settings screens remain detail destinations exposed from the new `新闻汇总` landing page menu.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Jetpack Compose, WorkManager, Koin, Kotlin coroutines, kotlinx-datetime, existing `AiService` and news services.

---

## File Structure

- Create `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsModels.kt`: window keys, source types, source items, citation helpers, generation results.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsWindowPolicy.kt`: local-day window calculation, due-window detection, next-run delay calculation.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsPrompt.kt`: prompt construction and citation validation.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`: source collection, AI call, persistence orchestration.
- Create `shared/src/commonMain/kotlin/com/dailysatori/data/repository/UnifiedNewsSummaryRepository.kt`: SQLDelight wrapper for summary/source rows.
- Modify `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`: tables and queries for unified summaries and sources; favorite-date-range query.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`: bump `DatabaseConfig.currentSchemaVersion` from `6L` to `7L`.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`: add `migrateV6ToV7()`.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt`: expose favorite articles by date range.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`: register repository and service.
- Create `app/src/main/kotlin/com/dailysatori/core/worker/UnifiedNewsWorker.kt`: WorkManager worker and scheduler.
- Modify `app/src/main/kotlin/com/dailysatori/DailySatoriApplication.kt`: schedule unified news work on startup.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`: UI state, load/regenerate, secondary page mode, citation routing events.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`: landing page, menu, summary content, history/status, secondary destinations.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt`: clickable citation token rendering.
- Modify `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`: register `UnifiedNewsViewModel`.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`: reduce bottom tabs and replace first tab with `新闻汇总`.
- Modify `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`: pass local article detail navigation into `UnifiedNewsScreen` through `HomeScreen`.
- Test files:
- Create `shared/src/commonTest/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsWindowPolicyTest.kt` if common tests are configured; otherwise use app unit tests for pure helpers.
- Create `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt` for source-level behavior checks matching existing test style.

Do not create git commits during execution unless the user explicitly asks for commits.

---

### Task 1: Pure Window And Citation Policy

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsModels.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsWindowPolicy.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsPrompt.kt`
- Create: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write failing tests for windows and citations**

Add these tests to `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`:

```kotlin
package com.dailysatori

import com.dailysatori.service.unifiednews.UnifiedNewsSourceItem
import com.dailysatori.service.unifiednews.UnifiedNewsSourceType
import com.dailysatori.service.unifiednews.UnifiedNewsWindowKey
import com.dailysatori.service.unifiednews.citationTokens
import com.dailysatori.service.unifiednews.dueUnifiedNewsWindows
import com.dailysatori.service.unifiednews.invalidCitationTokens
import com.dailysatori.service.unifiednews.nextUnifiedNewsWindow
import com.dailysatori.service.unifiednews.unifiedNewsWindowFor
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnifiedNewsBehaviorTest {
    private val zone = TimeZone.of("Asia/Shanghai")

    @Test
    fun finalWindowAtMidnightTargetsPreviousNaturalDay() {
        val window = unifiedNewsWindowFor(
            key = UnifiedNewsWindowKey.FINAL,
            dueAt = Instant.parse("2026-05-16T16:00:00Z"),
            timeZone = zone,
        )

        assertEquals("2026-05-16", window.summaryDate)
        assertEquals("final", window.key.value)
        assertEquals(Instant.parse("2026-05-15T16:00:00Z").toEpochMilliseconds(), window.startMs)
        assertEquals(Instant.parse("2026-05-16T15:59:59.999Z").toEpochMilliseconds(), window.endMs)
    }

    @Test
    fun delayedRunKeepsNamedWindowEnd() {
        val window = unifiedNewsWindowFor(
            key = UnifiedNewsWindowKey.W1330,
            dueAt = Instant.parse("2026-05-16T05:30:00Z"),
            timeZone = zone,
        )

        assertEquals("2026-05-16", window.summaryDate)
        assertEquals(Instant.parse("2026-05-15T16:00:00Z").toEpochMilliseconds(), window.startMs)
        assertEquals(Instant.parse("2026-05-16T05:30:00Z").toEpochMilliseconds(), window.endMs)
    }

    @Test
    fun nextWindowAfterMorningIsLunchWindow() {
        val next = nextUnifiedNewsWindow(
            now = Instant.parse("2026-05-16T01:00:00Z"),
            timeZone = zone,
        )

        assertEquals(UnifiedNewsWindowKey.W1330, next.key)
        assertEquals(Instant.parse("2026-05-16T05:30:00Z"), next.dueAt)
    }

    @Test
    fun dueWindowsAtMorningIncludePreviousFinalAndMorningOnly() {
        val due = dueUnifiedNewsWindows(
            now = Instant.parse("2026-05-16T01:00:00Z"),
            timeZone = zone,
        )

        assertEquals(listOf(UnifiedNewsWindowKey.FINAL, UnifiedNewsWindowKey.W0800), due.map { it.key })
        assertEquals("2026-05-15", due.first().summaryDate)
        assertEquals("2026-05-16", due.last().summaryDate)
    }

    @Test
    fun citationValidationRejectsUnknownTokens() {
        val sources = listOf(
            UnifiedNewsSourceItem(refKey = "R1", sourceType = UnifiedNewsSourceType.REMOTE_DIGEST, title = "远程", summary = "摘要"),
            UnifiedNewsSourceItem(refKey = "F1", sourceType = UnifiedNewsSourceType.LOCAL_FAVORITE, title = "收藏", summary = "摘要"),
        )
        val content = "AI 相关趋势升温。[R1][F2]\n本地收藏补充了背景。[F1]"

        assertEquals(listOf("R1", "F2", "F1"), citationTokens(content))
        assertEquals(listOf("F2"), invalidCitationTokens(content, sources))
    }

    @Test
    fun sourceTypePrefixesAreStable() {
        assertTrue(UnifiedNewsSourceType.REMOTE_DIGEST.prefix == "R")
        assertTrue(UnifiedNewsSourceType.CRAYFISH_GENERAL.prefix == "C")
        assertTrue(UnifiedNewsSourceType.CRAYFISH_DJI.prefix == "D")
        assertTrue(UnifiedNewsSourceType.LOCAL_FAVORITE.prefix == "F")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest`

Expected: fails with unresolved references for `UnifiedNewsWindowKey`, `unifiedNewsWindowFor`, and citation helpers.

- [ ] **Step 3: Implement minimal models and helpers**

Create `UnifiedNewsModels.kt`:

```kotlin
package com.dailysatori.service.unifiednews

enum class UnifiedNewsWindowKey(val value: String, val hour: Int, val minute: Int) {
    W0800("0800", 8, 0),
    W1330("1330", 13, 30),
    W1800("1800", 18, 0),
    W2100("2100", 21, 0),
    FINAL("final", 0, 0),
}

enum class UnifiedNewsSourceType(val dbValue: String, val prefix: String) {
    REMOTE_DIGEST("remote_digest", "R"),
    REMOTE_ARTICLE("remote_article", "R"),
    CRAYFISH_GENERAL("crayfish_general", "C"),
    CRAYFISH_DJI("crayfish_dji", "D"),
    LOCAL_FAVORITE("local_favorite", "F"),
}

data class UnifiedNewsWindow(
    val key: UnifiedNewsWindowKey,
    val summaryDate: String,
    val startMs: Long,
    val endMs: Long,
)

data class NextUnifiedNewsWindow(
    val key: UnifiedNewsWindowKey,
    val dueAt: kotlinx.datetime.Instant,
)

data class UnifiedNewsSourceItem(
    val refKey: String,
    val sourceType: UnifiedNewsSourceType,
    val sourceId: Long? = null,
    val sourceFilename: String? = null,
    val sourceUrl: String? = null,
    val title: String,
    val summary: String,
    val sourceTime: Long? = null,
    val content: String = "",
)
```

Create `UnifiedNewsWindowPolicy.kt`:

```kotlin
package com.dailysatori.service.unifiednews

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

private val RunOrder = listOf(
    UnifiedNewsWindowKey.W0800,
    UnifiedNewsWindowKey.W1330,
    UnifiedNewsWindowKey.W1800,
    UnifiedNewsWindowKey.W2100,
    UnifiedNewsWindowKey.FINAL,
)

fun unifiedNewsWindowFor(
    key: UnifiedNewsWindowKey,
    dueAt: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): UnifiedNewsWindow {
    val dueLocal = dueAt.toLocalDateTime(timeZone)
    val summaryDate = if (key == UnifiedNewsWindowKey.FINAL) dueLocal.date.minus(1, DateTimeUnit.DAY) else dueLocal.date
    val start = summaryDate.atStartOfDayIn(timeZone)
    val end = if (key == UnifiedNewsWindowKey.FINAL) {
        summaryDate.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).minus(1, DateTimeUnit.MILLISECOND)
    } else {
        key.dueInstantOn(summaryDate, timeZone)
    }
    return UnifiedNewsWindow(
        key = key,
        summaryDate = summaryDate.toString(),
        startMs = start.toEpochMilliseconds(),
        endMs = end.toEpochMilliseconds(),
    )
}

fun nextUnifiedNewsWindow(
    now: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): NextUnifiedNewsWindow {
    val localNow = now.toLocalDateTime(timeZone)
    val today = localNow.date
    for (key in RunOrder) {
        val due = key.dueInstantOn(if (key == UnifiedNewsWindowKey.FINAL) today.plus(1, DateTimeUnit.DAY) else today, timeZone)
        if (due > now) return NextUnifiedNewsWindow(key, due)
    }
    return NextUnifiedNewsWindow(UnifiedNewsWindowKey.W0800, UnifiedNewsWindowKey.W0800.dueInstantOn(today.plus(1, DateTimeUnit.DAY), timeZone))
}

fun dueUnifiedNewsWindows(
    now: Instant,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<UnifiedNewsWindow> {
    val localNow = now.toLocalDateTime(timeZone)
    val today = localNow.date
    val due = mutableListOf<UnifiedNewsWindow>()
    val todayFinalDue = UnifiedNewsWindowKey.FINAL.dueInstantOn(today, timeZone)
    if (todayFinalDue <= now) due += unifiedNewsWindowFor(UnifiedNewsWindowKey.FINAL, todayFinalDue, timeZone)
    RunOrder.filterNot { it == UnifiedNewsWindowKey.FINAL }.forEach { key ->
        val dueAt = key.dueInstantOn(today, timeZone)
        if (dueAt <= now) due += unifiedNewsWindowFor(key, dueAt, timeZone)
    }
    return due
}

private fun UnifiedNewsWindowKey.dueInstantOn(date: LocalDate, timeZone: TimeZone): Instant =
    LocalDateTime(date, LocalTime(hour, minute)).toInstant(timeZone)
```

Create `UnifiedNewsPrompt.kt`:

```kotlin
package com.dailysatori.service.unifiednews

private val CitationRegex = Regex("\\[([RCDF]\\d+)]")

fun citationTokens(content: String): List<String> =
    CitationRegex.findAll(content).map { it.groupValues[1] }.toList()

fun invalidCitationTokens(content: String, sources: List<UnifiedNewsSourceItem>): List<String> {
    val valid = sources.map { it.refKey }.toSet()
    return citationTokens(content).filterNot { it in valid }.distinct()
}

fun buildUnifiedNewsPrompt(window: UnifiedNewsWindow, sources: List<UnifiedNewsSourceItem>): String {
    val sourceText = sources.joinToString("\n\n") { source ->
        """[${source.refKey}] ${source.title}
来源类型: ${source.sourceType.dbValue}
摘要: ${source.summary}
正文: ${source.content.take(3000)}""".trimIndent()
    }
    return """请基于以下来源，生成中文 Markdown 新闻汇总。

要求：
1. 只能使用给定来源，不要编造事实。
2. `重点速览` 和 `值得关注` 中每个事实判断都必须带引用，例如 [R1][F2]。
3. 引用必须完全匹配来源编号，不要创造不存在的编号。
4. 输出结构使用：# 今日统一新闻总结、## 重点速览、## 值得关注。

日期: ${window.summaryDate}
窗口: ${window.key.value}

来源：
$sourceText""".trimIndent()
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest`

Expected: tests pass.

---

### Task 2: SQLDelight Tables, Repository, And Migration

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/UnifiedNewsSummaryRepository.kt`

- [ ] **Step 1: Write repository API expectation test as source-level test**

Append to `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`:

```kotlin
@Test
fun unifiedNewsSchemaAndRepositoryExposeRequiredOperations() {
    val schema = java.io.File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
    val repo = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/UnifiedNewsSummaryRepository.kt").readText()
    val articleRepo = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt").readText()
    val migration = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt").readText()
    val config = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt").readText()

    assertTrue(schema.contains("CREATE TABLE unified_news_summary"))
    assertTrue(schema.contains("CREATE TABLE unified_news_source"))
    assertTrue(schema.contains("selectUnifiedNewsSummaries"))
    assertTrue(schema.contains("selectFavoriteArticlesByDateRange"))
    assertTrue(repo.contains("class UnifiedNewsSummaryRepository"))
    assertTrue(repo.contains("fun replaceSources"))
    assertTrue(articleRepo.contains("fun getFavoritesByDateRangeSync"))
    assertTrue(migration.contains("migrateV6ToV7"))
    assertTrue(config.contains("currentSchemaVersion = 7L"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsSchemaAndRepositoryExposeRequiredOperations`

Expected: fails because files/strings do not exist yet.

- [ ] **Step 3: Add SQLDelight schema and queries**

In `DailySatori.sq`, add tables after `weekly_summary` and add queries near article and summary queries:

```sql
CREATE TABLE unified_news_summary (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    summary_date TEXT NOT NULL,
    window_key TEXT NOT NULL,
    window_start_ms INTEGER NOT NULL,
    window_end_ms INTEGER NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    status TEXT NOT NULL,
    error_message TEXT,
    source_warnings TEXT,
    generated_at INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    UNIQUE(summary_date, window_key)
);

CREATE TABLE unified_news_source (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    summary_id INTEGER NOT NULL REFERENCES unified_news_summary(id) ON DELETE CASCADE,
    ref_key TEXT NOT NULL,
    source_type TEXT NOT NULL,
    source_id INTEGER,
    source_filename TEXT,
    source_url TEXT,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    source_time INTEGER,
    UNIQUE(summary_id, ref_key)
);

selectFavoriteArticlesByDateRange:
SELECT * FROM article
WHERE is_favorite = 1 AND created_at >= ? AND created_at <= ?
ORDER BY created_at DESC;

selectUnifiedNewsSummaries:
SELECT * FROM unified_news_summary ORDER BY window_end_ms DESC;

selectLatestSuccessfulUnifiedNewsSummary:
SELECT * FROM unified_news_summary
WHERE status = 'success'
ORDER BY window_end_ms DESC
LIMIT 1;

selectUnifiedNewsSummaryByWindow:
SELECT * FROM unified_news_summary WHERE summary_date = ? AND window_key = ?;

selectUnifiedNewsSources:
SELECT * FROM unified_news_source WHERE summary_id = ? ORDER BY id ASC;

upsertUnifiedNewsSummary:
INSERT INTO unified_news_summary (
    summary_date, window_key, window_start_ms, window_end_ms, title, content, status,
    error_message, source_warnings, generated_at, created_at, updated_at
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(summary_date, window_key) DO UPDATE SET
    window_start_ms = excluded.window_start_ms,
    window_end_ms = excluded.window_end_ms,
    title = excluded.title,
    content = excluded.content,
    status = excluded.status,
    error_message = excluded.error_message,
    source_warnings = excluded.source_warnings,
    generated_at = excluded.generated_at,
    updated_at = excluded.updated_at;

deleteUnifiedNewsSources:
DELETE FROM unified_news_source WHERE summary_id = ?;

insertUnifiedNewsSource:
INSERT INTO unified_news_source (
    summary_id, ref_key, source_type, source_id, source_filename, source_url,
    title, summary, source_time
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
```

- [ ] **Step 4: Add migration and version bump**

Change `DatabaseConfig.currentSchemaVersion` to `7L`. In `DatabaseMigration.runMigrations()`, add:

```kotlin
if (currentVersion < 7) {
    migrateV6ToV7()
}
```

Add method:

```kotlin
private fun migrateV6ToV7() {
    log.i { "Migration V6 -> V7: Unified news summary tables" }
    try {
        runSql("""
            CREATE TABLE IF NOT EXISTS unified_news_summary (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                summary_date TEXT NOT NULL,
                window_key TEXT NOT NULL,
                window_start_ms INTEGER NOT NULL,
                window_end_ms INTEGER NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                status TEXT NOT NULL,
                error_message TEXT,
                source_warnings TEXT,
                generated_at INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                UNIQUE(summary_date, window_key)
            )
        """.trimIndent())
        runSql("""
            CREATE TABLE IF NOT EXISTS unified_news_source (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                summary_id INTEGER NOT NULL REFERENCES unified_news_summary(id) ON DELETE CASCADE,
                ref_key TEXT NOT NULL,
                source_type TEXT NOT NULL,
                source_id INTEGER,
                source_filename TEXT,
                source_url TEXT,
                title TEXT NOT NULL,
                summary TEXT NOT NULL,
                source_time INTEGER,
                UNIQUE(summary_id, ref_key)
            )
        """.trimIndent())
        log.i { "Created unified news tables" }
    } catch (e: Exception) {
        log.w(e) { "Migration V6->V7 failed" }
    }
}
```

- [ ] **Step 5: Add repositories**

In `ArticleRepository.kt`, add:

```kotlin
fun getFavoritesByDateRangeSync(startMs: Long, endMs: Long): List<Article> =
    q.selectFavoriteArticlesByDateRange(startMs, endMs).executeAsList()
```

Create `UnifiedNewsSummaryRepository.kt`:

```kotlin
package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.service.unifiednews.UnifiedNewsSourceItem
import com.dailysatori.service.unifiednews.UnifiedNewsWindow
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Unified_news_summary
import com.dailysatori.shared.db.Unified_news_source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class UnifiedNewsSummaryRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Unified_news_summary>> =
        q.selectUnifiedNewsSummaries().asFlow().mapToList(Dispatchers.IO)

    fun getLatestSuccessful(): Unified_news_summary? =
        q.selectLatestSuccessfulUnifiedNewsSummary().executeAsOneOrNull()

    fun getByWindow(summaryDate: String, windowKey: String): Unified_news_summary? =
        q.selectUnifiedNewsSummaryByWindow(summaryDate, windowKey).executeAsOneOrNull()

    fun getSources(summaryId: Long): List<Unified_news_source> =
        q.selectUnifiedNewsSources(summaryId).executeAsList()

    fun upsertSummary(
        window: UnifiedNewsWindow,
        title: String,
        content: String,
        status: String,
        errorMessage: String?,
        sourceWarnings: String?,
        generatedAt: Long?,
    ): Unified_news_summary {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.upsertUnifiedNewsSummary(
            window.summaryDate,
            window.key.value,
            window.startMs,
            window.endMs,
            title,
            content,
            status,
            errorMessage,
            sourceWarnings,
            generatedAt,
            now,
            now,
        )
        return getByWindow(window.summaryDate, window.key.value)!!
    }

    fun replaceSources(summaryId: Long, sources: List<UnifiedNewsSourceItem>) {
        q.transaction {
            q.deleteUnifiedNewsSources(summaryId)
            sources.forEach { source ->
                q.insertUnifiedNewsSource(
                    summaryId,
                    source.refKey,
                    source.sourceType.dbValue,
                    source.sourceId,
                    source.sourceFilename,
                    source.sourceUrl,
                    source.title,
                    source.summary,
                    source.sourceTime,
                )
            }
        }
    }
}
```

- [ ] **Step 6: Run tests and SQLDelight generation**

Run: `./gradlew :shared:generateCommonMainDailySatoriDatabaseInterface :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsSchemaAndRepositoryExposeRequiredOperations`

Expected: SQLDelight generation succeeds and the source-level test passes.

---

### Task 3: Source Collection And AI Generation Service

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Add source-level service tests**

Append:

```kotlin
@Test
fun unifiedNewsServicePersistsEmptyWindowWithoutAiCall() {
    val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

    assertTrue(source.contains("status = UnifiedNewsSummaryStatus.EMPTY.value"))
    assertTrue(source.contains("skip the AI call"))
}

@Test
fun unifiedNewsServiceFailsUnknownCitations() {
    val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

    assertTrue(source.contains("invalidCitationTokens"))
    assertTrue(source.contains("UnifiedNewsSummaryStatus.FAILED.value"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsServicePersistsEmptyWindowWithoutAiCall --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsServiceFailsUnknownCitations`

Expected: fails because the service file does not exist.

- [ ] **Step 3: Add status model**

Add to `UnifiedNewsModels.kt`:

```kotlin
enum class UnifiedNewsSummaryStatus(val value: String) {
    PENDING("pending"),
    SUCCESS("success"),
    FAILED("failed"),
    EMPTY("empty"),
}

data class UnifiedNewsGenerationResult(
    val success: Boolean,
    val status: UnifiedNewsSummaryStatus,
    val message: String? = null,
)
```

- [ ] **Step 4: Implement service orchestration**

Create `UnifiedNewsSummaryService.kt`:

```kotlin
package com.dailysatori.service.unifiednews

import co.touchlab.kermit.Logger
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.UnifiedNewsSummaryRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.crayfishnews.CrayfishNewsResult
import com.dailysatori.service.crayfishnews.CrayfishNewsService
import com.dailysatori.config.RemoteNewsConfig
import com.dailysatori.service.remotenews.RemoteNewsResult
import com.dailysatori.service.remotenews.RemoteNewsService
import kotlinx.datetime.Clock

class UnifiedNewsSummaryService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val articleRepo: ArticleRepository,
    private val settingRepo: SettingRepository,
    private val remoteNewsService: RemoteNewsService,
    private val crayfishNewsService: CrayfishNewsService,
    private val summaryRepo: UnifiedNewsSummaryRepository,
) {
    private val log = Logger.withTag("UnifiedNews")

    suspend fun generate(window: UnifiedNewsWindow, force: Boolean = false): UnifiedNewsGenerationResult {
        if (!force && summaryRepo.getByWindow(window.summaryDate, window.key.value)?.status == UnifiedNewsSummaryStatus.SUCCESS.value) {
            return UnifiedNewsGenerationResult(true, UnifiedNewsSummaryStatus.SUCCESS, "already generated")
        }

        val collected = collectSources(window)
        if (collected.sources.isEmpty()) {
            val row = summaryRepo.upsertSummary(
                window = window,
                title = "暂无可总结新闻",
                content = "",
                status = UnifiedNewsSummaryStatus.EMPTY.value,
                errorMessage = null,
                sourceWarnings = collected.warnings.joinToString("\n").takeIf { it.isNotBlank() },
                generatedAt = Clock.System.now().toEpochMilliseconds(),
            )
            summaryRepo.replaceSources(row.id, emptyList())
            return UnifiedNewsGenerationResult(true, UnifiedNewsSummaryStatus.EMPTY, "skip the AI call: no sources")
        }

        val config = aiConfigService.getDefaultConfig()
            ?: return saveFailure(window, collected.sources, collected.warnings, "请先配置默认 AI 服务")

        return try {
            val content = aiService.summarize(
                content = buildUnifiedNewsPrompt(window, collected.sources),
                systemPrompt = "你是严谨的中文新闻编辑，只能根据用户提供的来源做综合总结。",
                apiAddress = config.api_address,
                apiToken = config.api_token,
                modelName = config.model_name,
                provider = config.provider,
            )
            val invalid = invalidCitationTokens(content, collected.sources)
            if (invalid.isNotEmpty()) {
                saveFailure(window, collected.sources, collected.warnings, "AI 返回了无效引用: ${invalid.joinToString()}")
            } else {
                val row = summaryRepo.upsertSummary(
                    window = window,
                    title = "今日统一新闻总结",
                    content = content,
                    status = UnifiedNewsSummaryStatus.SUCCESS.value,
                    errorMessage = null,
                    sourceWarnings = collected.warnings.joinToString("\n").takeIf { it.isNotBlank() },
                    generatedAt = Clock.System.now().toEpochMilliseconds(),
                )
                summaryRepo.replaceSources(row.id, collected.sources)
                UnifiedNewsGenerationResult(true, UnifiedNewsSummaryStatus.SUCCESS)
            }
        } catch (e: Exception) {
            log.w(e) { "Failed to generate unified news summary" }
            saveFailure(window, collected.sources, collected.warnings, e.message ?: "统一新闻总结生成失败")
        }
    }

    private suspend fun collectSources(window: UnifiedNewsWindow): CollectedSources {
        val warnings = mutableListOf<String>()
        val sources = mutableListOf<UnifiedNewsSourceItem>()
        sources += collectLocalFavorites(window)
        sources += collectRemoteDigests(window, warnings)
        sources += collectCrayfish(window, warnings)
        return CollectedSources(assignRefKeys(sources), warnings)
    }

    private fun collectLocalFavorites(window: UnifiedNewsWindow): List<UnifiedNewsSourceItem> =
        articleRepo.getFavoritesByDateRangeSync(window.startMs, window.endMs).map { article ->
            UnifiedNewsSourceItem(
                refKey = "",
                sourceType = UnifiedNewsSourceType.LOCAL_FAVORITE,
                sourceId = article.id,
                sourceUrl = article.url,
                title = article.ai_title ?: article.title ?: article.url ?: "本地收藏",
                summary = article.ai_content ?: article.comment ?: "",
                sourceTime = article.created_at,
                content = article.ai_markdown_content ?: article.ai_content ?: "",
            )
        }

    private suspend fun collectRemoteDigests(window: UnifiedNewsWindow, warnings: MutableList<String>): List<UnifiedNewsSourceItem> {
        val config = remoteNewsService.configOrFailure(settingRepo.get(SettingKeys.remoteNewsBaseUrl), settingRepo.get(SettingKeys.remoteNewsApiToken))
        if (config !is RemoteNewsResult.Success) {
            warnings += "远程新闻未配置或不可用"
            return emptyList()
        }
        return when (val result = remoteNewsService.fetchDigests(config.value, 1, RemoteNewsConfig.digestsPageSize)) {
            is RemoteNewsResult.Success -> result.value.digests.mapNotNull { digest ->
                val sourceTime = digest.generatedAt?.let(::parseSourceTimeMillis) ?: digest.startedAt?.let(::parseSourceTimeMillis)
                if (sourceTime == null || sourceTime !in window.startMs..window.endMs) return@mapNotNull null
                UnifiedNewsSourceItem(
                    refKey = "",
                    sourceType = UnifiedNewsSourceType.REMOTE_DIGEST,
                    sourceId = digest.id,
                    title = digest.title ?: digest.date ?: "远程新闻总结",
                    summary = digest.summary.orEmpty(),
                    sourceTime = sourceTime,
                    content = digest.sections.joinToString("\n") { it.summary.orEmpty() },
                )
            }
            is RemoteNewsResult.Failure -> {
                warnings += result.message
                emptyList()
            }
        }
    }

    private suspend fun collectCrayfish(window: UnifiedNewsWindow, warnings: MutableList<String>): List<UnifiedNewsSourceItem> {
        val config = crayfishNewsService.configOrFailure(settingRepo.get(SettingKeys.crayfishNewsBaseUrl), settingRepo.get(SettingKeys.crayfishNewsApiToken))
        if (config !is CrayfishNewsResult.Success) {
            warnings += "小龙虾新闻未配置或不可用"
            return emptyList()
        }
        return when (val result = crayfishNewsService.fetchNewsList(config.value, limit = 50)) {
            is CrayfishNewsResult.Success -> {
                val general = result.value.general.mapNotNull { item -> crayfishSource(window, UnifiedNewsSourceType.CRAYFISH_GENERAL, item.filename, item.generated, item.preview) }
                val dji = result.value.dji.mapNotNull { item -> crayfishSource(window, UnifiedNewsSourceType.CRAYFISH_DJI, item.filename, item.generated, item.preview) }
                general + dji
            }
            is CrayfishNewsResult.Failure -> {
                warnings += result.message
                emptyList()
            }
        }
    }

    private fun crayfishSource(window: UnifiedNewsWindow, type: UnifiedNewsSourceType, filename: String, generated: String?, preview: String): UnifiedNewsSourceItem? {
        val sourceTime = generated?.let(::parseSourceTimeMillis) ?: return null
        if (sourceTime !in window.startMs..window.endMs) return null
        return UnifiedNewsSourceItem(
            refKey = "",
            sourceType = type,
            sourceFilename = filename,
            title = generated.takeIf { !it.isNullOrBlank() } ?: filename,
            summary = preview,
            sourceTime = sourceTime,
            content = preview,
        )
    }

    private fun assignRefKeys(sources: List<UnifiedNewsSourceItem>): List<UnifiedNewsSourceItem> =
        sources.groupBy { it.sourceType.prefix }.flatMap { (prefix, grouped) ->
            grouped.mapIndexed { index, source -> source.copy(refKey = "$prefix${index + 1}") }
        }

    private fun saveFailure(window: UnifiedNewsWindow, sources: List<UnifiedNewsSourceItem>, warnings: List<String>, message: String): UnifiedNewsGenerationResult {
        val row = summaryRepo.upsertSummary(
            window = window,
            title = "统一新闻总结生成失败",
            content = "",
            status = UnifiedNewsSummaryStatus.FAILED.value,
            errorMessage = message,
            sourceWarnings = warnings.joinToString("\n").takeIf { it.isNotBlank() },
            generatedAt = Clock.System.now().toEpochMilliseconds(),
        )
        summaryRepo.replaceSources(row.id, sources)
        return UnifiedNewsGenerationResult(false, UnifiedNewsSummaryStatus.FAILED, message)
    }

    private data class CollectedSources(val sources: List<UnifiedNewsSourceItem>, val warnings: List<String>)
}

fun parseSourceTimeMillis(value: String): Long? =
    runCatching { kotlinx.datetime.Instant.parse(value).toEpochMilliseconds() }.getOrNull()
```

- [ ] **Step 5: Register in DI**

In `SharedModule.kt`, add imports and registrations:

```kotlin
import com.dailysatori.data.repository.UnifiedNewsSummaryRepository
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryService
```

Add repository near other repositories:

```kotlin
single { UnifiedNewsSummaryRepository(get()) }
```

Add service near other services:

```kotlin
single { UnifiedNewsSummaryService(get(), get(), get(), get(), get(), get(), get()) }
```

- [ ] **Step 6: Run focused tests and compile**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsServicePersistsEmptyWindowWithoutAiCall --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsServiceFailsUnknownCitations :app:compileDebugKotlin`

Expected: tests pass and Kotlin compilation succeeds.

---

### Task 4: WorkManager Scheduler And Startup Backfill

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/core/worker/UnifiedNewsWorker.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/DailySatoriApplication.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Add source-level worker tests**

Append:

```kotlin
@Test
fun unifiedNewsWorkerUsesOneTimeWorkAndSchedulesStartup() {
    val worker = java.io.File("src/main/kotlin/com/dailysatori/core/worker/UnifiedNewsWorker.kt").readText()
    val app = java.io.File("src/main/kotlin/com/dailysatori/DailySatoriApplication.kt").readText()

    assertTrue(worker.contains("OneTimeWorkRequestBuilder<UnifiedNewsWorker>"))
    assertTrue(worker.contains("setInitialDelay"))
    assertTrue(worker.contains("enqueueBackfill"))
    assertTrue(worker.contains("scheduleNext"))
    assertTrue(app.contains("UnifiedNewsScheduler(this).ensureScheduled()"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsWorkerUsesOneTimeWorkAndSchedulesStartup`

Expected: fails because worker file does not exist.

- [ ] **Step 3: Implement worker and scheduler**

Create `UnifiedNewsWorker.kt`:

```kotlin
package com.dailysatori.core.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryService
import com.dailysatori.service.unifiednews.dueUnifiedNewsWindows
import com.dailysatori.service.unifiednews.nextUnifiedNewsWindow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class UnifiedNewsScheduler(private val context: Context) {
    fun ensureScheduled() {
        scheduleNext()
        enqueueBackfill()
    }

    fun scheduleNext(now: Instant = Clock.System.now()) {
        val next = nextUnifiedNewsWindow(now)
        val delayMs = (next.dueAt.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<UnifiedNewsWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(UnifiedNewsWorker.KEY_MODE to UnifiedNewsWorker.MODE_DUE))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WorkNameNext, ExistingWorkPolicy.REPLACE, request)
    }

    fun enqueueBackfill() {
        val request = OneTimeWorkRequestBuilder<UnifiedNewsWorker>()
            .setInputData(workDataOf(UnifiedNewsWorker.KEY_MODE to UnifiedNewsWorker.MODE_BACKFILL))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WorkNameBackfill, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WorkNameNext = "unified-news-next"
        const val WorkNameBackfill = "unified-news-backfill"
    }
}

class UnifiedNewsWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val service = GlobalContext.get().get<UnifiedNewsSummaryService>()
            val now = Clock.System.now()
            dueUnifiedNewsWindows(now).forEach { window -> service.generate(window) }
            UnifiedNewsScheduler(applicationContext).scheduleNext(now)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_MODE = "mode"
        const val MODE_DUE = "due"
        const val MODE_BACKFILL = "backfill"
    }
}
```

- [ ] **Step 4: Wire startup scheduling**

In `DailySatoriApplication.kt`, import and call:

```kotlin
import com.dailysatori.core.worker.UnifiedNewsScheduler
```

After `BackupScheduler(this).ensureScheduled()`:

```kotlin
UnifiedNewsScheduler(this).ensureScheduled()
```

- [ ] **Step 5: Run tests and compile**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsWorkerUsesOneTimeWorkAndSchedulesStartup :app:compileDebugKotlin`

Expected: test passes and app compiles. `dueUnifiedNewsWindows()` must only return windows whose target time is already due; future windows must not be generated early.

---

### Task 5: Unified News ViewModel, UI, And Reduced Bottom Tabs

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticlesViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Add navigation source-level tests**

Append:

```kotlin
@Test
fun homeTabsAreReducedAndFirstTabIsUnifiedNews() {
    val home = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()

    assertTrue(home.contains("TabItem(\"新闻汇总\""))
    assertFalse(home.contains("TabItem(\"文章\""))
    assertFalse(home.contains("TabItem(\"远程新闻\""))
    assertFalse(home.contains("TabItem(\"设置\""))
    assertTrue(home.contains("UnifiedNewsScreen"))
}

@Test
fun unifiedNewsMenuContainsSecondaryDestinations() {
    val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

    assertTrue(screen.contains("本地文章"))
    assertTrue(screen.contains("本地收藏"))
    assertTrue(screen.contains("远程新闻"))
    assertTrue(screen.contains("小龙虾新闻"))
    assertTrue(screen.contains("设置"))

    val articleList = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListScreen.kt").readText()
    val articleVm = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticlesViewModel.kt").readText()
    assertTrue(articleList.contains("showFavoritesOnly: Boolean = false"))
    assertTrue(articleVm.contains("fun setFavoritesOnly"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.homeTabsAreReducedAndFirstTabIsUnifiedNews --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsMenuContainsSecondaryDestinations`

Expected: fails because UI does not exist and tabs are unchanged.

- [ ] **Step 3: Add ViewModel**

Create `UnifiedNewsViewModel.kt`:

```kotlin
package com.dailysatori.ui.feature.unifiednews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.UnifiedNewsSummaryRepository
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryService
import com.dailysatori.service.unifiednews.UnifiedNewsWindowKey
import com.dailysatori.service.unifiednews.unifiedNewsWindowFor
import com.dailysatori.shared.db.Unified_news_source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

enum class UnifiedNewsPage { SUMMARY, LOCAL_ARTICLES, LOCAL_FAVORITES, REMOTE_NEWS, CRAYFISH_NEWS, SETTINGS }

data class UnifiedNewsState(
    val page: UnifiedNewsPage = UnifiedNewsPage.SUMMARY,
    val title: String = "新闻汇总",
    val content: String = "",
    val status: String = "",
    val error: String? = null,
    val sources: List<Unified_news_source> = emptyList(),
    val isLoading: Boolean = false,
)

class UnifiedNewsViewModel(
    private val repo: UnifiedNewsSummaryRepository,
    private val service: UnifiedNewsSummaryService,
) : ViewModel() {
    private val _state = MutableStateFlow(UnifiedNewsState())
    val state: StateFlow<UnifiedNewsState> = _state.asStateFlow()

    fun loadInitial() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val latest = repo.getLatestSuccessful()
            _state.update {
                if (latest == null) it.copy(isLoading = false, status = "暂无总结")
                else it.copy(
                    isLoading = false,
                    title = latest.title,
                    content = latest.content,
                    status = latest.window_key,
                    sources = repo.getSources(latest.id),
                )
            }
        }
    }

    fun switchPage(page: UnifiedNewsPage) = _state.update { it.copy(page = page) }

    fun regenerateCurrentWindow() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            val window = unifiedNewsWindowFor(UnifiedNewsWindowKey.W2100, Clock.System.now())
            val result = service.generate(window, force = true)
            if (!result.success) _state.update { it.copy(error = result.message, isLoading = false) }
            loadInitial()
        }
    }
}
```

- [ ] **Step 4: Make local favorites reachable as a filtered article list**

In `ArticlesViewModel.kt`, add:

```kotlin
fun setFavoritesOnly(enabled: Boolean) {
    if (_state.value.showFavoritesOnly == enabled) return
    _state.update { it.copy(showFavoritesOnly = enabled) }
    loadArticles()
}
```

In `ArticleListScreen.kt`, change the signature:

```kotlin
fun ArticleListScreen(
    onArticleClick: (Long) -> Unit = {},
    showFavoritesOnly: Boolean = false,
)
```

After ViewModel/state setup, add:

```kotlin
LaunchedEffect(showFavoritesOnly) {
    viewModel.setFavoritesOnly(showFavoritesOnly)
}
```

- [ ] **Step 5: Add citation text renderer**

Create `CitationText.kt`:

```kotlin
package com.dailysatori.ui.feature.unifiednews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dailysatori.shared.db.Unified_news_source

@Composable
fun CitationText(
    content: String,
    sources: List<Unified_news_source>,
    onCitationClick: (Unified_news_source) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourceByRef = sources.associateBy { it.ref_key }
    FlowRow(modifier = modifier) {
        splitCitationText(content).forEach { part ->
            val source = sourceByRef[part.refKey]
            if (source != null) {
                Text(
                    text = "[${part.text}]",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onCitationClick(source) },
                )
            } else {
                Text(part.text)
            }
        }
    }
}

data class CitationPart(val text: String, val refKey: String? = null)

fun splitCitationText(content: String): List<CitationPart> {
    val regex = Regex("\\[([RCDF]\\d+)]")
    val parts = mutableListOf<CitationPart>()
    var cursor = 0
    regex.findAll(content).forEach { match ->
        if (match.range.first > cursor) parts += CitationPart(content.substring(cursor, match.range.first))
        parts += CitationPart(match.groupValues[1], match.groupValues[1])
        cursor = match.range.last + 1
    }
    if (cursor < content.length) parts += CitationPart(content.substring(cursor))
    return parts
}
```

- [ ] **Step 6: Add screen and menu**

Create `UnifiedNewsScreen.kt`:

```kotlin
package com.dailysatori.ui.feature.unifiednews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.ArticleListScreen
import com.dailysatori.ui.feature.crayfishnews.CrayfishNewsScreen
import com.dailysatori.ui.feature.remotenews.RemoteNewsScreen
import com.dailysatori.ui.feature.settings.SettingsScreen
import com.dailysatori.ui.feature.settings.SettingsViewModel
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun UnifiedNewsScreen(
    settingsViewModel: SettingsViewModel,
    onArticleClick: (Long) -> Unit,
) {
    val viewModel: UnifiedNewsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadInitial() }

    when (state.page) {
        UnifiedNewsPage.LOCAL_ARTICLES -> ArticleListScreen(onArticleClick = onArticleClick)
        UnifiedNewsPage.LOCAL_FAVORITES -> ArticleListScreen(onArticleClick = onArticleClick, showFavoritesOnly = true)
        UnifiedNewsPage.REMOTE_NEWS -> RemoteNewsScreen()
        UnifiedNewsPage.CRAYFISH_NEWS -> CrayfishNewsScreen(onBackToRemoteNews = { viewModel.switchPage(UnifiedNewsPage.SUMMARY) })
        UnifiedNewsPage.SETTINGS -> SettingsScreen(settingsViewModel)
        UnifiedNewsPage.SUMMARY -> UnifiedNewsSummaryContent(state, viewModel)
    }
}

@Composable
private fun UnifiedNewsSummaryContent(state: UnifiedNewsState, viewModel: UnifiedNewsViewModel) {
    AppScaffold(title = "新闻汇总", showBack = false, actions = { UnifiedNewsMenu(viewModel) }) { modifier ->
        if (state.isLoading) {
            LoadingIndicator(modifier = modifier)
        } else {
            Column(modifier = modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(state.title)
                if (state.error != null) Text(state.error)
                CitationText(content = state.content.ifBlank { "暂无统一新闻总结" }, sources = state.sources, onCitationClick = {})
            }
        }
    }
}

@Composable
private fun UnifiedNewsMenu(viewModel: UnifiedNewsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(text = { Text("本地文章") }, leadingIcon = { Icon(Icons.Default.Article, null) }, onClick = { viewModel.switchPage(UnifiedNewsPage.LOCAL_ARTICLES); expanded = false })
        DropdownMenuItem(text = { Text("本地收藏") }, leadingIcon = { Icon(Icons.Default.Favorite, null) }, onClick = { viewModel.switchPage(UnifiedNewsPage.LOCAL_FAVORITES); expanded = false })
        DropdownMenuItem(text = { Text("远程新闻") }, leadingIcon = { Icon(Icons.Default.Article, null) }, onClick = { viewModel.switchPage(UnifiedNewsPage.REMOTE_NEWS); expanded = false })
        DropdownMenuItem(text = { Text("小龙虾新闻") }, leadingIcon = { Icon(Icons.Default.Article, null) }, onClick = { viewModel.switchPage(UnifiedNewsPage.CRAYFISH_NEWS); expanded = false })
        DropdownMenuItem(text = { Text("设置") }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { viewModel.switchPage(UnifiedNewsPage.SETTINGS); expanded = false })
        DropdownMenuItem(text = { Text("刷新/重新生成") }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { viewModel.regenerateCurrentWindow(); expanded = false })
    }
}
```

- [ ] **Step 7: Register ViewModel and replace tabs**

In `ViewModelModule.kt`, import and add:

```kotlin
import com.dailysatori.ui.feature.unifiednews.UnifiedNewsViewModel
```

```kotlin
viewModel { UnifiedNewsViewModel(get(), get()) }
```

In `HomeScreen.kt`, import `UnifiedNewsScreen`, change `tabs` to four items:

```kotlin
val tabs = listOf(
    TabItem("新闻汇总", Icons.Filled.Language, Icons.Outlined.Language),
    TabItem("日记", Icons.Filled.Book, Icons.Outlined.Book),
    TabItem("读书", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    TabItem("AI", Icons.Filled.SmartToy, Icons.Outlined.SmartToy),
)

const val AI_CHAT_TAB_INDEX = 3
```

Change content mapping:

```kotlin
0 -> UnifiedNewsScreen(settingsViewModel = settingsViewModel, onArticleClick = onArticleClick)
1 -> DiaryScreen()
2 -> BooksScreen(
    selectedBookId = selectedBookId,
    selectedViewpointId = selectedViewpointId,
    bookAnalysisMessage = bookAnalysisMessage,
    onSelectedBookConsumed = onSelectedBookConsumed,
)
AI_CHAT_TAB_INDEX -> AiChatScreen(onArticleClick = onAiArticleClick)
```

- [ ] **Step 8: Run tests and compile**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.homeTabsAreReducedAndFirstTabIsUnifiedNews --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsMenuContainsSecondaryDestinations :app:compileDebugKotlin`

Expected: tests pass and app compiles. If `FlowRow` import is unavailable, implement the same token-splitting behavior with a `Row`/`Column` layout in `CitationText.kt`; citations must remain individually clickable in this task.

---

### Task 6: Citation Click Routing To Source Details

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Add citation routing source-level test**

Append:

```kotlin
@Test
fun unifiedNewsCitationRoutingHandlesAllSourceTypes() {
    val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
    val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

    assertTrue(viewModel.contains("sealed class UnifiedNewsNavigationTarget"))
    assertTrue(viewModel.contains("RemoteDigest"))
    assertTrue(viewModel.contains("RemoteArticle"))
    assertTrue(viewModel.contains("CrayfishGeneral"))
    assertTrue(viewModel.contains("CrayfishDji"))
    assertTrue(viewModel.contains("LocalArticle"))
    assertTrue(viewModel.contains("selectedRemoteDigest"))
    assertTrue(viewModel.contains("selectedRemoteArticle"))
    assertTrue(viewModel.contains("selectedCrayfishDetail"))
    assertTrue(screen.contains("onCitationClick"))
    assertTrue(screen.contains("RemoteDigestDetailScreen"))
    assertTrue(screen.contains("RemoteArticleDetailScreen"))
    assertTrue(screen.contains("CrayfishNewsDetailScreen"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsCitationRoutingHandlesAllSourceTypes`

Expected: fails because targets are not implemented.

- [ ] **Step 3: Add navigation target model and citation handler**

In `UnifiedNewsViewModel.kt`, extend constructor dependencies so citation clicks can open source details directly:

```kotlin
class UnifiedNewsViewModel(
    private val repo: UnifiedNewsSummaryRepository,
    private val service: UnifiedNewsSummaryService,
    private val settingRepo: com.dailysatori.data.repository.SettingRepository,
    private val remoteNewsService: com.dailysatori.service.remotenews.RemoteNewsService,
    private val crayfishNewsService: com.dailysatori.service.crayfishnews.CrayfishNewsService,
) : ViewModel() {
```

Update `ViewModelModule.kt` registration:

```kotlin
viewModel { UnifiedNewsViewModel(get(), get(), get(), get(), get()) }
```

Add target and detail state:

```kotlin
sealed class UnifiedNewsNavigationTarget {
    data class RemoteDigest(val id: Long) : UnifiedNewsNavigationTarget()
    data class RemoteArticle(val id: Long) : UnifiedNewsNavigationTarget()
    data class CrayfishGeneral(val filename: String) : UnifiedNewsNavigationTarget()
    data class CrayfishDji(val filename: String) : UnifiedNewsNavigationTarget()
    data class LocalArticle(val id: Long) : UnifiedNewsNavigationTarget()
}
```

Extend `UnifiedNewsState`:

```kotlin
val navigationTarget: UnifiedNewsNavigationTarget? = null,
val selectedRemoteDigest: com.dailysatori.service.remotenews.RemoteDigest? = null,
val selectedRemoteArticle: com.dailysatori.service.remotenews.RemoteArticle? = null,
val selectedCrayfishDetail: com.dailysatori.service.crayfishnews.CrayfishNewsDetail? = null,
val selectedCrayfishTitle: String = "小龙虾新闻",
```

Add handler and direct open functions:

```kotlin
fun openCitation(source: com.dailysatori.shared.db.Unified_news_source) {
    val target = when (source.source_type) {
        "remote_digest" -> source.source_id?.let { UnifiedNewsNavigationTarget.RemoteDigest(it) }
        "remote_article" -> source.source_id?.let { UnifiedNewsNavigationTarget.RemoteArticle(it) }
        "crayfish_general" -> source.source_filename?.let { UnifiedNewsNavigationTarget.CrayfishGeneral(it) }
        "crayfish_dji" -> source.source_filename?.let { UnifiedNewsNavigationTarget.CrayfishDji(it) }
        "local_favorite" -> source.source_id?.let { UnifiedNewsNavigationTarget.LocalArticle(it) }
        else -> null
    }
    if (target == null) _state.update { it.copy(error = "来源无法打开") }
    else openTarget(target)
}

fun clearNavigationTarget() = _state.update { it.copy(navigationTarget = null) }

fun closeSourceDetail() = _state.update {
    it.copy(selectedRemoteDigest = null, selectedRemoteArticle = null, selectedCrayfishDetail = null, navigationTarget = null)
}

private fun openTarget(target: UnifiedNewsNavigationTarget) {
    when (target) {
        is UnifiedNewsNavigationTarget.LocalArticle -> _state.update { it.copy(navigationTarget = target) }
        is UnifiedNewsNavigationTarget.RemoteDigest -> openRemoteDigest(target.id)
        is UnifiedNewsNavigationTarget.RemoteArticle -> openRemoteArticle(target.id)
        is UnifiedNewsNavigationTarget.CrayfishGeneral -> openCrayfish("general", target.filename, "小龙虾新闻")
        is UnifiedNewsNavigationTarget.CrayfishDji -> openCrayfish("dji", target.filename, "大疆新闻")
    }
}

private fun openRemoteDigest(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val config = remoteNewsService.configOrFailure(
            settingRepo.get(com.dailysatori.config.SettingKeys.remoteNewsBaseUrl),
            settingRepo.get(com.dailysatori.config.SettingKeys.remoteNewsApiToken),
        )
        if (config !is com.dailysatori.service.remotenews.RemoteNewsResult.Success) {
            _state.update { it.copy(error = "远程新闻无法打开") }
            return@launch
        }
        when (val result = remoteNewsService.fetchDigest(config.value, id)) {
            is com.dailysatori.service.remotenews.RemoteNewsResult.Success -> _state.update { it.copy(selectedRemoteDigest = result.value.digest) }
            is com.dailysatori.service.remotenews.RemoteNewsResult.Failure -> _state.update { it.copy(error = result.message) }
        }
    }
}

private fun openRemoteArticle(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        val config = remoteNewsService.configOrFailure(
            settingRepo.get(com.dailysatori.config.SettingKeys.remoteNewsBaseUrl),
            settingRepo.get(com.dailysatori.config.SettingKeys.remoteNewsApiToken),
        )
        if (config !is com.dailysatori.service.remotenews.RemoteNewsResult.Success) {
            _state.update { it.copy(error = "远程文章无法打开") }
            return@launch
        }
        when (val result = remoteNewsService.fetchArticle(config.value, id)) {
            is com.dailysatori.service.remotenews.RemoteNewsResult.Success -> _state.update { it.copy(selectedRemoteArticle = result.value.article) }
            is com.dailysatori.service.remotenews.RemoteNewsResult.Failure -> _state.update { it.copy(error = result.message) }
        }
    }
}

private fun openCrayfish(category: String, filename: String, title: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val config = crayfishNewsService.configOrFailure(
            settingRepo.get(com.dailysatori.config.SettingKeys.crayfishNewsBaseUrl),
            settingRepo.get(com.dailysatori.config.SettingKeys.crayfishNewsApiToken),
        )
        if (config !is com.dailysatori.service.crayfishnews.CrayfishNewsResult.Success) {
            _state.update { it.copy(error = "小龙虾新闻无法打开") }
            return@launch
        }
        when (val result = crayfishNewsService.fetchNewsFile(config.value, category, filename)) {
            is com.dailysatori.service.crayfishnews.CrayfishNewsResult.Success -> _state.update { it.copy(selectedCrayfishDetail = result.value, selectedCrayfishTitle = title) }
            is com.dailysatori.service.crayfishnews.CrayfishNewsResult.Failure -> _state.update { it.copy(error = result.message) }
        }
    }
}
```

- [ ] **Step 4: Wire all source detail destinations**

In `UnifiedNewsScreen`, pass citation clicks:

```kotlin
CitationText(
    content = state.content.ifBlank { "暂无统一新闻总结" },
    sources = state.sources,
    onCitationClick = viewModel::openCitation,
)
```

At the top of `UnifiedNewsScreen`, show direct details before the page switch:

```kotlin
when {
    state.selectedRemoteArticle != null -> {
        RemoteArticleDetailScreen(article = state.selectedRemoteArticle!!, onBack = viewModel::closeSourceDetail)
        return
    }
    state.selectedRemoteDigest != null -> {
        RemoteDigestDetailScreen(
            digest = state.selectedRemoteDigest!!,
            onBack = viewModel::closeSourceDetail,
            onArticleClick = { id -> viewModel.openCitationSource("remote_article", id, null) },
        )
        return
    }
    state.selectedCrayfishDetail != null -> {
        CrayfishNewsDetailScreen(news = state.selectedCrayfishDetail!!, onBack = viewModel::closeSourceDetail)
        return
    }
}
```

Add this public helper to `UnifiedNewsViewModel` so nested remote digest article clicks also open details directly:

```kotlin
fun openCitationSource(sourceType: String, sourceId: Long?, filename: String?) {
    val source = object {
        val source_type = sourceType
        val source_id = sourceId
        val source_filename = filename
    }
    val target = when (source.source_type) {
        "remote_article" -> source.source_id?.let { UnifiedNewsNavigationTarget.RemoteArticle(it) }
        "remote_digest" -> source.source_id?.let { UnifiedNewsNavigationTarget.RemoteDigest(it) }
        "crayfish_general" -> source.source_filename?.let { UnifiedNewsNavigationTarget.CrayfishGeneral(it) }
        "crayfish_dji" -> source.source_filename?.let { UnifiedNewsNavigationTarget.CrayfishDji(it) }
        else -> null
    }
    if (target != null) openTarget(target)
}
```

Then handle local article navigation:

```kotlin
LaunchedEffect(state.navigationTarget) {
    val target = state.navigationTarget
    if (target is UnifiedNewsNavigationTarget.LocalArticle) {
        onArticleClick(target.id)
        viewModel.clearNavigationTarget()
    }
}
```

- [ ] **Step 5: Run tests and compile**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsCitationRoutingHandlesAllSourceTypes :app:compileDebugKotlin`

Expected: test passes and app compiles. Local, remote, and Crayfish citations all open a concrete detail screen directly rather than only switching to a list page.

---

### Task 7: Final Verification And Device Install

**Files:**
- All files modified above.

- [ ] **Step 1: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: all unit tests pass.

- [ ] **Step 2: Run required compile check**

Run: `./gradlew :app:compileDebugKotlin`

Expected: compilation succeeds.

- [ ] **Step 3: Run full debug assemble**

Run: `./gradlew :app:assembleDebug`

Expected: APK builds successfully.

- [ ] **Step 4: Install and launch on connected device**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: install succeeds.

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: app launches.

- [ ] **Step 5: Manual smoke test**

On device:

- Confirm the first bottom tab is `新闻汇总`.
- Confirm bottom tabs are `新闻汇总`, `日记`, `读书`, `AI`.
- Open `新闻汇总` top-right menu and verify `本地文章`, `本地收藏`, `远程新闻`, `小龙虾新闻`, `设置`, and `刷新/重新生成` are present.
- Tap `本地文章` and open an article detail.
- Tap `设置` and verify settings opens from the menu.
- If a generated summary exists, tap a local favorite citation and verify it opens the local article detail.

---

## Self-Review

Spec coverage:

- Natural-day windows: Task 1, Task 4.
- Reliable WorkManager scheduling and startup backfill: Task 4.
- Persistence and migration: Task 2.
- Source collection and AI generation: Task 3.
- Clickable citations and navigation: Task 5, Task 6.
- Reduced bottom navigation and top-right secondary menu: Task 5.
- Verification commands: Task 7.

Known implementation risk:

- Direct remote and Crayfish citation opens require fetching source details from `UnifiedNewsViewModel`. Keep that logic small and reuse existing detail composables instead of duplicating detail UI.
