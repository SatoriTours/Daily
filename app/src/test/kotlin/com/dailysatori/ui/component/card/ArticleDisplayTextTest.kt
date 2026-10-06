package com.dailysatori.ui.component.card

import com.dailysatori.shared.db.Article
import com.dailysatori.ui.feature.article.articleMagazineMetaChips
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.ui.component.news.articleReaderMetadata
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleDisplayTextTest {
    @Test
    fun articleMetadataContainsOnlyAnExplicitDateWithoutDomainOrInternalStatus() {
        val article = article("标题", "解读标题", "https://githubstatus.com/incident")
        for (status in listOf("completed", "pending", "retrying", "error")) {
            assertEquals(listOf("收藏于 ${TimeUtils.formatDate(article.created_at)}"),
                articleMagazineMetaChips(article.copy(status = status), "发布于", "收藏于"))
        }
        assertEquals(listOf("发布于 ${TimeUtils.formatDate(article.created_at)}"),
            articleMagazineMetaChips(article.copy(pub_date = article.created_at), "发布于", "收藏于"))
    }

    @Test
    fun readerMetadataShowsEachSourceOnlyOnceInOneLine() {
        assertEquals("GitHub · 收藏于 2026-10-06", articleReaderMetadata(" GitHub ",
            listOf("github", "收藏于 2026-10-06", " "), "来源未知"))
        assertEquals("来源未知 · 发布于 2026-10-05", articleReaderMetadata(null,
            listOf("发布于 2026-10-05"), "来源未知"))
    }

    @Test
    fun displayTitleMatchesArticleDetailPriority() {
        val article = article(
            title = "原始 X 标题",
            aiTitle = "AI 整理标题",
            url = "https://example.com/posts/1",
        )

        assertEquals("AI 整理标题", articleDisplayTitle(article))
    }

    @Test
    fun displayTitleFallsBackToDomainWhenTitlesAreBlank() {
        val article = article(
            title = " ",
            aiTitle = " ",
            url = "https://www.example.com/posts/1",
        )

        assertEquals("example.com", articleDisplayTitle(article))
    }

    private fun article(
        title: String?,
        aiTitle: String?,
        url: String?,
    ): Article = Article(
        id = 1,
        title = title,
        ai_title = aiTitle,
        ai_content = null,
        ai_markdown_content = null,
        original_markdown_content = null,
        url = url,
        is_favorite = 0,
        source_type = "local",
        comment = "",
        status = "completed",
        cover_image = null,
        cover_image_url = null,
        pub_date = null,
        created_at = 1_700_000_000_000,
        updated_at = 1_700_000_000_000,
    )
}
