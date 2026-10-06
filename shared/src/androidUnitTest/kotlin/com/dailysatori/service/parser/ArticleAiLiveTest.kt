package com.dailysatori.service.parser

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.ai.AiService
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import kotlin.test.*

class ArticleAiLiveTest {
    @Test fun generatesChineseOverviewAndPreservesEnglishTranslationStructure() = runBlocking<Unit> {
        assumeTrue(System.getenv("DAILY_AI_LIVE_TEST") == "1")
        val configFile = File("../.local/ai-test.json")
        require(configFile.isFile) { "缺少 .local/ai-test.json，真实文章 AI 验证未运行" }
        val value = runCatching { Json.parseToJsonElement(configFile.readText()).jsonObject }
            .getOrElse { error("AI 测试配置格式无效") }
        fun required(key: String) = (value[key] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("AI 测试配置缺少 $key")
        val config = normalizeAiConfigValues(required("apiAddress"), required("apiToken"), required("modelName"), required("provider"))
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis = 120_000; connectTimeoutMillis = 15_000 } }.use { client ->
                val processor = ArticleAiProcessor(AiService(client), SettingRepository(DailySatoriDatabase(driver)))
                var overview: ArticleAiOverview? = null
                val chinese = "# 文章处理优化\n\n中文文章先完整保存原文，再生成标题和摘要。英文文章需要翻译全文。封面独立下载，不影响正文阅读。"
                var markdown = ""
                processor.process(1, chinese, "文章处理优化", config, onOverview = { overview = it }, onMarkdown = { markdown = it })
                assertNotNull(overview)
                assertEquals(chinese, markdown)
                val english = "# Reliable article processing\n\nSave the complete original article before generating a summary. " +
                    "Download the cover independently so readers can read immediately. Never discard the end of a long article.\n\n" +
                    "See [documentation](https://example.com/docs).\n\n```kotlin\nval attempts = 2\nprintln(attempts)\n```\n\n" +
                    "![Architecture](https://example.com/diagram.png)"
                processor.process(2, english, "Reliable article processing", config, onOverview = { overview = it }, onMarkdown = { markdown = it })
                assertTrue(markdown.contains("https://example.com/docs"))
                assertTrue(markdown.contains("https://example.com/diagram.png"))
                assertTrue(markdown.contains("val attempts = 2\nprintln(attempts)"))
                assertFalse(articleNeedsTranslation(markdown))
                assertNotNull(articleTranslationMarkdown(english, markdown))
            }
        }
    }
}
