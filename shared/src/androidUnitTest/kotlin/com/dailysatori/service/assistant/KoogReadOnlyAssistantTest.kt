package com.dailysatori.service.assistant

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.ai.*
import com.dailysatori.service.assistant.data.AssistantDataGateway
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class KoogReadOnlyAssistantTest {
    @Test
    fun originalIsDurableBeforeModelAndRealKoogExecutesOnlySafeReadTools() = runBlocking<Unit> {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val chats = ChatConversationRepository(db)
            chats.insert("session", "user", "我喜欢爬山", createdAt = 1)
            chats.insert("session", "assistant", "好的", createdAt = 2)
            driver.execute(null, "INSERT INTO diary(id,content,created_at,updated_at) VALUES (1,'上周去了香山',1,1)", 0)
            val configs = AIConfigRepository(db)
            configs.insert("openai", "https://example.test/v1", "configured-secret", "test-model", 1)
            var round = 0
            HttpClient(MockEngine { request ->
                assertEquals("找一下那次登山 configured-secret", chats.getBySession("session").last().content)
                val body = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
                assertFalse(body.toString().contains("configured-secret"))
                assertTrue(body.toString().contains("我喜欢爬山"))
                val names = body["tools"]!!.jsonArray.map { it.jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content }.toSet()
                assertEquals(setOf("list_data_sources", "query_data", "aggregate_data", "search_records", "read_record"), names)
                val message = when (round++) {
                    0 -> """{"tool_calls":[{"id":"search-1","type":"function","function":{"name":"search_records","arguments":"{\"query\":\"香山\",\"sources\":[\"diary\"]}"}}]}"""
                    1 -> {
                        assertTrue(body.toString().contains("香山"))
                        """{"tool_calls":[{"id":"read-1","type":"function","function":{"name":"read_record","arguments":"{\"source\":\"diary\",\"id\":\"1\"}"}}]}"""
                    }
                    else -> """{"content":"你上周去了香山。"}"""
                }
                respond("""{"choices":[{"message":$message}]}""", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { client ->
                val assistant = KoogReadOnlyAssistant(AiService(client), AiConfigService(configs),
                    AssistantDataGateway(driver), AssistantPrivacyRepository(driver), chats)
                val reply = assistant.answer("session", "找一下那次登山 configured-secret")
                assertEquals("你上周去了香山。", reply)
                assertEquals(3, round)
                assertEquals("assistant", chats.getBySession("session").last().role)
                assertEquals(reply, chats.getBySession("session").last().content)
            }
        }
    }

    @Test
    fun missingConfigurationDoesNotDiscardTheOriginalInput() = runBlocking<Unit> {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val chats = ChatConversationRepository(db)
            HttpClient(MockEngine { error("Must not send without configuration") }).use { client ->
                val assistant = KoogReadOnlyAssistant(AiService(client), AiConfigService(AIConfigRepository(db)),
                    AssistantDataGateway(driver), AssistantPrivacyRepository(driver), chats)
                assertFailsWith<IllegalStateException> { assistant.answer("offline", "断网时也不要丢掉这条输入") }
                assertEquals(listOf("断网时也不要丢掉这条输入"), chats.getBySession("offline").map { it.content })
            }
        }
    }

    @Test
    fun excludingInputWhileRequestRunsPreventsPersistingItsDerivedAnswer() = runBlocking<Unit> {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val chats = ChatConversationRepository(db)
            val privacy = AssistantPrivacyRepository(driver)
            val configs = AIConfigRepository(db)
            configs.insert("openai", "https://example.test/v1", "configured-secret", "test-model", 1)
            HttpClient(MockEngine {
                privacy.exclude("chat", chats.getBySession("one").last().id.toString())
                respond("""{"choices":[{"message":{"content":"不应保存的归纳"}}]}""", HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { client ->
                val assistant = KoogReadOnlyAssistant(AiService(client), AiConfigService(configs),
                    AssistantDataGateway(driver), privacy, chats)
                assertFailsWith<CancellationException> { assistant.answer("one", "稍后排除这条输入") }
                assertEquals(1, chats.getBySession("one").size)
            }
        }
    }
}
