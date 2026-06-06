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
        assertTrue(source.contains("fun deleteSession("))
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

        assertTrue(source.contains("想一想"))
        assertFalse(source.contains("深入想想"))
        assertTrue(source.contains("历史"))
        assertTrue(source.contains("已沉淀"))
        assertTrue(source.contains("新增问题"))
        assertTrue(source.contains("补角度"))
        assertTrue(source.contains("沉淀"))
        assertTrue(source.contains("回到当前"))
        assertFalse(source.contains("Text(\"开始\")"))
        assertFalse(source.contains("Text(\"当前\")"))
        assertFalse(source.contains("Text(\"历史\")"))
        assertFalse(source.contains("Text(\"已沉淀\")"))
        assertFalse(source.contains("Text(\"新增问题\")"))
        assertFalse(source.contains("沉淀这一段"))
        assertFalse(source.contains("换个角度聊"))
        assertFalse(source.contains("继续追问，或者把有用的部分沉淀下来。"))
        assertFalse(source.contains("展开观点"))
        assertFalse(source.contains("收起观点"))
        assertFalse(source.contains("从一个问题开始。也可以直接在底部输入自己的问题。"))
    }

    @Test
    fun reflectionSheetHandlesScrollStateAndHistoryActions() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()
        val lazyColumnBody = source.extractCallBlock("LazyColumn(")

        assertTrue(source.contains("LazyColumn("))
        assertTrue(source.contains("BookReflectionHeader("))
        assertFalse(lazyColumnBody.contains("BookReflectionHeader("))
        assertTrue(source.contains("state.isLoading"))
        assertTrue(source.contains("正在加载思考片段..."))
        assertTrue(source.contains("state.error"))
        assertTrue(source.contains("onViewSessionProcess"))
        assertTrue(source.contains("onShowCurrent()"))
        assertTrue(source.contains("onShowHistory"))
        assertTrue(source.contains("onShowSettled"))
        assertTrue(source.contains("BookReflectionHeaderActions("))
        assertTrue(source.contains("BookReflectionQuestionRows("))
        assertFalse(source.contains("BookReflectionGuideCard()"))
        assertTrue(source.contains("bookReflectionHistorySessions("))
        assertTrue(source.contains("BookReflectionHistory("))
        assertTrue(source.contains("BookReflectionSettled("))
        assertTrue(source.contains("Text(\"反思历史\""))
        assertTrue(source.contains("\"回到当前\""))
        assertTrue(source.contains("BookReflectionHistoryItem("))
        assertTrue(source.contains("onDeleteSessionRequest"))
        assertTrue(source.contains("combinedClickable("))
        assertTrue(source.contains("Modifier.fillMaxWidth().combinedClickable("))
        assertTrue(source.contains("onLongClick = onLongClick"))
        assertTrue(source.contains("ConfirmDialog("))
        assertTrue(source.contains("删除对话"))
        assertFalse(source.contains("Text(\"查看过程\")"))
        assertFalse(source.contains("Text(\"继续聊\")"))
        assertFalse(source.contains("onViewSessionProcess(session.id)\n                onToggleHistory()"))
        assertFalse(source.contains("Button(onClick = onGenerateSummary, enabled = !(isProcessing || isSummarizing), modifier = Modifier.fillMaxWidth())"))
        assertEquals(1, "LazyColumn\\(".toRegex().findAll(source).count())
    }

    @Test
    fun reflectionSheetKeepsInputOutsideScrollableContent() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()
        val lazyColumnBody = source.extractCallBlock("LazyColumn(")

        assertTrue(source.contains("Column(modifier = Modifier.fillMaxWidth().fillMaxHeight"))
        assertTrue(source.contains("BookReflectionScrollableContent("))
        assertTrue(source.contains("ChatInputField("))
        assertFalse(lazyColumnBody.contains("ChatInputField("))
    }

    @Test
    fun reflectionSheetAutoScrollsWhileGeneratingMessages() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()
        val lazyColumnBody = source.extractCallBlock("LazyColumn(")

        assertTrue(source.contains("rememberLazyListState()"))
        assertTrue(source.contains("LaunchedEffect(state.messages.size, state.isProcessing"))
        assertTrue(source.contains("listState.animateScrollToItem"))
        assertTrue(lazyColumnBody.contains("state = listState"))
    }

    @Test
    fun reflectionSheetUsesTitleRowTabsAndQuestionRows() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()
        val headerBody = source.extractCallBlock("private fun BookReflectionHeader(")

        assertTrue(source.contains("BookReflectionHeaderActions("))
        assertFalse(source.contains("BookReflectionGuideCard()"))
        assertTrue(source.contains("BookReflectionQuestionRows("))
        assertTrue(source.contains("补角度"))
        assertTrue(source.contains("举例子"))
        assertTrue(source.contains("反问我"))
        assertTrue(source.contains("继续追问"))
        assertTrue(source.contains("换个角度"))
        assertTrue(source.contains("Icon(Icons.Filled.Add"))
        assertTrue(source.contains("Icon(Icons.Filled.History"))
        assertTrue(source.contains("Icon(Icons.Filled.CheckCircle"))
        assertTrue(source.contains("contentDescription = \"新增问题\""))
        assertTrue(source.contains("contentDescription = \"历史\""))
        assertTrue(source.contains("contentDescription = \"已沉淀\""))
        assertFalse(source.contains("label = { Text(\"当前\") }"))
        assertFalse(source.contains("label = { Text(\"历史\") }"))
        assertFalse(source.contains("label = { Text(\"已沉淀\") }"))
        assertTrue(headerBody.contains("verticalAlignment = Alignment.CenterVertically"))
        assertFalse(source.contains("Text(if (summary.isBlank()) \"沉淀\" else \"更新\")"))
    }

    @Test
    fun reflectionSheetProvidesNewQuestionActionForCurrentView() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()
        val booksScreen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt").readText()

        assertTrue(source.contains("onNewQuestion: () -> Unit"))
        assertTrue(source.contains("onNewQuestion = onNewQuestion"))
        assertFalse(source.contains("BookReflectionNewQuestionAction("))
        assertFalse(source.contains("Text(\"新增问题\")"))
        assertTrue(booksScreen.contains("onNewQuestion = reflectionViewModel::createNewSegment"))
    }

    @Test
    fun reflectionHeaderActionsHaveStableSelectedAndEnabledStates() {
        val readyConversation = listOf(
            BookReflectionMessageUi("1", "user", "问题", 1L, "ready", ""),
            BookReflectionMessageUi("2", "assistant", "回答", 2L, "ready", ""),
        )

        val currentState = bookReflectionHeaderActionState(BookReflectionState())
        assertFalse(currentState.historySelected)
        assertFalse(currentState.settledSelected)
        assertTrue(currentState.newQuestionEnabled)
        assertTrue(currentState.historyEnabled)
        assertTrue(currentState.settledEnabled)

        val historyState = bookReflectionHeaderActionState(BookReflectionState(reflectionView = BookReflectionView.History))
        assertTrue(historyState.historySelected)
        assertFalse(historyState.settledSelected)

        val settledState = bookReflectionHeaderActionState(
            BookReflectionState(
                reflectionView = BookReflectionView.Settled,
                messages = readyConversation,
                activeSession = BookReflectionSessionUi(1L, 2L, "标题", "已经沉淀", "ready", "", 3L, 4L),
            ),
        )
        assertFalse(settledState.historySelected)
        assertTrue(settledState.settledSelected)

        val processingState = bookReflectionHeaderActionState(BookReflectionState(isProcessing = true))
        assertFalse(processingState.newQuestionEnabled)
        assertFalse(processingState.historyEnabled)
        assertFalse(processingState.settledEnabled)
    }

    @Test
    fun reflectionHeaderIconButtonsUsePrimarySelectedColors() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()
        val headerActionsBody = source.extractCallBlock("private fun BookReflectionHeaderActions(")

        assertTrue(source.contains("BookReflectionHeaderIconAction("))
        assertTrue(source.contains("selected = actionState.historySelected"))
        assertTrue(source.contains("selected = actionState.settledSelected"))
        assertTrue(source.contains("selectedColor = MaterialTheme.colorScheme.primary.copy"))
        assertTrue(source.contains("iconTint = if (selected) MaterialTheme.colorScheme.primary"))
        assertFalse(headerActionsBody.contains("onGenerateSummary"))
    }

    @Test
    fun reflectionSheetRemovesScatteredBodyActionButtons() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()

        assertFalse(source.contains("BookReflectionInitialActions("))
        assertFalse(source.contains("Text(\"开始想\")"))
        assertFalse(source.contains("Text(\"换个问法\")"))
        assertFalse(source.contains("Text(\"看历史\")"))
        assertFalse(source.contains("BookReflectionSettleRow("))
    }

    @Test
    fun alternativePromptChangesQuestionWithoutCreatingBlankSession() {
        assertEquals("换个角度看这个观点，它还可能提醒我什么？", bookReflectionAlternativePrompt())

        val sheet = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()
        assertTrue(sheet.contains("\"换个角度\" to bookReflectionAlternativePrompt()"))
        assertFalse(sheet.contains("onNewSegment = onNewSegment"))
    }

    @Test
    fun settleActionAppearsOnlyForActiveUsefulConversation() {
        assertFalse(bookReflectionShouldShowSettleAction(emptyList(), BookReflectionView.Current))
        assertFalse(
            bookReflectionShouldShowSettleAction(
                listOf(BookReflectionMessageUi("1", "user", "问题", 1L, "ready", "")),
                BookReflectionView.Current,
            ),
        )
        assertTrue(
            bookReflectionShouldShowSettleAction(
                listOf(
                    BookReflectionMessageUi("1", "user", "问题", 1L, "ready", ""),
                    BookReflectionMessageUi("2", "assistant", "回答", 2L, "ready", ""),
                ),
                BookReflectionView.Current,
            ),
        )
        assertFalse(
            bookReflectionShouldShowSettleAction(
                listOf(
                    BookReflectionMessageUi("1", "user", "问题", 1L, "ready", ""),
                    BookReflectionMessageUi("2", "assistant", "回答", 2L, "ready", ""),
                ),
                BookReflectionView.History,
            ),
        )
    }

    @Test
    fun settledSessionsIncludeOnlySummarizedSessions() {
        val sessions = listOf(
            BookReflectionSessionUi(1L, 2L, "未沉淀", "", "ready", "", 10L, null),
            BookReflectionSessionUi(2L, 2L, "已沉淀", "核心观点", "ready", "", 12L, 11L),
            BookReflectionSessionUi(3L, 2L, "空白沉淀", "   ", "ready", "", 14L, 13L),
        )

        assertEquals(listOf(2L), bookReflectionSettledSessions(sessions).map { it.id })
    }

    @Test
    fun historySessionsExcludeActiveEmptySession() {
        val active = BookReflectionSessionUi(1L, 2L, "新的思考", "", "none", "", 10L, null)
        val previous = BookReflectionSessionUi(2L, 2L, "已有对话", "", "none", "", 9L, null)
        val messages = listOf(BookReflectionMessageUi("1", "user", "问题", 1L, "ready", ""))

        assertEquals(
            listOf(2L),
            bookReflectionHistorySessions(
                sessions = listOf(active, previous),
                activeSession = active,
                activeMessages = emptyList(),
            ).map { it.id },
        )
        assertEquals(
            listOf(1L, 2L),
            bookReflectionHistorySessions(
                sessions = listOf(active, previous),
                activeSession = active,
                activeMessages = messages,
            ).map { it.id },
        )
    }

    @Test
    fun reflectionSheetSurfacesSummaryFailure() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()

        assertTrue(source.contains("summaryStatus == \"failed\""))
        assertTrue(source.contains("summaryError"))
        assertTrue(source.contains("沉淀失败，请稍后重试。"))
    }

    @Test
    fun reflectionSheetDisablesSwitchingAndSummaryActionsWhileProcessing() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt").readText()
        val viewModelSource = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt").readText()

        assertTrue(source.contains("enabled = actionState.newQuestionEnabled"))
        assertTrue(source.contains("enabled = actionState.historyEnabled"))
        assertTrue(source.contains("enabled = actionState.settledEnabled"))
        assertTrue(viewModelSource.contains("isBusy = state.isLoading || state.isProcessing || state.isSummarizing"))
        assertTrue(source.contains("BookReflectionHistory("))
    }

    @Test
    fun viewModelCancelsStreamingBeforeChangingReflectionSessions() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt").readText()
        val createNewSegmentBody = source.substringAfter("fun createNewSegment()").substringBefore("fun generateSummary()")
        val selectSessionBody = source.substringAfter("fun selectSession(sessionId: Long)").substringBefore("fun stopGeneration()")

        assertTrue(createNewSegmentBody.contains("stopGeneration()"))
        assertTrue(selectSessionBody.contains("stopGeneration()"))
    }

    @Test
    fun viewModelDoesNotSummarizeWhileAnswerIsStreaming() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt").readText()
        val generateSummaryBody = source.substringAfter("fun generateSummary()").substringBefore("fun retryLatest()")

        assertTrue(generateSummaryBody.contains("snapshot.isProcessing"))
        assertTrue(generateSummaryBody.contains("isStreaming"))
        assertTrue(generateSummaryBody.contains("status == \"streaming\""))
    }

    @Test
    fun viewModelDeletesReflectionSessionMessagesBeforeSession() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt").readText()
        val deleteBody = source.substringAfter("fun deleteSession(sessionId: Long)").substringBefore("fun stopGeneration()")

        assertTrue(deleteBody.contains("stopGeneration()"))
        assertTrue(deleteBody.contains("reflectionRepo.deleteMessagesBySession(sessionId)"))
        assertTrue(deleteBody.contains("reflectionRepo.deleteSession(sessionId)"))
        assertTrue(deleteBody.indexOf("deleteMessagesBySession(sessionId)") in 0 until deleteBody.indexOf("deleteSession(sessionId)"))
        assertTrue(deleteBody.contains("reloadActiveSession"))
    }

    private fun String.extractCallBlock(anchor: String): String {
        assertTrue(contains(anchor), "Missing call anchor: $anchor")
        val start = indexOf(anchor)
        val bodyStart = indexOf('{', start)
        assertTrue(bodyStart >= 0, "Missing block body for call anchor: $anchor")

        var depth = 0
        for (index in bodyStart until length) {
            when (this[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return substring(start, index + 1)
                }
            }
        }
        throw AssertionError("Missing closing brace for call anchor: $anchor")
    }
}
