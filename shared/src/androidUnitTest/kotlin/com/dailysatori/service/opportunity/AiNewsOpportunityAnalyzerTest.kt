package com.dailysatori.service.opportunity

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class AiNewsOpportunityAnalyzerTest {
    @Test
    fun translatedQuoteGetsOneCorrectionRequestUsingOriginalBody() = runBlocking {
        withAnalyzer(listOf(response("团队需要重试失败的任务。"), response("Teams need to retry failed jobs."))) { analyzer, requests ->
            assertEquals("Teams need to retry failed jobs.", analyzer.analyze(input)?.quote)
            assertEquals(2, requests.size)
            val repair = Json.parseToJsonElement(requests.last()["messages"]!!.jsonArray.last()
                .jsonObject["content"]!!.jsonPrimitive.content).jsonObject
            assertEquals(input.article.content, repair["articleBody"]!!.jsonPrimitive.content)
            assertEquals("团队需要重试失败的任务。", repair["invalidQuote"]!!.jsonPrimitive.content)
            assertTrue(repair["quoteCorrection"]!!.jsonPrimitive.content.isNotBlank())
        }
    }

    @Test
    fun whitespaceOnlyDifferenceReturnsOriginalWithoutAnotherRequest() = runBlocking {
        withAnalyzer(listOf(response("Teams need to retry failed jobs."))) { analyzer, requests ->
            val article = input.article.copy(content = "Teams  need to\nretry failed jobs.")
            assertEquals(article.content, analyzer.analyze(input.copy(article = article))?.quote)
            assertEquals(1, requests.size)
        }
    }

    @Test
    fun twoInvalidQuotesFailWithoutUnboundedRetry() = runBlocking {
        withAnalyzer(listOf(response("invented evidence"), response("still invented"))) { analyzer, requests ->
            val failure = assertFailsWith<NewsOpportunityAnalysisException> { analyzer.analyze(input) }
            assertEquals(OpportunityFailureReason.INVALID_QUOTE, failure.reason)
            assertEquals(2, requests.size)
        }
    }

    @Test
    fun correctionCanWithdrawUnsupportedOpportunity() = runBlocking {
        withAnalyzer(listOf(response("invented evidence"), """{"hasOpportunity":false}""")) { analyzer, _ ->
            assertNull(analyzer.analyze(input))
        }
    }

    @Test
    fun malformedResponseIsNotRetriedAsQuoteFailure() = runBlocking {
        withAnalyzer(listOf("{}")) { analyzer, requests ->
            val failure = assertFailsWith<NewsOpportunityAnalysisException> { analyzer.analyze(input) }
            assertEquals(OpportunityFailureReason.INVALID_RESPONSE, failure.reason)
            assertEquals(1, requests.size)
        }
    }

    private suspend fun withAnalyzer(
        responses: List<String>,
        test: suspend (AiNewsOpportunityAnalyzer, List<JsonObject>) -> Unit,
    ) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val configs = AIConfigRepository(DailySatoriDatabase(driver))
            configs.insert("openai", "https://example.com/v1", "test-token", "test-model", isDefault = 1)
            val requests = mutableListOf<JsonObject>()
            HttpClient(MockEngine { request ->
                requests += Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                val content = responses.getOrNull(requests.lastIndex) ?: throw CancellationException("Unexpected extra request")
                respond(buildJsonObject {
                    put("choices", buildJsonArray { add(buildJsonObject {
                        put("message", buildJsonObject { put("content", content) })
                    }) })
                }.toString())
            }).use { client -> test(AiNewsOpportunityAnalyzer(AiService(client), AiConfigService(configs)), requests) }
        }
    }

    private fun response(quote: String) = buildJsonObject {
        put("hasOpportunity", true)
        put("productIdea", "Failed job tracker")
        put("targetUser", "Small teams")
        put("userProblem", "Manual checks take time")
        put("category", "Developer tools")
        put("fact", "Jobs need retries")
        put("relevance", "Matches developer tools focus (inference)")
        put("mvp", "List failed jobs and send alerts")
        put("caveat", "Verify access to job status")
        put("quote", quote)
    }.toString()

    private val input = OpportunityAnalysisInput(
        ReadNewsArticle("news:1", "Job failures", "Teams need to retry failed jobs.", source = "Example", readAt = 0),
        "Developer tools", null,
    )

    private object PlainCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }
}
