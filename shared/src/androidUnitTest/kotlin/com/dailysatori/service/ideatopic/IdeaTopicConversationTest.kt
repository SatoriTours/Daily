package com.dailysatori.service.ideatopic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdeaTopicConversationTest {

    @Test
    fun cancellingSummaryStopsItsJobAndClearsPendingStatus() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        val gate = CompletableDeferred<Unit>()
        try {
            val topicId = fixture.captureTopic("摘要取消主题")
            val sessionId = fixture.service.createSession(topicId, "沟通")
            fixture.workflow.send(sessionId, "讨论")
            fixture.aiPort.summarizeGate = gate
            val job = launch { fixture.workflow.summarize(sessionId) }
            withTimeout(5_000) { while (fixture.aiPort.lastSummaryContext == null) yield() }
            fixture.workflow.cancel(topicId)
            val stoppedBeforeReturn = job.isCompleted
            gate.complete(Unit)
            job.join()
            assertTrue(stoppedBeforeReturn, "stop must wait for the summary request to finish")
            assertTrue(fixture.service.sessionOrThrow(sessionId).summaryStatus != IdeaSessionSummaryStatus.Pending)
            assertTrue(!fixture.service.isRequestActive(topicId))
        } finally {
            gate.complete(Unit)
            fixture.close()
        }
    }

    @Test
    fun constructingTheWriteOwnerRecoversPersistedPendingWork() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("恢复主题")
            val sessionId = fixture.service.createSession(topicId, "沟通")
            fixture.repository.insertMessage("unfinished", sessionId, IdeaMessageRoles.Assistant, "保留半截回复", IdeaMessageStatus.Pending, null, 1_000L)
            fixture.repository.updateSessionSummaryStatus(sessionId, IdeaSessionSummaryStatus.Pending, 1_000L)
            val restarted = IdeaTopicService(fixture.repository)
            assertEquals(IdeaMessageStatus.Interrupted, restarted.messagesSync(sessionId).single().status)
            assertEquals("保留半截回复", restarted.messagesSync(sessionId).single().content)
            assertTrue(restarted.sessionOrThrow(sessionId).summaryStatus != IdeaSessionSummaryStatus.Pending)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun sessionsAndMessagesStayIsolatedAndComplete() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("会话主题")
            val first = fixture.service.createSession(topicId, "第一次沟通")
            val second = fixture.service.createSession(topicId, "第二次沟通")

            fixture.workflow.send(first, "第一个问题")

            val firstMessages = fixture.service.messagesSync(first)
            assertEquals(setOf(IdeaMessageRoles.User, IdeaMessageRoles.Assistant), firstMessages.map { it.role }.toSet())
            assertEquals(listOf(IdeaMessageStatus.Complete, IdeaMessageStatus.Complete), firstMessages.map { it.status })
            assertEquals("第一个问题", firstMessages.first { it.role == IdeaMessageRoles.User }.content)
            assertEquals("AI 回复", firstMessages.first { it.role == IdeaMessageRoles.Assistant }.content)
            assertEquals(emptyList(), fixture.service.messagesSync(second))

            val sessions = fixture.service.sessionsSync(topicId)
            assertEquals(setOf("第一次沟通", "第二次沟通"), sessions.map { it.title }.toSet())
            assertEquals(setOf(first, second), sessions.map { it.id }.toSet())

            // The topic conversation never leaks into the global chat store.
            assertEquals(0, fixture.db.dailySatoriQueries.selectChatSessions().executeAsList().size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun equalTimestampsPaginateWithoutLossOrDuplication() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("分页主题")
            val sessionId = fixture.service.createSession(topicId, "分页会话")
            repeat(45) { index ->
                fixture.db.dailySatoriQueries.insertIdeaTopicMessage(
                    id = "msg-${index.toString().padStart(2, '0')}",
                    session_id = sessionId,
                    role = if (index % 2 == 0) IdeaMessageRoles.User else IdeaMessageRoles.Assistant,
                    content = "同时间消息 $index",
                    status = IdeaMessageStatus.Complete,
                    error = null,
                    created_at = 7_000L,
                )
            }

            val all = fixture.service.messagesSync(sessionId)
            assertEquals(45, all.size)
            val page1 = fixture.service.getMessagesBeforeSync(sessionId, Long.MAX_VALUE, "\uFFFF", 30)
            assertEquals(30, page1.size)
            val page2 = fixture.service.getMessagesBeforeSync(
                sessionId,
                page1.first().createdAt,
                page1.first().id,
                30,
            )
            assertEquals(15, page2.size)
            val merged = (page2 + page1).map { it.id }
            assertEquals(all.map { it.id }.toSet(), merged.toSet())
            assertEquals(merged.size, merged.toSet().size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun summaryCoversActualMessagesAndBecomesOutdatedAfterMoreTalk() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("摘要主题")
            val sessionId = fixture.service.createSession(topicId, "摘要会话")
            fixture.workflow.send(sessionId, "第一个问题")
            fixture.workflow.send(sessionId, "第二个问题")

            fixture.aiPort.summarizeResult = "两条消息的摘要"
            fixture.workflow.summarize(sessionId)

            val session = fixture.service.sessionOrThrow(sessionId)
            assertEquals("两条消息的摘要", session.summary)
            assertEquals(IdeaSessionSummaryStatus.Ready, session.summaryStatus)
            val completedIds = fixture.service.messagesSync(sessionId).map { it.id }
            assertEquals(completedIds, session.summaryCoveredMessageIds)
            assertEquals(completedIds.last(), session.summaryThroughMessageId)
            assertEquals(
                fixture.service.messagesSync(sessionId).map { it.id },
                fixture.aiPort.lastSummaryContext!!.messages.map { it.id },
            )

            fixture.workflow.send(sessionId, "继续追问")
            assertEquals(
                IdeaSessionSummaryStatus.NeedsUpdate,
                fixture.service.sessionOrThrow(sessionId).summaryStatus,
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun summaryRecordsOnlyTheMessagesActuallyIncludedInContext() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("部分覆盖主题")
            val sessionId = fixture.service.createSession(topicId, "长会话")
            repeat(60) { index ->
                fixture.db.dailySatoriQueries.insertIdeaTopicMessage(
                    id = "long-${index.toString().padStart(2, '0')}",
                    session_id = sessionId,
                    role = IdeaMessageRoles.User,
                    content = "消息$index-" + "内容".repeat(300),
                    status = IdeaMessageStatus.Complete,
                    error = null,
                    created_at = 8_000L + index,
                )
            }

            fixture.workflow.summarize(sessionId)

            val session = fixture.service.sessionOrThrow(sessionId)
            val total = fixture.service.messagesSync(sessionId).size
            assertTrue(session.summaryCoveredMessageIds.size < total, "clipped history must not claim full coverage")
            assertEquals(session.summaryCoveredMessageIds.last(), session.summaryThroughMessageId)
            assertEquals(
                fixture.aiPort.lastSummaryContext!!.messages.map { it.id },
                session.summaryCoveredMessageIds,
            )
            assertTrue(
                fixture.aiPort.lastSummaryContext!!.userPrompt.contains(IdeaAiOmittedMessagesMarker),
                "partial coverage must be marked in the prompt",
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun busyTopicRefusesSecondRequestAndBlockMergeAndDeleteUntilCancelled() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val mainTopic = fixture.captureTopic("主主题")
            val otherTopic = fixture.captureTopic("另一主题")
            val sessionId = fixture.service.createSession(mainTopic, "进行中会话")
            val gate = CompletableDeferred<Unit>()
            fixture.aiPort.replyGate = gate

            val job = launch { fixture.workflow.send(sessionId, "开始一个长请求") }
            awaitPendingMessage(fixture, sessionId)

            assertEquals(IdeaTopicError.Busy, assertFailsWith<IdeaTopicException> {
                fixture.workflow.send(sessionId, "第二个请求")
            }.code)
            assertEquals(IdeaTopicError.Busy, assertFailsWith<IdeaTopicException> {
                fixture.service.merge(otherTopic, mainTopic)
            }.code)
            assertEquals(IdeaTopicError.Busy, assertFailsWith<IdeaTopicException> {
                fixture.service.delete(mainTopic)
            }.code)

            fixture.workflow.cancel(mainTopic)
            job.join()
            fixture.aiPort.replyGate = null

            val messages = fixture.service.messagesSync(sessionId)
            assertEquals(1, messages.count { it.status == IdeaMessageStatus.Complete && it.role == IdeaMessageRoles.User })
            assertEquals(1, messages.count { it.status == IdeaMessageStatus.Interrupted })
            assertEquals(mainTopic, fixture.service.merge(otherTopic, mainTopic))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cancelKeepsUserMessageAndAllowsRetry() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("取消主题")
            val sessionId = fixture.service.createSession(topicId, "取消会话")
            val gate = CompletableDeferred<Unit>()
            fixture.aiPort.replyGate = gate

            val job = launch { fixture.workflow.send(sessionId, "会取消的问题") }
            awaitPendingMessage(fixture, sessionId)
            fixture.workflow.cancel(topicId)
            job.join()

            val afterCancel = fixture.service.messagesSync(sessionId)
            assertEquals("会取消的问题", afterCancel.first { it.role == IdeaMessageRoles.User }.content)
            assertEquals(IdeaMessageStatus.Complete, afterCancel.first { it.role == IdeaMessageRoles.User }.status)
            assertEquals(IdeaMessageStatus.Interrupted, afterCancel.first { it.role == IdeaMessageRoles.Assistant }.status)

            fixture.tick()
            fixture.aiPort.replyGate = null
            fixture.workflow.send(sessionId, "重试的问题")
            val afterRetry = fixture.service.messagesSync(sessionId)
            assertEquals(4, afterRetry.size)
            val retriedReply = afterRetry.first { it.role == IdeaMessageRoles.Assistant && it.status == IdeaMessageStatus.Complete }
            assertEquals("AI 回复", retriedReply.content)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun restartRecoveryInterruptsLeftoverPendingReplies() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("恢复主题")
            val sessionId = fixture.service.createSession(topicId, "恢复会话")
            fixture.db.dailySatoriQueries.insertIdeaTopicMessage(
                id = "leftover",
                session_id = sessionId,
                role = IdeaMessageRoles.Assistant,
                content = "上一进程的半截回复",
                status = IdeaMessageStatus.Pending,
                error = null,
                created_at = 3_000L,
            )

            fixture.workflow.recoverInterruptedRequests()

            val message = fixture.service.messagesSync(sessionId).single()
            assertEquals(IdeaMessageStatus.Interrupted, message.status)
            assertEquals("上一进程的半截回复", message.content)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun failedRequestsKeepUserMessageAndNeverStoreErrorTextAsAnswer() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("失败主题")
            val sessionId = fixture.service.createSession(topicId, "失败会话")
            fixture.aiPort.failure = IdeaTopicException(IdeaTopicError.AiNotConfigured)

            assertFailsWith<IdeaTopicException> { fixture.workflow.send(sessionId, "未配置时的问题") }

            val messages = fixture.service.messagesSync(sessionId)
            assertEquals(2, messages.size)
            assertEquals("未配置时的问题", messages.first { it.role == IdeaMessageRoles.User }.content)
            assertEquals(IdeaMessageStatus.Complete, messages.first { it.role == IdeaMessageRoles.User }.status)
            val failedReply = messages.first { it.role == IdeaMessageRoles.Assistant }
            assertEquals(IdeaMessageStatus.Failed, failedReply.status)
            assertEquals(IdeaTopicError.AiNotConfigured.name, failedReply.error)
            assertEquals("（部分）", failedReply.content)
            assertTrue(!failedReply.content.contains("NotConfigured"))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun mergedSessionKeepsOriginalOwnerButUsesMainTopicContext() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val fromTopic = fixture.captureTopic("被合并主题")
            val intoTopic = fixture.captureTopic("主主题")
            val sessionId = fixture.service.createSession(fromTopic, "旧会话")
            fixture.workflow.send(sessionId, "合并前的问题")

            fixture.service.merge(fromTopic, intoTopic)

            val session = fixture.service.sessionOrThrow(sessionId)
            assertEquals(intoTopic, session.topicId)
            assertEquals(fromTopic, session.originalTopicId)

            fixture.workflow.send(sessionId, "合并后继续")
            val context = assertNotNull(fixture.aiPort.lastReplyContext)
            assertEquals(intoTopic, context.topicId)
            assertEquals(sessionId, context.sessionId)
            val mergedSourceId = fixture.service.getDetailSync(intoTopic)!!.sources.first().id
            assertTrue(context.allowedReferenceIds.contains(mergedSourceId))
            assertTrue(context.userPrompt.contains("被合并主题"))
        } finally {
            fixture.close()
        }
    }

    private suspend fun awaitPendingMessage(fixture: IdeaTopicTestFixture, sessionId: String) {
        withTimeout(5_000) {
            while (fixture.service.messagesSync(sessionId).none { it.status == IdeaMessageStatus.Pending }) {
                yield()
            }
        }
    }

    private suspend fun IdeaTopicTestFixture.captureTopic(title: String): String {
        val diaryId = createDiary("来源-$title")
        return service.capture(
            IdeaCaptureInput(
                source = diarySnapshot(diaryId, content = "来源-$title"),
                content = IdeaTopicContent(title = title),
            ),
        ).topicId
    }
}
