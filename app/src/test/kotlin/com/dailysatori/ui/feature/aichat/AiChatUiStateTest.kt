package com.dailysatori.ui.feature.aichat

import com.dailysatori.service.mcp.McpSearchResult
import com.dailysatori.ui.feature.home.AI_CHAT_TAB_INDEX
import com.dailysatori.ui.feature.home.TODAY_TAB_INDEX
import com.dailysatori.ui.feature.home.homeBottomBarVisibleForTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiChatUiStateTest {
    @Test
    fun chatInputUsesCompactLiquidGlassSizing() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt").readText()
        val compactSource = source.replace(Regex("\\s+"), " ")

        assertTrue(source.contains("private val ChatInputButtonSize = 34.dp"))
        assertTrue(source.contains("private val ChatInputContentMinHeight = Height.input"))
        assertFalse(source.contains("ChatInputMinHeight"))
        assertTrue(source.contains("RoundedCornerShape(Radius.circular)"))
        assertTrue(source.contains("MaterialTheme.colorScheme.surfaceContainerHighest"))
        assertTrue(compactSource.contains("contentAlignment = Alignment.CenterStart"))
        assertTrue(compactSource.contains("heightIn(min = ChatInputContentMinHeight)"))
        assertTrue(compactSource.contains("padding(contentPadding)"))
        assertFalse(compactSource.contains(".heightIn(min = ChatInputMinHeight) .padding(contentPadding)"))
        assertTrue(source.contains("contentPadding = PaddingValues"))
        assertTrue(source.contains("modifier = Modifier.size(ChatInputButtonSize)"))
        assertTrue(source.contains("textStyle = MaterialTheme.typography.bodyMedium.copy"))
        assertTrue(source.contains("style = MaterialTheme.typography.bodyMedium"))
        assertTrue(source.contains("minLines = 1"))
        assertTrue(source.contains("maxLines = if (compact) 1 else 3"))
    }

    @Test
    fun blankAssistantAnswerIsSuppressed() {
        assertNull(buildAssistantMessageOrNull("   ", emptyList(), emptyList(), now = 1L))
    }

    @Test
    fun nonBlankAssistantAnswerBuildsMessage() {
        val message = buildAssistantMessageOrNull("## 结论\n可以。", emptyList(), listOf("完成"), now = 1L)

        assertEquals("assistant", message?.role)
        assertEquals("## 结论\n可以。", message?.content)
        assertEquals(listOf("完成"), message?.steps)
    }

    @Test
    fun streamingChunkCreatesOrUpdatesSingleAssistantMessage() {
        val initial = AiChatState(messages = listOf(ChatMessageUi("u1", "user", "你好", 1L)))
        val first = initial.withStreamingAssistantChunk(
            messageId = "a1",
            chunk = "第一段",
            now = 2L,
        )
        val second = first.withStreamingAssistantChunk(
            messageId = "a1",
            chunk = "第二段",
            now = 3L,
        )

        assertEquals(2, second.messages.size)
        assertEquals("第一段第二段", second.messages.last().content)
        assertTrue(second.messages.last().isStreaming)
        assertTrue(second.isProcessing)
        assertEquals("", second.currentStep)
    }

    @Test
    fun streamingFinalizationAttachesReferencesAndStopsStreaming() {
        val state = AiChatState(messages = listOf(ChatMessageUi("a1", "assistant", "草稿", 1L, isStreaming = true)))
        val refs = listOf(McpSearchResult(1, "article", "新闻", "摘要", "2026-05-30"))

        val finished = state.finishedStreamingAssistant(
            messageId = "a1",
            finalContent = "最终回答",
            searchResults = refs,
            steps = listOf("完成"),
        )

        assertFalse(finished.messages.first().isStreaming)
        assertFalse(finished.isProcessing)
        assertEquals("", finished.currentStep)
        assertEquals("最终回答", finished.messages.first().content)
        assertEquals(refs, finished.messages.first().searchResults)
        assertEquals(listOf("完成"), finished.messages.first().steps)
    }

    @Test
    fun streamingFinalizationAppendsFinalAnswerWhenNoChunkArrived() {
        val initial = AiChatState(messages = listOf(ChatMessageUi("u1", "user", "你好", 1L)), isProcessing = true)
        val refs = listOf(McpSearchResult(1, "article", "新闻", "摘要", "2026-05-30"))

        val finished = initial.finishedStreamingAssistant(
            messageId = "a1",
            finalContent = "最终回答",
            searchResults = refs,
            steps = listOf("完成"),
            now = 2L,
        )

        assertFalse(finished.isProcessing)
        assertEquals("", finished.currentStep)
        assertEquals(2, finished.messages.size)
        assertEquals(ChatMessageUi("a1", "assistant", "最终回答", 2L, searchResults = refs, steps = listOf("完成")), finished.messages.last())
    }

    @Test
    fun streamingFinalizationDetectsErrorFromDisplayedFallbackContent() {
        val state = AiChatState(
            messages = listOf(ChatMessageUi("a1", "assistant", aiChatBlankResponseMessage(), 1L, isStreaming = true)),
        )

        val finished = state.finishedStreamingAssistant(
            messageId = "a1",
            finalContent = "",
            searchResults = emptyList(),
            steps = emptyList(),
        )

        assertTrue(finished.messages.first().isError)
        assertEquals(aiChatBlankResponseMessage(), finished.messages.first().content)
    }

    @Test
    fun streamingFinalizationDoesNotAppendBlankFinalAnswerWhenNoChunkArrived() {
        val initial = AiChatState(messages = listOf(ChatMessageUi("u1", "user", "你好", 1L)), isProcessing = true)

        val finished = initial.finishedStreamingAssistant(
            messageId = "a1",
            finalContent = "",
            searchResults = emptyList(),
            steps = emptyList(),
            now = 2L,
        )

        assertFalse(finished.isProcessing)
        assertEquals("", finished.currentStep)
        assertEquals(initial.messages, finished.messages)
    }

    @Test
    fun finalizedStreamingAssistantMessageUsesExistingUiTimestampForPersistence() {
        val refs = listOf(McpSearchResult(1, "article", "新闻", "摘要", "2026-05-30"))
        val finished = AiChatState(
            messages = listOf(ChatMessageUi("a1", "assistant", "草稿", 10L, isStreaming = true)),
            isProcessing = true,
        ).finishedStreamingAssistant(
            messageId = "a1",
            finalContent = "最终回答",
            searchResults = refs,
            steps = listOf("完成"),
            now = 20L,
        )

        val message = finished.finalizedAssistantMessageForPersistence("a1")

        assertEquals(10L, message?.timestamp)
        assertEquals("最终回答", message?.content)
        assertEquals(refs, message?.searchResults)
        assertEquals(listOf("完成"), message?.steps)
        assertFalse(message?.isStreaming ?: true)
    }

    @Test
    fun cancelledStreamingAssistantRemovesTransientMessage() {
        val state = AiChatState(
            messages = listOf(
                ChatMessageUi("u1", "user", "你好", 1L),
                ChatMessageUi("a1", "assistant", "半段", 2L, isStreaming = true),
            ),
            isProcessing = true,
            currentStep = "正在生成",
        )

        val cancelled = state.cancelledStreamingAssistant("a1")

        assertEquals(listOf(ChatMessageUi("u1", "user", "你好", 1L)), cancelled.messages)
        assertFalse(cancelled.isProcessing)
        assertEquals(aiChatStoppedStatusText(), cancelled.currentStep)
    }

    @Test
    fun emptyStreamingChunkLeavesStateUnchanged() {
        val state = AiChatState(
            messages = listOf(ChatMessageUi("a1", "assistant", "草稿", 1L, isStreaming = true)),
            isProcessing = true,
            currentStep = "正在生成",
        )

        assertEquals(state, state.withStreamingAssistantChunk(messageId = "a1", chunk = "", now = 2L))
    }

    @Test
    fun stoppedGenerationUsesTransientStatusText() {
        assertEquals("已停止生成", aiChatStoppedStatusText())
    }

    @Test
    fun stoppedGenerationStateReturnsToIdleWithStatus() {
        val state = AiChatState(isProcessing = true, currentStep = "正在生成回答...")

        val stopped = state.stoppedGeneration()

        assertFalse(stopped.isProcessing)
        assertEquals("已停止生成", stopped.currentStep)
    }

    @Test
    fun inputActionSwitchesBetweenSendAndStop() {
        assertEquals(ChatInputAction.Send, chatInputAction(isProcessing = false))
        assertEquals(ChatInputAction.Stop, chatInputAction(isProcessing = true))
        assertEquals("发送", chatInputActionDescription(ChatInputAction.Send))
        assertEquals("停止生成", chatInputActionDescription(ChatInputAction.Stop))
    }

    @Test
    fun messagePresentationWeakensUserAndStructuresAssistant() {
        assertEquals(ChatMessageTreatment.MutedUserNote, chatMessageTreatment(role = "user", isError = false))
        assertEquals(ChatMessageTreatment.StructuredAssistantNote, chatMessageTreatment(role = "assistant", isError = false))
        assertEquals(ChatMessageTreatment.ErrorNote, chatMessageTreatment(role = "assistant", isError = true))
        assertFalse(assistantMessageUsesEditorialRail())
        assertTrue(userMessageUsesMutedContainer())
    }

    @Test
    fun assistantMessageAvoidsIntrinsicSizingAroundMarkdown() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt").readText()

        assertFalse(source.contains("IntrinsicSize"))
        assertFalse(source.contains("height(IntrinsicSize.Min)"))
    }

    @Test
    fun chatBubblesUseWechatAlignmentAndNoAssistantRail() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt").readText()

        assertTrue(source.contains("horizontalAlignment = if (isUser) Alignment.End else Alignment.Start"))
        assertTrue(source.contains("horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start"))
        assertTrue(source.contains("widthIn(max = ChatUserBubbleMaxWidth)"))
        assertTrue(source.contains("if (isUser) widthIn(max = ChatUserBubbleMaxWidth) else fillMaxWidth()"))
        assertFalse(source.contains("widthIn(max = ChatAssistantBubbleMaxWidth)"))
        assertFalse(source.contains("drawRoundRect("))
        assertFalse(source.contains("AssistantKicker("))
        assertFalse(source.contains("text = \"AI 回复\""))
    }

    @Test
    fun chatMessageActionsUseHorizontalFloatingRow() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt").readText()

        assertTrue(source.contains("Popup("))
        assertTrue(source.contains("Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs))"))
        assertTrue(source.contains("ChatMessageActionButton("))
        assertFalse(source.contains("DropdownMenuItem("))
    }

    @Test
    fun streamingAssistantUsesPlainTextBeforeMarkdown() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt").readText()

        assertTrue(source.contains("isStreaming = message.isStreaming"))
        assertTrue(source.contains("if (isStreaming)"))
        assertTrue(source.contains("Text("))
        assertTrue(source.contains("Markdown("))
    }

    @Test
    fun streamingAssistantSkipsStructuredParsingBeforePlainText() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt").readText()

        assertTrue(source.indexOf("if (isStreaming)") < source.indexOf("structuredAssistantContent(content)"))
    }

    @Test
    fun viewModelUsesStreamingMcpPathAndPersistsFinalMessage() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt").readText()

        assertTrue(source.contains("processQueryStreaming("))
        assertTrue(source.contains("withStreamingAssistantChunk("))
        assertTrue(source.contains("finishedStreamingAssistant("))
        assertTrue(source.contains("finalizedAssistantMessageForPersistence(assistantMessageId)?.let { persistMessage(it) }"))
    }

    @Test
    fun assistantReplyExtractsReadableTitleAndBody() {
        val structured = structuredAssistantContent("## 结论\n可以把焦虑写成控制感。")

        assertEquals("结论", structured.title)
        assertEquals("可以把焦虑写成控制感。", structured.body)
    }

    @Test
    fun assistantReplyFallsBackToBriefTitle() {
        val structured = structuredAssistantContent("可以把焦虑写成控制感。")

        assertEquals("AI 回复", structured.title)
        assertEquals("可以把焦虑写成控制感。", structured.body)
    }

    @Test
    fun plainAssistantReplyDoesNotRenderFallbackTitle() {
        assertFalse(assistantShouldRenderStructuredTitle(structuredAssistantContent("普通回答")))
        assertTrue(assistantShouldRenderStructuredTitle(structuredAssistantContent("## 结论\n普通回答")))
    }

    @Test
    fun assistantReplyDoesNotRenderHashtagAsStructuredTitle() {
        val structured = structuredAssistantContent("#hashtag\n正文")

        assertFalse(assistantShouldRenderStructuredTitle(structured))
    }

    @Test
    fun assistantReplyDoesNotRenderInvalidAtxHeadingAsStructuredTitle() {
        val structured = structuredAssistantContent("####### not heading\n正文")

        assertFalse(assistantShouldRenderStructuredTitle(structured))
    }

    @Test
    fun assistantReplyRendersValidAtxHeadingAsStructuredTitle() {
        val structured = structuredAssistantContent("## 标题\n正文")

        assertTrue(assistantShouldRenderStructuredTitle(structured))
    }

    @Test
    fun assistantReplyPreservesIntroWhenHeadingIsNotFirstLine() {
        val structured = structuredAssistantContent("先说结论。\n\n## 细节\n后续内容。")

        assertEquals("AI 回复", structured.title)
        assertEquals("先说结论。\n\n## 细节\n后续内容。", structured.body)
    }

    @Test
    fun assistantReplyDoesNotUseCodeCommentAsTitle() {
        val structured = structuredAssistantContent("```bash\n# comment\n```\n说明。")

        assertEquals("AI 回复", structured.title)
        assertEquals("```bash\n# comment\n```\n说明。", structured.body)
    }

    @Test
    fun chatInputOffersEditorialQuickPrompts() {
        assertEquals(listOf("整理今天", "提炼主题", "搜索记忆"), chatInputSuggestionLabels())
        assertEquals("问点什么", chatInputPlaceholderText())
    }

    @Test
    fun chatInputSuggestionsOnlyShowWhenIdleAndEmpty() {
        assertTrue(chatInputShowsSuggestions(inputText = "", isProcessing = false))
        assertFalse(chatInputShowsSuggestions(inputText = "已有内容", isProcessing = false))
        assertFalse(chatInputShowsSuggestions(inputText = "", isProcessing = true))
    }

    @Test
    fun chatInputSuggestionKeepsExistingText() {
        assertEquals("整理今天", chatInputTextAfterSuggestion(currentText = "", suggestion = "整理今天"))
        assertEquals("已有内容 整理今天", chatInputTextAfterSuggestion(currentText = "已有内容", suggestion = "整理今天"))
    }

    @Test
    fun emptyStateUsesEditorialWelcomeInsteadOfGenericPlaceholder() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt").readText()

        assertTrue(source.contains("AiChatWelcomeBrief("))
        assertTrue(source.contains("text = \"Assistant Note\""))
        assertTrue(source.contains("text = \"把今天的阅读和想法整理成一条线索\""))
        assertFalse(source.contains("EmptyState("))
    }

    @Test
    fun topBarDoesNotExposeRefreshOrMemorySearchAction() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt").readText()

        assertFalse(aiChatShowsRefreshAction())
        assertFalse(aiChatShowsMemorySearchAction())
        assertFalse(screen.contains("MemorySearchSheet("))
        assertFalse(screen.contains("showMemorySheet"))
        assertFalse(screen.contains("contentDescription = \"记忆搜索\""))
    }

    @Test
    fun processingStateUsesBubbleLoadingOnly() {
        assertFalse(aiChatShowsTopProgressIndicator(isProcessing = true, currentStep = "正在查询数据..."))
        assertTrue(aiChatShowsThinkingBubble(isProcessing = true, hasStreamingAssistant = false))
        assertFalse(aiChatShowsThinkingBubble(isProcessing = true, hasStreamingAssistant = true))
        assertFalse(aiChatShowsThinkingBubble(isProcessing = false, hasStreamingAssistant = false))
    }

    @Test
    fun thinkingIndicatorUsesUnifiedBlueAiIconChip() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt").readText()

        assertTrue(source.contains("Icons.Filled.AutoAwesome"))
        assertTrue(source.contains("MaterialTheme.colorScheme.primary"))
        assertTrue(source.contains("val showThinking = aiChatShowsThinkingBubble("))
        assertTrue(source.contains("text = \"AI 正在思考\""))
        assertFalse(source.contains("text = \"思考中...\""))
    }

    @Test
    fun chatDisplayMessagesKeepsNaturalOrderForBottomAnchoredList() {
        val oldest = ChatMessageUi("old", "user", "最旧", 1L)
        val newest = ChatMessageUi("new", "assistant", "最新", 2L)

        assertEquals(listOf(oldest, newest), aiChatDisplayMessages(listOf(oldest, newest)))
    }

    @Test
    fun aiChatScreenDoesNotAllocateReversedListOnRecomposition() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt").readText()

        assertFalse(source.contains("asReversed()"))
        assertFalse(source.contains("reverseLayout = true"))
        assertFalse(source.contains("animateScrollToItem(displayMessages.lastIndex)"))
        assertTrue(source.contains("animateScrollToItem(targetIndex, scrollOffset = bottomScrollOffset)"))
    }

    @Test
    fun activeChatChangesStillAutoScroll() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt").readText()

        assertTrue(source.contains("aiChatDisplayMessages(state.messages)"))
        assertFalse(source.contains("scrollToItem(currentMessageCount - 1)"))
        assertFalse(source.contains("animateScrollToItem(currentMessageCount - 1)"))
    }

    @Test
    fun chatInitialHistoryUsesLatestPageInsteadOfFullSession() {
        assertEquals(12, aiChatHistoryPageSize())

        val repository = java.io.File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/ChatConversationRepository.kt").readText()
        val viewModel = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt").readText()

        assertTrue(repository.contains("fun getLatestBySession("))
        assertTrue(viewModel.contains("chatConversationRepo.getLatestBySession(latestSession"))
        assertFalse(viewModel.contains("chatConversationRepo.getBySession(latestSession)"))
    }

    @Test
    fun pagedChatQueriesAvoidNestedSubqueriesThatCrashSqlDelightRuntime() {
        val schema = java.io.File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val latestQuery = schema.substringAfter("selectLatestChatBySession:").substringBefore("selectChatBefore:")
        val beforeQuery = schema.substringAfter("selectChatBefore:").substringBefore("insertChat:")

        assertFalse(latestQuery.contains("FROM ("))
        assertFalse(beforeQuery.contains("FROM ("))
    }

    @Test
    fun olderChatHistoryLoadsOnlyAfterUserScrollsToTop() {
        assertFalse(
            aiChatShouldLoadOlder(
                firstVisibleItemIndex = 0,
                firstVisibleItemKey = "old",
                oldestMessageId = "old",
                totalItemsCount = 12,
                isScrollInProgress = false,
                canLoadOlder = true,
                isLoadingOlder = false,
                messageCount = 12,
            ),
        )
        assertTrue(
            aiChatShouldLoadOlder(
                firstVisibleItemIndex = 1,
                firstVisibleItemKey = "old",
                oldestMessageId = "old",
                totalItemsCount = 12,
                isScrollInProgress = true,
                canLoadOlder = true,
                isLoadingOlder = false,
                messageCount = 12,
            ),
        )
        assertFalse(
            aiChatShouldLoadOlder(
                firstVisibleItemIndex = 8,
                firstVisibleItemKey = "old",
                oldestMessageId = "old",
                totalItemsCount = 12,
                isScrollInProgress = true,
                canLoadOlder = true,
                isLoadingOlder = false,
                messageCount = 12,
            ),
        )
    }

    @Test
    fun aiChatShouldLoadOlderRequiresVisibleOldestMessageKey() {
        assertTrue(
            aiChatShouldLoadOlder(
                firstVisibleItemIndex = 1,
                firstVisibleItemKey = "current-oldest",
                oldestMessageId = "current-oldest",
                totalItemsCount = 12,
                isScrollInProgress = true,
                canLoadOlder = true,
                isLoadingOlder = false,
                messageCount = 12,
            ),
        )
        assertFalse(
            aiChatShouldLoadOlder(
                firstVisibleItemIndex = 1,
                firstVisibleItemKey = "previous-oldest",
                oldestMessageId = "current-oldest",
                totalItemsCount = 12,
                isScrollInProgress = true,
                canLoadOlder = true,
                isLoadingOlder = false,
                messageCount = 12,
            ),
        )
    }

    @Test
    fun aiChatBottomScrollTargetIncludesStatusRows() {
        assertEquals(
            2,
            aiChatBottomScrollTargetIndex(messageCount = 3, showThinking = false, showStoppedStatus = false),
        )
        assertEquals(
            3,
            aiChatBottomScrollTargetIndex(messageCount = 3, showThinking = true, showStoppedStatus = false),
        )
        assertEquals(
            3,
            aiChatBottomScrollTargetIndex(messageCount = 3, showThinking = false, showStoppedStatus = true),
        )
        assertEquals(
            -1,
            aiChatBottomScrollTargetIndex(messageCount = 0, showThinking = true, showStoppedStatus = false),
        )
    }

    @Test
    fun aiChatNearBottomAllowsOldBottomWhenStatusRowIsAppended() {
        assertTrue(aiChatIsNearBottom(lastVisibleIndex = 0, targetIndex = 2, appendedStatusRows = 1))
        assertFalse(aiChatIsNearBottom(lastVisibleIndex = 0, targetIndex = 2, appendedStatusRows = 0))
        assertTrue(aiChatIsNearBottom(lastVisibleIndex = null, targetIndex = 2, appendedStatusRows = 1))
    }

    @Test
    fun initialLoadedChatForcesOneBottomScroll() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt").readText()

        assertTrue(aiChatShouldForceInitialBottomScroll(hasCompletedInitialScroll = false, messageCount = 12))
        assertFalse(aiChatShouldForceInitialBottomScroll(hasCompletedInitialScroll = true, messageCount = 12))
        assertFalse(aiChatShouldForceInitialBottomScroll(hasCompletedInitialScroll = false, messageCount = 0))
        assertTrue(source.contains("scrollToItem(targetIndex)"))
        assertTrue(source.contains("scrollToItem(targetIndex, scrollOffset = bottomScrollOffset)"))
    }

    @Test
    fun bottomScrollOffsetAlignsTallLastMessageToViewportBottom() {
        assertEquals(
            400,
            aiChatBottomScrollOffset(itemSize = 1000, viewportSize = 600, afterContentPadding = 0),
        )
        assertEquals(
            0,
            aiChatBottomScrollOffset(itemSize = 300, viewportSize = 600, afterContentPadding = 0),
        )
        assertEquals(
            432,
            aiChatBottomScrollOffset(itemSize = 1000, viewportSize = 600, afterContentPadding = 32),
        )
    }

    @Test
    fun chatListUsesContentTypesForFasterReuse() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt").readText()

        assertEquals("user", aiChatMessageContentType(ChatMessageUi("u", "user", "问题", 1L)))
        assertEquals("assistant", aiChatMessageContentType(ChatMessageUi("a", "assistant", "回答", 2L)))
        assertTrue(source.contains("contentType = { aiChatMessageContentType(it) }"))
    }

    @Test
    fun chatScreenRequestsOlderHistoryFromLazyListTop() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt").readText()

        assertTrue(source.contains("snapshotFlow"))
        assertTrue(source.contains("aiChatShouldLoadOlder("))
        assertTrue(source.contains("viewModel::loadOlderMessages"))
    }

    @Test
    fun chatScreenAutoScrollsToComputedBottomTarget() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt").readText()

        assertTrue(source.contains("aiChatBottomScrollTargetIndex("))
        assertTrue(source.contains("animateScrollToItem(targetIndex, scrollOffset = bottomScrollOffset)"))
    }

    @Test
    fun homeBottomBarRemainsVisibleOnAiTab() {
        val home = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()
        val bottomBar = home.substringAfter("private fun HomeBottomBarSurface(").substringBefore("@Composable\nprivate fun AiCompactInputRow(")
        val scaffoldCall = home.substringAfter("Scaffold(").substringBefore(") { innerPadding ->")

        assertTrue(chatInputUsesImePadding())
        assertTrue(homeBottomBarVisibleForTab(AI_CHAT_TAB_INDEX))
        assertTrue(homeBottomBarVisibleForTab(TODAY_TAB_INDEX))
        assertTrue(home.contains("contentAlignment = Alignment.BottomCenter"))
        assertTrue(home.contains("HomeBottomBarSurface("))
        assertTrue(bottomBar.contains(".imePadding()"))
        assertFalse(scaffoldCall.contains("bottomBar ="))
    }

    @Test
    fun aiTabUsesSharedMorphingHomeBottomBar() {
        val home = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt").readText()
        val input = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt").readText()

        assertTrue(home.contains("homeBottomBarVisibleForTab(selectedIndex)"))
        assertTrue(home.contains("val isAiMode = selectedIndex == AI_CHAT_TAB_INDEX"))
        assertTrue(home.contains("visible = isAiMode"))
        assertTrue(home.contains("HomeBottomBarSurface("))
        assertTrue(home.contains("Icons.Filled.Language"))
        assertTrue(home.contains("ChatInputField("))
        assertTrue(home.contains("selectedIndex = TODAY_TAB_INDEX"))
        assertFalse(screen.contains("AiChatCompactBottomBar("))
        assertFalse(screen.contains("Icons.Filled.Home"))
        assertFalse(screen.contains("bottomBar = {"))
        assertTrue(input.contains("fun ChatInputField("))
        assertTrue(input.contains("compact: Boolean = false"))
        assertTrue(input.contains("singleLine = compact"))
        assertTrue(home.contains("compact = true"))
    }

    @Test
    fun aiBottomBarUsesDirectionalSlideInsteadOfWeightMorph() {
        val home = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()
        val bottomBar = home.substringAfter("private fun HomeBottomBarSurface(").substringBefore("@Composable\nprivate fun AiCompactInputRow(")

        assertTrue(home.contains("AnimatedVisibility("))
        assertFalse(home.contains("AnimatedContent("))
        assertTrue(home.contains("visible = isAiMode"))
        assertTrue(home.contains("enter = homeBottomBarEnterTransition()"))
        assertTrue(home.contains("exit = homeBottomBarExitTransition()"))
        assertTrue(home.contains("private const val HomeBottomBarSlideDurationMillis = 480"))
        assertTrue(home.contains("animationSpec = tween(HomeBottomBarSlideDurationMillis)"))
        assertTrue(home.contains("slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(HomeBottomBarSlideDurationMillis))"))
        assertTrue(home.contains("slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(HomeBottomBarSlideDurationMillis))"))
        assertTrue(home.contains("selectedIndex = TODAY_TAB_INDEX"))
        assertFalse(bottomBar.contains("home-bottom-ai-input-weight"))
        assertFalse(bottomBar.contains("home-bottom-tabs-weight"))
        assertFalse(bottomBar.contains("Modifier.weight(inputWeight)"))
    }

    @Test
    fun aiExpandedBottomBarKeepsStableOuterContainerDuringSlide() {
        val home = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()
        val bottomBar = home.substringAfter("private fun HomeBottomBarSurface(").substringBefore("@Composable\nprivate fun AiCompactInputRow(")
        val glassBody = home.substringAfter("private fun HomeGlassSurface(").substringBefore("@Composable\nprivate fun HomeTabNavigationBar(")
        val gradle = java.io.File("../gradle/libs.versions.toml").readText()
        val appGradle = java.io.File("build.gradle.kts").readText()

        assertTrue(gradle.contains("haze = \"1.7.2\""))
        assertTrue(gradle.contains("haze = { group = \"dev.chrisbanes.haze\", name = \"haze\", version.ref = \"haze\" }"))
        assertTrue(appGradle.contains("implementation(libs.haze)"))
        assertTrue(home.contains("rememberHazeState()"))
        assertTrue(home.contains(".hazeSource(state = hazeState)"))
        assertTrue(glassBody.contains(".hazeEffect("))
        assertTrue(glassBody.contains("state = hazeState"))
        assertTrue(glassBody.contains("style = HazeDefaults.style("))
        assertTrue(glassBody.contains("backgroundColor = Color.Transparent"))
        assertTrue(glassBody.contains("blurRadius = HomeBottomBarHazeBlurRadius"))
        assertTrue(glassBody.contains("noiseFactor = HomeBottomBarHazeNoiseFactor"))
        assertTrue(home.contains("private val HomeBottomBarHazeBlurRadius = 10.dp"))
        assertTrue(home.contains("private const val HomeBottomBarGlassAlpha = 0.10f"))
        assertTrue(home.contains("private const val HomeBottomBarAiGlassAlpha = 0.16f"))
        assertTrue(home.contains("private const val HomeBottomBarHazeTintAlpha = 0.08f"))
        assertTrue(home.contains("private const val HomeBottomBarHazeNoiseFactor = 0.02f"))
        assertTrue(home.contains("private const val HomeBottomBarGlassTopHighlightAlpha = 0.08f"))
        assertTrue(home.contains("private const val HomeBottomBarGlassBottomRefractionAlpha = 0.04f"))
        assertTrue(bottomBar.contains("color = MaterialTheme.colorScheme.surface.copy(alpha = HomeBottomBarGlassAlpha)"))
        assertTrue(bottomBar.contains("Box(\n        modifier = Modifier\n            .navigationBarsPadding()"))
        assertTrue(bottomBar.contains("HomeGlassSurface(\n            modifier = Modifier.fillMaxWidth()"))
        assertFalse(bottomBar.contains("HomeGlassSurface(\n        modifier = Modifier\n            .navigationBarsPadding()"))
        assertFalse(glassBody.contains("navigationBarsPadding()"))
        assertFalse(glassBody.contains("imePadding()"))
        assertTrue(home.contains("Brush.verticalGradient("))
        assertTrue(glassBody.contains("Brush.horizontalGradient("))
        assertTrue(glassBody.contains("HomeBottomBarGlassTopHighlightAlpha"))
        assertTrue(glassBody.contains("HomeBottomBarGlassBottomRefractionAlpha"))
        assertFalse(bottomBar.contains("border ="))
        assertFalse(home.contains("BorderStroke("))
        assertFalse(bottomBar.contains("home-bottom-container-alpha"))
        assertFalse(bottomBar.contains("copy(alpha = containerAlpha)"))
    }

    @Test
    fun aiOverlayHidesUnderlyingTabsToAvoidDuplicateMapIcon() {
        val home = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()
        val bottomBar = home.substringAfter("private fun HomeBottomBarSurface(").substringBefore("@Composable\nprivate fun AiCompactInputRow(")

        assertTrue(bottomBar.contains("visible = !isAiMode"))
        assertTrue(bottomBar.contains("label = \"home-bottom-tabs\""))
        assertFalse(bottomBar.contains("HomeTabNavigationBar(\n                selectedIndex = selectedIndex"))
    }

    @Test
    fun aiOverlayDoesNotAddSeparateRowBackground() {
        val home = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()
        val compactBody = home.substringAfter("private fun AiCompactInputRow(").substringBefore("@Composable\nprivate fun HomeTabNavigationBar(")

        assertFalse(compactBody.contains("color = MaterialTheme.colorScheme.surfaceContainerHigh"))
        assertFalse(compactBody.contains("border ="))
        assertTrue(compactBody.contains("color = MaterialTheme.colorScheme.surface.copy(alpha = HomeBottomBarAiGlassAlpha)"))
        assertTrue(compactBody.contains("HomeGlassSurface("))
        assertTrue(compactBody.contains("Row("))
    }

    @Test
    fun aiChatListAvoidsExtraBottomGapAboveHomeOwnedInput() {
        val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt").readText()
        val lazyList = screen.substringAfter("LazyColumn(").substringBefore(") {\n                items(")

        assertTrue(lazyList.contains("contentPadding = PaddingValues(top = Spacing.m, bottom = 0.dp)"))
        assertFalse(lazyList.contains("bottom = Spacing.l"))
    }

    @Test
    fun resolvesChatMessageActions() {
        val user = ChatMessageUi("u1", "user", "问题", 1L)
        val assistant = ChatMessageUi("a1", "assistant", "回答", 2L)
        val messages = listOf(user, assistant)

        assertEquals(listOf(ChatMessageAction.Copy, ChatMessageAction.Delete, ChatMessageAction.ReAsk), chatMessageActions(user))
        assertEquals("问题", reAskContentForMessage(messages, assistant))
        assertEquals(listOf(user), deleteChatMessage(messages, assistant.id))
    }

    @Test
    fun referenceListDefaultsToThreeAndExpandsAll() {
        assertEquals(3, visibleReferenceCount(totalCount = 10, expanded = false))
        assertEquals(10, visibleReferenceCount(totalCount = 10, expanded = true))
        assertEquals(2, visibleReferenceCount(totalCount = 2, expanded = false))
    }

    @Test
    fun referenceSectionDefaultsCollapsedUntilOpened() {
        assertEquals(0, visibleReferenceCount(totalCount = 10, sectionExpanded = false, listExpanded = false))
        assertEquals(3, visibleReferenceCount(totalCount = 10, sectionExpanded = true, listExpanded = false))
        assertEquals(10, visibleReferenceCount(totalCount = 10, sectionExpanded = true, listExpanded = true))
        assertEquals("点击展开", referenceSectionActionText(expanded = false))
        assertEquals("收起引用", referenceSectionActionText(expanded = true))
    }

    @Test
    fun referenceExpansionTextShowsRemainingCount() {
        assertEquals("展开剩余 7 条", referenceExpansionText(totalCount = 10, expanded = false))
        assertEquals("收起引用", referenceExpansionText(totalCount = 10, expanded = true))
        assertEquals(null, referenceExpansionText(totalCount = 3, expanded = false))
    }

    @Test
    fun referenceSectionUsesFullWidthReadableCards() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt").readText()

        assertFalse(source.contains("fillMaxWidth(0.86f)"))
        assertFalse(source.contains(".padding(start = Spacing.s)"))
        assertTrue(source.contains("referenceSummaryMaxLines()"))
        assertEquals(4, referenceSummaryMaxLines())
    }

    @Test
    fun diaryReferenceParsesImagesAndTags() {
        assertEquals(listOf("diary_images/a.jpg", "diary_images/b.jpg"), diaryReferenceImagePaths("diary_images/a.jpg, null, diary_images/b.jpg"))
        assertEquals(listOf("生活", "思考"), diaryReferenceTags("生活, null, 思考"))
    }
}
