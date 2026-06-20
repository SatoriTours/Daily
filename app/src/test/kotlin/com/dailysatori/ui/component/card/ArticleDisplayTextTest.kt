package com.dailysatori.ui.component.card

import com.dailysatori.shared.db.Article
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleDisplayTextTest {
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
        url = url,
        is_favorite = 0,
        comment = "",
        status = "completed",
        cover_image = null,
        cover_image_url = null,
        pub_date = null,
        created_at = 1_700_000_000_000,
        updated_at = 1_700_000_000_000,
    )
}
