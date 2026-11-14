package com.dailysatori.service.hotnews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.unifiednews.*
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import kotlin.test.*

class HotNewsDailyGenerationTest {
    @Test fun dailyGenerationIncludesAllAvailableCorePlatformsAndExcludesDisabledSourcesOnRegeneration() =
        withGenerator("## 今日要点\n- 多个平台讨论资料导出工具。[H1][F1]") { fixture ->
            assertTrue(fixture.service.generateDaily().success)
            val first = fixture.summaries.getLatestSuccessful()!!
            val sources = fixture.summaries.getSources(first.id)
            assertEquals(HotNewsPlatform.entries.filter { it.core }.map { it.key }.toSet(), sources
                .filter { it.source_type == "hot_news" }.map { it.source_filename!!.substringBefore(':') }.toSet())
            assertTrue(fixture.prompts.single().contains("github 的来源正文"))
            assertTrue(fixture.prompts.single().contains("linuxdo 的来源正文"))
            fixture.hotNews.configure(HotNewsPlatform.V2EX, false, HotNewsPlatform.V2EX.defaultFeedUrl)
            assertTrue(fixture.service.generateDaily(force = true).success)
            assertFalse(fixture.summaries.getSources(first.id).any { it.source_filename?.startsWith("v2ex:") == true })
            assertFalse(fixture.prompts.last().contains("v2ex 的来源正文"))
            assertEquals(2, fixture.prompts.size)
        }

    @Test fun dailyGenerationRejectsAiOutputThatOmitsAvailableHotContent() =
        withGenerator("## 今日要点\n- 收藏文章介绍了资料导出流程。[F1]") { fixture ->
            val result = fixture.service.generateDaily()
            assertFalse(result.success)
            assertEquals(UnifiedNewsSummaryStatus.FAILED, result.status)
            assertTrue(result.message.orEmpty().contains("热点"))
            assertNull(fixture.summaries.getLatestSuccessful())
        }

    @Test fun dailyGenerationStillSucceedsWithOnlyFavoritesWhenAllCorePlatformsAreDisabled() =
        withGenerator("## 今日要点\n- 收藏文章介绍了资料导出流程。[F1]") { fixture ->
            HotNewsPlatform.entries.filter { it.core }.forEach { platform ->
                fixture.hotNews.configure(platform, false, fixture.hotNews.preferences.value.source(platform).feedUrl)
            }
            assertTrue(fixture.service.generateDaily().success)
            assertTrue(fixture.summaries.getSources(fixture.summaries.getLatestSuccessful()!!.id)
                .all { it.source_type == "local_favorite" })
        }

    @Test fun dailyGenerationMarksRateLimitRetryable() =
        withGenerator("", HttpStatusCode.TooManyRequests) { fixture ->
            val result = fixture.service.generateDaily()
            assertFalse(result.success)
            assertEquals(true, result.retryable)
        }

    @Test fun dailyGenerationKeepsAuthenticationFailurePermanent() =
        withGenerator("", HttpStatusCode.Unauthorized) { fixture ->
            val result = fixture.service.generateDaily()
            assertFalse(result.success)
            assertEquals(false, result.retryable)
        }

    @Test fun preservedContentStillReportsRateLimitAsRetryable() =
        withGenerator("", HttpStatusCode.TooManyRequests) { fixture ->
            seedExistingContent(fixture)
            val result = fixture.service.generateDaily(force = true)
            assertFalse(result.success)
            assertEquals(true, result.retryable)
            val preserved = fixture.summaries.getByWindow(
                dailyUnifiedNewsWindowFor().summaryDate, UnifiedNewsWindowKey.DAILY.value)
            assertEquals("# 旧的中文汇总内容", preserved?.content)
        }

    @Test fun preservedContentStillReportsAuthenticationFailureAsPermanent() =
        withGenerator("", HttpStatusCode.Unauthorized) { fixture ->
            seedExistingContent(fixture)
            val result = fixture.service.generateDaily(force = true)
            assertFalse(result.success)
            assertEquals(false, result.retryable)
        }

    @Test fun anInnerAiRequestTimeoutIsExplainedAsRetryableWhileTheRunIsStillActive() =
        withGenerator("", cancelWithTimeout = true) { fixture ->
            val result = fixture.service.generateDaily()

            assertFalse(result.success)
            assertTrue(result.message!!.contains("超时"), "an inner timeout must be explained: " + result.message)
            assertEquals(true, result.retryable, "a request timeout is retryable")
            assertFalse(result.deferred)
        }

