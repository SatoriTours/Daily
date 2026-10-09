package com.dailysatori.service.reminder

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Explicitly opt in to paid network requests; ordinary unit test runs skip this test. */
class ReminderAiLiveTest {
    @Test
    fun monthlyRenewalUsesRealAiAndProductionDraftValidation() = runBlocking {
        assumeTrue("Set DAILY_AI_LIVE_TEST=1 to run a real AI request", System.getenv("DAILY_AI_LIVE_TEST") == "1")
        val config = readConfig()
        val now = Clock.System.now()
        val zone = TimeZone.of("Asia/Shanghai")
        val fragments = splitReminderInput("每个月的 2 号提醒我给 DMIT 的日本服务器续费")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val configs = AIConfigRepository(DailySatoriDatabase(driver))
            configs.insert(config.required("provider"), config.required("apiAddress"),
                config.required("apiToken"), config.required("modelName"), isDefault = 1)
            HttpClient(OkHttp) {
                install(HttpTimeout) { requestTimeoutMillis = 90_000; connectTimeoutMillis = 15_000 }
            }.use { client ->
                val remote = ReminderAiInterpretationRemote(AiService(client), configs)
                val response = remote.interpretBatch(fragments, now, zone)
                // Save the AI response before decoding, so even unexpected decoding failures are inspectable.
                val report = File("../build/ai-live/reminder-response.json")
                report.parentFile?.mkdirs()
                report.writeText(buildJsonObject { put("response", response) }.toString())
                val codec = ReminderDraftCodec(now = { now }, currentTimeZone = { zone })
                val decoded = ReminderBatchCodec(codec).decode(response, zone)
                report.writeText(buildJsonObject {
                    put("requestedAt", now.toString())
                    put("timezone", zone.id)
                    put("response", response)
                    put("failure", decoded.failure?.let(::JsonPrimitive) ?: JsonNull)
                    put("drafts", JsonArray(decoded.drafts.map { Json.parseToJsonElement(codec.encode(it.draft)) }))
                }.toString())
                assertNull(decoded.failure, "响应结构有误，检查 build/ai-live/reminder-response.json")
                assertEquals(listOf(0), decoded.drafts.map { it.sourceIndex })
                val draft = decoded.drafts.single().draft
                assertTrue(draft.validationErrors.isEmpty(), draft.validationErrors.joinToString("；"))
                assertEquals(ReminderRecurrence.Monthly(2), draft.recurrence)
                assertEquals("09:00", draft.firstReminderTime.toString())
                assertEquals(draft.startDate, draft.endDate)
                assertEquals(2, draft.startDate?.dayOfMonth)
                val localNow = now.toLocalDateTime(zone)
                assertTrue(draft.startDate!! > localNow.date ||
                    draft.startDate == localNow.date && draft.firstReminderTime!! > localNow.time)
            }
        } finally {
            driver.close()
        }
    }

    private fun readConfig(): JsonObject {
        val path = File("../.local/ai-test.json")
        require(path.isFile) { "请创建项目根目录的 .local/ai-test.json" }
        return runCatching { Json.parseToJsonElement(path.readText()).jsonObject }
            .getOrElse { error(".local/ai-test.json 必须是合法 JSON；配置内容不会输出") }
    }

    private fun JsonObject.required(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
            ?.takeIf { it.isNotEmpty() } ?: error("请填写 .local/ai-test.json 的 $key")

    private object PlainCipher : SecretValueCipher {
        override fun encrypt(value: String): String = value
        override fun decrypt(value: String): String = value
        override fun isEncrypted(value: String): Boolean = false
    }
}
