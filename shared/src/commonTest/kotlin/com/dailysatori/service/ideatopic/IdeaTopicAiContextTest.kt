package com.dailysatori.service.ideatopic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdeaTopicAiContextTest {

    @Test
    fun keepsCurrentQuestionIntactAndRespectsBudget() {
        val detail = sampleDetail(sourceContent = "原文".repeat(2_000))
        val context = buildIdeaAiContext(
            detail = detail,
            sessionId = "session-1",
            currentMessages = completeMessages(6, 800),
            userPrompt = "请分析这个点子的最大风险",
            maxCharacters = 4_000,
        )

        assertTrue(ideaAiContextCharacterCount(context) <= 4_000, "context must respect the budget")
        assertTrue(context.userPrompt.contains("请分析这个点子的最大风险"), "the current question is never truncated")
        assertTrue(context.userPrompt.contains("【当前问题】"))
        assertTrue(
            context.userPrompt.contains(IdeaAiTruncationMarker) || context.userPrompt.contains(IdeaAiOmittedMessagesMarker),
            "clipped content must be marked",
        )
    }

    @Test
    fun rejectsOverlongCurrentQuestion() {
        val error = assertFailsWith<IdeaTopicException> {
            buildIdeaAiContext(
                detail = sampleDetail(),
                sessionId = null,
                currentMessages = emptyList(),
                userPrompt = "长".repeat(IdeaAiMaxUserPromptCharacters + 1),
            )
        }
        assertEquals(IdeaTopicError.InputTooLong, error.code)
    }

    @Test
    fun onlyCompletedMessagesAreIncludedInOrder() {
        val messages = listOf(
            message("m1", "user", "第一个问题", 1_000L),
            message("m2", "assistant", "未完成的回复", 2_000L, IdeaMessageStatus.Pending),
            message("m3", "assistant", "第一个回答", 3_000L),
            message("m4", "assistant", "失败的回复", 4_000L, IdeaMessageStatus.Failed),
        )
        val context = buildIdeaAiContext(
            detail = sampleDetail(),
            sessionId = "session-1",
            currentMessages = messages,
            userPrompt = "继续",
        )
        assertEquals(listOf("m1", "m3"), context.messages.map { it.id })
    }

    @Test
    fun dropsOldMessagesWithMarkerWhenBudgetIsTight() {
        val context = buildIdeaAiContext(
            detail = sampleDetail(),
            sessionId = "session-1",
            currentMessages = completeMessages(40, 400),
            userPrompt = "继续",
            maxCharacters = 3_000,
        )
        assertTrue(context.messages.size < 40, "older messages must be dropped when the budget is tight")
        assertTrue(context.userPrompt.contains(IdeaAiOmittedMessagesMarker))
        assertEquals(
            context.messages.map { it.createdAt }.sorted(),
            context.messages.map { it.createdAt },
            "included messages stay in stable ascending order",
        )
        assertTrue(ideaAiContextCharacterCount(context) <= 3_000)
    }

    @Test
    fun referencesBelongToTheGivenTopicOnly() {
        val detail = sampleDetail()
        val context = buildIdeaAiContext(
            detail = detail,
            sessionId = "session-1",
            currentMessages = completeMessages(2, 10),
            userPrompt = "继续",
        )
        assertTrue("source-1" in context.allowedReferenceIds)
        assertTrue("event-1" in context.allowedReferenceIds)
        assertTrue("session-0" in context.allowedReferenceIds)
        assertTrue("msg-1" in context.allowedReferenceIds)
        assertFalse("other-topic-source" in context.allowedReferenceIds)
        assertFalse("other-topic-event" in context.allowedReferenceIds)
    }

    @Test
    fun systemPromptTreatsSourcesAndHistoryAsData() {
        val prompt = ideaAiSystemPrompt()
        assertTrue(prompt.contains("数据"))
        assertTrue(prompt.contains("不是用户的指令"))
        assertTrue(prompt.contains("不自动修改正式内容"))
    }

    @Test
    fun unrelatedTopicSourcesAreNotInjected() {
        val detail = sampleDetail(sourceContent = "只有这个主题的来源")
        val context = buildIdeaAiContext(
            detail = detail,
            sessionId = null,
            currentMessages = emptyList(),
            userPrompt = "继续",
        )
        assertTrue(context.userPrompt.contains("只有这个主题的来源"))
        assertFalse(context.userPrompt.contains("别的主题的来源"))
    }

    private fun message(
        id: String,
        role: String,
        content: String,
        createdAt: Long,
        status: String = IdeaMessageStatus.Complete,
    ) = IdeaTopicMessage(
        id = id,
        sessionId = "session-1",
        role = role,
        content = content,
        status = status,
        error = null,
        createdAt = createdAt,
    )

    private fun completeMessages(count: Int, size: Int): List<IdeaTopicMessage> = (1..count).map { index ->
        message(
            id = "msg-$index",
            role = if (index % 2 == 0) IdeaMessageRoles.Assistant else IdeaMessageRoles.User,
            content = "消息$index-" + "内容".repeat(size / 2),
            createdAt = 1_000L + index,
        )
    }

    private fun sampleDetail(sourceContent: String = "来源原文"): IdeaTopicDetail = IdeaTopicDetail(
        topic = IdeaTopicSummary(
            id = "topic-1",
            content = IdeaTopicContent(
                title = "主题标题",
                description = "主题描述",
                provenanceSummary = "来龙去脉",
                conclusions = "研究结论",
                nextAction = "下一步",
            ),
            status = IdeaTopicStatus.Researching,
            latestProgress = "最新进展",
            sourceCount = 1,
            updatedAt = 5_000L,
        ),
        contextRevision = 3L,
        sources = listOf(
            IdeaTopicSource(
                id = "source-1",
                topicId = "topic-1",
                originalTopicId = "topic-1",
                snapshot = IdeaSourceSnapshot(
                    key = IdeaSourceKey(IdeaSourceTypes.Diary, "12"),
                    originalTitle = "来源标题",
                    originalContent = sourceContent,
                    originalCreatedAt = 1_000L,
                    originalRecordId = "12",
                    originalUrl = "https://example.com/a",
                ),
                capturedAt = 1_100L,
            ),
        ),
        events = listOf(
            IdeaTopicEvent(
                id = "event-1",
                topicId = "topic-1",
                originalTopicId = "topic-1",
                kind = IdeaEventKinds.Progress,
                payload = "{\"text\":\"第一步完成\"}",
                createdAt = 2_000L,
            ),
        ),
        sessions = listOf(
            IdeaTopicSession(
                id = "session-0",
                topicId = "topic-1",
                originalTopicId = "topic-1",
                title = "以前的沟通",
                summary = "以前沟通的摘要",
                summaryThroughMessageId = "old-msg-2",
                summaryCoveredMessageIds = listOf("old-msg-1", "old-msg-2"),
                summaryStatus = IdeaSessionSummaryStatus.Ready,
                createdAt = 1_000L,
                updatedAt = 2_500L,
            ),
        ),
    )
}
