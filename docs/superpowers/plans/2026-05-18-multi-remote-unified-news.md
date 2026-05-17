# Multi-Remote Unified News Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace separate remote/crayfish news handling with multiple configurable unified remote news services that all expose `top_articles_today` and feed one daily AI summary.

**Architecture:** Add a first-class `remote_news_source` table and repository, migrate the current single remote settings into one enabled source, and make unified summary generation iterate every enabled source. The remote service will use the new uniform `GET /api/v1/external/top_articles_today` endpoint and store all fetched remote articles as `remote_article` sources with `R` citations.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Ktor client, Jetpack Compose, Koin, WorkManager, JUnit.

---

## File Map

- Modify `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`: add `remote_news_source` table and SQLDelight queries.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`: bump schema version and remove source-type assumptions where needed.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`: add `migrateV7ToV8()` and migrate old `setting` rows into `remote_news_source`.
- Create `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteNewsSourceRepository.kt`: CRUD for configured remote news sources.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsModels.kt`: add `RemoteNewsSourceConfig` or extend existing config with `id/name`.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsService.kt`: add `fetchTopArticlesToday()` and dual auth headers.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsModels.kt`: remove `CRAYFISH_*` source types from unified generation path.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`: iterate all enabled remote sources, stop calling `CrayfishNewsService` for summaries, map all remote items to `REMOTE_ARTICLE`.
- Modify `app/src/main/kotlin/com/dailysatori/core/di/RepositoryModule.kt` or equivalent DI file: provide `RemoteNewsSourceRepository`.
- Modify `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`: update `RemoteNewsSettingsViewModel` dependencies.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsViewModel.kt`: support list CRUD.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsScreen.kt`: replace single-form settings with multi-source list/editor.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`: remove “小龙虾新闻” menu item and crayfish title overrides.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`: keep remote article details; remove crayfish navigation from unified summary path where no longer needed.
- Modify `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`: add regression tests for schema, endpoint, settings, and source collection.

---

### Task 1: Schema And Migration

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write failing schema migration test**

Add this test to `UnifiedNewsBehaviorTest`:

```kotlin
@Test
fun remoteNewsSourcesHaveDedicatedTableAndMigration() {
    val schema = java.io.File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
    val config = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt").readText()
    val migration = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt").readText()

    assertTrue(schema.contains("CREATE TABLE remote_news_source"))
    assertTrue(schema.contains("name TEXT NOT NULL"))
    assertTrue(schema.contains("base_url TEXT NOT NULL"))
    assertTrue(schema.contains("api_token TEXT NOT NULL"))
    assertTrue(schema.contains("enabled INTEGER NOT NULL DEFAULT 1"))
    assertTrue(schema.contains("selectEnabledRemoteNewsSources"))
    assertTrue(schema.contains("upsertRemoteNewsSource"))
    assertTrue(config.contains("currentSchemaVersion = 8L"))
    assertTrue(migration.contains("if (currentVersion < 8)"))
    assertTrue(migration.contains("migrateV7ToV8()"))
    assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS remote_news_source"))
    assertTrue(migration.contains("remote_news_base_url"))
    assertTrue(migration.contains("remote_news_api_token"))
}
```

