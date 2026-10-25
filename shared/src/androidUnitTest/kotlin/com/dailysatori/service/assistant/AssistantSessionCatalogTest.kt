package com.dailysatori.service.assistant

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssistantSessionCatalogTest {
    @Test
    fun listUsesFirstQuestionLatestPreviewAndLatestActivityWithoutEmptySessionRows() = runBlocking {
        withConversation { driver, conversation ->
            assertTrue(conversation.sessionSummaries().first().isEmpty())
            append(driver, "old", "assistant", "不是用户的问题", 1)
            append(driver, "old", "user", "\n  怎样安排阅读？\n具体问题的正文", 2)
            append(driver, "old", "assistant", "最新回复\n第二行", 3)
            append(driver, "recent", "user", "更近的对话", 4)
            val rows = conversation.sessionSummaries().first()
            assertEquals(listOf("recent", "old"), rows.map { it.id })
            assertEquals("怎样安排阅读？", rows.last().title)
            assertEquals("最新回复 第二行", rows.last().preview)
            assertEquals(3L, rows.last().updatedAt)
        }
    }

    @Test
    fun acceptedOriginalNotifiesPreviewAndDeletingSessionRemovesItsCatalogEntry() = runBlocking {
        withConversation { _, conversation ->
            val pending = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(2_000) { conversation.sessionSummaries().first { it.any { row -> row.preview == "中文原文" } } }
            }
            // Consent is absent: accepting and listing the local original must not need AI or a provider.
            conversation.send("s", "catalog-client", "中文原文")
            assertEquals("中文原文", pending.await().single().title)
            conversation.deleteSession("s")
            assertTrue(conversation.sessionSummaries().first().isEmpty())
        }
    }

    @Test
    fun boundedUnicodeTitleDoesNotSplitSurrogatePairsAndEqualTimesUseInsertionOrder() = runBlocking {
        withConversation { driver, conversation ->
            append(driver, "unicode", "user", "中😀".repeat(300), 1)
            append(driver, "unicode", "assistant", "第一条回复", 2)
            append(driver, "unicode", "assistant", "同一时刻的最后回复", 2)
            val row = conversation.sessionSummaries().first().single()
            assertEquals(240, row.title.codePointCount(0, row.title.length))
            assertTrue(!row.title.last().isHighSurrogate())
            assertEquals("同一时刻的最后回复", row.preview)
        }
    }

    private suspend fun withConversation(block: suspend (SqlDriver, PersistentAssistantConversation) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val actions = AssistantActionService(driver, RepositoryAssistantBusiness(driver) { _, _ -> })
            block(driver, PersistentAssistantConversation(driver, actions, AssistantMemoryService(driver)) {})
        }
    }

    private fun append(driver: SqlDriver, session: String, role: String, content: String, time: Long) {
        driver.execute(null, "INSERT INTO chat_conversation(session_id,role,content,created_at) VALUES(?,?,?,?)", 4) {
            bindString(0, session); bindString(1, role); bindString(2, content); bindLong(3, time)
        }
    }
}
