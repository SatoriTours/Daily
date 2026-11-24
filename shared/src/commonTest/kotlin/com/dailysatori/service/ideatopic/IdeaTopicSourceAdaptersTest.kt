package com.dailysatori.service.ideatopic

import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.opportunity.ReadNewsArticle
import com.dailysatori.shared.db.Diary_record as Diary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdeaTopicSourceAdaptersTest {

    @Test
    fun diaryCaptureKeepsTextAndNeverFabricatesAnalysis() {
        val diary = Diary(
            id = 7L,
            content = "第一行标题\n后面是正文 🍱",
            tags = "work,idea",
            mood = "calm",
            images = null,
            created_at = 111L,
            updated_at = 222L,
            parent_diary_id = null,
        )

        val input = diaryIdeaCaptureInput(diary)

        assertEquals(IdeaSourceTypes.Diary, input.source.key.type)
        assertEquals("7", input.source.key.recordId)
        assertEquals("7", input.source.originalRecordId)
        assertEquals("第一行标题", input.source.originalTitle)
        assertEquals("第一行标题\n后面是正文 🍱", input.source.originalContent)
        assertEquals(111L, input.source.originalCreatedAt)
        assertNull(input.source.analysisId)
        assertNull(input.source.analysisContent, "diary tags must never become AI analysis")
        assertNull(input.source.analysisCreatedAt)
        assertNull(input.source.analysisVersion)
        assertEquals("第一行标题", input.content.title)
        assertNull(input.targetTopicId)
    }

    @Test
    fun unsavedDiaryWithTemporaryIdCannotBeCaptured() {
        val unsaved = Diary(
            id = 0L,
            content = "还没保存的日记",
            tags = null,
            mood = null,
            images = null,
            created_at = 0L,
            updated_at = 0L,
            parent_diary_id = null,
        )
        assertFailsWith<IllegalArgumentException> { diaryIdeaCaptureInput(unsaved) }
    }

    @Test
    fun newsOpportunityKeepsRawArticleAndAnalysisSeparate() {
        val opportunity = NewsOpportunity(
            id = "opp-42",
            article = ReadNewsArticle(
                key = "article-key",
                title = "新闻原始标题",
                content = "新闻原始正文",
                url = "https://example.com/news",
                source = "example",
                publishedAt = "2026-10-01",
                readAt = 1_000L,
                localArticleId = 42L,
            ),
            title = "机会标题",
            category = "product",
            fact = "事实描述",
            relevance = "相关性与价值",
            action = "建议下一步",
            caveat = "需要注意的风险",
            quote = "原文引用句",
            createdAt = 2_000L,
        )

        val input = opportunityIdeaCaptureInput(opportunity)

        assertEquals(IdeaSourceTypes.NewsOpportunity, input.source.key.type)
        assertEquals("opp-42", input.source.key.recordId)
        assertEquals("新闻原始标题", input.source.originalTitle)
        assertEquals("新闻原始正文", input.source.originalContent)
        assertEquals("42", input.source.originalRecordId)
        assertEquals("https://example.com/news", input.source.originalUrl)
        assertEquals("opp-42", input.source.analysisId)
        assertEquals(2_000L, input.source.analysisCreatedAt)
        val analysis = assertNotNull(input.source.analysisContent)
        assertTrue(analysis.contains("事实：事实描述"))
        assertTrue(analysis.contains("建议行动：建议下一步"))
        assertTrue(analysis.contains("风险与注意：需要注意的风险"))
        assertEquals("机会标题", input.content.title)
        assertEquals("事实描述", input.content.description)
        assertEquals("相关性与价值", input.content.conclusions)
        assertEquals("建议下一步", input.content.nextAction)
    }

    @Test
    fun sourcesWithoutAnalysisFieldsDoNotFabricateThem() {
        val opportunity = NewsOpportunity(
            id = "opp-empty",
            article = ReadNewsArticle(
                key = "article-key",
                title = "标题",
                content = "正文",
                url = null,
                source = "example",
                publishedAt = null,
                readAt = 1_000L,
                localArticleId = null,
            ),
            title = "",
            category = "",
            fact = "",
            relevance = "",
            action = "",
            caveat = "",
            quote = "",
            createdAt = 3_000L,
        )

        val input = opportunityIdeaCaptureInput(opportunity)

        assertNull(input.source.analysisContent)
        assertNull(input.source.originalUrl)
        assertNull(input.source.originalRecordId)
    }

    @Test
    fun articleCaptureKeepsStoredTextAndOptionalSummary() {
        val input = articleIdeaCaptureInput(
            articleId = 42L,
            title = "  文章标题  ",
            originalContent = "文章原始正文",
            summary = "  AI 摘要正文  ",
            url = "  https://example.com/article  ",
            createdAt = 1_700L,
        )

        assertEquals(IdeaSourceKey(IdeaSourceTypes.Article, "42"), input.source.key)
        assertEquals("文章标题", input.source.originalTitle)
        assertEquals("文章原始正文", input.source.originalContent)
        assertEquals(1_700L, input.source.originalCreatedAt)
        assertEquals("42", input.source.originalRecordId)
        assertEquals("https://example.com/article", input.source.originalUrl)
        assertEquals("AI 摘要正文", input.source.analysisContent)
        assertEquals("文章标题", input.content.title)
        assertEquals("AI 摘要正文", input.content.description)
        assertNull(input.targetTopicId)
    }

    @Test
    fun articleCaptureFallsBackToStableKeysWithoutInventingAnalysis() {
        val byUrl = articleIdeaCaptureInput(
            articleId = null,
            title = "标题",
            originalContent = "正文",
            summary = "   ",
            url = " https://example.com/x ",
        )
        assertEquals(IdeaSourceKey(IdeaSourceTypes.Article, "https://example.com/x"), byUrl.source.key)
        assertNull(byUrl.source.originalRecordId)
        assertNull(byUrl.source.analysisContent)

        val byTitle = articleIdeaCaptureInput(
            articleId = 0L,
            title = "  只靠标题  ",
            originalContent = "正文",
            sourceKeyId = "  stable-key  ",
        )
        assertEquals(IdeaSourceKey(IdeaSourceTypes.Article, "stable-key"), byTitle.source.key)
        assertNull(byTitle.source.originalRecordId)

        assertEquals(
            IdeaSourceKey(IdeaSourceTypes.Article, "只靠标题"),
            articleIdeaCaptureInput(articleId = 0L, title = " 只靠标题 ", originalContent = "").source.key,
        )
    }

    @Test
    fun articleCaptureRequiresSomeIdentityAndBoundsPrefilledContent() {
        assertFailsWith<IllegalArgumentException> {
            articleIdeaCaptureInput(articleId = null, title = "   ", originalContent = "正文")
        }
        val longSummary = "摘要".repeat(400)
        val input = articleIdeaCaptureInput(
            articleId = 1L,
            title = "标".repeat(80),
            originalContent = "正文",
            summary = longSummary,
        )
        assertEquals(60, input.content.title.length)
        assertEquals(500, input.content.description.length)
        assertEquals(800, input.source.analysisContent!!.length)
    }

    @Test
    fun articleCaptureNeverCollidesWithDiaryOrOpportunityKeys() {
        val diary = Diary(42L, "内容", null, null, null, 1L, 1L, null)
        assertEquals(IdeaSourceKey("diary", "42"), diaryIdeaCaptureInput(diary).source.key)
        assertEquals(
            IdeaSourceKey("article", "42"),
            articleIdeaCaptureInput(articleId = 42L, title = "标题", originalContent = "正文").source.key,
        )
    }

    @Test
    fun captureKeysAreStableAndDoNotCollideAcrossSourceTypes() {
        val diary = Diary(7L, "内容", null, null, null, 1L, 1L, null)
        val opportunity = NewsOpportunity(
            id = "7",
            article = ReadNewsArticle("k", "标题", "正文", null, "src", null, 1L, null),
            title = "机会",
            category = "",
            fact = "",
            relevance = "",
            action = "",
            caveat = "",
            quote = "",
            createdAt = 1L,
        )

        assertEquals(IdeaSourceKey("diary", "7"), diaryIdeaCaptureInput(diary).source.key)
        assertEquals(IdeaSourceKey("news_opportunity", "7"), opportunityIdeaCaptureInput(opportunity).source.key)
        assertTrue(diaryIdeaCaptureInput(diary).source.key != opportunityIdeaCaptureInput(opportunity).source.key)
    }

    @Test
    fun localAndRemoteArticlesWithTheSameNumberKeepDistinctSourceKeys() {
        val local = articleIdeaCaptureInput(articleId = 42L, title = "本地文章", originalContent = "正文")
        val remote = articleIdeaCaptureInput(
            articleId = null,
            title = "远程文章",
            originalContent = "正文",
            sourceKeyId = "remote:42",
        )

        assertEquals(IdeaSourceKey(IdeaSourceTypes.Article, "42"), local.source.key)
        assertEquals("42", local.source.originalRecordId)
        assertEquals(IdeaSourceKey(IdeaSourceTypes.Article, "remote:42"), remote.source.key)
        assertNull(remote.source.originalRecordId)
        assertTrue(local.source.key != remote.source.key)
    }

    @Test
    fun nonLocalArticleRejectsANamespaceFreeNumericSourceKey() {
        assertFailsWith<IllegalArgumentException> {
            articleIdeaCaptureInput(articleId = null, title = "远程文章", originalContent = "正文", sourceKeyId = " 42 ")
        }
        assertFailsWith<IllegalArgumentException> {
            articleIdeaCaptureInput(articleId = 0L, title = "远程文章", originalContent = "正文", sourceKeyId = "42")
        }
    }
}
