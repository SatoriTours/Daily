package com.dailysatori.service.opportunity

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiPurpose
import com.dailysatori.service.ai.AiRequestCoordinator
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.ai.withAiRequestSession
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verification-only end-to-end probe: real AiService + real admission gate + real analyzer.
 * Design claim: congestion is reported as deferred (nothing sent, no failure), never as NETWORK_ERROR.
 */
class NewsCongestionEndToEndTest {
    @Test
    fun congestedGateDefersInsteadOfReportingANetworkFailure() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val configs = AIConfigRepository(db)
            configs.insert("deepseek", "https://example.com/v1", "test-key", "deepseek-flash", isDefault = 1)
            configs.setPurposeConfig(AiPurpose.EXTERNAL_CONTENT, configs.getAllSync().first().id)
            val store = NewsOpportunityStore(SettingRepository(db))
            val articles = listOf(
                ReadNewsArticle(key = "a", title = "标题a", content = "正文a 内容足够长", source = "测试", readAt = 1L),
                ReadNewsArticle(key = "b", title = "标题b", content = "正文b 内容足够长", source = "测试", readAt = 1L),
            )
            val gate = AiRequestCoordinator(backgroundIntervalMs = 0)
            HttpClient(MockEngine {
                delay(20_000)
                respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { client ->
                val service = NewsOpportunityService(
                    store = store,
                    analyzer = AiNewsOpportunityAnalyzer(AiService(client, gate), AiConfigService(AIConfigRepository(db))),
                    context = object : NewsOpportunityContext {
                        override val enabled = false
                        override fun verifiedContext(): String? = null
                    },
                    candidateSource = { articles },
                    analysisTimeoutMs = 400L,
                )
                val release = CompletableDeferred<Unit>()
                val holdersStarted = CompletableDeferred<Unit>()
                var holderCount = 0
                val holders = listOf(
                    launch { withAiRequestSession { gate.withRequest("https://example.com/v1", AiPurpose.EXTERNAL_CONTENT) { holderCount++; if (holderCount == 2) holdersStarted.complete(Unit); release.await() } } },
                    launch { withAiRequestSession { gate.withRequest("https://example.com/v1", AiPurpose.INTERACTIVE) { holderCount++; if (holderCount == 2) holdersStarted.complete(Unit); release.await() } } },
                )
                holdersStarted.await()
                service.saveFocus("关注点")

                val startedAt = System.currentTimeMillis()
                val outcome = runCatching { service.analyze() }
                val elapsed = System.currentTimeMillis() - startedAt
                println("REGRESSION_NEWS_E2E elapsed=" + elapsed + " outcome=" + outcome.getOrNull() +
                    " thrown=" + outcome.exceptionOrNull()?.let { it::class.simpleName + ": " + it.message } +
                    " stateError=" + service.state.value.error)
                release.complete(Unit)
                holders.forEach { it.cancel() }
                val result = outcome.getOrNull()
                assertEquals(2, result?.deferred, "congestion must be reported as deferred: " + outcome.exceptionOrNull())
                assertEquals(0, result?.completed)
            }
        }
    }
}
