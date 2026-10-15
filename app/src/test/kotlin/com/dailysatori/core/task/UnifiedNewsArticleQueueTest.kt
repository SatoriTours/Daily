package com.dailysatori.core.task

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.hotnews.*
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Unified_news_source
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import com.dailysatori.service.unifiednews.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.first
import kotlin.test.*

class UnifiedNewsArticleQueueTest {
    private val source = Unified_news_source(1, 1, "H1", "hot_news", null, "v2ex:8",
        "https://v2ex.com/t/8#comments", "阅读工具", "这是平台简介，不能当成原文", null)

    @Test fun duplicateLinksShareOneArticleAndOneDurableTask() = withQueue { queue, articles, tasks, scheduled, _ ->
        val ids = queue.enqueue(listOf(source, source.copy(id = 2, source_url = "https://v2ex.com/t/8")))
        assertEquals(2, ids.size)
        assertEquals(ids[1], ids[2])
        assertEquals(1, scheduled.size)
        val article = assertNotNull(articles.getById(ids.getValue(1)))
        assertEquals("https://v2ex.com/t/8", article.url)
        assertNull(article.original_markdown_content)
        assertNull(article.ai_content)
        assertEquals("save_article", tasks.getById(scheduled.single())!!.type)
        queue.enqueue(listOf(source))
        assertEquals(1, scheduled.distinct().size)
    }

    @Test fun capturedPlatformBodyIsReusedWithoutTreatingItsSummaryAsOriginal() = withQueue { queue, articles, _, _, store ->
        val body = "这是已经从作者页面取得的原文，介绍本地阅读工具的设计与资料导出方式。".repeat(3)
        store.save(HotNewsBoard(HotNewsPlatform.V2EX, listOf(HotNewsItem("8", HotNewsPlatform.V2EX, 2,
            source.title, "https://v2ex.com/t/8", originalContent = body))))
        // Recreate the service to read the saved board, as after an app restart.
        val rebuilt = fixtureQueue(articles, store)
        val id = rebuilt.enqueue(listOf(source)).getValue(1)
        assertEquals(body, articles.getById(id)!!.original_markdown_content)
        assertNull(articles.getById(id)!!.ai_content)
    }

    @Test fun completedArticleIsReusedWithoutNewAiWork() = withQueue { queue, articles, _, scheduled, _ ->
        val id = articles.insert(title = source.title, url = "https://v2ex.com/t/8",
            aiContent = "已有完整总结", aiMarkdownContent = "已有正文", status = "completed")
        assertEquals(mapOf(1L to id), queue.enqueue(listOf(source)))
        assertTrue(scheduled.isEmpty())
        assertEquals("已有完整总结", articles.getById(id)!!.ai_content)
    }

    @Test fun existingArticleWithOriginalButMissingSummaryUsesTheReprocessingQueue() = withQueue { queue, articles, tasks, scheduled, _ ->
        val id = articles.insert(title = source.title, url = "https://v2ex.com/t/8", status = "completed")
        articles.updateOriginalMarkdownContent(id, "作者保存的原文".repeat(20))
        assertEquals(mapOf(1L to id), queue.enqueue(listOf(source)))
        assertEquals("remote_article_reprocess", tasks.getById(scheduled.single())!!.type)
    }

    @Test fun failedProcessingIsNotAutomaticallyRestartedOnEveryVisit() = withQueue { queue, _, tasks, scheduled, _ ->
        queue.enqueue(listOf(source))
        val taskId = scheduled.single()
        assertTrue(tasks.claimForRun(taskId, "test", Long.MAX_VALUE))
        tasks.finishFailure(taskId, "fetch_failed", "正文暂不可用")
        queue.enqueue(listOf(source))
        assertEquals(listOf(taskId), scheduled)
        assertEquals("failed", tasks.getById(taskId)!!.status)
    }

    @Test fun pendingArticleWithSavedOriginalCanBeProcessedWithoutASourceUrl() = withQueue { queue, articles, tasks, scheduled, _ ->
        val id = articles.insert(title = "已有原文", url = "https://example.com/original")
        articles.updateOriginalMarkdownContent(id, "本地保存的真实正文，不应依赖列表的来源链接。".repeat(10))
        val local = source.copy(source_type = "local_favorite", source_id = id, source_url = null)
        assertEquals(mapOf(1L to id), queue.enqueue(listOf(local)))
        assertEquals("remote_article_reprocess", tasks.getById(scheduled.single())!!.type)
    }