- [ ] **Step 2: Run test and verify red**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.remoteNewsSourcesHaveDedicatedTableAndMigration --no-configuration-cache
```

Expected: FAIL because the table and migration do not exist.

- [ ] **Step 3: Add schema table and queries**

In `DailySatori.sq`, add the table near settings tables:

```sql
CREATE TABLE remote_news_source (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    base_url TEXT NOT NULL,
    api_token TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

Add queries near settings or unified news queries:

```sql
selectRemoteNewsSources:
SELECT * FROM remote_news_source ORDER BY created_at ASC;

selectEnabledRemoteNewsSources:
SELECT * FROM remote_news_source WHERE enabled = 1 ORDER BY created_at ASC;

selectRemoteNewsSourceById:
SELECT * FROM remote_news_source WHERE id = ?;

insertRemoteNewsSource:
INSERT INTO remote_news_source (name, base_url, api_token, enabled, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?);

updateRemoteNewsSource:
UPDATE remote_news_source
SET name = ?, base_url = ?, api_token = ?, enabled = ?, updated_at = ?
WHERE id = ?;

upsertRemoteNewsSource:
INSERT INTO remote_news_source (id, name, base_url, api_token, enabled, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON CONFLICT(id) DO UPDATE SET
    name = excluded.name,
    base_url = excluded.base_url,
    api_token = excluded.api_token,
    enabled = excluded.enabled,
    updated_at = excluded.updated_at;

deleteRemoteNewsSource:
DELETE FROM remote_news_source WHERE id = ?;
```

- [ ] **Step 4: Bump schema version and add migration**

In `Config.kt`, update:

```kotlin
object DatabaseConfig {
    const val name = "daily_satori.db"
    const val currentSchemaVersion = 8L
}
```

In `DatabaseMigration.runMigrations()` add after V7:

```kotlin
if (currentVersion < 8) {
    migrateV7ToV8()
}
```

Add this method:

```kotlin
private fun migrateV7ToV8() {
    log.i { "Migration V7 -> V8: Remote news source table" }
    try {
        runSql("""
            CREATE TABLE IF NOT EXISTS remote_news_source (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                base_url TEXT NOT NULL,
                api_token TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())
        val baseUrl = settingRepo.get(SettingKeys.remoteNewsBaseUrl).orEmpty().trim()
        val token = settingRepo.get(SettingKeys.remoteNewsApiToken).orEmpty().trim()
        if (baseUrl.isNotBlank() && token.isNotBlank()) {
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            runSql("""
                INSERT INTO remote_news_source (name, base_url, api_token, enabled, created_at, updated_at)
                SELECT '远程新闻', '$baseUrl', '$token', 1, $now, $now
                WHERE NOT EXISTS (SELECT 1 FROM remote_news_source)
            """.trimIndent())
        }
        log.i { "Created remote_news_source table" }
    } catch (e: Exception) {
        log.w(e) { "Migration V7->V8 failed" }
    }
}
```

If using raw SQL interpolation is unacceptable, implement `runSql` calls with sanitized values by replacing single quotes:

```kotlin
val safeBaseUrl = baseUrl.replace("'", "''")
val safeToken = token.replace("'", "''")
```

- [ ] **Step 5: Run schema test green**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.remoteNewsSourcesHaveDedicatedTableAndMigration --no-configuration-cache
```

Expected: PASS.

---

### Task 2: Repository And DI

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteNewsSourceRepository.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/RepositoryModule.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write failing repository test**

Add:

```kotlin
@Test
fun remoteNewsSourceRepositoryProvidesCrudMethods() {
    val repo = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/RemoteNewsSourceRepository.kt")
    val di = java.io.File("src/main/kotlin/com/dailysatori/core/di/RepositoryModule.kt").readText()

    assertTrue(repo.exists())
    val source = repo.readText()
    assertTrue(source.contains("class RemoteNewsSourceRepository"))
    assertTrue(source.contains("fun getAll()"))
    assertTrue(source.contains("fun getEnabled()"))
    assertTrue(source.contains("fun save("))
    assertTrue(source.contains("fun delete("))
    assertTrue(di.contains("RemoteNewsSourceRepository"))
}
```

- [ ] **Step 2: Run test red**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.remoteNewsSourceRepositoryProvidesCrudMethods --no-configuration-cache
```

Expected: FAIL because repository file does not exist.

- [ ] **Step 3: Create repository**

Create `RemoteNewsSourceRepository.kt`:

```kotlin
package com.dailysatori.data.repository

import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Remote_news_source
import kotlinx.datetime.Clock

class RemoteNewsSourceRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): List<Remote_news_source> =
        q.selectRemoteNewsSources().executeAsList()

    fun getEnabled(): List<Remote_news_source> =
        q.selectEnabledRemoteNewsSources().executeAsList()

    fun save(
        id: Long?,
        name: String,
        baseUrl: String,
        apiToken: String,
        enabled: Boolean,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        if (id == null) {
            q.insertRemoteNewsSource(name.trim(), baseUrl.trim(), apiToken.trim(), if (enabled) 1 else 0, now, now)
        } else {
            q.updateRemoteNewsSource(name.trim(), baseUrl.trim(), apiToken.trim(), if (enabled) 1 else 0, now, id)
        }
    }

    fun delete(id: Long) = q.deleteRemoteNewsSource(id)
}
```

- [ ] **Step 4: Register DI**

In `RepositoryModule.kt`, add import and binding matching existing style:

```kotlin
import com.dailysatori.data.repository.RemoteNewsSourceRepository
```

Add:

```kotlin
single { RemoteNewsSourceRepository(get()) }
```

- [ ] **Step 5: Run repository test green**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.remoteNewsSourceRepositoryProvidesCrudMethods --no-configuration-cache
```

Expected: PASS.

---

### Task 3: Unified Remote Endpoint

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsService.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write failing endpoint test**

Add:

```kotlin
@Test
fun remoteNewsServiceSupportsTopArticlesTodayEndpointAndDualAuth() {
    val service = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsService.kt").readText()

    assertTrue(service.contains("fun fetchTopArticlesToday"))
    assertTrue(service.contains("top_articles_today"))
    assertTrue(service.contains("bearerAuth(config.token)"))
    assertTrue(service.contains("header(\"X-Api-Token\", config.token)"))
    assertTrue(service.contains("limit"))
}
```

- [ ] **Step 2: Run test red**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.remoteNewsServiceSupportsTopArticlesTodayEndpointAndDualAuth --no-configuration-cache
```

Expected: FAIL.

- [ ] **Step 3: Add endpoint method**

In `RemoteNewsService.kt`, import:

```kotlin
import io.ktor.client.request.header
```

Add method:

```kotlin
suspend fun fetchTopArticlesToday(config: RemoteNewsConfigValues, page: Int = 1, limit: Int = 50): RemoteNewsResult<RemoteArticlesResponse> =
    request {
        client.get(buildTopArticlesTodayUrl(config.baseUrl, page, limit)) {
            bearerAuth(config.token)
            header("X-Api-Token", config.token)
        }.body()
    }
```

Add URL builder:

```kotlin
fun buildTopArticlesTodayUrl(baseUrl: String, page: Int = 1, limit: Int = 50): String {
    val normalizedBase = baseUrl.trim().trimEnd('/')
    val builder = URLBuilder("$normalizedBase/api/v1/external/top_articles_today")
    builder.parameters.append("page", page.toString())
    builder.parameters.append("per_page", limit.toString())
    builder.parameters.append("limit", limit.toString())
    return builder.buildString()
}
```

- [ ] **Step 4: Run endpoint test green**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.remoteNewsServiceSupportsTopArticlesTodayEndpointAndDualAuth --no-configuration-cache
```

Expected: PASS.

---

### Task 4: Unified Summary Uses All Enabled Remote Sources

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt`
- Modify DI constructor injection where `UnifiedNewsSummaryService` is provided.
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write failing unified collection test**

Add:

```kotlin
@Test
fun unifiedNewsCollectsAllEnabledRemoteSourcesOnly() {
    val models = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsModels.kt").readText()
    val service = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/unifiednews/UnifiedNewsSummaryService.kt").readText()

    assertTrue(service.contains("remoteNewsSourceRepo.getEnabled()"))
    assertTrue(service.contains("fetchTopArticlesToday"))
    assertFalse(service.contains("collectCrayfishNews"))
    assertFalse(service.contains("CrayfishNewsService"))
    assertFalse(models.contains("CRAYFISH_GENERAL"))
    assertFalse(models.contains("CRAYFISH_DJI"))
    assertTrue(models.contains("REMOTE_ARTICLE(\"remote_article\", \"R\")"))
}
```

- [ ] **Step 2: Run test red**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsCollectsAllEnabledRemoteSourcesOnly --no-configuration-cache
```

Expected: FAIL.

- [ ] **Step 3: Update model source types**

In `UnifiedNewsModels.kt`, change enum to:

```kotlin
enum class UnifiedNewsSourceType(val dbValue: String, val prefix: String) {
    REMOTE_ARTICLE("remote_article", "R"),
    LOCAL_FAVORITE("local_favorite", "F"),
}
```

- [ ] **Step 4: Update summary service constructor**

Replace `CrayfishNewsService` dependency with `RemoteNewsSourceRepository`:

```kotlin
private val remoteNewsSourceRepo: RemoteNewsSourceRepository,
```

Remove `CrayfishNewsService` imports and related crayfish collection functions.

- [ ] **Step 5: Implement remote source collection**

Replace `collectSources()` body:

```kotlin
private suspend fun collectSources(
    window: UnifiedNewsWindow,
    warnings: MutableList<String>,
    ignoreSourceTimeFilter: Boolean,
): List<UnifiedNewsSourceItem> {
    val sources = mutableListOf<UnifiedNewsSourceItem>()
    val refCounts = mutableMapOf<String, Int>()
    addSources(sources, refCounts, collectConfiguredRemoteArticles(window, warnings, ignoreSourceTimeFilter))
    addSources(sources, refCounts, collectLocalFavorites(window))
    return sources
}
```

Add:

```kotlin
private suspend fun collectConfiguredRemoteArticles(
    window: UnifiedNewsWindow,
    warnings: MutableList<String>,
    ignoreSourceTimeFilter: Boolean,
): List<UnifiedNewsSourceItem> {
    val all = mutableListOf<UnifiedNewsSourceItem>()
    remoteNewsSourceRepo.getEnabled().forEach { source ->
        val config = remoteNewsService.configOrFailure(source.base_url, source.api_token)
        when (config) {
            is RemoteNewsResult.Failure -> warnings += "${source.name}: ${config.message}"
            is RemoteNewsResult.Success -> when (val result = remoteNewsService.fetchTopArticlesToday(config.value, page = 1, limit = 50)) {
                is RemoteNewsResult.Failure -> warnings += "${source.name}: ${result.message}"
                is RemoteNewsResult.Success -> all += result.value.articles.mapNotNull {
                    it.toUnifiedSource(window, ignoreSourceTimeFilter)
                }
            }
        }
    }
    return deduplicateUnifiedNewsSources(all)
}
```

Keep `RemoteArticle.toUnifiedSource(...)` and remove digest-specific collection methods if no other code uses them in summary service.

- [ ] **Step 6: Update DI provider**

Where `UnifiedNewsSummaryService` is constructed, pass `RemoteNewsSourceRepository` and remove `CrayfishNewsService` parameter if the constructor no longer needs it.

- [ ] **Step 7: Run unified collection test green**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsCollectsAllEnabledRemoteSourcesOnly --no-configuration-cache
```

Expected: PASS.

---

### Task 5: Multi-Remote Settings UI

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write failing settings test**

Add:

```kotlin
@Test
fun remoteNewsSettingsSupportMultipleSources() {
    val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsViewModel.kt").readText()
    val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsScreen.kt").readText()

    assertTrue(viewModel.contains("RemoteNewsSourceRepository"))
    assertTrue(viewModel.contains("sources: List<Remote_news_source>"))
    assertTrue(viewModel.contains("editingId"))
    assertTrue(viewModel.contains("updateName"))
    assertTrue(viewModel.contains("deleteSource"))
    assertTrue(screen.contains("名称"))
    assertTrue(screen.contains("URL"))
    assertTrue(screen.contains("Token"))
    assertTrue(screen.contains("新增远程新闻"))
}
```

- [ ] **Step 2: Run test red**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.remoteNewsSettingsSupportMultipleSources --no-configuration-cache
```

Expected: FAIL.

- [ ] **Step 3: Update ViewModel state and methods**

Replace state with:

```kotlin
data class RemoteNewsSettingsState(
    val sources: List<Remote_news_source> = emptyList(),
    val editingId: Long? = null,
    val name: String = "",
    val baseUrl: String = "",
    val token: String = "",
    val enabled: Boolean = true,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val message: String? = null,
)
```

Constructor:

```kotlin
class RemoteNewsSettingsViewModel(
    private val sourceRepo: RemoteNewsSourceRepository,
    private val remoteNewsService: RemoteNewsService,
) : ViewModel()
```

Add methods:

```kotlin
fun updateName(value: String) = _state.update { it.copy(name = value, message = null) }
fun updateBaseUrl(value: String) = _state.update { it.copy(baseUrl = value, message = null) }
fun updateToken(value: String) = _state.update { it.copy(token = value, message = null) }
fun updateEnabled(value: Boolean) = _state.update { it.copy(enabled = value, message = null) }

fun startAdd() = _state.update { it.copy(editingId = null, name = "", baseUrl = "", token = "", enabled = true, message = null) }

fun startEdit(source: Remote_news_source) = _state.update {
    it.copy(
        editingId = source.id,
        name = source.name,
        baseUrl = source.base_url,
        token = source.api_token,
        enabled = source.enabled == 1L,
        message = null,
    )
}

fun load() {
    viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(sources = sourceRepo.getAll()) }
    }
}

