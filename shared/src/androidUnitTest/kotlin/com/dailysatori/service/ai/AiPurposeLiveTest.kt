package com.dailysatori.service.ai

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.book.BookReflectionPromptMessage
import com.dailysatori.service.book.BookReflectionService
import com.dailysatori.service.lifearchive.LifeArchiveAiService
import com.dailysatori.service.lifearchive.defaultLifeArchiveCategories
import com.dailysatori.service.parser.ArticleAiProcessor
import com.dailysatori.service.parser.normalizeAiConfigValues
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import kotlin.test.*

class AiPurposeLiveTest {
    @Test fun assignedModelsHandleFastInputExternalContentAndDeepReflection() = runBlocking<Unit> {
        assumeTrue("Opt in to live purpose routing validation", System.getenv("DAILY_AI_LIVE_TEST") == "1")
        val file = File("../.local/ai-test.json")
        require(file.isFile) { "缺少 .local/ai-test.json，真实功能分流验证未运行" }
        val value = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }
            .getOrElse { error("AI 测试配置格式无效") }
        fun required(key: String) = value[key]?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotBlank)
            ?: error("AI 测试配置缺少 $key")
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val repo = AIConfigRepository(db)
            repo.insert("openai", "https://invalid.example/v1", "unused-token", "invalid-default", 1)
            repo.insert(required("provider"), required("apiAddress"), required("apiToken"), required("modelName"))
            val assignedId = repo.getAllSync().single { it.model_name != "invalid-default" }.id
            AiPurpose.entries.forEach { repo.setPurposeConfig(it, assignedId) }
            HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis = 180_000; connectTimeoutMillis = 15_000 } }.use { client ->
                val ai = AiService(client)
                val configs = AiConfigService(repo)
                val records = LifeArchiveAiService(ai, repo).organize(
                    "我在 Spaceship 买了 example.com 域名，每年续费80元。", defaultLifeArchiveCategories(), emptyList())
                assertTrue(records.any { "example.com" in it.title })
                val background = assertNotNull(configs.getConfig(AiPurpose.EXTERNAL_CONTENT))
                var summary = ""
                ArticleAiProcessor(ai, SettingRepository(db)).process(1,
                    "10月8日，示例平台宣布服务恢复。此前定时任务可能失败，用户需要检查任务结果。根因报告尚未发布。",
                    "示例平台服务恢复", normalizeAiConfigValues(background.api_address, background.api_token, background.model_name, background.provider),
                    sourceUrl = "https://example.com/incident", onOverview = { summary = it.summary })
                assertTrue(summary.contains("**新闻事实：**"))
                val reflection = BookReflectionService(ai, configs, AiConversationSessionStore(SettingRepository(db))).summarize(
                    "可靠的软件", "失败恢复", listOf(
                        BookReflectionPromptMessage("user", "我以前把失败当成偶然，但现在意识到应先保存原始数据，再生成结果，失败时保留已完成部分。"),
                        BookReflectionPromptMessage("assistant", "还需要区分事实和推断，检查重试是否覆盖用户后来的修改。")))
                assertTrue(reflection.isNotBlank())
            }
        }
    }

    private object PlainCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }
}
