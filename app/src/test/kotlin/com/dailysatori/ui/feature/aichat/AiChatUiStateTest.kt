package com.dailysatori.ui.feature.aichat

import com.dailysatori.ui.feature.home.AI_CHAT_TAB_INDEX
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
        assertTrue(source.contains("maxLines = 3"))
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
        assertTrue(assistantMessageUsesEditorialRail())
        assertTrue(userMessageUsesMutedContainer())
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
        assertEquals("继续追问今天的新闻、日记或文章...", chatInputPlaceholderText())
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
        assertTrue(aiChatShowsThinkingBubble(isProcessing = true))
    }

    @Test
    fun chatHistoryDisplaysNewestMessageFirstForReverseLayout() {
        val oldest = ChatMessageUi("old", "user", "最旧", 1L)
        val newest = ChatMessageUi("new", "assistant", "最新", 2L)

        assertEquals(listOf(newest, oldest), aiChatDisplayMessages(listOf(oldest, newest)))
    }

    @Test
    fun activeChatChangesStillAutoScroll() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt").readText()

        assertTrue(source.contains("reverseLayout = true"))
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
                lastVisibleItemIndex = 0,
                totalItemsCount = 12,
                isScrollInProgress = false,
                canLoadOlder = true,
                isLoadingOlder = false,
                messageCount = 12,
            ),
        )
        assertTrue(
            aiChatShouldLoadOlder(
                lastVisibleItemIndex = 11,
                totalItemsCount = 12,
                isScrollInProgress = true,
                canLoadOlder = true,
                isLoadingOlder = false,
                messageCount = 12,
            ),
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
    fun firstCompositionNeverAutoScrolls() {
        val source = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt").readText()

        assertFalse(source.contains("scrollStateInitialized"))
        assertFalse(source.contains("previousMessageCount"))
    }

    @Test
    fun homeBottomBarRemainsVisibleOnAiTab() {
        assertTrue(chatInputUsesImePadding())
        assertTrue(homeBottomBarVisibleForTab(AI_CHAT_TAB_INDEX))
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
    fun diaryReferenceParsesImagesAndTags() {
        assertEquals(listOf("diary_images/a.jpg", "diary_images/b.jpg"), diaryReferenceImagePaths("diary_images/a.jpg, null, diary_images/b.jpg"))
        assertEquals(listOf("生活", "思考"), diaryReferenceTags("生活, null, 思考"))
    }
}
