package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiaryThreadSearchTest {
    @Test
    fun replyKeywordFindsRootInFlowAndSync() = withThread { fixture ->
        val rootId = fixture.root("主日记正文", createdAt = 1_000)
        val replyId = fixture.reply(rootId, "只在续写里的关键字", createdAt = 2_000)

        assertEquals(listOf(rootId), fixture.diaries.searchSync("只在续写里的关键字").map { it.id })
        assertEquals(listOf(rootId), runBlocking { fixture.diaries.search("只在续写里的关键字").first() }.map { it.id })
        assertEquals("只在续写里的关键字", fixture.diaries.getById(replyId)!!.content)
        assertEquals("主日记正文", fixture.diaries.getById(rootId)!!.content)
    }

    @Test
    fun replyMatchesAreDeduplicated() = withThread { fixture ->
        val rootId = fixture.root("主日记正文", createdAt = 1_000)
        fixture.reply(rootId, "重复命中关键字 first", createdAt = 2_000)
        fixture.reply(rootId, "重复命中关键字 second", createdAt = 3_000)

        assertEquals(listOf(rootId), fixture.diaries.searchSync("重复命中关键字").map { it.id })
    }

    @Test
    fun rootCountAndPaginationExcludeReplies() = withThread { fixture ->
        val rootId = fixture.root("主日记正文", createdAt = 1_000)
        fixture.reply(rootId, "续写一", createdAt = 2_000)
        fixture.reply(rootId, "续写二", createdAt = 3_000)

        assertEquals(1L, fixture.diaries.count())
        assertEquals(listOf(rootId), fixture.diaries.getAllSync().map { it.id })
        assertEquals(listOf(rootId), fixture.diaries.getLatestSync(10).map { it.id })
        assertEquals(listOf(rootId), runBlocking { fixture.diaries.getAll().first() }.map { it.id })
        assertEquals(listOf(rootId), runBlocking { fixture.diaries.getPaginated(10, 0).first() }.map { it.id })
        assertEquals(listOf(rootId), fixture.diaries.getByDateRangeSync(0, 10_000).map { it.id })
        assertEquals(listOf(rootId), runBlocking { fixture.diaries.getByDateRange(0, 10_000).first() }.map { it.id })
    }

    @Test
    fun tagAliasSearchStillWorksWithoutReplies() = withThread { fixture ->
        val rootId = fixture.root("今天去健身房", createdAt = 1_000)
        fixture.db.dailySatoriQueries.updateDiaryTags("健身", rootId)

        assertTrue(fixture.diaries.searchSync("健身").any { it.id == rootId })
    }

    private fun withThread(block: (Fixture) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            block(Fixture(db, DiaryRepository(db, driver)))
        } finally {
            driver.close()
        }
    }

    private class Fixture(val db: DailySatoriDatabase, val diaries: DiaryRepository) {
        fun root(content: String, createdAt: Long): Long {
            db.dailySatoriQueries.insertDiary(content, null, null, null, createdAt, createdAt)
            return db.dailySatoriQueries.selectAllDiaries().executeAsList().maxBy { it.id }.id
        }

        fun reply(rootId: Long, content: String, createdAt: Long): Long {
            db.dailySatoriQueries.insertDiaryReply(content, null, null, null, createdAt, createdAt, rootId)
            return db.dailySatoriQueries.selectDiaryRepliesForRoot(rootId).executeAsList().last().id
        }
    }
}