fun save() {
    viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(isSaving = true, message = null) }
        val current = state.value
        sourceRepo.save(current.editingId, current.name, current.baseUrl, current.token, current.enabled)
        _state.update { it.copy(isSaving = false, message = "远程新闻设置已保存") }
        load()
    }
}

fun deleteSource(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        sourceRepo.delete(id)
        _state.update { it.copy(message = "远程新闻已删除") }
        load()
    }
}
```

Update `testConnection()` to use `fetchTopArticlesToday(config.value, page = 1, limit = 1)`.

- [ ] **Step 4: Update screen**

Use the existing Material components in the file. The screen must include:

```kotlin
OutlinedTextField(value = state.name, onValueChange = viewModel::updateName, label = { Text("名称") })
OutlinedTextField(value = state.baseUrl, onValueChange = viewModel::updateBaseUrl, label = { Text("URL") })
OutlinedTextField(value = state.token, onValueChange = viewModel::updateToken, label = { Text("Token") })
Button(onClick = viewModel::save) { Text("保存") }
Button(onClick = viewModel::startAdd) { Text("新增远程新闻") }
```

Render `state.sources` as cards or list rows showing name and URL, with edit/delete actions.

- [ ] **Step 5: Run settings test green**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.remoteNewsSettingsSupportMultipleSources --no-configuration-cache
```

