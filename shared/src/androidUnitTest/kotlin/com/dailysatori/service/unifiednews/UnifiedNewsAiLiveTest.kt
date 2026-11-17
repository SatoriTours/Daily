package com.dailysatori.service.unifiednews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiPurpose
import com.dailysatori.service.ai.AiService
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedNewsAiLiveTest {
    @Test
    fun configuredAiProducesFullGroundedBriefingFromCurrentPrompt() = runBlocking<Unit> {
        assumeTrue("Opt in to real unified news validation", System.getenv("DAILY_AI_LIVE_TEST") == "1")
        val file = File("../.local/ai-test.json")
        require(file.isFile) { "缺少 .local/ai-test.json，真实统一新闻 AI 验证未运行" }
        val config = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }
            .getOrElse { error("AI 测试配置格式无效") }
        fun required(key: String) = config[key]?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotBlank)
            ?: error("AI 测试配置缺少 $key")
        val sources = listOf(
            UnifiedNewsSourceItem(
                refKey = "R1",
                sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE,
                sourceId = 1,
                title = "AG-UI 发布统一事件协议",
                summary = "AG-UI 通过事件协议连接 AI 代理与前端界面。",
                content = "AG-UI 通过轻量事件协议连接代理与用户界面，支持 SSE 与 WebSocket。" +
                    "团队可以用统一方式接收代理状态、内容输出与交互事件，减少不同框架之间的重复适配。",
            ),
            UnifiedNewsSourceItem(
                refKey = "R2",
                sourceType = UnifiedNewsSourceType.REMOTE_ARTICLE,
                sourceId = 2,
                title = "Open source document SDK adds embedding",
                summary = "An open source document SDK now supports embedding spreadsheets and documents.",
                content = "The SDK is released under Apache-2.0 and provides document, spreadsheet and AI SDKs. " +
                    "Teams can embed editing into existing products instead of building the entire editor in house.",
            ),
            UnifiedNewsSourceItem(
                refKey = "H1",
                sourceType = UnifiedNewsSourceType.HOT_NEWS,
                sourceId = 3,
                title = "平台热议代理工作流权限",
                summary = "社区讨论代理读取本地文件时的授权边界。",
                content = "社区项目尝试让浏览器中的 AI 连接本地文件工具，讨论集中在可读目录、可写操作和执行前确认。",
            ),
        )
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val configs = AIConfigRepository(DailySatoriDatabase(driver))
            configs.insert(required("provider"), required("apiAddress"), required("apiToken"), required("modelName"), isDefault = 1)
            HttpClient(OkHttp) {
                install(HttpTimeout) { requestTimeoutMillis = 180_000; connectTimeoutMillis = 15_000 }
            }.use { client ->
                val response = AiService(client).complete(
                    prompt = buildUnifiedNewsPrompt(
                        UnifiedNewsWindow(UnifiedNewsWindowKey.DAILY, "2026-11-18", 0L, 1L),
                        sources,
                    ),
                    apiAddress = required("apiAddress"),
                    apiToken = required("apiToken"),
                    modelName = required("modelName"),
                    provider = required("provider"),
                    temperature = 0.2,
                    purpose = AiPurpose.EXTERNAL_CONTENT,
                )
                val content = sanitizeGeneratedUnifiedNewsContent(response, sources)
                File("../build/ai-live/unified-news.md").apply { parentFile?.mkdirs() }.writeText(content)
                assertTrue(content.isNotBlank())
                listOf("## 今日要闻", "## 今日要点", "## 重要变化", "## 值得关注").forEach { heading ->
                    assertTrue(content.contains(heading), "缺少结构标题: $heading")
                }
                val overview = content.substringAfter("## 今日要闻").substringBefore("## 今日要点").trim()
                val overviewParagraphs = overview.split(Regex("""\n\s*\n""")).filter(String::isNotBlank)
                assertTrue(overview.isNotBlank())
                assertTrue(overviewParagraphs.size in 1..2, "今日要闻应为 1-2 段，实际 " + overviewParagraphs.size)
                val points = content.substringAfter("## 今日要点").substringBefore("## 重要变化")
                val entries = points.lines().filter { it.trim().startsWith("- ") }
                assertTrue(entries.isNotEmpty())
                assertTrue(entries.any { entry ->
                    val body = entry.substringAfter("：", "").substringBefore("[")
                    body.length >= 20
                }, "今日要点需要完整正文，而不是只有短标题")
                assertTrue(citationTokens(content).isNotEmpty())
                assertTrue(invalidCitationTokens(content, sources).isEmpty(), "输出包含无效引用编号")
                assertFalse(citationTokens(content).any { it.startsWith("C") || it.startsWith("D") })
                assertEquals(0, invalidCitationTokens(content, sources).size)
            }
        }
    }
}
