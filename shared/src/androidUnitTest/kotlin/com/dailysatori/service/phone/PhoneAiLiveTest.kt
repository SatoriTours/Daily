package com.dailysatori.service.phone

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.service.sms.*
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import kotlin.test.*

class PhoneAiLiveTest {
    @Test fun realRedactedMessageTasksPassProductionValidation() = runBlocking {
        assumeTrue("Opt in to real AI validation", System.getenv("DAILY_PHONE_AI_LIVE_TEST") == "1")
        val file = File("../.local/ai-test.json")
        require(file.isFile) { "Missing .local/ai-test.json" }
        val config = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrElse { error("Invalid AI test configuration") }
        fun required(key: String) = config[key]?.jsonPrimitive?.content?.takeIf(String::isNotBlank) ?: error("Missing AI test field: $key")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val configs = AIConfigRepository(db, PlainCipher)
            configs.insert(required("provider"), required("apiAddress"), required("apiToken"), required("modelName"), 1)
            HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis = 90_000; connectTimeoutMillis = 15_000 } }.use { client ->
                val service = SmsReminderService(SmsSourceRepository(db, PlainCipher), ReminderRepository(db), SettingRepository(db),
                    SmsReminderAi(AiService(client), configs))
                for ((text, category) in listOf("请领取包裹" to "pickup", "Please renew service" to "renewal")) {
                    val draft = assertNotNull(service.analyzeDraft(SmsSource("synthetic", text), Clock.System.now(), TimeZone.UTC))
                    assertEquals(category, draft.category)
                    assertTrue(draft.title.isNotBlank() && draft.title.none(Char::isDigit))
                    assertNull(draft.deadlineMs)
                }
            }
        } finally { driver.close() }
    }
    private object PlainCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }
}
