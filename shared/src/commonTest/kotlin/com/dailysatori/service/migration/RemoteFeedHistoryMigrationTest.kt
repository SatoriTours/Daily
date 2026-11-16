package com.dailysatori.service.migration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.RemoteFeedHistoryRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.remotenews.RemoteFeedHistoryState
import com.dailysatori.service.remotenews.RemoteFeedHistoryStatus
import com.dailysatori.service.remotenews.RemoteFeedHistoryStrategy
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Schema 51: one new per-source policy column and one independent history walk table. */
class RemoteFeedHistoryMigrationTest {
    private val target = com.dailysatori.config.DatabaseConfig.currentSchemaVersion.toString()

    @Test
    fun anInstalledVersion50DatabaseKeepsItsSourcesAndGainsTheHistoryState() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = RemoteNewsSourceRepository(db)
            sources.save(null, "旧订阅", "https://feed.test/rss", "token", enabled = true)
            val sourceId = sources.getAll().single().id
            // Reproduce a version 50 database: no history table, no policy column.
            driver.execute(null, "DROP TABLE remote_feed_history_state", 0)
            driver.execute(
                null,
                "CREATE TABLE remote_news_source_old (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, base_url TEXT NOT NULL, " +
                    "api_token TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1, " +
                    "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)",
                0,
            )
            driver.execute(
                null,
                "INSERT INTO remote_news_source_old (id,name,base_url,api_token,enabled,created_at,updated_at) " +
                    "SELECT id,name,base_url,api_token,enabled,created_at,updated_at FROM remote_news_source",
                0,
            )
            driver.execute(null, "DROP TABLE remote_news_source", 0)
            driver.execute(null, "ALTER TABLE remote_news_source_old RENAME TO remote_news_source", 0)
            val settings = SettingRepository(db)
            settings.upsert("schema_version", "50")

            DatabaseMigration(driver, settings).runMigrations()

            assertEquals(target, settings.get("schema_version"))
            val migrated = assertNotNull(sources.getById(sourceId))
            assertEquals("https://feed.test/rss", migrated.base_url)
            assertEquals("token", migrated.api_token)
            assertEquals(
                "none", migrated.history_policy,
                "an installed source keeps its exact behaviour until the user starts a backfill",
            )

            val history = RemoteFeedHistoryRepository(db)
            assertNull(history.find(sourceId))
            history.save(sourceId, sampleState(), now = 100L)
            val columns = DatabaseSchemaInventory.strings(driver, "SELECT name FROM pragma_table_info('remote_feed_history_state')")
            assertTrue("control_epoch" in columns, "the walk ownership epoch is part of the migrated shape")
            val stored = assertNotNull(history.find(sourceId))
            assertEquals(0L, stored.controlEpoch)
            assertEquals(RemoteFeedHistoryStrategy.LATEST_500, stored.strategy)
            assertEquals(RemoteFeedHistoryStatus.RUNNING, stored.status)
            assertEquals(120, stored.importedCount)
            assertEquals(500, stored.targetCount)
            assertEquals("https://feed.test/page7", stored.cursorUrl)
            assertTrue(stored.lastError.isBlank())

            sources.save(null, "新订阅", "https://news.test/feed", "", enabled = true)
            val added = sources.getAll().first { it.name == "新订阅" }
            assertEquals("all_history", added.history_policy, "a new source keeps the product default")
        }
    }

    private fun sampleState() = RemoteFeedHistoryState(
        entryUrl = "https://feed.test/rss",
        strategy = RemoteFeedHistoryStrategy.LATEST_500,
        status = RemoteFeedHistoryStatus.RUNNING,
        startUrl = "https://feed.test/page1",
        cursorUrl = "https://feed.test/page7",
        lastPageUrl = "https://feed.test/page6",
        lastPageFingerprint = "abc",
        importedCount = 120,
        targetCount = 500,
        pagesRead = 6,
        requests = 6,
        startedAt = 50L,
    )
}

