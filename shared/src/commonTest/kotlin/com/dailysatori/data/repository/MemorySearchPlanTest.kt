package com.dailysatori.data.repository

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class MemorySearchPlanTest {
    @Test
    fun ftsHitsThatFillTheLimitNeverRunTheCompatibilityScan() = withMemory { driver, repo ->
        assertEquals(listOf(3L, 4L), repo.search("Kernel", 2).map { it.id })
        assertEquals(1, driver.reads.size)
        assertEquals(false, driver.reads.single().sql.contains(" LIKE "))
    }

    @Test
    fun insufficientFtsHitsTakeOnlyRemainingRowsInTheSameTransaction() = withMemory { driver, repo ->
        assertEquals(listOf(3L, 4L, 1L, 5L), repo.search("kernel", 4).map { it.id })
        assertEquals(2, driver.reads.size)
        val (fts, fallback) = driver.reads
        assertEquals(false, fts.sql.contains(" LIKE "))
        assertEquals(true, fallback.sql.contains(" LIKE "))
        assertSame(assertNotNull(fts.transaction), fallback.transaction)
        assertEquals(2L, fallback.limit)
    }

    @Test
    fun zeroLimitUsesNoSqlAndNegativeLimitsRemainUnbounded() = withMemory { driver, repo ->
        assertEquals(emptyList(), repo.search("kernel", 0))
        assertEquals(emptyList(), driver.reads)
        assertEquals(listOf(3L, 4L, 1L, 5L), repo.search("kernel", Long.MIN_VALUE).map { it.id })
        assertEquals(2, driver.reads.size)
        assertEquals(-1L, driver.reads.last().limit)
    }

    @Test
    fun oldSqlAndRepositoryReturnIdenticalIdsAcrossLimitsAndLegacyQueries() = withMemory { driver, repo ->
        val queries = listOf("kernel", "ernel", "中文", "KERNEL", "KernEL tool", "quoted \"label\"", "\"",
            "missing", "%", "_", "a_%", "", " ", "  KERNEL ")
        for (query in queries) for (limit in listOf(0L, 1L, 2L, 3L, 10L, -1L, -42L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            assertEquals(driver.originalIds(query, limit), repo.search(query, limit).map { it.id }, "$query/$limit")
        }
        assertEquals(listOf(2L), repo.search("中文").map { it.id })
        assertEquals(listOf(1L, 5L), repo.search("icrokern").map { it.id })
    }

    private fun withMemory(block: (TraceDriver, MemoryRepository) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { sqlite ->
            DailySatoriDatabase.Schema.create(sqlite)
            val driver = TraceDriver(sqlite)
            val db = DailySatoriDatabase(driver)
            db.dailySatoriQueries.transaction {
                listOf(
                    Triple("microkernel", "newest LIKE-only", 80L),
                    Triple("日记", "连续四字中文内容", 60L),
                    Triple("kernel", "ASCII exact token", 20L),
                    Triple("notes", "KernEL tool", 20L),
                    Triple("MicroKernel", "quoted \"label\" with a_%", 10L),
                ).forEach { (title, content, time) ->
                    db.dailySatoriQueries.insertMemory("content", null, null, title, content, null, time, time)
                }
            }
            block(driver, MemoryRepository(db))
        }
    }

    private class TraceDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
        data class Read(val sql: String, val transaction: Transacter.Transaction?, val limit: Long?)
        val reads = mutableListOf<Read>()

        override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<R> {
            var limit: Long? = null
            return delegate.executeQuery(identifier, sql, mapper, parameters) {
                val statement = this
                binders?.invoke(object : SqlPreparedStatement by statement {
                    override fun bindLong(index: Int, long: Long?) {
                        if (index == parameters - 1) limit = long
                        statement.bindLong(index, long)
                    }
                })
                reads += Read(sql, delegate.currentTransaction(), limit)
            }
        }

        fun originalIds(query: String, limit: Long): List<Long> {
            val sql = if (query.isBlank()) """
                SELECT id FROM memory_entry
                WHERE title LIKE '%' || ? || '%' OR content LIKE '%' || ? || '%'
                ORDER BY created_at DESC LIMIT ?
            """ else """
                SELECT id FROM memory_entry
                WHERE id IN (SELECT rowid FROM memory_entry_fts WHERE memory_entry_fts MATCH ?)
                   OR title LIKE '%' || ? || '%' OR content LIKE '%' || ? || '%'
                ORDER BY CASE WHEN id IN (SELECT rowid FROM memory_entry_fts WHERE memory_entry_fts MATCH ?) THEN 0 ELSE 1 END,
                    created_at DESC LIMIT ?
            """
            return delegate.executeQuery(null, sql, { cursor ->
                QueryResult.Value(buildList { while (cursor.next().value) add(checkNotNull(cursor.getLong(0))) })
            }, if (query.isBlank()) 3 else 5) {
                if (query.isBlank()) {
                    bindString(0, query); bindString(1, query); bindLong(2, limit)
                } else {
                    val phrase = query.toFtsPhraseQuery()
                    bindString(0, phrase); bindString(1, query); bindString(2, query)
                    bindString(3, phrase); bindLong(4, limit)
                }
            }.value
        }
    }
}
