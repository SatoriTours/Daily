# External Favorites Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first version of external favorites sync with an internal connector abstraction and an X Bookmarks connector that imports saved Posts into local favorite articles.

**Architecture:** Add SQLDelight tables for external favorite sources/items, repository APIs with encrypted source auth, a connector registry with X mapping/canonicalization, an importer that links external items to local `article` rows, a sync service/worker with explicit sync modes, and a settings page for source status/manual sync. OAuth UI is isolated in Android code; shared services remain testable without Android.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Koin, Ktor client, kotlinx.serialization, WorkManager, Compose Material3, kotlin.test.

---

## File Structure

- Modify `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`: add `external_favorite_source`, `external_favorite_item`, and SQLDelight queries.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`: bump schema version from `12L` to `13L`.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`: add V12 -> V13 migration for new tables.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/ExternalFavoriteModels.kt`: constants, sync modes, status values, DTOs, capabilities, source health.
- Create `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ExternalFavoriteSourceRepository.kt`: source CRUD, encrypted auth handling, source lifecycle.
- Create `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ExternalFavoriteItemRepository.kt`: item upsert, pending import/AI queries, status updates.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/XFavoriteCanonicalizer.kt`: X status URL normalization.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/XBookmarksConnector.kt`: X API response parsing and connector implementation.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/FavoriteConnectorRegistry.kt`: provider lookup.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/ExternalFavoriteImporter.kt`: external item -> local favorite article.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/ExternalFavoriteAiOrganizer.kt`: deterministic Markdown and bounded AI organization.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/FavoriteSyncService.kt`: sync orchestration and source-level token refresh guard.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`: register repositories/connectors/services.
- Create `app/src/main/kotlin/com/dailysatori/core/worker/ExternalFavoriteSyncWorker.kt`: WorkManager entry point.
- Modify `app/src/main/kotlin/com/dailysatori/core/di/AppModule.kt`: register scheduler.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt`: settings state/actions.
- Create `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt`: source list/detail actions.
- Modify `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`: register settings ViewModel.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`: add navigation row.
- Test files under `shared/src/commonTest/kotlin/com/dailysatori/service/externalfavorites/`, `shared/src/commonTest/kotlin/com/dailysatori/data/repository/`, `app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/`, and `app/src/test/kotlin/com/dailysatori/core/worker/`.

---

### Task 1: Database Schema and Migration

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/ExternalFavoriteSchemaTest.kt`

- [ ] **Step 1: Write the schema test**

Create `shared/src/commonTest/kotlin/com/dailysatori/data/repository/ExternalFavoriteSchemaTest.kt`:

```kotlin
package com.dailysatori.data.repository

import kotlin.test.Test
import kotlin.test.assertTrue

