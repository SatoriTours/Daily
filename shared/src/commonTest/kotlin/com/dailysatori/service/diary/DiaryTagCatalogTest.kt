package com.dailysatori.service.diary

import app.cash.sqldelight.db.*
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryTagRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class DiaryTagCatalogTest {
    @Test
    fun catalogReadsTagIndexWithoutLoadingEveryBodyAndProvenance() = runBlocking {
        Fixture().use { f ->
            repeat(50) { f.diaries.create("很长的私人正文".repeat(500), "读书") }
            f.driver.statements.clear()
            val catalog = f.tags.observeCatalog().first()
            assertEquals(mapOf("读书" to 50), catalog.counts)
            assertTrue(f.driver.statements.size <= 8, "Catalog must use a bounded number of queries")
            assertFalse(f.driver.statements.any { fullDiaryProjection.containsMatchIn(it) })
        }
    }

    @Test
    fun catalogCountsEachCanonicalTagOncePerDiaryAndTracksEditsAndDeletes() = runBlocking {
        Fixture().use { f ->
            val first = f.diaries.create("正文", "感想,感悟,读书")
            val second = f.diaries.create("正文", "读书")
            val results = Channel<DiaryTagCatalog>(Channel.UNLIMITED)
            val job = launch { f.tags.observeCatalog().collect { results.send(it) } }
            suspend fun awaitCounts(expected: Map<String, Int>) = withTimeout(2_000) {
                var catalog = results.receive()
                while (catalog.counts != expected) catalog = results.receive()
            }
            try {
                awaitCounts(mapOf("感悟" to 1, "读书" to 2))
                f.tags.edit(first, listOf("旅行"))
                awaitCounts(mapOf("旅行" to 1, "读书" to 1))
                f.diaries.delete(second)
                awaitCounts(mapOf("旅行" to 1))
            } finally { job.cancel(); results.close() }
        }
    }

    @Test
    fun provenanceObservationReadsOnlyTheSelectedDiary() = runBlocking {
        Fixture().use { f ->
            val id = f.diaries.create("正文")
            repeat(30) { f.diaries.create("其他正文", "旅行") }
            f.tags.apply(assertNotNull(f.tags.prepare(id)), listOf("读书"))
            f.driver.statements.clear()
            assertEquals(listOf("读书"), f.tags.observeState(id).first().automatic)
            assertTrue(f.driver.statements.size <= 4, f.driver.statements.joinToString(" | "))
            assertFalse(f.driver.statements.any { it.contains("ORDER BY created_at", ignoreCase = true) })
        }
    }

    private val fullDiaryProjection = Regex("select\\s+\\*\\s+from\\s+diary\\b|select[^;]*\\bdiary\\.content\\b", RegexOption.IGNORE_CASE)

    private class Fixture : AutoCloseable {
        val driver = TrackingDriver(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))
        init { DailySatoriDatabase.Schema.create(driver) }
        private val db = DailySatoriDatabase(driver)
        val diaries = DiaryRepository(db, driver)
        val tags = DiaryTagRepository(db, com.dailysatori.data.repository.DiaryThreadRepository(db, driver))
        override fun close() = driver.close()
    }

    private class TrackingDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
        val statements = java.util.Collections.synchronizedList(mutableListOf<String>())
        override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<R> {
            statements += sql
            return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
        }
    }
}
