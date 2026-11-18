package com.dailysatori.service.articleai

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.shared.db.Ai_config
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class ArticleAiOutputValidationTest {
    private val source = """
        The public library reopens after a complete electrical renovation.

        Visit the [library website](https://example.org/library). Use `max_books = 5`.

        On weekends, the children's reading room will be open to the public.

        Set the firmware configuration to version 1.23 before trying this feature.
    """.trimIndent()
    private val complete = """
        电气系统翻修完成后，公共图书馆重新开放。

        请访问[图书馆网站](https://example.org/library)，并使用`max_books = 5`。

        周末，儿童阅览室对公众开放。

        尝试使用这项功能前，请先将固件配置设为 1.23 版。
    """.trimIndent()

    @Test fun rejectsMissingLinkCodeAndVersion() = runBlocking<Unit> {
        for (bad in listOf(complete.replace("https://example.org/library", "https://elsewhere.org/"),
            complete.replace("max_books = 5", "max_books = 2"), complete.replace("1.23", "1.24"))) {
            assertFailsWith<InvalidArticleAiOutput> { request(bad) }
        }
    }

    @Test fun rejectsMissingParagraphsAndUnexpectedField() = runBlocking<Unit> {
        val long = (1..8).joinToString("\n\n") { "There is an entire independent paragraph about library opening. ".repeat(3) }
        assertFailsWith<InvalidArticleAiOutput> { request("图书馆开放。", original = long) }
        assertFailsWith<InvalidArticleAiOutput> { request(complete, extra = true) }
        assertFailsWith<InvalidArticleAiOutput> { request(source) }
    }

    @Test fun acceptsNaturalWordOrderAndFullWidthDigits() = runBlocking<Unit> {
        val translated = complete.replace("1.23", "１.２３")
        assertEquals(translated, request(translated).translation)
    }

    private suspend fun request(translation: String, original: String = source, extra: Boolean = false): ArticleAiOutput {
        val content = buildJsonObject {
            put("summary", "图书馆重新开放。")
            put("translation", translation)
            if (extra) put("unexpected", "extra")
        }.toString()
        val response = buildJsonObject {
            putJsonArray("choices") { addJsonObject {
                put("finish_reason", "stop")
                putJsonObject("message") { put("content", content) }
            } }
        }
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            HttpClient(MockEngine { respond(response.toString()) }).use { http ->
                val remote = ConfiguredArticleAiRemote(AiConfigService(AIConfigRepository(DailySatoriDatabase(driver))), AiService(http))
                remote.request(Ai_config(1, "openai", "https://example.org/v1", "test", "test", 1, 0, 0),
                    ArticleAiMode.BOTH, ArticleAiInput(original, false, 500, 2000))
            }
        }
    }
}
