package com.dailysatori.service.ideatopic

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdeaTopicPersistenceTest {

    @Test
    fun diaryCaptureIsManualAndIdempotent() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val diaryId = fixture.createDiary("做个 🍱 记录饮食的小工具，先看能不能坚持用一周")
            val input = IdeaCaptureInput(
                source = diarySnapshot(
                    diaryId = diaryId,
                    title = "记录饮食",
                    content = "做个 🍱 记录饮食的小工具，先看能不能坚持用一周",
                ),
                content = IdeaTopicContent(title = "  记录饮食小工具  ", description = "先做最小版本"),
            )

            val first = fixture.service.capture(input)
            val repeated = fixture.service.capture(input)

            assertEquals(first.topicId, repeated.topicId)
            assertTrue(repeated.alreadyCaptured, "second capture must report an existing topic")
            assertEquals(false, first.alreadyCaptured)

            val detail = assertNotNull(fixture.service.getDetailSync(first.topicId))
            assertEquals(1, detail.sources.size)
            assertEquals(IdeaTopicStatus.PendingResearch, detail.topic.status)
            assertEquals("记录饮食小工具", detail.topic.content.title)
            assertEquals("先做最小版本", detail.topic.content.description)
            assertEquals(listOf(IdeaEventKinds.Captured), detail.events.map { it.kind })
            assertEquals(1, detail.topic.sourceCount)
            assertEquals(
                first.topicId,
                fixture.service.findBySourceSync(IdeaSourceKey(IdeaSourceTypes.Diary, diaryId.toString())),
            )

            val source = detail.sources.single()
            assertEquals("做个 🍱 记录饮食的小工具，先看能不能坚持用一周", source.snapshot.originalContent)
            assertNull(source.snapshot.analysisContent, "a diary capture must not fabricate AI analysis")
            assertEquals(source.topicId, source.originalTopicId)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun opportunityCapturePreservesRawAndAnalysis() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val snapshot = opportunitySnapshot("opp-1")
            val result = fixture.service.capture(
                IdeaCaptureInput(source = snapshot, content = IdeaTopicContent(title = "新闻机会")),
            )

            val source = assertNotNull(fixture.service.getDetailSync(result.topicId)).sources.single()
            assertEquals("新闻原文", source.snapshot.originalContent)
            assertEquals("机会提炼", source.snapshot.analysisContent)
            assertEquals("https://example.com/a", source.snapshot.originalUrl)
            assertEquals("42", source.snapshot.originalRecordId)
            assertEquals("opp-1", source.snapshot.analysisId)
            assertEquals(2_000L, source.snapshot.analysisCreatedAt)
            assertEquals("v1", source.snapshot.analysisVersion)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun attachToExistingDoesNotOverwriteContent() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val firstDiary = fixture.createDiary("第一个来源")
            val created = fixture.service.capture(
                IdeaCaptureInput(
                    source = diarySnapshot(firstDiary, content = "第一个来源"),
                    content = IdeaTopicContent(title = "主标题", description = "已确认描述"),
                ),
            )

            val secondDiary = fixture.createDiary("第二个来源")
            val attached = fixture.service.capture(
                IdeaCaptureInput(
                    source = diarySnapshot(secondDiary, content = "第二个来源"),
                    content = IdeaTopicContent(title = "不应覆盖的标题", description = "不应覆盖的描述"),
                    targetTopicId = created.topicId,
                ),
            )

            assertEquals(created.topicId, attached.topicId)
            assertEquals(false, attached.alreadyCaptured)
            val detail = assertNotNull(fixture.service.getDetailSync(created.topicId))
            assertEquals("主标题", detail.topic.content.title)
            assertEquals("已确认描述", detail.topic.content.description)
            assertEquals(2, detail.sources.size)
            assertEquals(2, detail.topic.sourceCount)
            assertEquals(listOf(IdeaEventKinds.Captured, IdeaEventKinds.Captured), detail.events.map { it.kind })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun blankTitleRejected() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val diaryId = fixture.createDiary("空白标题")
            val error = assertFailsWith<IdeaTopicException> {
                fixture.service.capture(
                    IdeaCaptureInput(
                        source = diarySnapshot(diaryId),
                        content = IdeaTopicContent(title = "   \n  "),
                    ),
                )
            }
            assertEquals(IdeaTopicError.InvalidInput, error.code)
            assertEquals(0, fixture.service.observeSummaries().first().size)
            assertNull(fixture.service.findBySourceSync(IdeaSourceKey(IdeaSourceTypes.Diary, diaryId.toString())))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun equalTimestampsHaveStableOrder() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            fixture.clockMs = 5_000L
            val ids = (1..4).map { index ->
                val diaryId = fixture.createDiary("同时间 $index", createdAt = 5_000L)
                fixture.service.capture(
                    IdeaCaptureInput(
                        source = diarySnapshot(diaryId, content = "同时间 $index"),
                        content = IdeaTopicContent(title = "同时间 $index"),
                    ),
                ).topicId
            }

            val firstRead = fixture.service.observeSummaries().first().map { it.id }
            val secondRead = fixture.service.observeSummaries().first().map { it.id }

            assertEquals(firstRead, secondRead)
            assertEquals(ids.sortedDescending(), firstRead)
            assertEquals(ids.toSet().size, firstRead.size)
            firstRead.forEach { id ->
                assertEquals(5_000L, assertNotNull(fixture.service.getDetailSync(id)).topic.updatedAt)
            }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun deletedTopicCanBeCapturedAgainAsNewRecord() = runBlocking {
        val fixture = IdeaTopicTestFixture()
        try {
            val diaryId = fixture.createDiary("删除后重新收录")
            val snapshot = diarySnapshot(diaryId, content = "删除后重新收录")
            val first = fixture.service.capture(
                IdeaCaptureInput(source = snapshot, content = IdeaTopicContent(title = "第一次")),
            )
            fixture.repository.deleteComponent(first.topicId)
            assertNull(fixture.service.findBySourceSync(snapshot.key))

            val again = fixture.service.capture(
                IdeaCaptureInput(source = snapshot, content = IdeaTopicContent(title = "第二次")),
            )
            assertEquals(false, again.alreadyCaptured)
            assertTrue(again.topicId != first.topicId)
            assertEquals("第二次", assertNotNull(fixture.service.getDetailSync(again.topicId)).topic.content.title)
        } finally {
            fixture.close()
        }
    }
}
