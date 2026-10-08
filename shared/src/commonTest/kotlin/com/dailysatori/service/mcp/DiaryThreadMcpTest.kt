package com.dailysatori.service.mcp

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryThreadRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiaryThreadMcpTest {
    @Test
    fun replyEvidenceKeepsRootIdAndTheReplyPassage() = withThread { db, diaries, threads ->
        val rootId = runBlocking { diaries.create(content = "主正文".repeat(200)) }
        val replyId = db.dailySatoriQueries.transactionWithResult {
            db.dailySatoriQueries.insertDiaryReply("后续补充的关键事实", null, null, null, 5_000, 5_000, rootId)
            db.dailySatoriQueries.selectDiaryRepliesForRoot(rootId).executeAsList().single().id
        }
        val matches = diaries.searchSync("后续补充的关键事实")
        val threadSource = requireNotNull(threads.getSource(rootId)).content

        val json = diaryListToJson(matches) { diary ->
            val content = if (diary.id == rootId) threadSource else diary.content
            diarySearchPassage(content, "后续补充的关键事实")
        }

        assertEquals(listOf(rootId), matches.map { it.id })
        val entry = json.single().jsonObject
        assertEquals(rootId.toString(), entry["id"]!!.jsonPrimitive.content)
        assertTrue(entry["content"]!!.jsonPrimitive.content.contains("后续补充的关键事实"))
        assertTrue(threadSource.contains("后续补充的关键事实"))
        assertEquals(replyId, db.dailySatoriQueries.selectDiaryById(replyId).executeAsOne().id)
    }

    @Test
    fun passageKeepsTheHitInsteadOfTheRootPrefix() {
        val root = "旧".repeat(900)
        val content = "$root【续写 #2】\n真正的结论是应该放弃"

        val passage = diarySearchPassage(content, "真正的结论")

        assertTrue(passage.contains("真正的结论"))
        assertTrue(passage.length < content.length, "passage must not keep the whole body")
    }

    @Test
    fun passageFallsBackToWholeContentWhenKeywordMissing() {
        assertEquals("没有命中", diarySearchPassage("没有命中", "缺失"))
    }

    private fun withThread(block: (DailySatoriDatabase, DiaryRepository, DiaryThreadRepository) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            block(db, DiaryRepository(db, driver), DiaryThreadRepository(db, driver))
        } finally {
            driver.close()
        }
    }
}
