package com.dailysatori.service.articleai

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.*
import com.dailysatori.service.ai.AiHttpFailure
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.shared.db.Ai_config
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class ArticleAiQueueTest {
    @Test fun confirmedSummaryReplacesQueuedTranslationEvenAfterManualPromotion() = withFixture {
        val id = save("force", "A trusted article in English.", "这是已经核验的来源摘要。")
        queue.onNewBody(articles.getById(id)!!)
        queue.manual(id)
        queue.manual(id, ArticleAiMode.SUMMARY, redo = true)
        queue.manual(id, ArticleAiMode.SUMMARY, redo = true)
        tick()
        tick()
        assertEquals(listOf(ArticleAiMode.SUMMARY), requests.map { it.first })
    }

    @Test fun compatibleRunningIntentCoalescesAndMissingFieldFollowsOnce() = withFixture {
        val id = save("force-running", "A trusted article in English.", "这是已经核验的来源摘要。")
        queue.manual(id)
        beforeResponse = {
            queue.manual(id, ArticleAiMode.TRANSLATION, redo = true)
            queue.manual(id, ArticleAiMode.BOTH, redo = true)
            queue.manual(id, ArticleAiMode.BOTH, redo = true)
        }
        tick()
        beforeResponse = {}
        tick()
        tick()
        assertEquals(listOf(ArticleAiMode.TRANSLATION, ArticleAiMode.SUMMARY), requests.map { it.first })
    }

    @Test fun intentChangedAfterWorkerSelectedJobButBeforeRequestUsesLatestMode() = withFixture {
        val id = save("selected", "A trusted article in English.", "这是已经核验的来源摘要。")
        queue.onNewBody(articles.getById(id)!!)
        tick { queue.manual(id, ArticleAiMode.SUMMARY, redo = true) }
        assertEquals(ArticleAiMode.SUMMARY, requests.single().first)
    }

    @Test fun prioritizesManualAndKeepsOnePaidRequestAcrossSources() = withFixture {
        val history = save("history", "This history is yet to be processed.")
        val news = save("new", "This recent article is written in English.")
        val duplicate = save("dup", "This recent article is written in English.\n")
        assertEquals(2, queue.historyStatus().pending)
        assertTrue(requests.isEmpty())
        queue.startHistory()
        queue.pauseHistory()
        queue.manual(duplicate)
        tick()
        assertEquals(listOf(ArticleAiMode.TRANSLATION), requests.map { it.first })
        assertNotNull(view(news).translation)
        assertNull(view(duplicate).summary)
        tick()
        assertEquals(1, requests.size)
        queue.resumeHistory()
        tick()
        assertNotNull(view(history).translation)
        assertEquals(2, requests.size)
    }

    @Test fun explicitRequestProcessesStoredPreviewWithoutWaitingForFullBody() = withFixture {
        val id = save("preview", "A short preview paragraph in English about a release.\n\nA second preview paragraph.",
            bodyKind = ArticleBodyKind.PREVIEW)
        queue.onNewBody(articles.getById(id)!!)
        assertTrue(requests.isEmpty())
        queue.manual(id)
        tick()
        assertEquals(listOf(ArticleAiMode.TRANSLATION), requests.map { it.first })
        val result = view(id)
        assertEquals(ArticleBodyKind.PREVIEW, result.kind)
        assertNull(result.summary)
        assertNotNull(result.translation)
    }

    @Test fun refreshRepeatsRequestsForUnchangedBodyWhileAiProcessReusesResults() = withFixture {
        val id = save("redo", "A trusted article in English about reliable processing.")
        queue.manual(id)
        tick()
        assertEquals(1, requests.size)
        queue.manual(id)
        tick()
        assertEquals(1, requests.size, "AI 处理不重抓，也不应重复付费请求")
        queue.manual(id, redo = true)
        tick()
        assertEquals(2, requests.size, "刷新即使正文相同也要重新跑 AI")
    }

    @Test fun retranslationReplacesOnlyTranslationAndRetainsItAfterFailure() = withFixture {
        val id = save("retranslate", "The library is open today. ".repeat(40))
        queue.manual(id)
        tick()
        val started = view(id)
        assertNotNull(started.translation)
        translated = "重新生成的中文译文。"
        queue.manual(id, ArticleAiMode.TRANSLATION, redo = true)
        tick()
        assertEquals(ArticleAiMode.TRANSLATION, requests.last().first)
        assertEquals(started.summary, view(id).summary)
        val replacement = assertNotNull(view(id).translation)
        assertNotEquals(started.translation, replacement)
        assertTrue(replacement.split("\n\n").all { it == translated })
        beforeResponse = { error("fixture failure") }
        queue.manual(id, ArticleAiMode.TRANSLATION, redo = true)
        tick()
        assertEquals("failed", view(id).jobState)
        assertEquals(replacement, view(id).translation)
        assertEquals(started.summary, view(id).summary)
    }

    @Test fun versionChangeWhileRemoteRunsFencesTheResult() = withFixture {
        val id = save("moving", "Original English news from yesterday.")
        queue.manual(id)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        beforeResponse = {
            started.complete(Unit)
            finish.await()
        }
        kotlinx.coroutines.coroutineScope {
            val worker = async { tick() }
            started.await()
            assertEquals("running", view(id).jobState)
            queue.manual(id)
            save("moving", "Completely different article after publication.")
            finish.complete(Unit)
            worker.await()
        }
        assertEquals(1, requests.size)
        assertNull(view(id).translation)
        save("another", "Original English news from yesterday.")
        assertNull(view(articles.getByUrl("https://example.com/another")!!.id).translation)
    }

    @Test fun englishParagraphAfterChineseIntroductionAutomaticallyGetsTranslationOnly() = withFixture {
        val chinese = "这是市立图书馆的简体中文导读，内容概括了馆方长期提供的借阅和阅览服务。".repeat(6)
        val english = "The library reopens on May 13. The children's room is on the second floor."
        val id = save("bilingual", "$chinese\n\n$english", "市立图书馆即将重新开放。")
        queue.onNewBody(articles.getById(id)!!)
        tick()
        assertEquals(ArticleAiMode.TRANSLATION, requests.single().first)
        assertEquals("市立图书馆即将重新开放。", view(id).summary)
        assertEquals("中文翻译", view(id).translation)
        assertTrue(view(id).original.orEmpty().endsWith(english))
        queue.onNewBody(articles.getById(id)!!)
        tick()
        assertEquals(1, requests.size)
    }

    @Test fun verifiedUpstreamOnlyRequestsTranslationAndFailedRedoKeepsIt() = withFixture {
        val id = save("upstream", "A trusted article in English.", "这是已经核验的来源摘要。")
        queue.manual(id)
        tick()
        assertEquals(ArticleAiMode.TRANSLATION, requests.single().first)
        assertTrue(view(id).sourceSummary)
        val translation = view(id).translation
        beforeResponse = { throw AiHttpFailure(429) }
        queue.manual(id, ArticleAiMode.BOTH, redo = true)
        tick()
        assertEquals("failed", view(id).jobState)
        assertEquals("http_429", view(id).blocked)
        val next = save("next", "Another English news article.")
        queue.manual(next)
        tick()
        assertEquals(2, requests.size)
        assertEquals(translation, view(id).translation)
    }

    @Test fun neverRetriesAmbiguousTransportFailureButRetries503Once() = withFixture {
        val id = save("retry", "Some brief news in English.")
        queue.manual(id)
        beforeResponse = { throw AiHttpFailure(503) }
        tick()
        assertEquals("queued", view(id).jobState)
        tick()
        assertEquals(1, requests.size)
        clock += 31_000
        beforeResponse = { error("simulated timeout after sending") }
        tick()
        assertEquals("failed", view(id).jobState)
        clock += 3_600_000
        queue.onNewBody(articles.getById(id)!!)
        tick()
        assertEquals(2, requests.size)
    }

    @Test fun upgradeCreatesPassiveTablesAndPreservesOriginal() = withFixture {
        val id = save("old", "Old article body.")
        listOf("attempt", "job", "result", "source").forEach { driver.execute(null, "DROP TABLE article_ai_$it", 0) }
        driver.execute(null, "ALTER TABLE remote_feed_staged_article DROP COLUMN body_kind", 0)
        driver.execute(null, "ALTER TABLE remote_feed_staged_article DROP COLUMN summary_language", 0)
        driver.execute(null, "ALTER TABLE remote_article_sync_item DROP COLUMN ai_feed_admission", 0)
        settings.upsert(SettingKeys.schemaVersion, "52")
        DatabaseMigration(driver, settings).runMigrations()
        assertEquals(DatabaseConfig.currentSchemaVersion.toString(), settings.get(SettingKeys.schemaVersion))
        assertEquals("Old article body.", articles.getById(id)!!.reusableOriginalBody())
        assertFalse(queue.historyStatus().running)
        assertEquals(0, queue.historyStatus().waiting)
        assertTrue(requests.isEmpty())
    }

    @Test fun v53KeepsExistingMappingHistoricalAndDoesNotDiscardCachedContent() = withFixture {
        val id = save("installed", "A prior English article with already verified content.")
        queue.manual(id)
        tick()
        val oldTranslation = view(id).translation
        val source = RemoteNewsSourceRepository(db)
        source.save(null, "Fixture", "https://example.org/feed", "", enabled = true)
        val remoteId = source.getAll().single().id
        val mappings = RemoteArticleSyncRepository(db)
        mappings.upsertMapping(remoteId, 73, id, "https://example.com/installed", "2026-11-15", 1L)
        driver.execute(null, "ALTER TABLE remote_article_sync_item DROP COLUMN ai_feed_admission", 0)
        settings.upsert(SettingKeys.schemaVersion, "53")
        DatabaseMigration(driver, settings).runMigrations()
        assertEquals("historical", mappings.findByRemoteIdentity(remoteId, 73)?.ai_feed_admission)
        assertEquals(oldTranslation, view(id).translation)
        assertEquals(1, requests.size)
    }

    @Test fun overBudgetBodyTranslatesEverySectionBeforePublishing() = withFixture {
        val body = "A complete sentence in a long article. ".repeat(600).trim()
        val id = save("oversize", body)
        queue.manual(id, ArticleAiMode.BOTH, redo = true)
        tick()
        val translated = requests.filter { it.first.translation }.map { it.second }
        assertTrue(translated.size > 1)
        assertEquals(body, translated.joinToString(" ") { it.body })
        val result = view(id)
        assertEquals(body, result.original)
        assertFalse(result.summaryPartial)
        assertFalse(result.translationPartial)
        assertEquals(translated.size, result.translation.orEmpty().split("\n\n").size)
    }

    @Test fun requiresStopAndAllFieldsAndKeepsUnknownUsageNull() {
        val payload = buildJsonObject { put("summary", "中文概要"); put("translation", "中文译文") }.toString()
        val output = parseArticleAiResponse(response("stop", payload), ArticleAiMode.BOTH, 100)
        assertEquals(ArticleAiUsage(), output.usage)
        assertFailsWith<InvalidArticleAiOutput> { parseArticleAiResponse(response("length", payload), ArticleAiMode.BOTH, 100) }
        assertFailsWith<InvalidArticleAiOutput> { parseArticleAiResponse(response("stop", "{\"summary\":\"中文\"}"), ArticleAiMode.BOTH, 100) }
    }

    private fun response(finish: String, content: String) = buildJsonObject {
        putJsonArray("choices") { addJsonObject {
            put("finish_reason", finish)
            putJsonObject("message") { put("content", content) }
        } }
    }

    private fun withFixture(block: suspend Fixture.() -> Unit) = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            Fixture(driver).block()
        }
    }

    private class Fixture(val driver: JdbcSqliteDriver) {
        val db = DailySatoriDatabase(driver)
        val settings = SettingRepository(db)
        val articles = ArticleRepository(db)
        private val repo = ArticleAiRepository(db, settings)
        val requests = mutableListOf<Pair<ArticleAiMode, ArticleAiInput>>()
        var beforeResponse: suspend () -> Unit = {}
        var translated = "中文翻译"
        var clock = 100L
        private val remote = object : ArticleAiRemote {
            override fun config() = Ai_config(1, "openai", "https://example.com", "fixture", "test", 1, 0, 0)
            override suspend fun request(config: Ai_config, mode: ArticleAiMode, input: ArticleAiInput): ArticleAiOutput {
                requests += mode to input
                beforeResponse()
                return ArticleAiOutput("中文摘要".takeIf { mode.summary }, translated.takeIf { mode.translation },
                    ArticleAiUsage(20, 10, null))
            }
        }
        val queue = ArticleAiQueue(repo, articles, AsyncTaskRepository(db), remote, {}, { clock })

        fun save(
            key: String,
            body: String,
            summary: String? = null,
            bodyKind: ArticleBodyKind = ArticleBodyKind.BODY,
        ): Long {
            val remote = RemoteArticle(1, "Title", "https://example.com/$key", summary,
                content = body, bodyKind = bodyKind,
                summaryLanguage = if (summary != null) "zh-CN" else null)
            val id = articles.saveRemoteArticleForSync(remote)!!.article.id
            repo.record(articles.getById(id)!!, remote)
            return id
        }

        fun view(id: Long) = repo.view(articles.getById(id)!!)
        suspend fun tick(beforeClaim: () -> Unit = {}) = queue.execute(1, "{}", "{}", object : AsyncTaskProgressReporter {
            override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) {
                if (current == 0L) beforeClaim()
            }
        })
    }
}
