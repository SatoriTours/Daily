package com.dailysatori.data.repository

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 同步快照必须在一次事务里读完：外部并发写入不能插在“原文”与“版本”两次读取之间，
 * 否则生成请求可能把不含最新续写的汇总标成当前版本。
 */
class DiaryThreadSnapshotIsolationTest {
    @Test
    fun getSnapshotReadsEntriesVersionAndSummaryInOneTransaction() = withIsolatedFile { fixture ->
        val rootId = fixture.seedRootWithReply()
        val revisionBefore = fixture.threads.getSnapshot(rootId)!!.revision

        val snapshot = fixture.threads.getSnapshot(rootId)

        assertTrue(fixture.concurrentWriteBlocked, "getSnapshot 的原文与版本读取必须同处一个事务")
        assertEquals(2, snapshot!!.entries.size)
        assertEquals(revisionBefore, snapshot.revision)
    }

    @Test
    fun getSourceReadsWholeThreadInOneTransaction() = withIsolatedFile { fixture ->
        val rootId = fixture.seedRootWithReply()

        val source = fixture.threads.getSource(rootId)

        assertTrue(fixture.concurrentWriteBlocked, "getSource 必须在一个事务里读取整串原文与版本")
        assertTrue(source!!.content.contains("续写正文"))
    }

    @Test
    fun sourcesReadsCatalogInOneTransaction() = withIsolatedFile { fixture ->
        fixture.seedRootWithReply()

        val sources = fixture.threads.sources()

        assertTrue(fixture.concurrentWriteBlocked, "sources 必须在一个事务里读取目录与版本")
        assertEquals(1, sources.size)
        assertTrue(sources.single().content.contains("续写正文"))
    }

    private fun withIsolatedFile(block: (Fixture) -> Unit) {
        val directory = Files.createTempDirectory("thread-snapshot").toFile()
        val path = File(directory, "thread.db").absolutePath
        var writer: JdbcSqliteDriver? = null
        try {
            var concurrentWriteBlocked = false
            var hooked = false
            val reader = InterceptingDriver(JdbcSqliteDriver("jdbc:sqlite:$path")) { sql ->
                if (!hooked && sql.contains("diary_thread_revision")) {
                    hooked = true
                    val target = requireNotNull(writer)
                    try {
                        target.execute(
                            null,
                            "INSERT INTO diary(content, tags, mood, images, created_at, updated_at, parent_diary_id) " +
                                "VALUES('并发写入', NULL, NULL, NULL, 9999, 9999, 1)",
                            0,
                        )
                    } catch (_: Exception) {
                        concurrentWriteBlocked = true
                    }
                }
            }
            DailySatoriDatabase.Schema.create(reader)
            writer = JdbcSqliteDriver("jdbc:sqlite:$path").also { it.execute(null, "PRAGMA busy_timeout=0", 0) }
            val db = DailySatoriDatabase(reader)
            block(
                Fixture(
                    reader = reader,
                    db = db,
                    threads = DiaryThreadRepository(db, reader),
                    concurrentWriteBlockedProvider = { concurrentWriteBlocked },
                ),
            )
        } finally {
            writer?.close()
            directory.deleteRecursively()
        }
    }

    private class Fixture(
        val reader: InterceptingDriver,
        val db: DailySatoriDatabase,
        val threads: DiaryThreadRepository,
        val concurrentWriteBlockedProvider: () -> Boolean,
    ) {
        val concurrentWriteBlocked: Boolean get() = concurrentWriteBlockedProvider()

        fun seedRootWithReply(): Long {
            db.dailySatoriQueries.insertDiary("主日记正文", null, null, null, 1_000, 1_000)
            db.dailySatoriQueries.insertDiaryReply("续写正文", null, null, null, 2_000, 2_000, 1)
            return 1L
        }
    }

    private class InterceptingDriver(
        private val delegate: SqlDriver,
        private val onQuery: (String) -> Unit,
    ) : SqlDriver by delegate {
        override fun <R> executeQuery(
            identifier: Int?,
            sql: String,
            mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int,
            binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> {
            onQuery(sql)
            return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
        }

        override fun close() = delegate.close()
    }
}
