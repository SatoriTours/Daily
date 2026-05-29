# Diary Month AI Summary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the diary title bar status inset and add database-backed AI month summaries for the last three months plus daily current-month refresh checks.

**Architecture:** Add a SQLDelight-backed `diary_month_summary` cache keyed by `month_key`. A focused shared service computes month fingerprints from diary count and latest update time, generates summaries with the default AI config, and updates cache only when needed. `DiaryViewModel` triggers the background refresh and exposes cached summaries to `DiaryScreen` without blocking diary rendering.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Koin, Kotlin coroutines, Compose Material 3, existing `AiService` and `AiConfigService`.

---

### Task 1: Fix Title Bar Status Inset

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/theme/MainContentRhythmTest.kt`

- [ ] **Step 1: Add a source assertion for status bar padding**

Add this assertion to the diary-related part of `mainCardsUseSharedPaddingAndMarkdownPreset()`:

```kotlin
val diaryScreen = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt").readText()
assertTrue(diaryScreen.contains("statusBarsPadding()"))
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.MainContentRhythmTest --no-configuration-cache`

Expected: fails because `DiaryScreen.kt` does not contain `statusBarsPadding()`.

- [ ] **Step 3: Add status inset handling**

In `DiaryScreen.kt`, import `androidx.compose.foundation.layout.statusBarsPadding` and change the top-level content column to:

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .padding(horizontal = Spacing.m),
) {
```

- [ ] **Step 4: Run the focused test and compile**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.MainContentRhythmTest --no-configuration-cache`

Expected: pass.

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

---

### Task 2: Add Database Cache Table And Repository

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/DiaryMonthSummaryRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`

- [ ] **Step 1: Add SQLDelight table and queries**

Add after the `diary` table:

```sql
CREATE TABLE diary_month_summary (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    month_key TEXT NOT NULL UNIQUE,
    summary TEXT NOT NULL DEFAULT '',
    diary_count INTEGER NOT NULL DEFAULT 0,
    latest_diary_updated_at INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'pending',
    error_message TEXT,
    generated_at INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

Add queries after diary queries:

```sql
selectDiaryMonthSummaryByMonth:
SELECT * FROM diary_month_summary WHERE month_key = ?;

selectDiaryMonthSummaries:
SELECT * FROM diary_month_summary ORDER BY month_key DESC;

