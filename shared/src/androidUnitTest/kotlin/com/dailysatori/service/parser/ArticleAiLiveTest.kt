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
                val chinese = "# 示例云服务故障恢复公告\n\n10月5日22:49 UTC，示例云服务宣布服务故障已恢复。" +
                    "故障期间定时任务可能失败，用户应核查并补跑必要任务。根因报告尚未发布，不能认定事故由 AI 负载引起。"
                var markdown = ""
                processor.process(1, chinese, "示例云服务故障恢复公告", config, sourceUrl = "https://example.com/incident",
                    onOverview = { overview = it }, onMarkdown = { markdown = it })
                val news = assertNotNull(overview).summary
                assertTrue(news.contains("**新闻事实：**"))
                assertTrue(news.contains("10月5日"))
                assertTrue(news.contains("根因"))
                assertTrue(news.contains("**为什么重要（分析）：**"))
                assertTrue(news.contains("**工作／技术决策（分析）：**"))
                assertTrue(news.endsWith("来源：[原文](<https://example.com/incident>)"))
                assertEquals(chinese, markdown)
                val english = "# Reliable article processing\n\nSave the complete original article before generating a summary. " +
                    "Download the cover independently so readers can read immediately. Never discard the end of a long article.\n\n" +
                    "See [documentation](https://example.com/docs).\n\n```kotlin\nval attempts = 2\nprintln(attempts)\n```\n\n" +
                    "![Architecture](https://example.com/diagram.png)"
                processor.process(2, english, "Reliable article processing", config, sourceUrl = "https://example.com/article",
                    onOverview = { overview = it }, onMarkdown = { markdown = it })
                assertTrue(assertNotNull(overview).summary.contains("**核心内容：**"))
                assertTrue(markdown.contains("https://example.com/docs"))
                assertTrue(markdown.contains("https://example.com/diagram.png"))
                assertTrue(markdown.contains("val attempts = 2\nprintln(attempts)"))
                assertFalse(articleNeedsTranslation(markdown))
                assertNotNull(articleTranslationMarkdown(english, markdown))
            }
        }
    }
}
