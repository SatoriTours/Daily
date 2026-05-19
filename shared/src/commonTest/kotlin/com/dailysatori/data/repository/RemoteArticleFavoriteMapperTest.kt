package com.dailysatori.data.repository

import com.dailysatori.service.remotenews.RemoteArticle
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
