package com.dailysatori.service.hotnews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class HotNewsArticleLoaderTest {
    private val original = "Android skills provide modular instructions and resources for Android development."
    private val item = HotNewsItem("android/skills", HotNewsPlatform.GITHUB, 7, "android/skills", "https://github.com/android/skills")

    @Test fun savedOriginalFromFailedProcessingDoesNotCountAsCompletedArticle() = withArticles { articles, hotNews, fetches ->
        val id = articles.insert(title = item.originalTitle, aiMarkdownContent = original, url = item.url, status = "error", isFavorite = 1)
        val loader = HotNewsArticleLoader(articles, hotNews) { complete(articles, it) }
        val loaded = loader.load(item)
        assertEquals(id, loaded.id)
        assertEquals("Android 开发工具说明", loaded.ai_title)
        assertEquals("已完成的中文摘要。", loaded.ai_content)
        assertEquals("completed", loaded.status)
        assertEquals(1L, loaded.is_favorite)
        assertEquals(original, loaded.original_markdown_content)
        assertTrue(fetches.isEmpty(), "已有原文时应直接恢复 AI 处理")
    }

    @Test fun missingSummaryIsRepairedEvenWhenStatusSaysCompleted() = withArticles { articles, hotNews, _ ->
        articles.insert(title = item.originalTitle, aiTitle = "Android 开发工具说明", aiMarkdownContent = "已翻译的中文正文。",
            url = item.url, status = "completed")
        val loaded = HotNewsArticleLoader(articles, hotNews) { complete(articles, it) }.load(item)
        assertEquals("已完成的中文摘要。", loaded.ai_content)
    }

    @Test fun successfulSummaryIsVisibleWhileTranslationRunsAndAfterItFails() = withArticles { articles, hotNews, _ ->
        val id = articles.insert(title = item.originalTitle, aiMarkdownContent = original, url = item.url, status = "error")
        val overviewVisible = CompletableDeferred<Unit>()
        val releaseTranslation = CompletableDeferred<Unit>()
        val snapshots = mutableListOf<Article>()
        val loader = HotNewsArticleLoader(articles, hotNews) {
            articles.updateAiContent(it, "成功生成的中文摘要。", "Android 开发工具说明", null)
            releaseTranslation.await()
            articles.updateStatus(it, "error")
            throw IllegalStateException("Translation failed")
        }
        val result = async {
            runCatching { loader.load(item) { article ->
                snapshots += article
                if (article.ai_content == "成功生成的中文摘要。") overviewVisible.complete(Unit)
            } }
        }
        try { withTimeout(3_000) { overviewVisible.await() } }
        finally { releaseTranslation.complete(Unit) }
        assertTrue(result.await().isFailure)
        assertEquals("成功生成的中文摘要。", snapshots.last().ai_content)
        assertEquals("error", snapshots.last().status)
        assertEquals(original, articles.getById(id)!!.original_markdown_content)
    }

    @Test fun completeCachedArticleRemainsAvailableWithoutNetworkOrAi() = withArticles { articles, hotNews, fetches ->
        val id = articles.insert(title = item.originalTitle, aiTitle = "Android 开发工具说明", aiContent = "已经保存的摘要。",
            aiMarkdownContent = "已经保存的中文正文。", url = item.url, status = "completed")
        val loaded = HotNewsArticleLoader(articles, hotNews) { error("Completed article must be reused") }.load(item)
        assertEquals(id, loaded.id)
        assertEquals("已经保存的摘要。", loaded.ai_content)
        assertTrue(fetches.isEmpty())
    }

    @Test fun newArticleSavesFullOriginalBeforeStartingAi() = withArticles { articles, hotNews, fetches ->
        val loaded = HotNewsArticleLoader(articles, hotNews) { id ->
            assertEquals(original, articles.getById(id)!!.original_markdown_content)
            complete(articles, id)
        }.load(item)
        assertEquals(listOf(item.url), fetches)
        assertEquals("已完成的中文摘要。", loaded.ai_content)
    }

    private fun complete(articles: ArticleRepository, id: Long) {
        articles.updateAiContent(id, "已完成的中文摘要。", "Android 开发工具说明", null)
        articles.updateAiMarkdownContent(id, "开发工具提供模块化说明和资源。")
        articles.updateStatus(id, "completed")
    }

    private fun withArticles(test: suspend CoroutineScope.(ArticleRepository, HotNewsService, MutableList<String>) -> Unit) = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val database = DailySatoriDatabase(driver)
            val articles = ArticleRepository(database)
            val fetches = mutableListOf<String>()
            HttpClient(MockEngine { error("Ranking refresh is not needed to open an article") }).use { client ->
                val hotNews = HotNewsService(HotNewsClient(client), HotNewsStore(SettingRepository(database)), { it },
                    loadContent = { url -> fetches += url; original })
                test(articles, hotNews, fetches)
            }
        }
    }
}
