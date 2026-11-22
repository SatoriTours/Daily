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
    @Test fun shortEnglishOnlyTranslatesAndLongEnglishAlsoSummarizes() = runBlocking<Unit> {
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
                assertNull(analyzeArticleBody("短中文正文不需要调用模型。").automatic(false))

                val english = """
                    The library will reopen on May 13 after repairs. Readers can keep their current cards.

                    The reading room opens at 9 am. Check the [library website](https://example.com/library).

                    The sample configuration is `max_books = 5`. The children's room is on the second floor.
                """.trimIndent()
                val short = assertNotNull(analyzeArticleBody(english).automatic(false))
                assertEquals(ArticleAiMode.TRANSLATION, short)
                val result = request(service, config, short, english)
                assertNull(result.summary)
                val translation = assertNotNull(result.translation)
                assertTrue(translation.contains("https://example.com/library") && translation.contains("max_books = 5"))
                assertTrue(translation.contains("儿童"))

                val longEnglish = english + "\n\n" + """
                    The city council approved a renovation plan after public consultation. Staff will offer guided tours
                    of the restored building during the first month. The basement now houses the local history collection,
                    including maps and photographs donated by residents. Visitors can request these materials at the desk.
                    An elevator connects all three floors, and a new ramp provides step-free access from the main street.
                    The public computers remain free for library card holders. Visitors without cards can request a temporary
                    pass, subject to availability. A quiet study area has been added next to the reading room, and five
                    individual desks can be booked through the library's website. Group study rooms remain on the ground floor.
                    The outdoor reading garden will open in June, after workers finish the new path. A volunteer team has planted
                    native flowers near the entrance. A separate return box beside the entrance will remain open outside regular hours.
                    The library will publish the schedule for summer workshops later this month.
                """.trimIndent()
                val both = assertNotNull(analyzeArticleBody(longEnglish).automatic(false))
                assertEquals(ArticleAiMode.BOTH, both)
                val longResult = request(service, config, both, longEnglish)
                val summary = assertNotNull(longResult.summary)
                assertTrue(summary.contains("图书馆"))
                assertFalse(summary.contains("\n") || summary.contains("为什么重要") || summary.contains("技术决策"))
                val fullText = assertNotNull(longResult.translation)
                assertTrue(fullText.contains("志愿") && fullText.contains("工作坊"),
                    "Full translation must retain details near the end of the source")
            }
        }
    }

    private suspend fun request(service: ArticleAiRemote, config: Ai_config, mode: ArticleAiMode, body: String): ArticleAiOutput {
        val limits = articleModelLimits(config.model_name)
        val inputs = assertNotNull(articleReadingInputs(body, mode, limits))
        // Do not emit the provider error or HTTP payload to the test report.
        return try {
            requestFullArticleReading(limits, inputs) { sectionMode, input -> service.request(config, sectionMode, input) }
        } catch (error: Exception) {
            val status = (error as? com.dailysatori.service.ai.AiHttpFailure)?.status
            error("Article AI live request failed: ${error::class.simpleName}, HTTP=${status ?: "unknown"}")
        }
    }
}
