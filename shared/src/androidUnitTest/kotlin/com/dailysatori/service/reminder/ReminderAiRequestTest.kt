package com.dailysatori.service.reminder

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReminderAiRequestTest {
    @Test
    fun deepSeekReminderDisablesThinkingForSingleAndBatchParsing() = runBlocking {
        withRemote("deepseek") { remote, _, requests ->
            remote.interpret("明天九点交水费", now, TimeZone.UTC)
            remote.interpretBatch(listOf(ReminderInputFragment(0, "明天九点交水费")), now, TimeZone.UTC)

            assertEquals(2, requests.size)
            requests.forEach { request ->
                assertEquals("disabled", request["thinking"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
                assertEquals("deepseek-flash", request["model"]?.jsonPrimitive?.content)
            }
        }
    }

    @Test
    fun ordinaryDeepSeekCompletionPreservesDefaultThinkingMode() = runBlocking {
        withRemote("deepseek") { _, service, requests ->
            service.complete("分析这份材料", "https://example.com/v1", "test-key", "deepseek-flash", "deepseek")

            assertNull(requests.single()["thinking"])
        }
    }

    @Test
    fun otherProvidersDoNotReceiveDeepSeekThinkingParameters() = runBlocking {
        withRemote("openai") { remote, _, requests ->
            remote.interpretBatch(listOf(ReminderInputFragment(0, "明天九点交水费")), now, TimeZone.UTC)

            assertNull(requests.single()["thinking"])
        }
    }

    private suspend fun withRemote(
        provider: String,
        test: suspend (ReminderAiInterpretationRemote, AiService, List<JsonObject>) -> Unit,
    ) {
        val requests = mutableListOf<JsonObject>()
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val configs = AIConfigRepository(DailySatoriDatabase(driver), PlainCipher)
            configs.insert(provider, "https://example.com/v1", "test-key", "deepseek-flash", isDefault = 1)
            HttpClient(MockEngine { request ->
                requests += Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                respond("""{"choices":[{"message":{"content":"[]"}}]}""")
            }).use { client ->
                val service = AiService(client)
                test(ReminderAiInterpretationRemote(service, configs), service, requests)
            }
        } finally {
            driver.close()
        }
    }

    private val now = Instant.parse("2026-10-03T08:00:00Z")

    private object PlainCipher : SecretValueCipher {
        override fun encrypt(value: String): String = value
        override fun decrypt(value: String): String = value
        override fun isEncrypted(value: String): Boolean = false
    }
}
