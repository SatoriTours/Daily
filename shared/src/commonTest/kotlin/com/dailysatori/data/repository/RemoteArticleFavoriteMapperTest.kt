package com.dailysatori.data.repository

import com.dailysatori.service.remotenews.RemoteArticle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteArticleFavoriteMapperTest {
    @Test
    fun mapsRemoteArticleIntoLocalFavoriteFields() {
        val article = RemoteArticle(
            id = 42,
            title = "Remote Title",
            url = " https://example.com/article ",
            summary = "Summary text",
            viewpoints = listOf("Point A", " ", "Point B"),
            coverUrl = "https://example.com/cover.jpg",
            content = "# Original",
            processedAt = "2026-05-20T08:30:00Z",
        )

        val fields = article.toLocalFavoriteArticleFields()

        assertEquals("Remote Title", fields.title)
        assertEquals("Remote Title", fields.aiTitle)
        assertEquals("https://example.com/article", fields.url)
        assertEquals("Summary text\n\n## 关键观点\n\n- Point A\n- Point B", fields.aiContent)
        assertEquals("# Original", fields.aiMarkdownContent)
        assertEquals("https://example.com/cover.jpg", fields.coverImageUrl)
        assertEquals("completed", fields.status)
        assertEquals(1L, fields.isFavorite)
        assertNotNull(fields.pubDate)
    }

    @Test
    fun omitsBlankSummaryAndUsesViewpointsOnly() {
        val article = RemoteArticle(
            id = 7,
            title = "Title",
            url = "https://example.com/only-viewpoints",
            summary = " ",
            viewpoints = listOf("Only point"),
        )

        val fields = article.toLocalFavoriteArticleFields()

        assertEquals("## 关键观点\n\n- Only point", fields.aiContent)
    }

    @Test
    fun returnsNullPubDateForUnparseableRemoteTime() {
        val article = RemoteArticle(
            id = 8,
            title = "Title",
            url = "https://example.com/no-date",
            createdAt = "not-a-date",
        )

        val fields = article.toLocalFavoriteArticleFields()

        assertNull(fields.pubDate)
        assertTrue(fields.url!!.contains("example.com"))
    }

    @Test
    fun publishedAtTakesPrecedenceForLocalPubDate() {
        val article = RemoteArticle(
            id = 11,
            title = "Published",
            url = "https://example.com/published",
            publishedAt = "2026-05-18T08:00:00Z",
            processedAt = "2026-05-20T08:30:00Z",
            createdAt = "2026-05-21T08:30:00Z",
        )

        val fields = article.toLocalFavoriteArticleFields()

        assertEquals(remoteArticleTimeMillis("2026-05-18T08:00:00Z"), fields.pubDate)
    }

    @Test
    fun detectsEnglishRemoteArticleNeedsLocalAiReprocessing() {
        val article = RemoteArticle(
            id = 9,
            title = "OpenAI launches a new coding model",
            url = "https://example.com/english",
            summary = "The company announced a major update for developers and enterprise teams.",
            content = "# OpenAI launches a new coding model\n\nThe company announced a major update for developers and enterprise teams. The model improves reliability and long-context reasoning.",
        )

        assertTrue(article.needsLocalAiReprocessingForChineseOutput())
    }

    @Test
    fun keepsChineseRemoteArticleCompletedWithoutReprocessing() {
        val article = RemoteArticle(
            id = 10,
            title = "OpenAI 发布新的编程模型",
            url = "https://example.com/chinese",
            summary = "公司宣布面向开发者和企业团队的重要更新。",
            content = "# OpenAI 发布新的编程模型\n\n公司宣布面向开发者和企业团队的重要更新，模型提升了可靠性和长上下文推理能力。",
        )

        assertFalse(article.needsLocalAiReprocessingForChineseOutput())
    }

    @Test
    fun blankRemoteArticleUrlDoesNotTriggerLocalAiReprocessing() {
        val article = RemoteArticle(
            id = 12,
            title = "OpenAI launches new coding model",
            url = " ",
            summary = "The company announced a major update for developers and enterprise teams.",
            content = "The model improves reliability and long context reasoning for teams building software every day.",
        )

        assertFalse(article.needsLocalAiReprocessingForChineseOutput())
    }

    @Test
    fun cachedRemoteArticleUsesSummaryOrTitleAsMarkdownFallback() {
        val withSummary = RemoteArticle(id = 13, title = "Title", summary = "Summary")
        val titleOnly = RemoteArticle(id = 14, title = "Title Only")

        assertEquals("Summary", withSummary.toLocalCachedArticleFields().aiMarkdownContent)
        assertEquals("Title Only", titleOnly.toLocalCachedArticleFields().aiMarkdownContent)
    }
}
