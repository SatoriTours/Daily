package com.dailysatori.ui.feature.ideatopic

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.IdeaTopicRepository
import com.dailysatori.service.ideatopic.*
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.opportunity.ReadNewsArticle
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Diary
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class IdeaTopicEntryTest {

    @Test
    fun diaryCaptureNeverFabricatesAnalysisAndRequiresSavedId() {
        val unsaved = Diary(
            id = 0L,
            content = "临时日记",
            tags = "work,ideas",
            mood = null,
            images = null,
            created_at = 1_000L,
            updated_at = 1_000L,
        )
        assertFailsWith<IllegalArgumentException> {
            diaryIdeaCaptureInput(unsaved)
        }

        val saved = unsaved.copy(id = 42L)
        val input = diaryIdeaCaptureInput(saved)
        assertEquals("42", input.source.key.recordId)
        assertEquals(IdeaSourceTypes.Diary, input.source.key.type)
        assertNull(input.source.analysisContent, "Diary tags must not be fabricated into AI analysis")
        assertEquals("临时日记", input.content.title)
    }

    @Test
    fun newsOpportunityKeepsArticleAndAnalysisSeparateWithoutTouchingSavedOrIgnored() {
        val opportunity = NewsOpportunity(
            id = "opp-10",
            article = ReadNewsArticle(
                key = "art-1",
                title = "新闻标题",
                content = "新闻原文正文",
                url = "https://example.com/news/1",
                source = "科技周刊",
                publishedAt = "2026-10-08",
                readAt = 2_000L,
                localArticleId = 1L,
            ),
            title = "点子标题",
            category = "科技",
            fact = "提炼的事实",
            relevance = "相关性分析",
            action = "建议行动",
            caveat = "风险注意",
            quote = "引用段落",
            createdAt = 2_000L,
            saved = true,
            ignored = false,
            reminderId = "rem-1",
        )

        val input = opportunityIdeaCaptureInput(opportunity)
        assertEquals("新闻原文正文", input.source.originalContent)
        assertNotNull(input.source.analysisContent)
        assertTrue(input.source.analysisContent!!.contains("提炼的事实"))
        assertTrue(input.source.analysisContent!!.contains("相关性分析"))

        // Original opportunity state remains unchanged
        assertTrue(opportunity.saved)
        assertFalse(opportunity.ignored)
        assertEquals("rem-1", opportunity.reminderId)
    }

    @Test
    fun duplicateSourceResolvesToMainTopicAcrossMergesAndCanBeRecapturedAfterDelete() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        val repo = IdeaTopicRepository(db)
        var clock = 10_000L
        val service = IdeaTopicService(repo, now = { clock++ })

        try {
            val diary = Diary(
                id = 99L,
                content = "可被合并与删除收录的日记",
                tags = null,
                mood = null,
                images = null,
                created_at = 1_000L,
                updated_at = 1_000L,
            )
            val input = diaryIdeaCaptureInput(diary)
            val captured1 = service.capture(input)

            // Lookup resolves to captured topic
            val found1 = service.findBySourceSync(input.source.key)
            assertEquals(captured1.topicId, found1)

            // Create another main topic and merge topic 1 into topic 2
            val targetInput = input.copy(
                source = input.source.copy(key = IdeaSourceKey(IdeaSourceTypes.Diary, "100")),
                content = IdeaTopicContent(title = "目标主题"),
            )
            val captured2 = service.capture(targetInput)
            val finalTarget = service.merge(captured1.topicId, captured2.topicId)
            assertEquals(captured2.topicId, finalTarget)

            // Source lookup now resolves to the merged main topic
            val foundAfterMerge = service.findBySourceSync(input.source.key)
            assertEquals(finalTarget, foundAfterMerge)

            // Delete the component
            service.delete(finalTarget)
            val foundAfterDelete = service.findBySourceSync(input.source.key)
            assertNull(foundAfterDelete, "Source lookup must return null after topic component deletion")

            // Recapture succeeds with a new topic
            val recaptured = service.capture(input)
            assertNotNull(recaptured.topicId)
            assertNotEquals(captured1.topicId, recaptured.topicId)
            assertEquals(recaptured.topicId, service.findBySourceSync(input.source.key))
        } finally {
            driver.close()
        }
    }
}
