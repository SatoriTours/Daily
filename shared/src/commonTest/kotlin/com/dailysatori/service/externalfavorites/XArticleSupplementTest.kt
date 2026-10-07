package com.dailysatori.service.externalfavorites

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.External_favorite_item
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class XArticleSupplementTest {
    @Test fun longCardTitleAndPreviewDoNotPreventFullBodyFetch() = runBlocking {
        organize("https://t.co/a", """{"primary_url":"https://x.com/i/article/456","canonical_tweet_url":"https://x.com/writer/status/123","url_title":"This is a long title over twenty characters","url_description":"This preview is not the complete body"}""", expectedCalls = 1)
    }

    @Test fun longIntroductoryPostDoesNotPreventArticleFetch() = runBlocking {
        organize("An introductory post with more than twenty characters, but no actual article body.", """{"is_article":true,"article_content_complete":false}""", expectedCalls = 1)
    }

    @Test fun confirmedArticleBodyDoesNotFetchAgain() = runBlocking {
        organize("Complete article body with the final paragraph preserved.", """{"is_article":true,"article_content_complete":true,"primary_url":"https://x.com/i/article/456"}""", expectedCalls = 0)
    }

    @Test fun confirmedShortArticleDoesNotFetchAgain() = runBlocking {
        organize("完整短文。", """{"is_article":true,"article_content_complete":true}""", expectedCalls = 0)
    }

    @Test fun ordinaryLongPostDoesNotFetchAgain() = runBlocking {
        organize("This ordinary post is complete and longer than twenty characters.", "{}", expectedCalls = 0)
    }

    @Test fun previewOnlyFailureIsNotPassedToAiAsFullArticle() = runBlocking {
        organize("An introductory post long enough to have been mistaken for a complete article.", """{"is_article":true,"article_content_complete":false}""", expectedCalls = 1, body = null)
    }

    @Test fun supplementDoesNotOverwriteAnExistingOriginal() = runBlocking {
        organize("Only an introduction, not the complete body.", """{"is_article":true,"article_content_complete":false}""", 1, original = "User-preserved original")
    }

    private suspend fun organize(text: String, metadata: String, expectedCalls: Int, body: String? = "Full article including its final paragraph.", original: String? = null) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = ExternalFavoriteSourceRepository(db, { it }, { it })
            val items = ExternalFavoriteItemRepository(db)
            val articles = ArticleRepository(db)
            val sourceId = sources.save(provider = "x", displayName = "X", accountId = "42", accountName = "Writer", authJson = "{}", enabled = true)
            val (item, _) = items.upsertDraft(sourceId, ExternalFavoriteItemDraft("x", "123", "https://x.com/writer/status/123", "Article", text, "Writer", null, null, metadata, contentHash = "hash", aiInputHash = "ai"))
            val articleId = articles.insert(title = "Article", url = "https://x.com/writer/status/123", status = "completed")
            items.markImported(item.id, articleId, false)
            if (original != null) articles.updateOriginalMarkdownContent(articleId, original)
            var calls = 0
            var aiCalls = 0
            val organizer = ExternalFavoriteAiOrganizer(items, articles,
                supplementResolver = object : ExternalFavoriteSupplementResolver {
                    override suspend fun resolve(item: External_favorite_item, input: ExternalFavoriteAiInput, httpLogger: FavoriteSyncHttpLogger, taskId: Long?): ExternalFavoriteSupplement? {
                        calls++
                        return body?.let { ExternalFavoriteSupplement(input.canonicalUrl, "Article", it, "x_article", articleContentComplete = true) }
                    }
                }, generateAnalysis = { input ->
                    aiCalls++
                    assertEquals(if (expectedCalls == 1) body else null, input.supplementText)
                    ExternalFavoriteAiAnalysis("Article", "Summary", "Markdown")
                })
            organizer.organizePending()
            assertEquals(expectedCalls, calls)
            if (body == null) {
                assertEquals(0, aiCalls)
                assertEquals("failed", items.getBySourceExternalId(sourceId, "123")?.ai_status)
            } else {
                assertEquals(1, aiCalls)
                if (original != null) assertEquals(original, articles.getById(articleId)?.original_markdown_content)
                else if (expectedCalls == 1) assertTrue(articles.getById(articleId)?.original_markdown_content.orEmpty().contains(body))
            }
        }
    }
}
