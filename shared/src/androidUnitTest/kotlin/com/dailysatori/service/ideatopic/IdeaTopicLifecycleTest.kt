package com.dailysatori.service.ideatopic

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdeaTopicLifecycleTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun updateContentWritesBeforeAndAfterSnapshot() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("初始标题", description = "初始描述")
            fixture.service.updateContent(
                topicId,
                IdeaTopicContent(title = "修订标题", description = "修订描述", nextAction = "下一步"),
            )

            val detail = assertNotNull(fixture.service.getDetailSync(topicId))
            assertEquals("修订标题", detail.topic.content.title)
            assertEquals("下一步", detail.topic.content.nextAction)
            val revision = detail.events.last { it.kind == IdeaEventKinds.ContentUpdated }
            val payload = json.decodeFromString(IdeaContentUpdatedEventPayload.serializer(), revision.payload)
            assertEquals("初始标题", payload.before.title)
            assertEquals("修订标题", payload.after.title)
            assertEquals(1L, detail.contextRevision)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun setStatusIgnoresRepeatAndAllowsReopening() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("状态主题")
            fixture.service.setStatus(topicId, IdeaTopicStatus.Completed)
            fixture.service.setStatus(topicId, IdeaTopicStatus.Completed)

            var detail = assertNotNull(fixture.service.getDetailSync(topicId))
            assertEquals(IdeaTopicStatus.Completed, detail.topic.status)
            assertEquals(1, detail.events.count { it.kind == IdeaEventKinds.StatusChanged })

            fixture.service.setStatus(topicId, IdeaTopicStatus.Advancing)
            detail = assertNotNull(fixture.service.getDetailSync(topicId))
            assertEquals(IdeaTopicStatus.Advancing, detail.topic.status)
            assertEquals(2, detail.events.count { it.kind == IdeaEventKinds.StatusChanged })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun progressIsAppendOnlyAndLatestWins() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("进展主题")
            fixture.clockMs = 2_000L
            fixture.service.appendProgress(topicId, "第一步完成")
            fixture.clockMs = 3_000L
            fixture.service.appendProgress(topicId, "第二步完成")

            val detail = assertNotNull(fixture.service.getDetailSync(topicId))
            assertEquals(listOf("第一步完成", "第二步完成"), detail.events.filter { it.kind == IdeaEventKinds.Progress }.map {
                json.decodeFromString(IdeaProgressEventPayload.serializer(), it.payload).text
            })
            assertEquals("第二步完成", detail.topic.latestProgress)
            assertEquals("第二步完成", fixture.service.observeSummaries().first().single().latestProgress)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun mergeKeepsTargetContentAndMovesOwnership() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val fromTopic = fixture.captureTopic("被合并主题")
            val intoTopic = fixture.captureTopic("主主题", description = "主主题描述")
            fixture.repository.createSession("session-from", fromTopic, fromTopic, "旧会话", fixture.clockMs)

            val finalTarget = fixture.service.merge(fromTopic, intoTopic)

            assertEquals(intoTopic, finalTarget)
            assertEquals(intoTopic, fixture.service.resolveTopicId(fromTopic))
            val detail = assertNotNull(fixture.service.getDetailSync(fromTopic))
            assertEquals(intoTopic, detail.topic.id)
            assertEquals("主主题", detail.topic.content.title)
            assertEquals("主主题描述", detail.topic.content.description)
            assertEquals(2, detail.sources.size)
            val session = detail.sessions.single()
            assertEquals(intoTopic, session.topicId)
            assertEquals(fromTopic, session.originalTopicId)
            assertEquals(listOf(intoTopic), fixture.service.observeSummaries().first().map { it.id })

            val merged = detail.events.last { it.kind == IdeaEventKinds.Merged }
            val payload = json.decodeFromString(IdeaMergedEventPayload.serializer(), merged.payload)
            assertEquals(fromTopic, payload.fromTopicId)
            assertEquals("被合并主题", payload.fromTitle)
            assertEquals("主主题", payload.intoTitle)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun chainedMergeResolvesToFinalTargetAndKeepsOriginalOwnership() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val first = fixture.captureTopic("A 主题")
            val second = fixture.captureTopic("B 主题")
            val third = fixture.captureTopic("C 主题")

            fixture.service.merge(first, second)
            fixture.service.merge(second, third)

            assertEquals(third, fixture.service.resolveTopicId(first))
            assertEquals(third, fixture.service.resolveTopicId(second))
            assertEquals(third, fixture.service.resolveTopicId(third))
            assertEquals(listOf(third), fixture.service.observeSummaries().first().map { it.id })

            val detail = assertNotNull(fixture.service.getDetailSync(first))
            assertEquals(third, detail.topic.id)
            assertEquals(3, detail.sources.size)
            assertEquals(setOf(first, second, third), detail.sources.map { it.originalTopicId }.toSet())
            assertEquals(2, detail.events.count { it.kind == IdeaEventKinds.Merged })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun mergingDuplicateSourceKeepsSnapshotHistoryInMergeEvent() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val diaryId = fixture.createDiary("共享来源")
            val fromTopic = fixture.captureTopic("被合并主题")
            val intoTopic = fixture.captureTopic("主主题")
            val snapshot = diarySnapshot(diaryId, title = "共享来源", content = "共享来源")
            fixture.insertSourceDirect(fromTopic, "dup-1", snapshot)
            fixture.insertSourceDirect(intoTopic, "dup-2", snapshot.copy(originalContent = "后来更新的原文"))

            fixture.service.merge(fromTopic, intoTopic)

            val detail = assertNotNull(fixture.service.getDetailSync(intoTopic))
            assertEquals(1, detail.sources.count { it.snapshot.key == snapshot.key })
            val merged = detail.events.last { it.kind == IdeaEventKinds.Merged }
            val payload = json.decodeFromString(IdeaMergedEventPayload.serializer(), merged.payload)
            assertEquals(1, payload.duplicateSourceSnapshots.size)
            assertEquals("共享来源", payload.duplicateSourceSnapshots.single().originalContent)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun selfMergeAndCyclesRejectedAndMissingTopicNotFound() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val first = fixture.captureTopic("A 主题")
            val second = fixture.captureTopic("B 主题")
            val third = fixture.captureTopic("C 主题")

            assertEquals(IdeaTopicError.InvalidInput, assertFailsWith<IdeaTopicException> {
                fixture.service.merge(first, first)
            }.code)

            fixture.service.merge(first, second)
            assertEquals(IdeaTopicError.MergeCycle, assertFailsWith<IdeaTopicException> {
                fixture.service.merge(second, first)
            }.code)
            assertEquals(IdeaTopicError.AlreadyMerged, assertFailsWith<IdeaTopicException> {
                fixture.service.merge(first, third)
            }.code)
            assertEquals(IdeaTopicError.NotFound, assertFailsWith<IdeaTopicException> {
                fixture.service.merge(second, "missing-topic")
            }.code)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun failedMergeTransactionRollsBackEverything() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val fromTopic = fixture.captureTopic("被合并主题")
            val intoTopic = fixture.captureTopic("主主题")
            val failingEventId = fixture.peekNextId()
            fixture.db.dailySatoriQueries.insertIdeaTopicEvent(
                id = failingEventId,
                topic_id = intoTopic,
                original_topic_id = intoTopic,
                kind = IdeaEventKinds.Progress,
                payload_json = "{}",
                created_at = fixture.clockMs,
            )

            assertFailsWith<Exception> { fixture.service.merge(fromTopic, intoTopic) }

            val fromRow = assertNotNull(fixture.repository.getTopicRowSync(fromTopic))
            assertNull(fromRow.merged_into_topic_id, "failed merge must not leave a merged pointer")
            val fromDetail = assertNotNull(fixture.repository.getDetailSync(fromTopic))
            assertEquals(1, fromDetail.sources.size)
            assertEquals(0, fromDetail.events.count { it.kind == IdeaEventKinds.Merged })
            assertEquals(fromTopic, fixture.service.resolveTopicId(fromTopic))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun deleteRemovesWholeComponentAndKeepsOriginalEntries() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val diaryId = fixture.createDiary("原始日记内容")
            fixture.insertReminderDirect("reminder-1", "原始提醒")
            val first = fixture.captureTopic("A 主题", diaryId = diaryId)
            val second = fixture.captureTopic("B 主题")
            val third = fixture.captureTopic("C 主题")
            fixture.service.merge(first, second)
            fixture.service.merge(second, third)
            fixture.repository.createSession("session-1", third, third, "会话", fixture.clockMs)

            fixture.service.delete(third)

            assertNull(fixture.service.resolveTopicId(first))
            assertNull(fixture.service.resolveTopicId(second))
            assertNull(fixture.service.resolveTopicId(third))
            assertEquals(0, fixture.service.observeSummaries().first().size)
            assertEquals("原始日记内容", fixture.db.dailySatoriQueries.selectDiaryById(diaryId).executeAsOne().content)
            assertEquals("原始提醒", fixture.db.dailySatoriQueries.selectReminderById("reminder-1").executeAsOne().content)

            val again = fixture.service.capture(
                IdeaCaptureInput(
                    source = diarySnapshot(diaryId, content = "原始日记内容"),
                    content = IdeaTopicContent(title = "重新收录"),
                ),
            )
            assertEquals(false, again.alreadyCaptured)
            assertEquals("重新收录", fixture.service.getDetailSync(again.topicId)!!.topic.content.title)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun updateRejectsBlankTitleAndMissingTopic() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val topicId = fixture.captureTopic("标题")
            assertEquals(IdeaTopicError.InvalidInput, assertFailsWith<IdeaTopicException> {
                fixture.service.updateContent(topicId, IdeaTopicContent(title = " ")) 
            }.code)
            assertEquals(IdeaTopicError.NotFound, assertFailsWith<IdeaTopicException> {
                fixture.service.updateContent("missing", IdeaTopicContent(title = "标题"))
            }.code)
            assertEquals(IdeaTopicError.InvalidInput, assertFailsWith<IdeaTopicException> {
                fixture.service.appendProgress(topicId, "   ")
            }.code)
            assertEquals(IdeaTopicError.NotFound, assertFailsWith<IdeaTopicException> {
                fixture.service.setStatus("missing", IdeaTopicStatus.Researching)
            }.code)
            assertTrue(fixture.service.observeSummaries().first().isNotEmpty())
        } finally {
            fixture.close()
        }
    }

    private suspend fun IdeaTopicTestFixture.captureTopic(
        title: String,
        description: String = "",
        diaryId: Long? = null,
    ): String {
        val id = diaryId ?: createDiary("来源-$title")
        return service.capture(
            IdeaCaptureInput(
                source = diarySnapshot(id, content = "来源-$title"),
                content = IdeaTopicContent(title = title, description = description),
            ),
        ).topicId
    }
}