class ExternalFavoriteSchemaTest {
    @Test
    fun externalFavoriteSchemaDefinesSourceScopedIdentity() {
        val schema = this::class.java.classLoader
            ?.getResource("sqldelight/com/dailysatori/shared/db/DailySatori.sq")
            ?.readText()
            ?: java.io.File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()

        assertTrue("CREATE TABLE external_favorite_source" in schema)
        assertTrue("CREATE TABLE external_favorite_item" in schema)
        assertTrue("UNIQUE(source_id, external_id)" in schema)
        assertTrue("normalized_json TEXT NOT NULL DEFAULT ''" in schema)
        assertTrue("debug_json TEXT NOT NULL DEFAULT ''" in schema)
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :shared:allTests --tests com.dailysatori.data.repository.ExternalFavoriteSchemaTest`

Expected: FAIL because the new table names are missing from `DailySatori.sq`.

- [ ] **Step 3: Add tables and queries**

In `DailySatori.sq`, add after `remote_news_source`:

```sql
CREATE TABLE external_favorite_source (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    provider TEXT NOT NULL,
    display_name TEXT NOT NULL,
    account_id TEXT NOT NULL,
    account_name TEXT NOT NULL DEFAULT '',
    enabled INTEGER NOT NULL DEFAULT 1,
    sync_interval_minutes INTEGER NOT NULL DEFAULT 720,
    last_sync_started_at INTEGER,
    last_sync_completed_at INTEGER,
    last_success_at INTEGER,
    last_sync_window_started_at INTEGER,
    last_items_seen_count INTEGER NOT NULL DEFAULT 0,
    last_pages_seen_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT NOT NULL DEFAULT '',
    last_error_code TEXT NOT NULL DEFAULT '',
    last_error_message TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'idle',
    last_sync_mode TEXT NOT NULL DEFAULT 'recent',
    rate_limit_reset_at INTEGER,
    auth_json TEXT NOT NULL DEFAULT '',
    config_json TEXT NOT NULL DEFAULT '',
    capabilities_json TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    UNIQUE(provider, account_id)
);

CREATE TABLE external_favorite_item (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source_id INTEGER NOT NULL REFERENCES external_favorite_source(id) ON DELETE CASCADE,
    provider TEXT NOT NULL,
    external_id TEXT NOT NULL,
    canonical_url TEXT,
    title TEXT NOT NULL DEFAULT '',
    text TEXT NOT NULL DEFAULT '',
    author_name TEXT NOT NULL DEFAULT '',
    source_created_at INTEGER,
    favorited_at INTEGER,
    normalized_json TEXT NOT NULL DEFAULT '',
    debug_json TEXT NOT NULL DEFAULT '',
    content_hash TEXT NOT NULL DEFAULT '',
    ai_input_hash TEXT NOT NULL DEFAULT '',
    article_id INTEGER REFERENCES article(id) ON DELETE SET NULL,
    sync_status TEXT NOT NULL DEFAULT 'seen',
    import_status TEXT NOT NULL DEFAULT 'not_imported',
    ai_status TEXT NOT NULL DEFAULT 'pending',
    last_error_code TEXT NOT NULL DEFAULT '',
    last_error_message TEXT NOT NULL DEFAULT '',
    first_seen_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    UNIQUE(source_id, external_id)
);
```

Add source queries:

```sql
selectExternalFavoriteSources:
SELECT * FROM external_favorite_source ORDER BY provider, account_name, display_name;

selectEnabledExternalFavoriteSources:
SELECT * FROM external_favorite_source WHERE enabled = 1 ORDER BY provider, account_name, display_name;

selectExternalFavoriteSourceById:
SELECT * FROM external_favorite_source WHERE id = ?;

selectExternalFavoriteSourceByProviderAccount:
SELECT * FROM external_favorite_source WHERE provider = ? AND account_id = ?;

insertExternalFavoriteSource:
INSERT INTO external_favorite_source (
    provider, display_name, account_id, account_name, enabled, sync_interval_minutes,
    status, auth_json, config_json, capabilities_json, created_at, updated_at
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

updateExternalFavoriteSource:
UPDATE external_favorite_source
SET display_name = ?, account_name = ?, enabled = ?, sync_interval_minutes = ?,
    status = ?, auth_json = ?, config_json = ?, capabilities_json = ?, updated_at = ?
WHERE id = ?;

updateExternalFavoriteSourceSyncState:
UPDATE external_favorite_source
SET last_sync_started_at = ?, last_sync_completed_at = ?, last_success_at = ?,
    last_sync_window_started_at = ?, last_items_seen_count = ?, last_pages_seen_count = ?,
    last_error = ?, last_error_code = ?, last_error_message = ?, status = ?,
    last_sync_mode = ?, rate_limit_reset_at = ?, updated_at = ?
WHERE id = ?;

deleteExternalFavoriteSource:
DELETE FROM external_favorite_source WHERE id = ?;
```

Add item queries:

```sql
selectExternalFavoriteItemBySourceExternalId:
SELECT * FROM external_favorite_item WHERE source_id = ? AND external_id = ?;

selectExternalFavoriteItemsBySource:
SELECT * FROM external_favorite_item WHERE source_id = ? ORDER BY first_seen_at DESC;

selectExternalFavoriteItemsPendingImport:
SELECT * FROM external_favorite_item
WHERE import_status IN ('not_imported', 'failed')
ORDER BY first_seen_at DESC
LIMIT ?;

selectExternalFavoriteItemsPendingAi:
SELECT * FROM external_favorite_item
WHERE ai_status IN ('pending', 'failed') AND article_id IS NOT NULL
ORDER BY first_seen_at DESC
LIMIT ?;

insertExternalFavoriteItem:
INSERT INTO external_favorite_item (
    source_id, provider, external_id, canonical_url, title, text, author_name,
    source_created_at, favorited_at, normalized_json, debug_json, content_hash,
    ai_input_hash, article_id, sync_status, import_status, ai_status,
    last_error_code, last_error_message, first_seen_at, last_seen_at, created_at, updated_at
) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

updateExternalFavoriteItem:
UPDATE external_favorite_item
SET canonical_url = ?, title = ?, text = ?, author_name = ?, source_created_at = ?,
    favorited_at = ?, normalized_json = ?, debug_json = ?, content_hash = ?,
    ai_input_hash = ?, sync_status = ?, last_error_code = ?, last_error_message = ?,
    last_seen_at = ?, updated_at = ?
WHERE id = ?;

updateExternalFavoriteItemImportState:
UPDATE external_favorite_item
SET article_id = ?, import_status = ?, ai_status = ?, last_error_code = ?,
    last_error_message = ?, updated_at = ?
WHERE id = ?;

updateExternalFavoriteItemAiState:
UPDATE external_favorite_item
SET ai_status = ?, last_error_code = ?, last_error_message = ?, updated_at = ?
WHERE id = ?;
```

- [ ] **Step 4: Add migration**

In `Config.kt`, change:

```kotlin
const val currentSchemaVersion = 13L
```

In `DatabaseMigration.runMigrations()`, after V11 -> V12:

```kotlin
if (currentVersion < 13) {
    migrateV12ToV13()
}
```

Add `migrateV12ToV13()` with `CREATE TABLE IF NOT EXISTS` SQL matching the schema above.

- [ ] **Step 5: Run the schema test**

Run: `./gradlew :shared:allTests --tests com.dailysatori.data.repository.ExternalFavoriteSchemaTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt shared/src/commonTest/kotlin/com/dailysatori/data/repository/ExternalFavoriteSchemaTest.kt
git commit -m "feat: add external favorite schema"
```

---

### Task 2: Shared Models and Repositories

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/ExternalFavoriteModels.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ExternalFavoriteSourceRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ExternalFavoriteItemRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/ExternalFavoriteRepositoryTest.kt`

- [ ] **Step 1: Write repository tests**

Create `ExternalFavoriteRepositoryTest.kt` with tests for:

```kotlin
@Test
fun sourceRepositoryEncryptsAuthJsonAndDecryptsOnRead() {
    val fixture = externalFavoriteRepositoryFixture()
    val id = fixture.sourceRepo.saveXSourceForTest(
        displayName = "X Daily",
        accountId = "42",
        accountName = "daily",
        authJson = """{"access_token":"a"}""",
    )

    val raw = fixture.db.dailySatoriQueries.selectExternalFavoriteSourceById(id).executeAsOne()
    assertTrue(raw.auth_json.startsWith("enc:v1:"))
    assertEquals("""{"access_token":"a"}""", fixture.sourceRepo.getById(id)!!.auth_json)
}

@Test
fun itemRepositoryUpsertsBySourceScopedExternalId() {
    val fixture = externalFavoriteRepositoryFixture()
    val firstSource = fixture.sourceRepo.saveXSourceForTest("X One", "1", "one", "{}")
    val secondSource = fixture.sourceRepo.saveXSourceForTest("X Two", "2", "two", "{}")

    fixture.itemRepo.upsertDraft(firstSource, xDraft("123", "https://x.com/one/status/123"))
    fixture.itemRepo.upsertDraft(secondSource, xDraft("123", "https://x.com/one/status/123"))

    assertEquals(1, fixture.itemRepo.getBySource(firstSource).size)
    assertEquals(1, fixture.itemRepo.getBySource(secondSource).size)
}

@Test
fun deleteSourceRemovesSourceItemsButKeepsArticleRows() {
    val fixture = externalFavoriteRepositoryFixture()
    val sourceId = fixture.sourceRepo.saveXSourceForTest("X Daily", "42", "daily", "{}")
    val articleId = fixture.articleRepo.insert(title = "Saved", url = "https://x.com/daily/status/123", isFavorite = 1, status = "completed")
    val item = fixture.itemRepo.upsertDraft(sourceId, xDraft("123", "https://x.com/daily/status/123")).first
    fixture.itemRepo.markImported(item.id, articleId, duplicateLinked = false)

    fixture.sourceRepo.delete(sourceId)

    assertTrue(fixture.itemRepo.getBySource(sourceId).isEmpty())
    assertNotNull(fixture.articleRepo.getById(articleId))
}
```

Include helper functions in the same test file: `externalFavoriteRepositoryFixture()`, `saveXSourceForTest(...)`, and `xDraft(...)`. The fixture uses `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)`, `DailySatoriDatabase.Schema.create(driver)`, `DailySatoriDatabase(driver)`, a deterministic `SecretCipher` fake that prefixes `enc:v1:`, and the real repositories.

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :shared:allTests --tests com.dailysatori.data.repository.ExternalFavoriteRepositoryTest`

Expected: FAIL because repositories and model types do not exist.

- [ ] **Step 3: Add models**

Create `ExternalFavoriteModels.kt`:

```kotlin
package com.dailysatori.service.externalfavorites

enum class ExternalFavoriteProvider(val id: String) { X("x") }
enum class FavoriteSyncMode { recent, history, full_rescan, retry_failed }
enum class ExternalSourceStatus { idle, syncing, auth_required, auth_check_required, rate_limited, paused, failed }
enum class ExternalSourceHealth { healthy, needs_auth, limited, paused, failing, never_synced }
enum class ExternalItemSyncStatus { seen, skipped, stale, deleted_remote_unknown, failed }
enum class ExternalItemImportStatus { not_imported, imported, duplicate_linked, failed }
enum class ExternalItemAiStatus { not_needed, pending, processing, completed, failed }

data class FavoriteConnectorCapabilities(
    val maxPageSize: Int,
    val defaultBackoffMinutes: Int,
    val maxPagesPerRun: Int,
    val maxItemsPerRun: Int,
    val supportsFolders: Boolean,
    val supportsFavoritedAt: Boolean,
    val supportsWriteBack: Boolean,
    val supportsRefreshToken: Boolean,
)

data class ExternalFavoriteItemDraft(
    val provider: String,
    val externalId: String,
    val canonicalUrl: String?,
    val title: String,
    val text: String,
    val authorName: String,
    val sourceCreatedAt: Long?,
    val favoritedAt: Long?,
    val normalizedJson: String,
    val debugJson: String = "",
    val contentHash: String,
    val aiInputHash: String,
)

data class FavoriteFetchPage(
    val items: List<ExternalFavoriteItemDraft>,
    val nextCursor: String?,
    val rateLimitResetAt: Long? = null,
) {
    val exhausted: Boolean get() = nextCursor == null
}

fun sourceHealth(status: String, lastSuccessAt: Long?, lastErrorCode: String): ExternalSourceHealth = when (status) {
    "auth_required", "auth_check_required" -> ExternalSourceHealth.needs_auth
    "rate_limited" -> ExternalSourceHealth.limited
    "paused" -> ExternalSourceHealth.paused
    "failed" -> ExternalSourceHealth.failing
    else -> if (lastSuccessAt == null && lastErrorCode.isBlank()) ExternalSourceHealth.never_synced else ExternalSourceHealth.healthy
}
```

- [ ] **Step 4: Add repositories**

Implement `ExternalFavoriteSourceRepository` mirroring `RemoteNewsSourceRepository`: encrypt `authJson` with `SecretCipher.encrypt()`, decrypt on reads, `delete(id)`, and `markAuthCheckRequiredAfterRestore()`.

Implement `ExternalFavoriteItemRepository` with:

```kotlin
fun upsertDraft(sourceId: Long, draft: ExternalFavoriteItemDraft): Pair<External_favorite_item, Boolean>
fun pendingImport(limit: Long): List<External_favorite_item>
fun pendingAi(limit: Long): List<External_favorite_item>
fun markImported(itemId: Long, articleId: Long, duplicateLinked: Boolean)
fun markImportFailed(itemId: Long, code: String, message: String)
fun markAiState(itemId: Long, status: String, code: String = "", message: String = "")
```

- [ ] **Step 5: Register repositories**

In `SharedModule.kt`, add:

```kotlin
single { ExternalFavoriteSourceRepository(get(), get()) }
single { ExternalFavoriteItemRepository(get()) }
```

- [ ] **Step 6: Run tests**

Run: `./gradlew :shared:allTests --tests com.dailysatori.data.repository.ExternalFavoriteRepositoryTest`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/ExternalFavoriteModels.kt shared/src/commonMain/kotlin/com/dailysatori/data/repository/ExternalFavoriteSourceRepository.kt shared/src/commonMain/kotlin/com/dailysatori/data/repository/ExternalFavoriteItemRepository.kt shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt shared/src/commonTest/kotlin/com/dailysatori/data/repository/ExternalFavoriteRepositoryTest.kt
git commit -m "feat: add external favorite repositories"
```

---

### Task 3: X Canonicalizer and Connector Mapping

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/XFavoriteCanonicalizer.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/FavoriteConnectorRegistry.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/XBookmarksConnector.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/externalfavorites/XBookmarksConnectorTest.kt`

- [ ] **Step 1: Write connector tests**

Create tests:

```kotlin
@Test
fun canonicalizesXAndTwitterStatusUrls() {
    val urls = listOf(
        "https://twitter.com/jack/status/20?s=20",
        "https://mobile.twitter.com/jack/status/20",
        "https://x.com/i/status/20",
        "https://x.com/jack/status/20?utm_source=test",
    )
    assertEquals(setOf("https://x.com/jack/status/20", "https://x.com/i/status/20"), urls.map { canonicalizeXStatusUrl(it) }.toSet())
}

@Test
fun parsesBookmarkResponseIntoDrafts() {
    val json = """{"data":[{"id":"123","text":"Saved post","author_id":"42","created_at":"2026-06-01T00:00:00.000Z"}],"includes":{"users":[{"id":"42","username":"daily","name":"Daily"}]},"meta":{"result_count":1}}"""
    val page = XBookmarksResponseParser.parse(json)
    assertEquals("123", page.items.single().externalId)
    assertEquals("https://x.com/daily/status/123", page.items.single().canonicalUrl)
    assertEquals(null, page.nextCursor)
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :shared:allTests --tests com.dailysatori.service.externalfavorites.XBookmarksConnectorTest`

Expected: FAIL because parser/canonicalizer do not exist.

- [ ] **Step 3: Implement canonicalizer**

`XFavoriteCanonicalizer.kt` should extract `/status/{id}` or `/i/status/{id}`, normalize host to `x.com`, strip query/fragment, and prefer `https://x.com/{username}/status/{id}` when username is available.

- [ ] **Step 4: Implement connector parser**

`XBookmarksConnector.kt` should:

- Define serializable DTOs for `data`, `includes.users`, `includes.media`, `meta.next_token`.
- Build `ExternalFavoriteItemDraft`.
- Set capabilities with conservative values: `maxPageSize = 100`, `defaultBackoffMinutes = 15`, `maxPagesPerRun = 3`, `maxItemsPerRun = 300`.
- Build GET `/2/users/{accountId}/bookmarks` with `tweet.fields`, `expansions`, and `pagination_token`.

- [ ] **Step 5: Implement registry**

`FavoriteConnectorRegistry`:

```kotlin
class FavoriteConnectorRegistry(private val connectors: List<FavoriteConnector>) {
    fun get(provider: String): FavoriteConnector? = connectors.firstOrNull { it.provider == provider }
}
```

- [ ] **Step 6: Run tests**

Run: `./gradlew :shared:allTests --tests com.dailysatori.service.externalfavorites.XBookmarksConnectorTest`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/XFavoriteCanonicalizer.kt shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/FavoriteConnectorRegistry.kt shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/XBookmarksConnector.kt shared/src/commonTest/kotlin/com/dailysatori/service/externalfavorites/XBookmarksConnectorTest.kt
git commit -m "feat: add x bookmarks connector"
```

---

### Task 4: Importer and Deterministic Markdown

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/ExternalFavoriteImporter.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/externalfavorites/ExternalFavoriteImporterTest.kt`

- [ ] **Step 1: Write importer tests**

Tests:

```kotlin
@Test
fun importsXItemAsFavoriteArticleWithOriginalBlock() {
    val fixture = externalFavoriteRepositoryFixture()
    val sourceId = fixture.sourceRepo.saveXSourceForTest("X Daily", "42", "daily", "{}")
    fixture.itemRepo.upsertDraft(sourceId, xDraft("123", "https://x.com/daily/status/123", text = "Saved post"))

    val imported = ExternalFavoriteImporter(fixture.itemRepo, fixture.articleRepo).importPending(limit = 10)

    assertEquals(1, imported)
    val article = fixture.articleRepo.getByUrl("https://x.com/daily/status/123")!!
    assertEquals(1, article.is_favorite)
    assertTrue(article.ai_markdown_content!!.contains("## 原文"))
    assertTrue(article.ai_markdown_content!!.contains("Saved post"))
}

@Test
fun duplicateXItemsLinkToExistingArticleByCanonicalUrl() {
    val fixture = externalFavoriteRepositoryFixture()
    val firstSource = fixture.sourceRepo.saveXSourceForTest("X One", "1", "one", "{}")
    val secondSource = fixture.sourceRepo.saveXSourceForTest("X Two", "2", "two", "{}")
    fixture.itemRepo.upsertDraft(firstSource, xDraft("123", "https://x.com/daily/status/123"))
    fixture.itemRepo.upsertDraft(secondSource, xDraft("123", "https://x.com/daily/status/123"))

    ExternalFavoriteImporter(fixture.itemRepo, fixture.articleRepo).importPending(limit = 10)

    assertEquals(1, fixture.articleRepo.searchSync("https://x.com/daily/status/123").size)
}

@Test
fun importerDoesNotOverwriteUserCommentOrRicherMarkdown() {
    val fixture = externalFavoriteRepositoryFixture()
    val sourceId = fixture.sourceRepo.saveXSourceForTest("X Daily", "42", "daily", "{}")
    fixture.articleRepo.insert(
        title = "User title",
        aiMarkdownContent = "# Rich local note\n\nUser details",
        url = "https://x.com/daily/status/123",
        isFavorite = 1,
        comment = "Do not overwrite",
        status = "completed",
    )
    fixture.itemRepo.upsertDraft(sourceId, xDraft("123", "https://x.com/daily/status/123", text = "Short"))

    ExternalFavoriteImporter(fixture.itemRepo, fixture.articleRepo).importPending(limit = 10)

    val article = fixture.articleRepo.getByUrl("https://x.com/daily/status/123")!!
    assertEquals("Do not overwrite", article.comment)
    assertEquals("# Rich local note\n\nUser details", article.ai_markdown_content)
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :shared:allTests --tests com.dailysatori.service.externalfavorites.ExternalFavoriteImporterTest`

Expected: FAIL because importer does not exist.

- [ ] **Step 3: Add ArticleRepository helper**

Add:

```kotlin
fun saveExternalFavoriteArticle(
    title: String,
    url: String,
    summary: String,
    markdown: String,
    pubDate: Long?,
): Article
```

Implementation should find by URL first, preserve `comment`, preserve richer existing `ai_markdown_content`, set `is_favorite = 1`, and return the article.

- [ ] **Step 4: Implement importer**

`ExternalFavoriteImporter`:

```kotlin
class ExternalFavoriteImporter(
    private val itemRepo: ExternalFavoriteItemRepository,
    private val articleRepo: ArticleRepository,
) {
    fun importPending(limit: Long = 50): Int
}
```

Generate Markdown:

```markdown
# X 收藏

## 原文

- 作者：{author}
- 时间：{created}
- 链接：{url}

{text}

## AI 整理

待整理
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :shared:allTests --tests com.dailysatori.service.externalfavorites.ExternalFavoriteImporterTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/ExternalFavoriteImporter.kt shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt shared/src/commonTest/kotlin/com/dailysatori/service/externalfavorites/ExternalFavoriteImporterTest.kt
git commit -m "feat: import external favorites as articles"
```

---

### Task 5: Sync Service

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/FavoriteSyncService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/externalfavorites/FavoriteSyncServiceTest.kt`

- [ ] **Step 1: Write sync service tests**

Tests:

```kotlin
@Test
fun recentSyncUsesConnectorLimitsAndUpsertsItems() {
    val fixture = favoriteSyncServiceFixture(connector = FakeConnector(pageCount = 5, maxPagesPerRun = 2))
    val sourceId = fixture.sourceRepo.saveXSourceForTest("X Daily", "42", "daily", """{"access_token":"a"}""")

    fixture.service.syncSource(sourceId, FavoriteSyncMode.recent)

    assertEquals(2, fixture.connector.fetchCalls)
    assertEquals(2, fixture.itemRepo.getBySource(sourceId).size)
}

@Test
fun retryFailedDoesNotFetchProviderPages() {
    val fixture = favoriteSyncServiceFixture(connector = ThrowingFetchConnector())
    val sourceId = fixture.sourceRepo.saveXSourceForTest("X Daily", "42", "daily", """{"access_token":"a"}""")

    fixture.service.syncSource(sourceId, FavoriteSyncMode.retry_failed)

    assertEquals(0, fixture.connector.fetchCalls)
    assertEquals(1, fixture.importer.importCalls)
}

@Test
fun concurrentRefreshCannotOverwriteNewerAuth() = runTest {
    val fixture = favoriteSyncServiceFixture(connector = RefreshingConnector())
    val sourceId = fixture.sourceRepo.saveXSourceForTest("X Daily", "42", "daily", """{"refresh_token":"old"}""")

    coroutineScope {
        launch { fixture.service.syncSource(sourceId, FavoriteSyncMode.recent) }
        launch { fixture.service.syncSource(sourceId, FavoriteSyncMode.recent) }
    }

    assertEquals("""{"refresh_token":"newest"}""", fixture.sourceRepo.getById(sourceId)!!.auth_json)
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :shared:allTests --tests com.dailysatori.service.externalfavorites.FavoriteSyncServiceTest`

Expected: FAIL because sync service does not exist.

- [ ] **Step 3: Implement service**

`FavoriteSyncService.syncSource(sourceId: Long, mode: FavoriteSyncMode)` should:

- Load source.
- Resolve connector.
- Mark source syncing.
- Refresh auth under a per-source `Mutex`.
- Fetch pages until mode/capability limit or conservative early stop.
- Upsert items.
- Run importer for changed items.
- Run AI organizer within small budget.
- Store counts, status, errors, and `last_sync_mode`.

- [ ] **Step 4: Register service**

In `SharedModule.kt`, add:

```kotlin
single { FavoriteConnectorRegistry(listOf(get<XBookmarksConnector>())) }
single { XBookmarksConnector(get()) }
single { ExternalFavoriteImporter(get(), get()) }
single { ExternalFavoriteAiOrganizer(get(), get(), get()) }
single { FavoriteSyncService(get(), get(), get(), get(), get()) }
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :shared:allTests --tests com.dailysatori.service.externalfavorites.FavoriteSyncServiceTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/externalfavorites/FavoriteSyncService.kt shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt shared/src/commonTest/kotlin/com/dailysatori/service/externalfavorites/FavoriteSyncServiceTest.kt
git commit -m "feat: add external favorite sync service"
```

---

### Task 6: Android Worker and Scheduler

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/core/worker/ExternalFavoriteSyncWorker.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/AppModule.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/worker/ExternalFavoriteSyncWorkerTest.kt`

- [ ] **Step 1: Write worker request tests**

Test request construction:

```kotlin
@Test
fun buildsUniqueWorkNamePerSourceAndMode() {
    assertEquals("external-favorite-sync-7-recent", externalFavoriteSyncWorkName(7, "recent"))
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.worker.ExternalFavoriteSyncWorkerTest`

Expected: FAIL because worker helper does not exist.

- [ ] **Step 3: Implement worker**

Implement:

```kotlin
fun externalFavoriteSyncWorkName(sourceId: Long, mode: String) = "external-favorite-sync-$sourceId-$mode"

class ExternalFavoriteSyncScheduler(private val context: Context) {
    fun enqueue(sourceId: Long, mode: String = "recent") {
        val request = OneTimeWorkRequestBuilder<ExternalFavoriteSyncWorker>()
            .setInputData(workDataOf(ExternalFavoriteSyncWorker.KEY_SOURCE_ID to sourceId, ExternalFavoriteSyncWorker.KEY_MODE to mode))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(externalFavoriteSyncWorkName(sourceId, mode), ExistingWorkPolicy.KEEP, request)
    }

    fun enqueuePeriodic(sourceId: Long, intervalMinutes: Long) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<ExternalFavoriteSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setInputData(workDataOf(ExternalFavoriteSyncWorker.KEY_SOURCE_ID to sourceId, ExternalFavoriteSyncWorker.KEY_MODE to "recent"))
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(externalFavoriteSyncWorkName(sourceId, "periodic"), ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
```

Worker obtains `FavoriteSyncService` from Koin and calls `syncSource(sourceId, FavoriteSyncMode.valueOf(mode))`.

- [ ] **Step 4: Register scheduler**

In `AppModule.kt`:

```kotlin
single { ExternalFavoriteSyncScheduler(androidContext()) }
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.worker.ExternalFavoriteSyncWorkerTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/core/worker/ExternalFavoriteSyncWorker.kt app/src/main/kotlin/com/dailysatori/core/di/AppModule.kt app/src/test/kotlin/com/dailysatori/core/worker/ExternalFavoriteSyncWorkerTest.kt
git commit -m "feat: schedule external favorite sync"
```

---

### Task 7: Settings UI

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsTextTest.kt`

- [ ] **Step 1: Write UI text tests**

Create tests asserting text helper functions:

```kotlin
@Test
fun healthSubtitleDoesNotPromiseRealtimeSync() {
    assertFalse(externalFavoritePeriodicSyncSubtitle("healthy").contains("实时"))
    assertTrue(externalFavoritePeriodicSyncSubtitle("healthy").contains("定期"))
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsTextTest`

Expected: FAIL because helpers/screen do not exist.

- [ ] **Step 3: Implement ViewModel**

State should include sources, message, manual sync in-flight source id, and derived health. Actions:

- `load()`
- `syncNow(sourceId)`
- `importOlder(sourceId)`
- `toggleEnabled(sourceId, enabled)`
- `deleteSource(sourceId)`
- `markRestoredSourcesAuthCheckRequired()`

- [ ] **Step 4: Implement screen**

Use `AppScaffold`, `SettingsSectionCard`, `SettingsRow`, and compact cards like `RemoteNewsSettingsScreen`. Include:

- Source provider/account.
- Health chip.
- Last success/attempt.
- Buttons: sync now, import older, enable/disable, delete.
- Empty state explaining X authorization will be added in the OAuth task.

- [ ] **Step 5: Register navigation**

In `SettingsScreen.kt`:

- Add `EXTERNAL_FAVORITES` to `SettingsPage`.
- Add a row under "网络与同步": title `外部收藏同步`, subtitle `同步 X 等平台收藏到本地收藏`.
- Route to `ExternalFavoritesSettingsScreen`.

In `ViewModelModule.kt`, register `ExternalFavoritesSettingsViewModel`.

- [ ] **Step 6: Run UI tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsTextTest`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsTextTest.kt
git commit -m "feat: add external favorites settings"
```

---

### Task 8: X OAuth Coordinator

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/service/externalfavorites/XOAuthCoordinator.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/AppModule.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt`
- Test: `app/src/test/kotlin/com/dailysatori/service/externalfavorites/XOAuthCoordinatorTest.kt`

- [ ] **Step 1: Write OAuth helper tests**

Tests:

```kotlin
@Test
fun authorizationUrlUsesPkceAndReadScopes() {
    val coordinator = XOAuthCoordinator(clientId = "client", redirectUri = "dailysatori://oauth/x", httpClient = FakeTokenHttpClient())
    val url = coordinator.authorizationUrl(state = "state", codeChallenge = "challenge")

    assertTrue(url.contains("code_challenge=challenge"))
    assertTrue(url.contains("bookmark.read"))
    assertTrue(url.contains("tweet.read"))
    assertTrue(url.contains("users.read"))
    assertTrue(url.contains("offline.access"))
    assertFalse(url.contains("client_secret"))
}

@Test
fun authConfigDoesNotContainClientSecret() {
    val config = XOAuthProviderConfig(clientId = "client", redirectUri = "dailysatori://oauth/x")

    assertEquals("client", config.clientId)
    assertEquals("dailysatori://oauth/x", config.redirectUri)
    assertFalse(config.toString().contains("secret", ignoreCase = true))
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.service.externalfavorites.XOAuthCoordinatorTest`

Expected: FAIL because coordinator does not exist.

- [ ] **Step 3: Implement coordinator**

Implement PKCE verifier/challenge, authorization URL builder, callback parser, and token exchange shell using Ktor. Store returned account source through `ExternalFavoriteSourceRepository`.

Use a build-config or settings-backed `client_id` value. Do not introduce a client secret.

- [ ] **Step 4: Add manifest deep link**

Add an activity/deep-link intent filter for the callback scheme chosen in provider config, for example `dailysatori://oauth/x`.

- [ ] **Step 5: Wire settings action**

Settings page "Add X" opens the authorization URL. Callback saves source and returns to settings.

- [ ] **Step 6: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.service.externalfavorites.XOAuthCoordinatorTest`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/service/externalfavorites/XOAuthCoordinator.kt app/src/main/AndroidManifest.xml app/src/main/kotlin/com/dailysatori/core/di/AppModule.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt app/src/test/kotlin/com/dailysatori/service/externalfavorites/XOAuthCoordinatorTest.kt
git commit -m "feat: add x oauth coordinator"
```

---

### Task 9: Final Verification

**Files:**
- Modify only files required by failing verification.

- [ ] **Step 1: Run focused tests**

Run:

```bash
./gradlew :shared:allTests --tests com.dailysatori.service.externalfavorites.*
./gradlew :shared:allTests --tests com.dailysatori.data.repository.ExternalFavorite*
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.externalfavorites.*
./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.worker.ExternalFavoriteSyncWorkerTest
```

Expected: all PASS.

- [ ] **Step 2: Run compile check**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run broad tests if time permits**

Run: `./gradlew test`

Expected: BUILD SUCCESSFUL. If existing unrelated tests fail, record exact failing tests and confirm external favorites focused tests still pass.

- [ ] **Step 4: Commit verification fixes**

If fixes were needed:

Run `git status --short`, add only files that are part of external favorite sync, and commit:

```bash
git commit -m "fix: stabilize external favorites sync"
```