    @Test fun aRealParentCancellationIsStillPropagated() {
        withGenerator("", engineHangs = true) { fixture ->
            val scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.currentCoroutineContext() + kotlinx.coroutines.Job(),
            )
            var cancelled = false
            val job = scope.launch {
                try { fixture.service.generateDaily() } catch (e: CancellationException) { cancelled = true; throw e }
            }
            kotlinx.coroutines.delay(200)
            job.cancelAndJoin()
            assertTrue(cancelled, "a system cancellation must reach the AI call, not be reported as a timeout")
        }
    }

    @Test fun aCongestedGateDefersAtThePathAdmissionCapBeforeTheOuterBudget() {
        val gate = com.dailysatori.service.ai.AiRequestCoordinator(backgroundIntervalMs = 0)
        withGenerator("## 今日要点\n- 多个平台讨论资料导出工具。[H1][F1]", gate = gate) { fixture ->
            val release = CompletableDeferred<Unit>()
            val held = CompletableDeferred<Unit>()
            var holders = 0
            val scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.currentCoroutineContext() + kotlinx.coroutines.Job(),
            )
            listOf(
                scope.launch { gate.withRequest("https://ai.example/v1", com.dailysatori.service.ai.AiPurpose.EXTERNAL_CONTENT) {
                    holders++; if (holders == 2) held.complete(Unit); release.await() } },
                scope.launch { gate.withRequest("https://ai.example/v1", com.dailysatori.service.ai.AiPurpose.INTERACTIVE) {
                    holders++; if (holders == 2) held.complete(Unit); release.await() } },
            )
            held.await()
            try {
                val began = System.currentTimeMillis()
                // budget 900ms => reserve 300ms, cap 15s is tighter than the 600ms left: congestion defers,
                // it never becomes the outer "AI 生成超时" failure.
                val result = fixture.service.generateDaily(force = true, aiSummaryBudgetMs = 900L)
                val elapsed = System.currentTimeMillis() - began

                assertTrue(result.deferred, "an unsent generation is a deferral: " + result.message)
                assertTrue(result.message!!.contains("AI 服务繁忙"), "the reason names congestion: " + result.message)
                assertTrue(elapsed < 900, "the deferral happens before the outer budget: " + elapsed + "ms")
            } finally {
                release.complete(Unit)
                scope.cancel()
            }
        }
    }

    @Test fun aBudgetSmallerThanTheSpentCollectionTimeDefersInsteadOfFailing() =
        withGenerator("## 今日要点\n- 多个平台讨论资料导出工具。[H1][F1]") { fixture ->
            // The budget is already consumed before the AI stage: nothing is sent, nothing fails.
            val result = fixture.service.generateDaily(force = true, aiSummaryBudgetMs = 1L)

            assertTrue(result.deferred, "an unsent generation never fails: " + result.message)
            assertTrue(result.message!!.contains("预算"), "the reason names the budget: " + result.message)
            assertNull(fixture.summaries.getLatestSuccessful(), "nothing is written for a deferral")
        }

    @Test fun aFailingStageSinkNeverChangesTheBusinessResult() =
        withGenerator("## 今日要点\n- 多个平台讨论资料导出工具。[H1][F1]") { fixture ->
            // Observability fails (for example a locked database while writing progress): the summary must
            // still be generated and persisted, and the next run must still get AI permits (no leak).
            val failing: suspend (String) -> Unit = { throw java.sql.SQLException("database is locked") }

            val first = fixture.service.generateDaily(force = true, onStage = failing)

            assertTrue(first.success, "a failing stage sink must not change the result: " + first.message)
            assertNotNull(fixture.summaries.getLatestSuccessful())
            val second = fixture.service.generateDaily(force = true, onStage = { })
            assertTrue(second.success, "a following run still completes: " + second.message)
        }

    @Test fun aCancellingStageSinkStillAbortsAndPersistsNothing() =
        withGenerator("## 今日要点\n- 多个平台讨论资料导出工具。[H1][F1]") { fixture ->
            val cancelled: suspend (String) -> Unit = { throw CancellationException("lease lost") }

            assertFailsWith<CancellationException> {
                fixture.service.generateDaily(force = true, onStage = cancelled)
            }
            assertNull(fixture.summaries.getLatestSuccessful(), "a cancelled run must not persist a summary")
        }

    private class LeaseLost : CancellationException("lease lost at permit")

    @Test fun aCancellingSinkAtPermitCancelsTheRunningAiRequest() {
        val gate = com.dailysatori.service.ai.AiRequestCoordinator(backgroundIntervalMs = 0)
        withGenerator("", engineHangs = true, gate = gate) { fixture ->
            var permitSeen = false
            val leaseLost: suspend (String) -> Unit = { stage ->
                if (stage == "ai_permit") {
                    permitSeen = true
                    throw LeaseLost()
                }
                if (stage == "ai_requesting") kotlinx.coroutines.delay(10_000)
            }
            val thrown = withTimeoutOrNull(10_000) {
                runCatching { fixture.service.generateDaily(force = true, onStage = leaseLost) }.exceptionOrNull()
            }
            assertTrue(permitSeen, "the permit stage was actually reported")
            // guard is true here: only a real abort can stop the write, not a lease check.
            assertIs<LeaseLost>(thrown, "the original cancellation must survive, not become an AI failure: " + thrown)
            assertNull(fixture.summaries.getLatestSuccessful(), "a cancelled run persists nothing")

            // The AI request was really cancelled and gave its permit back: the next run reaches the request
            // itself (a bounded AI timeout), not the admission queue.
            val next = fixture.service.generateDaily(force = true, aiSummaryBudgetMs = 2_000L, onStage = { })
            assertFalse(next.deferred, "the permit was released, so this run is not queued: " + next.message)
            assertTrue(next.message.orEmpty().contains("超时"), "the next run fails at the request: " + next.message)
        }
    }

    @Test fun aiFailureDetailsWithTokensNeverReachTheMessage() =
        withGenerator("", engineThrowsDetail = true) { fixture ->
            val result = fixture.service.generateDaily(force = true)

            assertFalse(result.success)
            val message = result.message.orEmpty()
            listOf("sk-secret", "token=", "ai.example/v1?token").forEach {
                assertFalse(message.contains(it), "leaked into the result message: " + it + " -> " + message)
            }
            assertTrue(message.contains("AI"), "a fixed classification is still shown: " + message)
        }

    @Test fun aCongestedGateStillReportsCollectionDoneAndDefersWithoutFailing() {
        val gate = com.dailysatori.service.ai.AiRequestCoordinator(backgroundIntervalMs = 0)
        withGenerator("## 今日要点\n- 多个平台讨论资料导出工具。[H1][F1]", gate = gate) { fixture ->
            val release = CompletableDeferred<Unit>()
            val held = CompletableDeferred<Unit>()
            var holders = 0
            val scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.currentCoroutineContext() + kotlinx.coroutines.Job(),
            )
            listOf(
                scope.launch { gate.withRequest("https://ai.example/v1", com.dailysatori.service.ai.AiPurpose.EXTERNAL_CONTENT) {
                    holders++; if (holders == 2) held.complete(Unit); release.await() } },
                scope.launch { gate.withRequest("https://ai.example/v1", com.dailysatori.service.ai.AiPurpose.INTERACTIVE) {
                    holders++; if (holders == 2) held.complete(Unit); release.await() } },
            )
            held.await()
            val stageLog = mutableListOf<String>()
            try {
                // Short injectable budget: the admission reserve is the existing min(budget/3, 15s).
                val first = fixture.service.generateDaily(force = true, aiSummaryBudgetMs = 400L) { stageLog += it }

                assertEquals(listOf("collected", "ai_waiting"), stageLog.take(2), "collection is reported first")
                assertFalse(first.success)
                assertTrue(first.deferred, "congestion is a deferral, not a failure")
                assertEquals(true, first.retryable)
                assertTrue(first.message.orEmpty().contains("AI 服务繁忙"))
                assertNull(fixture.summaries.getLatestSuccessful(), "a deferral never writes a summary")
            } finally {
                release.complete(Unit)
                scope.cancel()
            }
            // Freed gate: the same generation completes and really persists.
            val second = fixture.service.generateDaily(force = true, aiSummaryBudgetMs = 30_000L) { stageLog += it }
            assertTrue(second.success, "a frees gate completes: " + second.message)
            assertNotNull(fixture.summaries.getLatestSuccessful())
            // The real gate emits its own lifecycle: queued -> permit -> requesting -> received, then stages.
            listOf("ai_queued", "ai_permit", "ai_requesting", "ai_received", "validated", "persisted").forEach {
                assertTrue(stageLog.contains(it), "missing AI/stage event: " + it + " in " + stageLog)
            }
            assertTrue(stageLog.indexOf("ai_permit") < stageLog.indexOf("ai_requesting"),
                "a permit is reported before the request starts: " + stageLog)
        }
    }

    @Test fun aMissingAiConfigurationFailsPermanentlyInsteadOfDeferring() =
        withGenerator("## 今日要点\n- 多个平台讨论资料导出工具。[H1][F1]", withAiConfig = false) { fixture ->
            val result = fixture.service.generateDaily(force = true)

            assertFalse(result.success)
            assertFalse(result.deferred)
            assertEquals(false, result.retryable, "a permanent configuration error is not retried forever")
            assertTrue(result.message.orEmpty().contains("配置"))
        }

    @Test fun aSupersededRunCannotWriteItsLateResult() =
        withGenerator("## 今日要点\n- 多个平台讨论资料导出工具。[H1][F1]") { fixture ->
            // The old execution lost its lease after a newer window superseded it: the AI call may
            // finish, but the result must never be persisted over the newer window's summary.
            val result = fixture.service.generateDaily(force = true, persistGuard = { false })

            assertFalse(result.success)
            assertTrue(result.message!!.contains("已被更新的新闻窗口取代"))
            assertNull(fixture.summaries.getByWindow(dailyUnifiedNewsWindowFor().summaryDate,
                UnifiedNewsWindowKey.DAILY.value))
            assertEquals(1, fixture.prompts.size)
        }

    private fun seedExistingContent(fixture: Fixture) {
        fixture.summaries.upsertSummary(
            window = dailyUnifiedNewsWindowFor(),
            title = "旧中文汇总",
            content = "# 旧的中文汇总内容",
            status = UnifiedNewsSummaryStatus.SUCCESS.value,
            errorMessage = null,
            sourceWarnings = null,
            generatedAt = 1L,
        )
    }

    private data class Fixture(val service: UnifiedNewsSummaryService, val hotNews: HotNewsService,
        val summaries: UnifiedNewsSummaryRepository, val prompts: List<String>)

    private fun withGenerator(
        response: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        cancelWithTimeout: Boolean = false,
        engineHangs: Boolean = false,
        engineThrowsDetail: Boolean = false,
        gate: com.dailysatori.service.ai.AiRequestCoordinator = com.dailysatori.service.ai.AiRequestCoordinator(),
        withAiConfig: Boolean = true,
        block: suspend (Fixture) -> Unit,
    ) = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val store = HotNewsStore(SettingRepository(db))
            val now = Clock.System.now().toEpochMilliseconds()
            HotNewsPlatform.entries.filter { it.core }.forEach { platform ->
                store.save(HotNewsBoard(platform, listOf(HotNewsItem("1", platform, 1,
                    "${platform.displayName} 资料导出工具", "https://example.com/${platform.key}/1",
                    originalContent = "${platform.key} 的来源正文介绍了将阅读资料保存在本地，并导出为 Markdown 文档的方法。".repeat(2))), now))
            }
            val articles = ArticleRepository(db)
            articles.insert(title = "收藏文章的资料整理方法", url = "https://example.com/favorite", isFavorite = 1,
                aiContent = "收藏文章介绍了如何整理个人阅读资料，并导出为 Markdown 方便在不同工具之间移动。".repeat(2))
            val configs = AIConfigRepository(db)
            if (withAiConfig) {
                configs.insert("openai", "https://ai.example/v1", "test-token", "test-model", isDefault = 1)
            }
            val prompts = mutableListOf<String>()
            HttpClient(MockEngine { request ->
                if (engineThrowsDetail) {
                    throw kotlinx.io.IOException("connect failed https://ai.example/v1?token=sk-secret body=正文不应出现")
                }
                if (engineHangs) kotlinx.coroutines.awaitCancellation()
                if (cancelWithTimeout) withTimeout(1) { delay(1_000) }
                assertEquals("ai.example", request.url.host, "Fresh cached or disabled sources must not fetch")
                val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                prompts += body["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonPrimitive.content
                respond(buildJsonObject { putJsonArray("choices") { addJsonObject {
                    putJsonObject("message") { put("content", response) }; put("finish_reason", "stop")
                } } }.toString(), status)
            }).use { http ->
                val hotNews = HotNewsService(HotNewsClient(http), store, { error("Cached Chinese text must not translate") })
                val summaries = UnifiedNewsSummaryRepository(db)
                val service = UnifiedNewsSummaryService(AiService(http, gate), AiConfigService(configs), articles,
                    RemoteNewsSourceRepository(db), RemoteArticleSyncRepository(db), summaries, hotNews)
                block(Fixture(service, hotNews, summaries, prompts))
            }
        }
    }
}
