package com.dailysatori.service.ai

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.book.BookReflectionPromptMessage
import com.dailysatori.service.book.BookReflectionService
import com.dailysatori.service.lifearchive.LifeArchiveAiService
import com.dailysatori.service.reminder.ReminderAiInterpretationRemote
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.*
import kotlin.test.*

class AiPurposeFeatureTest {
    @Test fun reminderAndPrivateArchivesUseFastAssignmentNotTheDefault() = runBlocking {
        withServices { repo, ai, _, _, requests ->
            ReminderAiInterpretationRemote(ai, repo).interpret("明天开会", Instant.parse("2026-10-08T00:00:00Z"), TimeZone.UTC)
            LifeArchiveAiService(ai, repo).request(buildJsonObject { put("text", "整理资料") })
            assertEquals(listOf("fast.example", "fast.example"), requests.map { it.first })
            requests.forEach { (_, body) ->
                assertEquals("deepseek-v4-flash", body["model"]!!.jsonPrimitive.content)
                assertEquals("disabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            }
        }
    }

    @Test fun readingReflectionUsesDeepAssignmentAndNeverDisablesThinking() = runBlocking {
        withServices { _, ai, configs, sessions, requests ->
            BookReflectionService(ai, configs, sessions).summarize(
                "书名", "观点", listOf(BookReflectionPromptMessage("user", "我的理解")))
            val (host, body) = requests.single()
            assertEquals("deep.example", host)
            assertEquals("deepseek-v4-pro", body["model"]!!.jsonPrimitive.content)
            assertEquals("enabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals("high", body["reasoning_effort"]!!.jsonPrimitive.content)
        }
    }

    @Test fun failedAssignedProviderDoesNotSendInputToTheDefaultProvider() = runBlocking {
        withServices(status = HttpStatusCode.ServiceUnavailable) { repo, ai, _, _, requests ->
            assertFailsWith<IllegalStateException> {
                ReminderAiInterpretationRemote(ai, repo).interpret("明天开会", Instant.parse("2026-10-08T00:00:00Z"), TimeZone.UTC)
            }
            assertEquals(listOf("fast.example"), requests.map { it.first })
        }
    }

    private suspend fun withServices(status: HttpStatusCode = HttpStatusCode.OK, test: suspend (AIConfigRepository, AiService, AiConfigService, AiConversationSessionStore, List<Pair<String, JsonObject>>) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val repo = AIConfigRepository(DailySatoriDatabase(driver), PlainCipher)
            repo.insert("openai", "https://default.example/v1", "test-token", "default-model", 1)
            repo.insert("opencode-go", "https://fast.example/v1", "test-token", "deepseek-v4-flash")
            repo.insert("opencode-go", "https://deep.example/v1", "test-token", "deepseek-v4-pro")
            repo.setPurposeConfig(AiPurpose.INTERACTIVE, repo.getAllSync().single { it.model_name == "deepseek-v4-flash" }.id)
            repo.setPurposeConfig(AiPurpose.REFLECTION, repo.getAllSync().single { it.model_name == "deepseek-v4-pro" }.id)
            val requests = mutableListOf<Pair<String, JsonObject>>()
            HttpClient(MockEngine { request ->
                requests += request.url.host to Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                respond("""{"choices":[{"message":{"content":"OK"},"finish_reason":"stop"}]}""", status)
            }).use { client -> test(repo, AiService(client), AiConfigService(repo), AiConversationSessionStore(SettingRepository(DailySatoriDatabase(driver))), requests) }
        }
    }

    private object PlainCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }
}