upsertDiaryMonthSummary:
INSERT INTO diary_month_summary (
    month_key, summary, diary_count, latest_diary_updated_at, status,
    error_message, generated_at, created_at, updated_at
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(month_key) DO UPDATE SET
    summary = excluded.summary,
    diary_count = excluded.diary_count,
    latest_diary_updated_at = excluded.latest_diary_updated_at,
    status = excluded.status,
    error_message = excluded.error_message,
    generated_at = excluded.generated_at,
    updated_at = excluded.updated_at;
```

- [ ] **Step 2: Add migration V11**

Change `DatabaseConfig.currentSchemaVersion` from `10L` to `11L`.

In `DatabaseMigration.runMigrations()`, add:

```kotlin
if (currentVersion < 11) {
    migrateV10ToV11()
}
```

Add the method:

```kotlin
private fun migrateV10ToV11() {
    log.i { "Migration V10 -> V11: Diary month summary cache" }
    try {
        runSql("""
            CREATE TABLE IF NOT EXISTS diary_month_summary (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                month_key TEXT NOT NULL UNIQUE,
                summary TEXT NOT NULL DEFAULT '',
                diary_count INTEGER NOT NULL DEFAULT 0,
                latest_diary_updated_at INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'pending',
                error_message TEXT,
                generated_at INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
        log.i { "Created diary_month_summary table" }
    } catch (e: Exception) {
        log.w(e) { "Could not create diary_month_summary table" }
    }
}
```

- [ ] **Step 3: Create repository**

Create `DiaryMonthSummaryRepository.kt`:

```kotlin
package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Diary_month_summary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class DiaryMonthSummaryRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Diary_month_summary>> =
        q.selectDiaryMonthSummaries().asFlow().mapToList(Dispatchers.IO)

    fun getByMonth(monthKey: String): Diary_month_summary? =
        q.selectDiaryMonthSummaryByMonth(monthKey).executeAsOneOrNull()

    fun upsert(
        monthKey: String,
        summary: String,
        diaryCount: Long,
        latestDiaryUpdatedAt: Long,
        status: String,
        errorMessage: String?,
        generatedAt: Long?,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.upsertDiaryMonthSummary(
            monthKey,
            summary,
            diaryCount,
            latestDiaryUpdatedAt,
            status,
            errorMessage,
            generatedAt,
            now,
            now,
        )
    }
}
```

- [ ] **Step 4: Register repository in Koin**

In `SharedModule.kt`, import and register:

```kotlin
import com.dailysatori.data.repository.DiaryMonthSummaryRepository
```

```kotlin
single { DiaryMonthSummaryRepository(get()) }
```

- [ ] **Step 5: Run SQLDelight/codegen compile**

Run: `./gradlew :app:compileDebugKotlin`

Expected: generated `Diary_month_summary` type resolves and build succeeds.

---

### Task 3: Implement Month Summary Service

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryMonthSummaryService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`

- [ ] **Step 1: Add deterministic helper tests**

Create `shared/src/commonTest/kotlin/com/dailysatori/service/diary/DiaryMonthSummaryServiceTest.kt` with tests for current month selection, refresh decision, and prompt truncation.

- [ ] **Step 2: Implement service**

Create a service with these public methods:

```kotlin
class DiaryMonthSummaryService(
    private val diaryRepo: DiaryRepository,
    private val summaryRepo: DiaryMonthSummaryRepository,
    private val aiConfigService: AiConfigService,
    private val aiService: AiService,
) {
    suspend fun refreshRecentMonthsIfNeeded(nowMs: Long = Clock.System.now().toEpochMilliseconds())
    fun fallbackSummary(diaries: List<Diary>): String
}
```

Behavior:
- Compute month keys for current month and previous two months.
- For current month, regenerate if count or latest `updated_at` differs from cache.
- For previous two months, generate only when no cache exists.
- Skip months with zero diaries.
- Skip generation when no default AI config exists.
- On AI failure, preserve existing summary when present; otherwise write status `failed` with empty summary.
- Prompt asks for one concise Chinese sentence, no Markdown title, no bullet list, no fake facts.

- [ ] **Step 3: Register service in Koin**

In `SharedModule.kt`, import and register:

```kotlin
import com.dailysatori.service.diary.DiaryMonthSummaryService
```

```kotlin
single { DiaryMonthSummaryService(get(), get(), get(), get()) }
```

- [ ] **Step 4: Run shared tests**

Run: `./gradlew :shared:testDebugUnitTest --no-configuration-cache`

Expected: pass.

---

### Task 4: Wire Summaries Into Diary UI

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`

- [ ] **Step 1: Extend state**

Add to `DiaryState`:

```kotlin
val monthSummaries: Map<String, String> = emptyMap(),
```

- [ ] **Step 2: Inject repository and service**

Change `DiaryViewModel` constructor:

```kotlin
class DiaryViewModel(
    private val diaryRepo: DiaryRepository,
    private val memoryExtractService: MemoryExtractService,
    private val monthSummaryRepo: DiaryMonthSummaryRepository,
    private val monthSummaryService: DiaryMonthSummaryService,
) : ViewModel()
```

Update `ViewModelModule.kt` accordingly.

- [ ] **Step 3: Collect cached summaries and trigger background refresh**

In `init`, collect `monthSummaryRepo.getAll()` into `monthSummaries`, and launch `monthSummaryService.refreshRecentMonthsIfNeeded()` on `Dispatchers.IO`.

- [ ] **Step 4: Display cached AI summary**

Change `DiaryMonthHeader` signature to accept `summary: String?`. At call site:

```kotlin
DiaryMonthHeader(
    diaries = state.diaries.filter { diaryMonthKey(it) == diaryMonthKey(diary) },
    summary = state.monthSummaries[diaryMonthKey(diary)],
)
```

Inside header:

```kotlin
text = summary?.takeIf { it.isNotBlank() } ?: diaryMonthSummary(diaries)
```

- [ ] **Step 5: Run app tests and compile**

Run: `./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest --no-configuration-cache`

Run: `./gradlew :app:compileDebugKotlin`

Expected: both pass.

---

### Task 5: Overall Logic, Function, And Design Review

**Files:**
- Inspect: `DiaryScreen.kt`, `DiaryCard.kt`, `DiaryEditorSheet.kt`, `DiaryEditorToolbar.kt`, `DiaryViewModel.kt`, new summary service/repository.

- [ ] **Step 1: Review logic risks**

Check that card tap expands only, edit happens only through menu, delete confirms, month summary refresh does not block UI, and failed AI generation preserves old summary.

- [ ] **Step 2: Review function risks**

Check current-month updates after diary insert/update/delete. If delete changes current month fingerprint, ensure next daily open refreshes. Do not trigger AI on every save to avoid extra token cost.

- [ ] **Step 3: Review design risks**

Check title bar is below system status bar, footer is compact/right-aligned when no tags, horizontal three-dot icon is used, and theme colors remain light/dark compatible.

- [ ] **Step 4: Run final verification**

Run: `./gradlew :app:compileDebugKotlin`

Run: `./gradlew :app:assembleDebug`

Run: `./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest --no-configuration-cache`

Expected: all pass. If a real device is connected, install with `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug -Pandroid.injected.device.serial=<serial>` and launch `adb -s <serial> shell am start -n com.dailysatori/.MainActivity`.

---

## Self-Review

Spec coverage: The plan covers status bar/title placement, database cache, migration, recent three-month backfill, daily current-month refresh checks, UI display, and review of current diary logic/design.

Placeholder scan: No placeholders or TBD items remain.

Type consistency: Repository, service, and ViewModel names are consistent across tasks. SQLDelight generated type uses expected snake-case class naming `Diary_month_summary`.
