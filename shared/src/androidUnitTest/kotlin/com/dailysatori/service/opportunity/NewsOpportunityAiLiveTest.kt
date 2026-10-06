package com.dailysatori.service.opportunity

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import kotlin.test.*

class NewsOpportunityAiLiveTest {
    @Test
    fun configuredAiProducesVerifiableQuotesFromChineseAndEnglishMarkdown() = runBlocking<Unit> {
        assumeTrue("Opt in to real opportunity validation", System.getenv("DAILY_AI_LIVE_TEST") == "1")
        val file = File("../.local/ai-test.json")
        require(file.isFile) { "缺少 .local/ai-test.json，真实产品机会 AI 验证未运行" }
        val config = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }
            .getOrElse { error("AI 测试配置格式无效") }
        fun required(key: String) = config[key]?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotBlank)
            ?: error("AI 测试配置缺少 $key")
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val database = DailySatoriDatabase(driver)
            val configs = AIConfigRepository(database, PlainCipher)
            configs.insert(required("provider"), required("apiAddress"), required("apiToken"), required("modelName"), isDefault = 1)
            HttpClient(OkHttp) {
                install(HttpTimeout) { requestTimeoutMillis = 120_000; connectTimeoutMillis = 15_000 }
            }.use { client ->
                val service = NewsOpportunityService(NewsOpportunityStore(SettingRepository(database)),
                    AiNewsOpportunityAnalyzer(AiService(client), AiConfigService(configs)), NoThoughts)
                service.saveFocus("为小型开发团队开发定时任务失败提醒、重试记录和排障效率软件")
                listOf(
                    "# 调查报告\n\n**小型开发团队每天花两小时手工检查定时任务的失败记录。**\n" +
                        "现有服务只提供任务状态接口，没有集中失败提醒；团队需要自行记录重试结果。",
                    "# Small team survey\n\n**Small teams spend two hours each day manually checking failed scheduled jobs.**\n" +
                        "Existing services expose job status APIs but offer no consolidated failure alerts.\n" +
                        "Teams  need to record retry outcomes themselves.",
                ).forEachIndexed { index, body ->
                    service.markRead(ReadNewsArticle("live:$index", "任务失败调查 $index", body, source = "Test fixture", readAt = index.toLong()))
                }
                try { service.analyze() } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { error("真实产品机会验证失败：${(error as? NewsOpportunityAnalysisException)?.reason?.name ?: error::class.simpleName}") }
                assertEquals(2, service.state.value.items.size)
                service.state.value.items.forEach { item ->
                    assertTrue(item.quote.isNotBlank())
                    assertTrue(item.article.content.contains(item.quote))
                }
                assertEquals(0, service.state.value.pendingCount)
                assertNull(service.state.value.error)
            }
        }
    }

    private object NoThoughts : NewsOpportunityContext {
        override val enabled = false
        override fun verifiedContext(): String? = error("Thought access is disabled")
    }

    private object PlainCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }
}