Expected: PASS.

---

### Task 6: Unified News UI Removes Crayfish Distinction

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsContentFormat.kt` if source labels mention crayfish.
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write failing UI test**

Add:

```kotlin
@Test
fun unifiedNewsUiNoLongerShowsCrayfishAsSeparateNewsType() {
    val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
    val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt").readText()
    val format = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsContentFormat.kt").readText()

    assertFalse(screen.contains("小龙虾新闻"))
    assertFalse(screen.contains("CrayfishNewsScreen"))
    assertFalse(screen.contains("crayfishTitleOverrides"))
    assertFalse(viewModel.contains("CrayfishArticle"))
    assertFalse(format.contains("小龙虾"))
    assertTrue(format.contains("remote_article") && format.contains("远程新闻"))
}
```

- [ ] **Step 2: Run UI test red**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsUiNoLongerShowsCrayfishAsSeparateNewsType --no-configuration-cache
```

Expected: FAIL.

- [ ] **Step 3: Remove menu item and title override**

In `UnifiedNewsScreen.kt`:

- Remove `CrayfishNewsScreen` import and page branch if only used from unified menu.
- Remove menu item labeled `小龙虾新闻`.
- Remove `crayfishTitleOverrides`; pass no `titleOverrides`, or keep generic title overrides only if still needed for all remote sources.

