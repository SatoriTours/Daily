package com.dailysatori.service.hotnews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.unifiednews.*
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import kotlin.test.*

class HotNewsAiLiveTest {
    @Test fun configuredAiCombinesHotNewsAndNewsIntoGroundedChineseDailyPoints() = runBlocking {
        assumeTrue("Opt in to real hot news summary", System.getenv("DAILY_AI_LIVE_TEST") == "1")
        val file = File("../.local/ai-test.json")
        require(file.isFile) { "缺少 .local/ai-test.json，未运行真实新闻汇总验证" }
        val config = Json.parseToJsonElement(file.readText()).jsonObject
        fun required(key: String) = config[key]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            ?: error("AI 测试配置缺少 $key")
        val sources = listOf(
            UnifiedNewsSourceItem("H1", UnifiedNewsSourceType.HOT_NEWS, title = "将文档转换为 Markdown", summary = "文档转换工具",
                content = "GitHub 热榜项目 microsoft/markitdown 是将文件转换为 Markdown 的 Python 工具，支持办公文档。排名只代表关注度，不能推断使用人数、性能或市场需求。"),
            UnifiedNewsSourceItem("H2", UnifiedNewsSourceType.HOT_NEWS, title = "本地阅读工具设计", summary = "保存和导出阅读资料",
                content = "Hacker News 讨论中的阅读工具将文章保存在本地，并允许导出已保存的文档。来源没有提供下载量、活跃用户数或商业收入。"),
            UnifiedNewsSourceItem("R1", UnifiedNewsSourceType.REMOTE_ARTICLE, title = "阅读资料的导出与整理", summary = "在工具间移动个人资料",
                content = "订阅文章介绍了把阅读资料整理成 Markdown 并导出的流程，方便在不同笔记工具之间移动个人资料，没有比较不同工具的性能。"),
        )
        HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis = 120_000 } }.use { http ->
            val response = AiService(http).summarize(
                buildUnifiedNewsPrompt(UnifiedNewsWindow(UnifiedNewsWindowKey.DAILY, "2026-10-11", 0, 1), sources),
                apiAddress = required("apiAddress"), apiToken = required("apiToken"),
                modelName = required("modelName"), provider = required("provider"),
                systemPrompt = "你是 Daily Satori 的新闻编辑，请输出可靠、克制且带引用的中文 Markdown 汇总。",
            )
            assertTrue(invalidCitationTokens(response, sources).isEmpty())
            val content = sanitizeGeneratedUnifiedNewsContent(response, sources)
            assertTrue(citationTokens(content).any { it.startsWith("H") })
            val points = content.substringAfter("## 今日要点").substringBefore("\n## ").lines().filter { it.trim().startsWith("- ") }
            assertTrue(points.size in 1..5, "汇总应最多包含五条重点")
            assertTrue(points.all { hasValidCitationTokens(it, sources) && isChineseHotNewsText(it) })
        }
    }

    @Test fun configuredAiTranslatesGithubAndHackerNewsWithoutChangingTheirLinks() = runBlocking {
        assumeTrue("Opt in to real hot news translation", System.getenv("DAILY_AI_LIVE_TEST") == "1")
        val file = File("../.local/ai-test.json")
        require(file.isFile) { "缺少 .local/ai-test.json，未运行真实热榜翻译验证" }
        val config = Json.parseToJsonElement(file.readText()).jsonObject
        fun required(key: String) = config[key]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            ?: error("AI 测试配置缺少 $key")
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val configs = AIConfigRepository(DailySatoriDatabase(driver))
            configs.insert(required("provider"), required("apiAddress"), required("apiToken"), required("modelName"), isDefault = 1)
            HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis = 120_000 } }.use { http ->
                val input = listOf(
                    HotNewsItem("microsoft/markitdown", HotNewsPlatform.GITHUB, 3, "microsoft/markitdown",
                        "https://github.com/microsoft/markitdown", "A Python tool for converting files and office documents to Markdown."),
                    HotNewsItem("11", HotNewsPlatform.HACKER_NEWS, 8, "Building a local-first reading tool",
                        "https://example.com/reading", "A reading tool stores articles locally and supports exporting saved documents.",
                        discussionUrl = "https://news.ycombinator.com/item?id=11"),
                )
                val result = HotNewsTranslator(AiService(http), AiConfigService(configs)).translate(input)
                assertEquals(input.map { it.identity }, result.map { it.identity })
                assertEquals(input.map { it.url }, result.map { it.url })
                assertEquals(input.last().discussionUrl, result.last().discussionUrl)
                result.forEach { assertTrue(isChineseHotNewsText(it.chineseTitle.orEmpty())); assertTrue(isChineseHotNewsText(it.chineseSummary.orEmpty())) }
            }
        }
    }
}