    @Test fun backgroundRefreshQueuesOnlyTheLatestSelectedSources() = withQueue { queue, articles, tasks, scheduled, _ ->
        val summaries = UnifiedNewsSummaryRepository(database)
        listOf("2026-10-01", "2026-10-02").forEach { date ->
            summaries.saveSummaryWithSources(
                dailyUnifiedNewsWindowFor(Instant.parse("${date}T10:00:00Z"), TimeZone.UTC),
                "每日汇总", "已有内容", "success", null, null, 1,
                listOf(UnifiedNewsSourceItem("H1", UnifiedNewsSourceType.HOT_NEWS,
                    sourceUrl = "https://example.com/$date", title = "当日文章", summary = "平台摘要")))
        }
        queue.enqueueLatest()
        assertEquals(1L, articles.count())
        assertTrue(tasks.getById(scheduled.single())!!.payload_json.contains("2026-10-02"))
    }

    @Test fun invalidLinksAreNotQueuedOrSavedAsArticles() = withQueue { queue, articles, _, scheduled, _ ->
        assertTrue(queue.enqueue(listOf(source.copy(source_url = "javascript:alert(1)"),
            source.copy(id = 2, source_url = null))).isEmpty())
        assertEquals(0L, articles.count())
        assertTrue(scheduled.isEmpty())
    }

    @Test fun generatedSummaryIsObservedWithoutReloadingOrIncludingUnrelatedArticles() = withQueue { _, articles, _, _, _ ->
        runBlocking {
            val id = articles.insert(url = "https://v2ex.com/t/8")
            articles.insert(url = "https://example.com/unrelated", aiContent = "其他文章", status = "completed")
            val subscribed = CompletableDeferred<Unit>()
            val changes = async {
                articles.observeNewsSummaries(listOf(id)).take(2)
                    .onEach { subscribed.complete(Unit) }.toList()
            }
            subscribed.await()
            articles.updateStatus(id, "aiProcessing")
            articles.updateAiContent(id, "**核心内容：** 已生成的真实总结。", "阅读工具", null)
            val snapshots = changes.await()
            assertEquals(emptyMap(), snapshots.first())
            assertEquals(mapOf(id to "**核心内容：** 已生成的真实总结。"), snapshots.last())
        }
    }

    @Test fun partialAiFailureKeepsTheAlreadyGeneratedSummaryReadable() = withQueue { _, articles, _, _, _ ->
        runBlocking {
            val id = articles.insert(url = "https://example.com/partial",
                aiContent = "**核心内容：** 已完成的文章总结。", status = "error")
            assertEquals(mapOf(id to "**核心内容：** 已完成的文章总结。"),
                articles.observeNewsSummaries(listOf(id)).first())
        }
    }

    private lateinit var database: DailySatoriDatabase
    private lateinit var http: HttpClient
    private fun fixtureQueue(articles: ArticleRepository, store: HotNewsStore,
        scheduled: MutableList<Long> = mutableListOf()) = UnifiedNewsArticleQueue(
        UnifiedNewsSummaryRepository(database), articles, AsyncTaskRepository(database),
        HotNewsService(HotNewsClient(http), store, { it }), RemoteArticleSyncRepository(database), scheduled::add)

    private fun withQueue(block: (UnifiedNewsArticleQueue, ArticleRepository, AsyncTaskRepository,
        MutableList<Long>, HotNewsStore) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            database = DailySatoriDatabase(driver)
            HttpClient(MockEngine { error("Enqueue must not perform network requests") }).use { client ->
                http = client
                val articles = ArticleRepository(database)
                val tasks = AsyncTaskRepository(database)
                val store = HotNewsStore(SettingRepository(database))
                val scheduled = mutableListOf<Long>()
                block(fixtureQueue(articles, store, scheduled), articles, tasks, scheduled, store)
            }
        }
    }
}
