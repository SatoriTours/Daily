package com.dailysatori.ui.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookReflectionStateTest {
    @Test
    fun summaryActionTextUsesUpdateWhenSummaryExists() {
        assertEquals("沉淀这一段", bookReflectionSummaryActionText(summary = ""))
        assertEquals("更新沉淀", bookReflectionSummaryActionText(summary = "我理解到的核心：边界"))
    }

    @Test
    fun startingPromptsAreFocusedAndLimited() {
        assertEquals(
            listOf(
                "这个观点我可能漏掉了哪些角度？",
                "帮我用更具体的例子解释一下",
                "你反问我几个问题，帮我想清楚",
            ),
            bookReflectionStartingPrompts(),
        )
    }

    @Test
    fun titleFromQuestionTrimsAndLimitsLength() {
        assertEquals("这个观点为什么成立", bookReflectionTitleFromQuestion("  这个观点为什么成立？\n还能怎么理解  "))
        assertEquals("新的思考", bookReflectionTitleFromQuestion("   "))
        assertEquals("12345678901234567890", bookReflectionTitleFromQuestion("1234567890123456789012345"))
    }

    @Test
    fun titleFromSummaryCoreLineUsesCoreContent() {
        val summary = """
            我理解到的核心：真正的问题是把短期情绪当成长期判断。
            我补上的角度：需要区分感受和事实。
            还值得继续想的问题：我在哪些场景会这样？
        """.trimIndent()

        assertEquals("真正的问题是把短期情绪当成长期判断", bookReflectionTitleFromSummary(summary))
        assertEquals("12345678901234567890", bookReflectionTitleFromSummary("我理解到的核心：1234567890123456789012345。"))
        assertEquals("新的思考", bookReflectionTitleFromSummary("没有固定结构"))
    }

    @Test
    fun retryIsAllowedOnlyForLatestFailedAssistantOrLatestUser() {
        val messages = listOf(
            BookReflectionMessageUi("1", "user", "问题一", 1L, "ready", ""),
            BookReflectionMessageUi("2", "assistant", "失败", 2L, "failed", "网络错误"),
        )
        assertTrue(bookReflectionCanRetryLatest(messages))

        val readyMessages = listOf(
            BookReflectionMessageUi("1", "user", "问题一", 1L, "ready", ""),
            BookReflectionMessageUi("2", "assistant", "回答", 2L, "ready", ""),
        )
        assertFalse(bookReflectionCanRetryLatest(readyMessages))

        val onlyUser = listOf(BookReflectionMessageUi("1", "user", "问题一", 1L, "ready", ""))
        assertTrue(bookReflectionCanRetryLatest(onlyUser))
    }

    @Test
    fun schemaDefinesBookReflectionTablesAndQueries() {
        val schema = java.io.File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()

        assertTrue(schema.contains("CREATE TABLE book_viewpoint_ai_session"))
        assertTrue(schema.contains("CREATE TABLE book_viewpoint_ai_message"))
        assertTrue(schema.contains("selectBookReflectionSessionsByViewpoint:"))
        assertTrue(schema.contains("insertBookReflectionMessage:"))
        assertTrue(schema.contains("updateBookReflectionSummary:"))
    }

    @Test
    fun migrationDefinesVersionTwelveForBookReflection() {
        val config = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt").readText()
        val migration = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt").readText()

        assertTrue(config.contains("currentSchemaVersion = 12L"))
        assertTrue(migration.contains("if (currentVersion < 12)"))
        assertTrue(migration.contains("migrateV11ToV12()"))
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS book_viewpoint_ai_session"))
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS book_viewpoint_ai_message"))
    }
}
