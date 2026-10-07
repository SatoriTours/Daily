package com.dailysatori.service.ideatopic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IdeaTopicDraftTest {

    @Test
    fun proposalNeverChangesOfficialContent() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("草稿主题", description = "原始描述")
            val sourceId = fixture.service.getDetailSync(topicId)!!.sources.single().id
            fixture.aiPort.proposeResult = IdeaDraftContent(
                content = IdeaTopicContent(title = "AI 建议标题", description = "AI 建议描述", nextAction = "AI 建议下一步"),
                referenceIds = listOf(sourceId),
            )

            val draft = fixture.workflow.propose(topicId)

            assertEquals(IdeaDraftState.Pending, draft.state)
            assertEquals("AI 建议标题", draft.proposal.content.title)
            assertEquals("原始描述", fixture.service.getDetailSync(topicId)!!.topic.content.description)
            assertEquals(listOf(draft.id), fixture.service.draftsSync(topicId).map { it.id })
            assertTrue(draft.baseRevision == fixture.service.getDetailSync(topicId)!!.contextRevision)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun applyingEditedDraftWritesContentAndRevisionSnapshot() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("草稿主题", description = "原始描述")
            val draft = fixture.workflow.propose(topicId)

            fixture.service.applyDraft(
                draft.id,
                IdeaTopicContent(title = "人工确认后的标题", description = "人工确认后的描述"),
            )

            val detail = fixture.service.getDetailSync(topicId)!!
            assertEquals("人工确认后的标题", detail.topic.content.title)
            assertEquals("人工确认后的描述", detail.topic.content.description)
            assertEquals(IdeaTopicStatus.PendingResearch, detail.topic.status, "AI must not change the business status")
            assertEquals(1, detail.events.count { it.kind == IdeaEventKinds.ContentUpdated })
            val applied = detail.events.single { it.kind == IdeaEventKinds.DraftApplied }
            val payload = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString(kotlinx.serialization.json.JsonObject.serializer(), applied.payload)
            assertTrue(payload.keys.contains("before") && payload.keys.contains("after"))
            assertEquals(IdeaDraftState.Applied, fixture.service.draftsSync(topicId).single().state)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun discardingDraftKeepsContentAndIsIdempotent() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("丢弃草稿主题", description = "原始描述")
            val draft = fixture.workflow.propose(topicId)

            fixture.service.discardDraft(draft.id)
            fixture.service.discardDraft(draft.id)

            val detail = fixture.service.getDetailSync(topicId)!!
            assertEquals("原始描述", detail.topic.content.description)
            assertEquals(1, detail.events.count { it.kind == IdeaEventKinds.DraftDiscarded })
            assertEquals(IdeaDraftState.Discarded, fixture.service.draftsSync(topicId).single().state)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun unknownReferenceIsRejectedAndNoDraftPersisted() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("引用主题")
            fixture.aiPort.proposeResult = IdeaDraftContent(
                content = IdeaTopicContent(title = "引用不存在的来源"),
                referenceIds = listOf("unknown-source-id"),
            )

            assertEquals(IdeaTopicError.InvalidAiResponse, assertFailsWith<IdeaTopicException> {
                fixture.workflow.propose(topicId)
            }.code)

            assertEquals(emptyList(), fixture.service.draftsSync(topicId))
            assertTrue(!fixture.workflow.isBusy(topicId))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun invalidJsonIsRejected() {
        val allowed = setOf("source-1")
        assertEquals(IdeaTopicError.InvalidAiResponse, assertFailsWith<IdeaTopicException> {
            parseIdeaDraftResponse("这不是 JSON", allowed)
        }.code)
        assertEquals(IdeaTopicError.InvalidAiResponse, assertFailsWith<IdeaTopicException> {
            parseIdeaDraftResponse("{\"description\":\"缺少标题\"}", allowed)
        }.code)
        assertEquals(IdeaTopicError.InvalidAiResponse, assertFailsWith<IdeaTopicException> {
            parseIdeaDraftResponse("{\"title\":\"标题\",\"status\":\"completed\"}", allowed)
        }.code, "executable fields such as status must be rejected")

        val parsed = parseIdeaDraftResponse(
            "```json\n{\"title\":\" 标题 \",\"description\":\"描述\",\"referenceIds\":[\"source-1\"]}\n```",
            allowed,
        )
        assertEquals("标题", parsed.content.title)
        assertEquals(listOf("source-1"), parsed.referenceIds)
    }

    @Test
    fun manualEditMakesDraftStaleAndUnconfirmable() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("过期草稿主题", description = "原始描述")
            val draft = fixture.workflow.propose(topicId)

            fixture.service.updateContent(topicId, IdeaTopicContent(title = "人工修改", description = "人工修改描述"))

            assertEquals(IdeaDraftState.Stale, fixture.service.draftsSync(topicId).single().state)
            assertEquals(IdeaTopicError.StaleDraft, assertFailsWith<IdeaTopicException> {
                fixture.service.applyDraft(draft.id, IdeaTopicContent(title = "过期草稿"))
            }.code)
            assertEquals("人工修改", fixture.service.getDetailSync(topicId)!!.topic.content.title)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun newMessagesAndSummariesMakeDraftStale() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("消息过期主题")
            val sessionId = fixture.service.createSession(topicId, "会话")
            val draft = fixture.workflow.propose(topicId)

            fixture.workflow.send(sessionId, "新消息")

            assertEquals(IdeaDraftState.Stale, fixture.service.draftsSync(topicId).single().state)
            assertEquals(IdeaTopicError.StaleDraft, assertFailsWith<IdeaTopicException> {
                fixture.service.applyDraft(draft.id, IdeaTopicContent(title = "过期草稿"))
            }.code)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun doubleConfirmationWritesOnlyOnce() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("重复确认主题")
            val draft = fixture.workflow.propose(topicId)

            fixture.service.applyDraft(draft.id, IdeaTopicContent(title = "第一次确认"))
            fixture.service.applyDraft(draft.id, IdeaTopicContent(title = "第二次确认"))

            val detail = fixture.service.getDetailSync(topicId)!!
            assertEquals("第一次确认", detail.topic.content.title)
            assertEquals(1, detail.events.count { it.kind == IdeaEventKinds.DraftApplied })
            assertEquals(1, detail.events.count { it.kind == IdeaEventKinds.ContentUpdated })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cancelledProposalWritesNothingAndReleasesTheRequest() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("取消草稿主题", description = "原始描述")
            val gate = CompletableDeferred<Unit>()
            fixture.aiPort.proposeGate = gate

            val job = launch { fixture.workflow.propose(topicId) }
            withTimeout(5_000) {
                while (!fixture.workflow.isBusy(topicId)) yield()
            }
            fixture.workflow.cancel(topicId)
            job.join()
            fixture.aiPort.proposeGate = null

            assertEquals(emptyList(), fixture.service.draftsSync(topicId))
            assertEquals("原始描述", fixture.service.getDetailSync(topicId)!!.topic.content.description)
            assertTrue(!fixture.workflow.isBusy(topicId))

            fixture.aiPort.proposeResult = IdeaDraftContent(IdeaTopicContent(title = "重试草稿"))
            val retried = fixture.workflow.propose(topicId)
            assertEquals("重试草稿", retried.proposal.content.title)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun mergedOrDeletedTopicCannotBeResurrectedByAnOldDraft() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val fromTopic = fixture.captureTopic("被合并草稿主题", description = "被合并描述")
            val intoTopic = fixture.captureTopic("主主题")
            val draft = fixture.workflow.propose(fromTopic)

            fixture.service.merge(fromTopic, intoTopic)

            assertEquals(IdeaTopicError.StaleDraft, assertFailsWith<IdeaTopicException> {
                fixture.service.applyDraft(draft.id, IdeaTopicContent(title = "复活草稿"))
            }.code)
            assertEquals("主主题", fixture.service.getDetailSync(intoTopic)!!.topic.content.title)

            fixture.service.delete(intoTopic)
            assertEquals(IdeaTopicError.NotFound, assertFailsWith<IdeaTopicException> {
                fixture.service.applyDraft(draft.id, IdeaTopicContent(title = "已删除后复活"))
            }.code)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun proposalIsRejectedWhileAnotherRequestRuns() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("忙碌草稿主题")
            val sessionId = fixture.service.createSession(topicId, "会话")
            val gate = CompletableDeferred<Unit>()
            fixture.aiPort.replyGate = gate

            val job = launch { fixture.workflow.send(sessionId, "进行中的请求") }
            withTimeout(5_000) {
                while (!fixture.workflow.isBusy(topicId)) yield()
            }

            assertEquals(IdeaTopicError.Busy, assertFailsWith<IdeaTopicException> {
                fixture.workflow.propose(topicId)
            }.code)

            fixture.workflow.cancel(topicId)
            job.join()
            fixture.aiPort.replyGate = null
        } finally {
            fixture.close()
        }
    }

    private suspend fun IdeaTopicTestFixture.captureTopic(
        title: String,
        description: String = "",
    ): String {
        val diaryId = createDiary("来源-$title")
        return service.capture(
            IdeaCaptureInput(
                source = diarySnapshot(diaryId, content = "来源-$title"),
                content = IdeaTopicContent(title = title, description = description),
            ),
        ).topicId
    }
}
