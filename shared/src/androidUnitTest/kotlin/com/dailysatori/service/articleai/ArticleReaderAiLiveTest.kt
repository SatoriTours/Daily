package com.dailysatori.service.articleai

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.shared.db.Ai_config
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import kotlin.test.*

class ArticleReaderAiLiveTest {
    @Test fun ChineseSummaryAndOneCombinedRequestPreservingSourceDetails() = runBlocking<Unit> {
        assumeTrue(System.getenv("DAILY_AI_LIVE_TEST") == "1")
        val file = File("../.local/ai-test.json")
        require(file.isFile) { "Missing local AI test configuration" }
        val local = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }
            .getOrElse { error("Invalid local AI test configuration") }
        fun required(key: String) = local[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            ?: error("Missing AI test setting: $key")
        val config = Ai_config(1, required("provider"), required("apiAddress"), required("apiToken"), required("modelName"), 1, 0, 0)
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            HttpClient(OkHttp) { install(HttpTimeout) { connectTimeoutMillis = 12_000; requestTimeoutMillis = 45_000 } }.use { client ->
                val service = ConfiguredArticleAiRemote(AiConfigService(AIConfigRepository(DailySatoriDatabase(driver))), AiService(client))
                val chinese = """
                    市图书馆将于五月十三日恢复开放。施工期间，馆方完成了馆内照明系统检修，更新了阅读区的桌椅，并扩大了无障碍通道。旧借书证仍然可以使用，不需要更换。
                    周末开放时间为上午九时至晚上六时，周一继续闭馆。阅览室可以直接入内，团体参观需要提前预约。儿童活动场次在官网公布，工作人员提醒读者带好借书证。
                """.trimIndent()
                val zh = assertNotNull(analyzeArticleBody(chinese).automatic(false))
                assertEquals(ArticleAiMode.SUMMARY, zh)
                val summary = assertNotNull(request(service, config, zh, chinese).summary)
                assertTrue(summary.length < chinese.length && summary.contains("图书馆"))

                val english = """
                    The library will reopen on May 13 after repairs. Readers can keep their current cards.

                    The reading room opens at 9 am. Check the [library website](https://example.com/library).

                    The sample configuration is `max_books = 5`. The children's room is on the second floor.
                """.trimIndent()
                val both = assertNotNull(analyzeArticleBody(english).automatic(false))
                assertEquals(ArticleAiMode.BOTH, both)
                val result = request(service, config, both, english)
                assertTrue(assertNotNull(result.summary).contains("图书馆"))
                val translation = assertNotNull(result.translation)
                assertTrue(translation.contains("https://example.com/library") && translation.contains("max_books = 5"))
                assertTrue(translation.contains("儿童"))
            }
        }
    }

    private suspend fun request(service: ArticleAiRemote, config: Ai_config, mode: ArticleAiMode, body: String): ArticleAiOutput {
        val input = assertNotNull(articleAiInput(body, mode, articleModelLimits(config.model_name)))
        // Do not emit the provider error or HTTP payload to the test report.
        return try {
            service.request(config, mode, input)
        } catch (error: Exception) {
            val status = (error as? com.dailysatori.service.ai.AiHttpFailure)?.status
            error("Article AI live request failed: ${error::class.simpleName}, HTTP=${status ?: "unknown"}")
        }
    }
}
