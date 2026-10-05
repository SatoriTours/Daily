package com.dailysatori.service.sms

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.security.SecretValueCipher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import kotlin.test.*

class SmsReminderAiRequestTest {
    @Test fun realHttpRequestContainsNoSenderAccountBalanceOrSourceDigits() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val configs = AIConfigRepository(DailySatoriDatabase(driver), Cipher)
            configs.insert("openai", "https://example.com/v1", "test-token", "test-model", isDefault = 1)
            val requests = mutableListOf<String>()
            HttpClient(MockEngine { request ->
                requests += request.body.toByteArray().decodeToString()
                respond("""{"choices":[{"message":{"content":"{\"actionable\":true,\"category\":\"top_up\",\"title\":\"Top up Skinny\",\"reason\":\"credit will expire\",\"evidence\":\"Top-Up\",\"deadlineIndex\":0}"}}]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { client ->
                val source = SmsSource("+64211234567", "Top-Up within the next 24 hours or credit will expire. Account DE8912345678, balance five dollars; card 6222123456789999")
                val input = assertNotNull(SmsAiInput.from(source, Instant.parse("2026-10-05T15:00:00Z"), TimeZone.UTC))
                SmsReminderAi(AiService(client), configs).analyze(input)
                val body = requests.single()
                listOf("+64211234567", "DE8912345678", "five dollars", "6222123456789999", "next 24 hours").forEach { assertFalse(body.contains(it), it) }
                assertTrue(body.contains("2026-10-06T15:00:00Z"))
            }
        } finally { driver.close() }
    }

    @Test fun providerErrorCannotPropagatePrivateResponseBodies() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val configs = AIConfigRepository(DailySatoriDatabase(driver), Cipher)
            configs.insert("openai", "https://example.com/v1", "test-token", "test-model", isDefault = 1)
            HttpClient(MockEngine { respond("private-provider-error-content", HttpStatusCode.BadRequest) }).use { client ->
                val input = assertNotNull(SmsAiInput.from(SmsSource("", "Top-Up within 24 hours"), Instant.parse("2026-10-05T15:00:00Z"), TimeZone.UTC))
                val failure = assertFailsWith<IllegalStateException> { SmsReminderAi(AiService(client), configs).analyze(input) }
                assertFalse(failure.message.orEmpty().contains("private-provider-error-content"))
                assertNull(failure.cause)
            }
        } finally { driver.close() }
    }

    private object Cipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }
}
