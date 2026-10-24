package com.dailysatori.service.assistant

import ai.koog.agents.core.tools.*
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.*
import ai.koog.prompt.message.*
import com.dailysatori.service.ai.AiService
import com.dailysatori.shared.db.Ai_config
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class KoogAiPromptExecutorTest {
    private val model = LLModel(LLMProvider.OpenAI, "test-model", listOf(LLMCapability.Tools))
    private val config = Ai_config(1, "openai", "https://example.test/v1", "test-secret", "test-model", 1, 0, 0)

    @Test
    fun transportKeepsMultipleCallsAndReasoningTogetherAcrossARoundTrip() = runBlocking {
        var request: JsonObject? = null
        HttpClient(MockEngine {
            request = Json.parseToJsonElement((it.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
            respond("""{"choices":[{"message":{"content":null,"reasoning_content":"private-reasoning","tool_calls":[{"id":"a","type":"function","function":{"name":"lookup","arguments":"{}"}},{"id":"b","type":"function","function":{"name":"lookup","arguments":"{}"}}]}}]}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { client ->
            val executor = KoogAiPromptExecutor(AiService(client), config)
            val first = executor.execute(prompt("first") { user("查询") }, model, listOf(ToolDescriptor("lookup", "lookup")))
            assertEquals(listOf("a", "b"), first.filterIsInstance<Message.Tool.Call>().map { it.id })
            val round = prompt("round") {
                user("查询")
                messages(first)
                tool { result(Message.Tool.Result("a", "lookup", "甲", RequestMetaInfo.Empty)) }
                tool { result(Message.Tool.Result("b", "lookup", "乙", RequestMetaInfo.Empty)) }
            }
            executor.execute(round, model, emptyList())
            val sent = request!!["messages"]!!.jsonArray.map { it.jsonObject }
            assertEquals(1, sent.count { it["tool_calls"] != null }, "Unexpected message grouping: $sent")
            val calls = sent.single { it["tool_calls"] != null }
            assertEquals(2, calls["tool_calls"]!!.jsonArray.size)
            assertEquals("private-reasoning", calls["reasoning_content"]!!.jsonPrimitive.content)
            assertEquals(listOf("a", "b"), sent.filter { it["role"]?.jsonPrimitive?.content == "tool" }
                .map { it["tool_call_id"]!!.jsonPrimitive.content })
            assertFalse(sent.toString().contains("test-secret"))
        }
    }

    @Test
    fun malformedResponseFailsWithoutRepeatingTheRequestOrLeakingBody() = runBlocking {
        var calls = 0
        HttpClient(MockEngine {
            calls++
            respond("""{"debug":"test-secret"}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { client ->
            val error = assertFailsWith<IllegalStateException> {
                KoogAiPromptExecutor(AiService(client), config).execute(prompt("x") { user("hi") }, model, emptyList())
            }
            assertEquals(1, calls)
            assertFalse(error.toString().contains("test-secret"))
            assertNull(error.cause)
        }
    }

    @Test
    fun streamingUsesExistingTransportAndReturnsActualDeltas() = runBlocking {
        HttpClient(MockEngine {
            respond("data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\ndata: [DONE]\n\n",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }).use { client ->
            val chunks = KoogAiPromptExecutor(AiService(client), config)
                .executeStreaming(prompt("stream") { user("hi") }, model).toList()
            assertEquals("你好", chunks.joinToString(""))
        }
    }

    @Test
    fun cancellationIsNotConvertedToARegularFailure() = runBlocking<Unit> {
        HttpClient(MockEngine { throw CancellationException("cancelled") }).use { client ->
            assertFailsWith<CancellationException> {
                KoogAiPromptExecutor(AiService(client), config).execute(prompt("x") { user("hi") }, model, emptyList())
            }
        }
    }
}
