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
    fun schemaDefinesExplicitBookReflectionCascadeDeleteQueries() {
        val schema = java.io.File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()

        assertTrue(schema.contains("deleteBookReflectionMessagesByViewpoint:"))
        assertTrue(schema.contains("deleteBookReflectionSessionsByViewpoint:"))
        assertTrue(schema.contains("deleteBookReflectionMessagesByBook:"))
        assertTrue(schema.contains("deleteBookReflectionSessionsByBook:"))
    }

    @Test
    fun viewpointRepositoryDeletesReflectionRowsBeforeViewpoints() {
        val source = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/BookViewpointRepository.kt").readText()
        val deleteBody = source.substringAfter("fun delete(id: Long)").substringBefore("fun deleteByBook")
        val deleteByBookBody = source.substringAfter("fun deleteByBook(bookId: Long)").substringBefore("fun getAllSync")

        assertTrue(deleteBody.indexOf("deleteBookReflectionMessagesByViewpoint") in 0 until deleteBody.indexOf("deleteBookReflectionSessionsByViewpoint"))
        assertTrue(deleteBody.indexOf("deleteBookReflectionSessionsByViewpoint") in 0 until deleteBody.indexOf("deleteViewpoint"))
        assertTrue(deleteByBookBody.indexOf("deleteBookReflectionMessagesByBook") in 0 until deleteByBookBody.indexOf("deleteBookReflectionSessionsByBook"))
        assertTrue(deleteByBookBody.indexOf("deleteBookReflectionSessionsByBook") in 0 until deleteByBookBody.indexOf("deleteViewpointsByBook"))
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

    @Test
    fun bookReflectionQueriesUseStableOrdering() {
        val schema = java.io.File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()

        assertEquals(2, "ORDER BY updated_at DESC, id DESC".toRegex().findAll(schema).count())
        assertTrue(schema.contains("ORDER BY last_opened_at DESC, id DESC LIMIT 1"))
        assertTrue(schema.contains("ORDER BY created_at ASC, id ASC"))
    }

    @Test
    fun bookReflectionRepositoryReadsInsertedIdsInTransactions() {
        val repository = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/BookViewpointAiRepository.kt").readText()

        assertTrue(repository.contains("fun createSession"))
        assertTrue(repository.contains("fun insertMessage"))
        assertEquals(2, "q.transactionWithResult".toRegex().findAll(repository).count())
    }

    @Test
    fun viewModelHasReadingReflectionActions() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt").readText()

        assertTrue(source.contains("fun openViewpoint("))
        assertTrue(source.contains("fun sendMessage("))
        assertTrue(source.contains("fun createNewSegment("))
        assertTrue(source.contains("fun generateSummary("))
        assertTrue(source.contains("fun retryLatest("))
        assertTrue(source.contains("fun toggleHistory("))
        assertTrue(source.contains("fun selectSession("))
        assertTrue(source.contains("fun stopGeneration("))
        assertTrue(source.contains("BookViewpointAiRepository"))
        assertTrue(source.contains("BookReflectionService"))
    }

    @Test
    fun viewModelIsRegisteredInKoin() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt").readText()

        assertTrue(source.contains("BookReflectionViewModel"))
        assertTrue(source.contains("reflectionRepo = get<BookViewpointAiRepository>()"))
        assertTrue(source.contains("reflectionService = get<BookReflectionService>()"))
    }

    @Test
    fun viewModelRetriesWithoutDuplicatingUserMessage() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt").readText()

        assertFalse(source.contains("sendMessage(latestQuestion)"))
        assertTrue(source.contains("insertUserMessage: Boolean"))
    }

    @Test
    fun viewModelHandlesCancellationSeparately() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt").readText()

        assertTrue(source.contains("CancellationException"))
        assertTrue(source.contains("已停止生成"))
    }

    @Test
    fun viewModelDoesNotLetStaleJobsClearNewProcessingState() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt").readText()

        assertTrue(source.contains("currentCoroutineContext()[Job]"))
        assertTrue(source.contains("if (activeJob == finishedJob)"))
    }

    @Test
    fun viewModelGuardsAsyncReloadsForCurrentSession() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt").readText()

        assertTrue(source.contains("force: Boolean = false"))
        assertTrue(source.contains("activeSession?.id != sessionId"))
        assertTrue(source.contains("force = true"))
    }

    @Test
    fun reflectionSheetUsesRequiredUserFacingLabels() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()

        assertTrue(source.contains("深入想想"))
        assertTrue(source.contains("沉淀这一段"))
        assertTrue(source.contains("更新沉淀"))
        assertTrue(source.contains("换个角度聊"))
        assertTrue(source.contains("历史"))
        assertTrue(source.contains("查看过程"))
        assertTrue(source.contains("继续聊"))
    }

    @Test
    fun reflectionSheetHandlesScrollStateAndHistoryActions() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()

        assertTrue(source.contains("LazyColumn("))
        assertTrue(source.contains("state.isLoading"))
        assertTrue(source.contains("正在加载思考片段..."))
        assertTrue(source.contains("state.error"))
        assertTrue(source.contains("onViewSessionProcess"))
        assertTrue(source.contains("onToggleHistory()"))
        assertFalse(source.contains("Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s))"))
        assertEquals(1, "LazyColumn\\(".toRegex().findAll(source).count())
    }

    @Test
    fun reflectionSheetSurfacesSummaryFailure() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()

        assertTrue(source.contains("summaryStatus == \"failed\""))
        assertTrue(source.contains("summaryError"))
        assertTrue(source.contains("沉淀失败，请稍后重试。"))
    }
}
