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
    fun topBarDoesNotExposeRefreshAction() {
        assertFalse(aiChatShowsRefreshAction())
        assertTrue(aiChatShowsMemorySearchAction())
    }

    @Test
    fun processingStateUsesBubbleLoadingOnly() {
        assertFalse(aiChatShowsTopProgressIndicator(isProcessing = true, currentStep = "正在查询数据..."))
        assertTrue(aiChatShowsThinkingBubble(isProcessing = true))
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
