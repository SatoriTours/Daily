package com.dailysatori.data.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class RemoteArticleSyncSchemaTest {
    @Test
    fun schemaDefinesRemoteArticleSyncMappingTableAndQueries() {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()

        assertTrue(schema.contains("CREATE TABLE remote_article_sync_item"))
        assertTrue(schema.contains("remote_source_id INTEGER NOT NULL REFERENCES remote_news_source(id) ON DELETE CASCADE"))
        assertTrue(schema.contains("remote_article_id INTEGER NOT NULL"))
        assertTrue(schema.contains("article_id INTEGER NOT NULL REFERENCES article(id) ON DELETE CASCADE"))
        assertTrue(schema.contains("source_date TEXT NOT NULL"))
        assertTrue(schema.contains("UNIQUE(remote_source_id, remote_article_id)"))
        assertTrue(schema.contains("upsertRemoteArticleSyncItem:"))
        assertTrue(schema.contains("selectRemoteArticleSyncItemsBySourceDate:"))
        assertTrue(schema.contains("selectRemoteArticleSyncItemByRemoteIdentity:"))
        assertTrue(schema.contains("selectRemoteArticleSyncItemByUrl:"))
        assertTrue(schema.contains("selectLastInsertedArticleId:"))
    }

    @Test
    fun migrationDefinesRemoteArticleSyncVersion() {
        val config = File("src/commonMain/kotlin/com/dailysatori/config/Config.kt").readText()
        val migration = File("src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt").readText()

        assertTrue(config.contains("currentSchemaVersion = 20L"))
        assertTrue(migration.contains("if (currentVersion < 15)"))
        assertTrue(migration.contains("migrateV14ToV15()"))
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS remote_article_sync_item"))
        assertTrue(migration.contains("if (currentVersion < 18)"))
        assertTrue(migration.contains("migrateV17ToV18()"))
        assertTrue(migration.contains("if (currentVersion < 19)"))
        assertTrue(migration.contains("migrateV18ToV19()"))
    }

    @Test
    fun articleSchemaSeparatesLocalSourceAndOriginalMarkdown() {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val migration = File("src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt").readText()

        assertTrue(schema.contains("original_markdown_content TEXT"))
        assertTrue(schema.contains("source_type TEXT NOT NULL DEFAULT 'local'"))
        assertTrue(schema.contains("selectLocalArticles:"))
        assertTrue(schema.contains("WHERE source_type != 'remote_news'"))
        assertTrue(migration.contains("migrateV15ToV16()"))
        assertTrue(migration.contains("WHERE id IN (SELECT article_id FROM remote_article_sync_item)"))
    }

    @Test
    fun schemaDefinesPerformanceIndexesAndTargetedRemoteArticleLookup() {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()

        assertTrue(schema.contains("CREATE INDEX IF NOT EXISTS idx_article_source_created"))
        assertTrue(schema.contains("CREATE INDEX IF NOT EXISTS idx_article_status_updated"))
        assertTrue(schema.contains("CREATE INDEX IF NOT EXISTS idx_article_no_url_remote_content"))
        assertTrue(schema.contains("ON article(title)\nWHERE url IS NULL;"))
        assertTrue(!schema.contains("ON article(title, ai_content, ai_markdown_content)\nWHERE url IS NULL;"))
        assertTrue(schema.contains("CREATE INDEX IF NOT EXISTS idx_external_favorite_item_source_first_seen"))
        assertTrue(schema.contains("CREATE INDEX IF NOT EXISTS idx_external_favorite_item_article"))
        assertTrue(schema.contains("CREATE INDEX IF NOT EXISTS idx_remote_article_sync_source_date"))
        assertTrue(schema.contains("CREATE INDEX IF NOT EXISTS idx_async_task_status_run_after_priority"))
        assertTrue(schema.contains("CREATE INDEX IF NOT EXISTS idx_async_task_unique_created"))
        assertTrue(schema.contains("CREATE INDEX IF NOT EXISTS idx_async_task_batch"))
        assertTrue(schema.contains("CREATE INDEX IF NOT EXISTS idx_external_favorite_item_source_import_first_seen"))
        assertTrue(schema.contains("selectArticleByNoUrlRemoteContent:"))
        assertTrue(schema.contains("WHERE url IS NULL"))
    }
}
