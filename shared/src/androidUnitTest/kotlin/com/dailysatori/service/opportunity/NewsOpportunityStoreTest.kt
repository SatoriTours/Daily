package com.dailysatori.service.opportunity

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class NewsOpportunityStoreTest {
    @Test
    fun largeArchiveRoundTripsWithoutOversizedRowsOrUnicodeLoss() = withDatabase { _, settings ->
        val archive = largeArchive()
        val store = NewsOpportunityStore(settings)
        store.save(archive)
        assertBoundedRows(settings)
        assertEquals(archive, NewsOpportunityStore(settings).load())
    }

    @Test
    fun oversizedLegacyArchiveIsReadInBoundedPiecesAndMigratedWithoutLosingState() = withDatabase { _, settings ->
        val archive = largeArchive()
        settings.upsert(KEY, Json.encodeToString(archive))
        assertEquals(archive, NewsOpportunityStore(settings).load())
        assertBoundedRows(settings)
        assertEquals(archive, NewsOpportunityStore(settings).load())
    }

    @Test
    fun shrinkingArchiveRemovesOldChunksWithoutTouchingOtherSettings() = withDatabase { _, settings ->
        settings.upsert("unrelated", "保留")
        val store = NewsOpportunityStore(settings)
        store.save(largeArchive())
        store.save(OpportunityArchive(focus = "新方向"))
        assertEquals(OpportunityArchive(focus = "新方向"), store.load())
        assertEquals(setOf(KEY, "unrelated"), settings.getAllKeys().toSet())
        assertEquals("保留", settings.get("unrelated"))
    }

    @Test
    fun missingChunkFailsInsteadOfReturningOrSavingAnEmptyArchive() = withDatabase { _, settings ->
        val store = NewsOpportunityStore(settings)
        store.save(largeArchive())
        val chunkKey = settings.getAllKeys().first { it.startsWith("$KEY:chunk:") }
        val manifest = settings.get(KEY)
        settings.delete(chunkKey)
        assertFails { store.load() }
        assertEquals(manifest, settings.get(KEY))
    }

    @Test
    fun changedChunkIsDetectedBeforePublishingCorruptData() = withDatabase { _, settings ->
        val store = NewsOpportunityStore(settings)
        store.save(largeArchive())
        val chunkKey = settings.getAllKeys().first { it.startsWith("$KEY:chunk:") }
        val chunk = settings.get(chunkKey)!!
        settings.upsert(chunkKey, "$chunk ")
        assertFails { store.load() }
    }

    @Test
    fun failedChunkWriteRollsBackAndKeepsPreviousArchive() = withDatabase { driver, settings ->
        val store = NewsOpportunityStore(settings)
        val original = largeArchive()
        store.save(original)
        driver.execute(null, """
            CREATE TRIGGER fail_opportunity_write BEFORE INSERT ON setting
            WHEN NEW.key = '$KEY:chunk:1'
            BEGIN SELECT RAISE(ABORT, 'simulated write failure'); END;
        """.trimIndent(), 0)
        assertFails { store.save(original.copy(focus = "修改方向")) }
        assertEquals(original, store.load())
    }

    @Test
    fun malformedLegacyArchiveIsNotOverwritten() = withDatabase { _, settings ->
        settings.upsert(KEY, "{broken")
        assertFails { NewsOpportunityStore(settings).load() }
        assertEquals("{broken", settings.get(KEY))
    }

    private fun largeArchive(): OpportunityArchive {
        val article = ReadNewsArticle("news-1", "新闻", "中文🙂𠮷正文".repeat(120_000), source = "测试", readAt = 123L)
        val opportunity = NewsOpportunity("opp-1", article, "软件方向", "工具", "事实", "相关", "行动", "风险", "中文", 456L,
            saved = true, ignored = true, reminderId = "reminder-1", savedAt = 789L)
        return OpportunityArchive(articles = listOf(article), candidates = listOf(article), items = listOf(opportunity),
            focus = "软件方向", checkpoints = listOf(OpportunityCheckpoint("news-1", "fingerprint")),
            lastAttemptAt = 999L, lastAttemptContext = "context", dismissedErrorTaskId = 487L)
    }

    private fun assertBoundedRows(settings: SettingRepository) {
        assertTrue(settings.getAll().all { (it.value_?.encodeToByteArray()?.size ?: 0) <= 128 * 1024 })
    }

    private fun withDatabase(block: (SqlDriver, SettingRepository) -> Unit) {
        val delegate = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val driver = BoundedReadDriver(delegate)
        try {
            DailySatoriDatabase.Schema.create(driver)
            block(driver, SettingRepository(DailySatoriDatabase(driver)))
        } finally { driver.close() }
    }

    // JVM SQLite has no Android CursorWindow; enforce a stricter per-cell limit at the SQL boundary.
    private class BoundedReadDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
        override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<R> =
            delegate.executeQuery(identifier, sql, { cursor ->
                mapper(object : SqlCursor by cursor {
                    override fun getString(index: Int): String? = cursor.getString(index).also {
                        check((it?.encodeToByteArray()?.size ?: 0) <= 128 * 1024) { "SQL value exceeds read window" }
                    }
                })
            }, parameters, binders)
    }

    private companion object { const val KEY = "news_opportunity_archive_v1" }
}
