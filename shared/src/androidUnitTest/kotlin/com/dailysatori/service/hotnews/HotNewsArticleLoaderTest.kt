package com.dailysatori.service.hotnews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
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
            assertTrue(hotNews.find(item.identity)!!.articleProcessing)
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
        try {
            withTimeout(3_000) { overviewVisible.await() }
            assertEquals("Android 开发工具说明", hotNews.find(item.identity)?.displayTitle)
            assertEquals("成功生成的中文摘要。", hotNews.find(item.identity)?.displaySummary)
            assertNull(hotNews.boards.value.first { it.platform == item.platform }.error)
        } finally { releaseTranslation.complete(Unit) }
        assertTrue(result.await().isFailure)
        assertEquals("成功生成的中文摘要。", snapshots.last().ai_content)
        assertEquals("error", snapshots.last().status)
        assertFalse(hotNews.find(item.identity)!!.articleProcessing)
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

    @Test fun detailSummaryRepairsFailedRankingAndPersistsIt() = withArticles { articles, hotNews, _ ->
        val snapshots = mutableListOf<HotNewsItem>()
        val loaded = HotNewsArticleLoader(articles, hotNews) { id ->
            articles.updateAiContent(id, "已完成的中文摘要。", "Android 开发工具说明", null)
            complete(articles, id)
        }.load(item) {
            hotNews.find(item.identity)?.let(snapshots::add)
        }
        assertEquals("completed", loaded.status)
        assertEquals("Android 开发工具说明", hotNews.find(item.identity)?.displayTitle)
        assertEquals("已完成的中文摘要。", hotNews.find(item.identity)?.displaySummary)
        assertNull(hotNews.boards.value.first { it.platform == item.platform }.error)
        assertTrue(snapshots.any { it.chineseTitle == "Android 开发工具说明" })
    }

    @Test fun cachedCompletedArticleAlsoRepairsFailedRankingWithoutReprocessing() = withArticles { articles, hotNews, _ ->
        articles.insert(title = item.originalTitle, aiTitle = "Android 开发工具说明", aiContent = "已经保存的摘要。",
            aiMarkdownContent = "已经保存的中文正文。", url = item.url, status = "completed")
        HotNewsArticleLoader(articles, hotNews) { error("Completed article must be reused") }.load(item)
        assertEquals("Android 开发工具说明", hotNews.find(item.identity)?.displayTitle)
        assertNull(hotNews.boards.value.first { it.platform == item.platform }.error)
    }

    @Test fun anotherUntranslatedItemKeepsBoardFailureAfterDetailRecovery() = withArticles(additionalItem = true) { articles, hotNews, _ ->
        HotNewsArticleLoader(articles, hotNews) { complete(articles, it) }.load(item)
        assertEquals("Android 开发工具说明", hotNews.find(item.identity)?.displayTitle)
        assertEquals(HotNewsError.TRANSLATION_FAILED, hotNews.boards.value.first { it.platform == item.platform }.error)
    }

    @Test fun cancellingDetailProcessingClearsPendingFlagWithoutInventingATranslation() = withArticles { articles, hotNews, _ ->
        assertFailsWith<CancellationException> {
            HotNewsArticleLoader(articles, hotNews) {
                assertTrue(hotNews.find(item.identity)!!.articleProcessing)
                throw CancellationException("Cancelled by navigation")
            }.load(item)
        }
        assertFalse(hotNews.find(item.identity)!!.articleProcessing)
        assertNull(hotNews.find(item.identity)?.chineseTitle)
        assertEquals(HotNewsError.TRANSLATION_FAILED, hotNews.boards.value.first { it.platform == item.platform }.error)
    }

    @Test fun staleItemCannotReplaceTheCurrentRankingTranslation() = withArticles { articles, hotNews, _ ->
        HotNewsArticleLoader(articles, hotNews) { complete(articles, it) }
            .load(item.copy(originalTitle = "An older title"))
        assertNull(hotNews.find(item.identity)?.chineseTitle)
        assertFalse(hotNews.find(item.identity)!!.articleProcessing)
    }

    @Test fun detailRecoveryDoesNotClearRankingFetchFailure() = withArticles(initialError = HotNewsError.FETCH_FAILED) { articles, hotNews, _ ->
        HotNewsArticleLoader(articles, hotNews) { complete(articles, it) }.load(item)
        assertEquals("Android 开发工具说明", hotNews.find(item.identity)?.displayTitle)
        assertEquals(HotNewsError.FETCH_FAILED, hotNews.boards.value.first { it.platform == item.platform }.error)
    }

    private fun complete(articles: ArticleRepository, id: Long) {
        articles.updateAiContent(id, "已完成的中文摘要。", "Android 开发工具说明", null)
        articles.updateAiMarkdownContent(id, "开发工具提供模块化说明和资源。")
        articles.updateStatus(id, "completed")
    }

    private fun withArticles(additionalItem: Boolean = false, initialError: HotNewsError = HotNewsError.TRANSLATION_FAILED,
        test: suspend CoroutineScope.(ArticleRepository, HotNewsService, MutableList<String>) -> Unit) = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val database = DailySatoriDatabase(driver)
            val articles = ArticleRepository(database)
            val fetches = mutableListOf<String>()
            HttpClient(MockEngine { error("Ranking refresh is not needed to open an article") }).use { client ->
                val store = HotNewsStore(SettingRepository(database))
                val items = listOf(item) + if (additionalItem) listOf(item.copy(id = "acme/pending",
                    originalTitle = "Another tool", url = "https://example.com/pending")) else emptyList()
                store.save(HotNewsBoard(item.platform, items, 1L, initialError))
                val hotNews = HotNewsService(HotNewsClient(client), store, { it },
                    loadContent = { url -> fetches += url; original })
                test(articles, hotNews, fetches)
                assertEquals(hotNews.boards.value.first { it.platform == item.platform }, store.load(item.platform),
                    "详情回写结果必须持久化，重启后不能恢复旧的失败状态")
            }
        }
    }
}
