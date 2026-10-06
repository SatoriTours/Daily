package com.dailysatori

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.ImageRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.core.task.SaveArticleTaskHandler
import com.dailysatori.core.task.saveArticleTaskPayloadJson
import com.dailysatori.platform.FileManager
import com.dailysatori.platform.WebViewLoader
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.asynctask.*
import com.dailysatori.service.externalfavorites.XBookmarksConnector
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.ui.feature.article.articleProcessingCardMessage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArticleIntakeRegressionTest {
    @Test fun articleListDoesNotReadEntireBodiesAndStillObservesNewSaves() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val repo = ArticleRepository(db)
            val body = "长文章正文".repeat(500_000)
            val id = repo.insert(title = "长文章", url = "https://example.com/large",
                aiContent = body, aiMarkdownContent = body)
            db.dailySatoriQueries.updateArticleOriginalMarkdownContent(body, 1L, id)
            val card = repo.getCards().first().single()
            assertTrue(card.ai_content.orEmpty().length <= 512, "列表只需要摘要预览")
            assertTrue(card.ai_markdown_content.orEmpty().length <= 512, "正文不应进入列表游标")
            assertNull(card.original_markdown_content)
            assertEquals(body, repo.getById(id)?.ai_markdown_content, "详情必须保留完整正文")
            val pending = repo.insert(url = "https://example.com/new", status = "pending")
            assertTrue(repo.getCards().first().any { it.id == pending })
        }
    }

    @Test fun cardQueriesKeepLocalFavoriteTagAndFullTextSearchSemantics() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val repo = ArticleRepository(db)
            val local = repo.insert(url = "https://example.com/local", title = "本地标题",
                aiContent = "预览".repeat(300) + "正文深处的检索词")
            val remote = repo.insert(url = "https://example.com/remote", title = "远程", isFavorite = 1)
            db.dailySatoriQueries.updateArticleSourceType("remote_news", 1L, remote)
            val tags = TagRepository(db)
            tags.setTagsForArticle(remote, listOf("测试标签"))
            val tagId = tags.getByName("测试标签")!!.id
            assertEquals(listOf(local), repo.getCardsSync().map { it.id })
            assertEquals(listOf(remote), repo.getCardsSync(favoritesOnly = true).map { it.id })
            assertEquals(listOf(remote), repo.getCardsSync(tagId = tagId).map { it.id })
            assertEquals(listOf(local), repo.getCardsSync(searchQuery = "正文深处的检索词").map { it.id })
            assertEquals(listOf(local), repo.getCardsSync(searchQuery = "本地标题").map { it.id })
        }
    }

    @Test fun normalizedIntakeCreatesOneImmediatePlaceholderAndDoesNotResetActiveProcessing() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val repo = ArticleRepository(DailySatoriDatabase(driver))
            val id = repo.createPendingFromUrl(" https://example.com/a/ ")
            assertEquals(id, repo.findIntakeArticle("https://example.com/a")?.id)
            assertEquals(id, repo.createPendingFromUrl("https://example.com/a"))
            repo.updateStatus(id, "aiProcessing")
            repo.createPendingFromUrl("https://example.com/a/")
            assertEquals("aiProcessing", repo.getById(id)?.status)
            repo.updateStatus(id, "error")
            repo.createPendingFromUrl("https://example.com/a")
            assertEquals("pending", repo.getById(id)?.status)
            assertEquals(1, repo.getCardsSync().size)
        }
    }

    @Test fun scopedCardSearchKeepsFavoriteTimeOrderAndDoesNotLeakOtherSources() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val repo = ArticleRepository(db)
            val first = repo.insert(url = "https://example.com/first", title = "共同检索词 第一篇")
            val second = repo.insert(url = "https://example.com/second", title = "共同检索词 第二篇")
            val other = repo.insert(url = "https://example.com/other", title = "共同检索词 其他来源")
            driver.execute(null, "INSERT INTO external_favorite_source(id, provider, account_id, display_name, created_at, updated_at) " +
                "VALUES (1, 'x', 'one', 'One', 0, 0), (2, 'x', 'two', 'Two', 0, 0)", 0)
            driver.execute(null, "INSERT INTO external_favorite_item(source_id, provider, external_id, article_id, " +
                "favorited_at, first_seen_at, last_seen_at, created_at, updated_at) VALUES " +
                "(1, 'x', 'first', $first, 100, 0, 0, 0, 0), " +
                "(1, 'x', 'second', $second, 200, 0, 0, 0, 0), " +
                "(2, 'x', 'other', $other, 300, 0, 0, 0, 0)", 0)
            assertEquals(listOf(second, first), repo.getCardsSync(sourceId = 1).map { it.id })
            assertEquals(listOf(second, first), repo.getCardsSync(searchQuery = "共同检索词", sourceId = 1).map { it.id })
            assertEquals(listOf(other), repo.getCardsSync(sourceId = 2).map { it.id })
        }
    }

    @Test fun saveTaskKeepsRetryVisibleAndOnlyMarksFinalFailure() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val articles = ArticleRepository(db)
            val tasks = AsyncTaskRepository(db)
            val url = "https://example.com/retry"
            val id = articles.createPendingFromUrl(url)
            val original = "# 测试文章\n" + "这是保存下来的原始正文，需要后台整理。".repeat(20)
            db.dailySatoriQueries.updateArticleOriginalMarkdownContent(original, 1L, id)
            HttpClient(MockEngine { error("已保存原文时不应访问网络") }).use { client ->
                val parser = WebpageParserService(articles, TagRepository(db), ImageRepository(db),
                    AiService(client), AiConfigService(AIConfigRepository(db, object : SecretValueCipher {
                        override fun encrypt(value: String) = value
                        override fun decrypt(value: String) = value
                        override fun isEncrypted(value: String) = false
                    })), WebViewLoader(), FileManager(),
                    client, ExternalFavoriteSourceRepository(db, { it }, { it }), XBookmarksConnector())
                val taskId = tasks.enqueue("save_article", saveArticleTaskPayloadJson(url),
                    uniqueKey = "save_article:$url", maxAttempts = 2)
                val runner = AsyncTaskRunner(tasks, AsyncTaskHandlerRegistry(listOf(SaveArticleTaskHandler(parser, tasks))))
                assertTrue(articles.getRecoverableForProcessingSync().isEmpty(), "后台恢复不能抢占已有保存任务")
                assertEquals(AsyncTaskRunOutcome.RetryScheduled, runner.run(taskId))
                assertEquals("retrying", articles.getById(id)?.status)
                assertNull(articles.getById(id)?.ai_content, "重试中的错误不应伪装为文章摘要")
                assertEquals("等待自动重试...", articleProcessingCardMessage("retrying"))
                driver.execute(null, "UPDATE async_task SET run_after_ms = 0 WHERE id = $taskId", 0)
                assertEquals(AsyncTaskRunOutcome.Failed, runner.run(taskId))
                assertEquals("error", articles.getById(id)?.status)
                assertNull(articles.getById(id)?.ai_content, "最终错误也不能存成正文或成功摘要")
                assertEquals("failed", tasks.getById(taskId)?.status)
            }
        }
    }
}
