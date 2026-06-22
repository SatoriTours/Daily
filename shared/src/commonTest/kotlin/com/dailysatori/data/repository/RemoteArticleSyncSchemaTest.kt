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

        assertTrue(config.contains("currentSchemaVersion = 16L"))
        assertTrue(migration.contains("if (currentVersion < 15)"))
        assertTrue(migration.contains("migrateV14ToV15()"))
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS remote_article_sync_item"))
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
}
