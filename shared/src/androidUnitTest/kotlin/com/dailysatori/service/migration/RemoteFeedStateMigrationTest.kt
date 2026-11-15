package com.dailysatori.service.migration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.RemoteFeedSyncStateRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.remotenews.RemoteFeedPagingMode
import com.dailysatori.service.remotenews.RemoteFeedSyncState
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteFeedStateMigrationTest {
    private val target = com.dailysatori.config.DatabaseConfig.currentSchemaVersion.toString()

    @Test
    fun aVersion46DatabaseGainsTheCurrentTablesAndKeepsItsSources() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = RemoteNewsSourceRepository(db)
            sources.save(null, "旧订阅", "https://feed.test/rss", "token", enabled = true)
            val sourceId = sources.getAll().single().id
            // A version 46 database never had these tables; the migration must create them without data loss.
            RemoteFeedStateMigration.drop(driver)
            val settings = SettingRepository(db)
            settings.upsert("schema_version", "46")

            DatabaseMigration(driver, settings).runMigrations()

            assertEquals(target, settings.get("schema_version"))
            assertEquals("https://feed.test/rss", sources.getById(sourceId)?.base_url)
            assertEquals("token", sources.getById(sourceId)?.api_token)
            val states = RemoteFeedSyncStateRepository(db)
            assertNull(states.find(sourceId))

            states.save(sourceId, sampleState(), now = 100L)
            val stored = assertNotNull(states.find(sourceId))
            assertEquals(RemoteFeedPagingMode.ARCHIVE, stored.mode)
            assertTrue(stored.gapOpen)
            assertTrue(stored.blocked)
            assertEquals("/archive/2026-09", stored.confirmedAnchorUrl)
            assertEquals(3, stored.scanPages)
            assertEquals(listOf(7L, 8L), stored.checkpointIds)
            assertEquals(listOf(9L, 8L), stored.targetEntryIds)
        }
    }

    @Test
    fun theInstalledVersion48ProgressTableIsRebuiltWithoutTouchingUserData() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = RemoteNewsSourceRepository(db)
            sources.save(null, "旧订阅", "https://feed.test/rss", "token", enabled = true)
            val sourceId = sources.getAll().single().id
            // Schema 48 is installed on real devices: it must migrate, not be mistaken for the new shape.
            RemoteFeedStateMigration.drop(driver)
            RemoteFeedStateMigration.createLegacyV48(driver)
            driver.execute(
                null,
                "INSERT INTO remote_feed_sync_state (remote_source_id, entry_url, paging_mode, baseline_ready, updated_at) " +
                    "VALUES (" + sourceId + ", 'https://feed.test/rss', 'paged', 1, 5)",
                0,
            )
            val settings = SettingRepository(db)
            settings.upsert("schema_version", "48")

            DatabaseMigration(driver, settings).runMigrations()

            assertEquals(target, settings.get("schema_version"))
            assertEquals("https://feed.test/rss", sources.getById(sourceId)?.base_url)
            val columns = DatabaseSchemaInventory.strings(driver, "SELECT name FROM pragma_table_info('remote_feed_sync_state')")
            assertTrue("checkpoint_ids" in columns)
            assertTrue("closure_reached" in columns)
            assertTrue("baseline_marker_ids" !in columns)
            val staged = DatabaseSchemaInventory.strings(driver, "SELECT name FROM pragma_table_info('remote_feed_staged_article')")
            assertTrue("stale" in staged)
            assertNull(RemoteFeedSyncStateRepository(db).find(sourceId), "only unconfirmed progress is dropped")
        }
    }

    @Test
    fun theInstalledVersion49ProgressRowsAreKeptWhenTheTargetColumnIsAdded() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = RemoteNewsSourceRepository(db)
            sources.save(null, "旧订阅", "https://feed.test/rss", "token", enabled = true)
            val sourceId = sources.getAll().single().id
            RemoteFeedStateMigration.drop(driver)
            RemoteFeedStateMigration.createLegacyV49(driver)
            driver.execute(
                null,
                "INSERT INTO remote_feed_sync_state (remote_source_id, entry_url, paging_mode, baseline_ready, " +
                    "checkpoint_ids, gap_open, cursor_url, scan_pages, updated_at) " +
                    "VALUES (" + sourceId + ", 'https://feed.test/rss', 'paged', 1, '7,8', 1, '/page5', 4, 5)",
                0,
            )
            val settings = SettingRepository(db)
            settings.upsert("schema_version", "49")

            DatabaseMigration(driver, settings).runMigrations()

            assertEquals(target, settings.get("schema_version"))
            assertEquals("https://feed.test/rss", sources.getById(sourceId)?.base_url)
            val columns = DatabaseSchemaInventory.strings(driver, "SELECT name FROM pragma_table_info('remote_feed_sync_state')")
            assertTrue("target_entry_ids" in columns)
            val stored = assertNotNull(RemoteFeedSyncStateRepository(db).find(sourceId), "progress rows survive the column migration")
            assertEquals(listOf(7L, 8L), stored.checkpointIds)
            assertEquals(4, stored.scanPages)
            assertEquals("/page5", stored.cursorUrl)
            assertTrue(stored.gapOpen)
        }
    }

    private fun sampleState() = RemoteFeedSyncState(
        entryUrl = "https://feed.test/rss",
        mode = RemoteFeedPagingMode.ARCHIVE,
        baselineReady = true,
        checkpointIds = listOf(7L, 8L),
        targetEntryIds = listOf(9L, 8L),
        confirmedAnchorUrl = "/archive/2026-09",
        confirmedAt = 90L,
        generation = 2L,
        gapOpen = true,
        scanStartUrl = "/archive/2026-10",
        cursorUrl = "/archive/2026-08",
        scanPages = 3,
        stagedBytes = 512,
        blocked = true,
        blockedReason = "累计扫描文档数达到上限",
    )
}