- [ ] **Step 4: Simplify ViewModel navigation target**

Remove crayfish-specific navigation target classes and open methods if no other summary code produces `crayfish_*`. Keep remote article detail loading.

- [ ] **Step 5: Update labels**

In `UnifiedNewsContentFormat.kt`, make labels:

```kotlin
fun unifiedNewsSourceTypeLabel(sourceType: String): String = when (sourceType) {
    "remote_article" -> "远程新闻"
    "local_favorite" -> "本地收藏"
    else -> "来源"
}
```

- [ ] **Step 6: Run UI test green**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest.unifiedNewsUiNoLongerShowsCrayfishAsSeparateNewsType --no-configuration-cache
```

Expected: PASS.

---

### Task 7: Full Verification And Device Install

**Files:**
- All modified files above.

- [ ] **Step 1: Run focused unified news tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Compile debug Kotlin**

Run:

```bash
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install to connected device**

Run:

```bash
adb devices -l
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug --no-configuration-cache
```

Expected: `Installed on 1 device.`

If no device is connected, run:

```bash
adb mdns services
adb connect <discovered-host>:<port>
```

- [ ] **Step 4: Restart app**

Run:

```bash
adb shell am force-stop com.dailysatori && adb shell am start -n com.dailysatori/.MainActivity
```

Expected: `Starting: Intent { cmp=com.dailysatori/.MainActivity }`

---

## Self-Review

- Spec coverage: The plan covers multi-source settings, schema migration, uniform `top_articles_today` interface, unified summary polling, no crayfish distinction, and verification.
- Placeholder scan: No TBD/TODO placeholders remain; every task has concrete files, code snippets, and commands.
- Type consistency: The repository uses SQLDelight generated `Remote_news_source`; ViewModel state and service collection use the same table fields `name`, `base_url`, `api_token`, and `enabled`.
