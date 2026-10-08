package com.dailysatori.service.ideatopic

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryThreadRepository
import com.dailysatori.service.diary.renderDiaryThreadContent
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryThreadIdeaIntegrationTest {
    @Test
    fun captureKeepsRootKeyOriginalTitleAndAllRepliesInsteadOfAiSummary() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val diaries = DiaryRepository(db, driver)
            val threads = DiaryThreadRepository(db, driver)
            val rootId = diaries.create("产品原始想法\n给家人做一个生活助手")
            val replyId = threads.createReply(rootId, "继续补充：先解决提醒事项，不做复杂聊天")
            val snapshot = requireNotNull(threads.getSnapshot(requireNotNull(threads.rootId(replyId))))
            threads.commitSummary(rootId, snapshot.revision, "仅供预览的 AI 汇总")

            val input = diaryIdeaCaptureInput(requireNotNull(threads.getSnapshot(rootId)))

            assertEquals(IdeaSourceKey(IdeaSourceTypes.Diary, rootId.toString()), input.source.key)
            assertEquals(rootId.toString(), input.source.originalRecordId)
            assertEquals("产品原始想法", input.source.originalTitle)
            assertEquals(renderDiaryThreadContent(snapshot.entries), input.source.originalContent)
            assertTrue(input.source.originalContent.contains("先解决提醒事项"))
            assertNull(input.source.analysisContent)
        } finally {
            driver.close()
        }
    }
}
