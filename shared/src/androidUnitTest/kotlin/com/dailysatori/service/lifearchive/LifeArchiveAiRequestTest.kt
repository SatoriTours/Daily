package com.dailysatori.service.lifearchive

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class LifeArchiveAiRequestTest {
    private val response = """{"records":[{"title":"example.com","categoryId":"domain","body":"","fields":[{"name":"付款方式","value":"尾号5678"}]}]}"""

    @Test
    fun organizeSendsOnlyCurrentInputAndFieldNames() = runBlocking {
        withService { service, requests ->
            service.organize("在 Spaceship 买了 example.com", defaultLifeArchiveCategories(), listOf("管理平台"))
            val message = requests.single()["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonPrimitive.content
            assertTrue(message.contains("在 Spaceship 买了 example.com"))
            assertTrue(message.contains("管理平台"))
            assertNull(requests.single()["tools"])
        }
    }

    @Test
    fun optimizationPreservesLocalMetadataAndUnmentionedFields() = runBlocking {
        withService { service, requests ->
            val old = LifeArchiveRecord("local-private-id", "example.com", "domain", fields = listOf(
                LifeArchiveField("邮箱", "me@example.com"), LifeArchiveField("付款方式", "尾号1234")),
                createdAt = 10, updatedAt = 20, sourceReminderId = "source-private-id", sourceReminderVersion = 1)
            val result = service.optimize(old, "付款卡改成尾号5678", defaultLifeArchiveCategories())
            assertEquals(old.id, result.id)
            assertEquals(old.createdAt, result.createdAt)
            assertEquals(old.sourceReminderId, result.sourceReminderId)
            assertEquals("me@example.com", result.fields.single { it.name == "邮箱" }.value)
            assertEquals("尾号5678", result.fields.single { it.name == "付款方式" }.value)
            val text = requests.single().toString()
            assertTrue(text.contains("me@example.com") && text.contains("付款卡改成尾号5678"))
            assertFalse(text.contains("local-private-id") || text.contains("source-private-id"))
        }
    }

    @Test
    fun privateCompletionErrorsDoNotExposeServerResponse() = runBlocking {
        withService(status = HttpStatusCode.BadRequest, body = "private server echo") { service, _ ->
            val error = assertFailsWith<IllegalStateException> { service.organize("my text", defaultLifeArchiveCategories(), emptyList()) }
            assertFalse(error.toString().contains("private server echo"))
            assertNull(error.cause)
        }
    }

    @Test
    fun cancellationIsPropagated() = runBlocking {
        withService(cancel = true) { service, _ ->
            assertFailsWith<CancellationException> { service.organize("my text", defaultLifeArchiveCategories(), emptyList()) }
        }
    }

    private suspend fun withService(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String? = null,
        cancel: Boolean = false,
        test: suspend (LifeArchiveAiService, List<JsonObject>) -> Unit,
    ) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val configs = AIConfigRepository(DailySatoriDatabase(driver))
            configs.insert("openai", "https://example.com/v1", "test", "test-model", isDefault = 1)
            val requests = mutableListOf<JsonObject>()
            HttpClient(MockEngine { request ->
                requests += Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                if (cancel) throw CancellationException("cancelled")
                respond(body ?: buildJsonObject {
                    putJsonArray("choices") { add(buildJsonObject { putJsonObject("message") { put("content", response) } }) }
                }.toString(), status)
            }).use { test(LifeArchiveAiService(AiService(it), configs), requests) }
        } finally { driver.close() }
    }

    private object PlainCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }
}
