package com.dailysatori.data.repository

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.diary.DiaryTagVocabulary
import com.dailysatori.service.diary.encodeDiaryImagePaths
import com.dailysatori.service.diary.parseDiaryImagePaths
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiaryQueryProjectionTest {
    @Test
    fun pageReadsOnlyItsOwnAttachmentsAndTagsAndEmptyPageReadsNeither() = withFixture {
        val latest = repository.getPaginated(1, 0).first().single()
        assertEquals(3L, latest.id)
        assertEquals("健身,复盘", latest.tags)
        assertEquals(listOf("/first", "https://example.test/second"), parseDiaryImagePaths(latest.images))
        assertEquals(2 to 2, driver.projectionRows())

        driver.reads.clear()
        assertEquals(1L, repository.getLatestSync(3).last().id)
        assertEquals(5 to 4, driver.projectionRows())

        driver.reads.clear()
        assertTrue(repository.getPaginated(1, 100).first().isEmpty())
        assertEquals(0 to 0, driver.projectionRows())
    }

    @Test
    fun searchKeepsChineseSubstringsAliasesAndRepliesButProjectsOnlyMatches() = withFixture {
        val expectations = listOf(
            "色天" to listOf(1L),
            "brit" to listOf(2L),
            "蓝色天空" to listOf(1L),
            "#健身锻炼" to listOf(3L),
            "不存在" to emptyList(),
        )
        for ((query, ids) in expectations) {
            assertEquals(ids, repository.searchSync(query).map { it.id }, query)
            assertEquals(ids, repository.search(query).first().map { it.id }, query)
        }
        driver.reads.clear()
        assertEquals(listOf(1L), repository.searchSync("色天").map { it.id })
        assertEquals(1 to 1, driver.projectionRows())
        assertEquals(listOf(3L, 1L), repository.searchSync("共同词").map { it.id })
    }

    @Test
    fun existingFlowsReactToAttachmentTagAndVocabularyEdits() = withFixture {
        coroutineScope {
            val pages = Channel<Pair<String?, String?>>(Channel.UNLIMITED)
            val page = launch(start = CoroutineStart.UNDISPATCHED) {
                repository.getPaginated(1, 0).collect { rows ->
                    val row = rows.single()
                    pages.send(row.tags to row.images)
                }
            }
            withTimeout(5_000) { pages.receive() }
            q.setDiaryAttachmentRemoteUrl("https://example.test/changed", 3)
            withTimeout(5_000) {
                while (parseDiaryImagePaths(pages.receive().second).last() != "https://example.test/changed") Unit
            }
            val aliases = Channel<List<Long>>(Channel.UNLIMITED)
            val search = launch(start = CoroutineStart.UNDISPATCHED) {
                repository.search("#训练").collect { aliases.send(it.map { row -> row.id }) }
            }
            assertEquals(emptyList(), withTimeout(5_000) { aliases.receive() })
            tags.writeVocabulary(DiaryTagVocabulary(aliases = mapOf("训练" to "健身")))
            withTimeout(5_000) { while (aliases.receive() != listOf(3L)) Unit }
            repository.update(3, "运动 共同词", "复盘", null, encodeDiaryImagePaths(listOf("/first", "https://example.test/changed")))
            withTimeout(5_000) { while (pages.receive().first != "复盘") Unit }
            assertEquals(emptyList(), withTimeout(5_000) {
                var ids = aliases.receive()
                while (ids.isNotEmpty()) ids = aliases.receive()
                ids
            })
            search.cancelAndJoin()
            page.cancelAndJoin()
        }
    }

    private fun withFixture(block: suspend Fixture.() -> Unit) = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { jdbc ->
            DailySatoriDatabase.Schema.create(jdbc)
            val driver = ProjectionDriver(jdbc)
            val db = DailySatoriDatabase(driver)
            val q = db.dailySatoriQueries
            val tags = DiaryTagStore(db)
            q.insertDiary("旧记录 共同词", null, 1, 1)
            q.insertDiary("celebrity", null, 2, 2)
            q.insertDiary("运动 共同词", null, 3, 3)
            q.insertDiaryReply("一片蓝色天空", null, 4, 4, 1)
            listOf(listOf("天气"), listOf("日常"), listOf("健身", "复盘"), listOf("续写")).forEachIndexed { index, names ->
                tags.initializeImported(index + 1L, names)
            }
            q.insertDiaryImage(1, "/old", null, 0, 1, 1)
            q.insertDiaryImage(2, "/middle", null, 0, 2, 2)
            q.insertDiaryImage(3, "", "https://example.test/second", 1, 3, 3)
            q.insertDiaryImage(3, "/first", null, 0, 3, 3)
            q.insertDiaryImage(4, "/reply", null, 0, 4, 4)
            q.insertDiaryImage(2, "/extra", null, 1, 2, 2)
            driver.reads.clear()
            Fixture(driver, db, DiaryRepository(db, driver), tags).block()
        }
    }

    private data class Fixture(
        val driver: ProjectionDriver,
        val db: DailySatoriDatabase,
        val repository: DiaryRepository,
        val tags: DiaryTagStore,
    ) {
        val q get() = db.dailySatoriQueries
    }

    private class ProjectionDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
        // The counting tests wait for first() to finish before clearing the trace.
        val reads = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, Int>>()

        fun projectionRows(): Pair<Int, Int> = rowsFrom("SELECT diary_id, local_path, remote_url") to
            reads.filter { (sql, _) -> sql.contains("FROM diary_tag_assignment a JOIN diary_tag_definition t") }
                .sumOf { it.second }

        private fun rowsFrom(prefix: String) = reads.filter { it.first.startsWith(prefix) }.sumOf { it.second }

        override fun <R> executeQuery(
            identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?,
        ): QueryResult<R> = delegate.executeQuery(identifier, sql, { cursor ->
            var rows = 0
            mapper(object : SqlCursor by cursor {
                override fun next(): QueryResult<Boolean> = cursor.next().also { if (it.value) rows++ }
            }).also { reads += sql to rows }
        }, parameters, binders)
    }
}
