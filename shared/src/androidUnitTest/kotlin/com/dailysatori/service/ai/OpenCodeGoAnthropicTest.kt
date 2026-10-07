package com.dailysatori.service.ai

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenCodeGoAnthropicTest {
    @Test
    fun miniMaxAndQwenSendNativeMessagesWithSystemPromptAndGoHeaders() = runBlocking {
        withServer { base, requests ->
            HttpClient(MockEngine { error("Anthropic must not use the OpenAI client") }).use { client ->
                withAiRequestSession("anthropic-conversation") {
                    val ai = AiService(client)
                    assertEquals("OK", ai.complete("Hello", base, "test-key", "minimax-m2.7", "opencode-go", "System instruction"))
                    assertEquals("OK", ai.completePrivate("Hello", base, "test-key", "qwen3.7-plus", "opencode-go", "Private system"))
                    val response = ai.chatCompletionStreaming(
                        messages = listOf(message("system", "Chat system"), message("user", "Hello")),
                        apiAddress = base, apiToken = "test-key", modelName = "qwen3.7-plus", provider = "opencode-go",
                        onChunk = { error("Anthropic retains non-streaming fallback") },
                    )
                    assertEquals("OK", response?.get("choices")?.jsonArray?.first()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content)
                }
            }
            assertEquals(3, requests.size)
            requests.forEach { request ->
                assertEquals("/zen/go/v1/messages", request.path)
                assertEquals("anthropic-conversation", request.session)
                assertTrue(request.userAgent.startsWith("DailySatori/"))
                assertEquals("test-key", request.apiKey)
                assertTrue(request.body["max_tokens"]!!.jsonPrimitive.content.toInt() > 0)
                assertFalse(request.body["messages"]!!.toString().contains("\"role\":\"system\""))
            }
            assertTrue(requests[0].body["system"].toString().contains("System instruction"))
            assertTrue(requests[1].body["system"].toString().contains("Private system"))
            assertEquals("qwen3.7-plus", requests[2].body["model"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun anthropicToolUseAndToolResultsKeepTheExistingOpenAiInternalContract() = runBlocking {
        withServer(toolResponse = true) { base, requests ->
            HttpClient(MockEngine { error("Wrong protocol") }).use { client ->
                val tools = listOf(Json.parseToJsonElement("""{"type":"function","function":{"name":"lookup","description":"Lookup","parameters":{"type":"object","properties":{"query":{"type":"string"}}}}}""").jsonObject)
                val ai = AiService(client)
                withAiRequestSession("tool-conversation") {
                    val response = ai.chatCompletion(listOf(message("user", "Search")), base, "test-key", "minimax-m2.7", "opencode-go", tools)!!
                    val assistant = response["choices"]!!.jsonArray.first().jsonObject["message"]!!.jsonObject
                    val call = assistant["tool_calls"]!!.jsonArray.first().jsonObject
                    assertEquals("call_1", call["id"]?.jsonPrimitive?.content)
                    assertEquals("lookup", call["function"]!!.jsonObject["name"]?.jsonPrimitive?.content)
                    ai.chatCompletion(
                        listOf(message("user", "Search"), assistant, buildJsonObject {
                            put("role", "tool"); put("tool_call_id", "call_1"); put("content", "Found")
                        }), base, "test-key", "minimax-m2.7", "opencode-go", tools,
                    )
                }
            }
            assertEquals(2, requests.size)
            assertEquals("lookup", requests[0].body["tools"]!!.jsonArray.first().jsonObject["name"]?.jsonPrimitive?.content)
            assertTrue(requests[1].body["messages"].toString().contains("tool_result"))
            assertTrue(requests[1].body["messages"].toString().contains("call_1"))
            assertTrue(requests.all { it.session == "tool-conversation" })
        }
    }

    @Test
    fun anthropicGoFailureDoesNotExposeProviderBodyOrSdkCause() = runBlocking {
        withServer(status = 403) { base, requests ->
            HttpClient(MockEngine { error("Wrong protocol") }).use { client ->
                val error = AiService(client).testConnection(base, "key", "minimax-m2.7", "opencode-go").exceptionOrNull()!!
                assertFalse(error.toString().contains("sensitive provider body"))
                // Coroutine stack-trace recovery may attach the same sanitized exception as a cause.
                assertTrue(generateSequence(error) { it.cause }.take(8).all {
                    !it.toString().contains("sensitive provider body") && it !is dev.langchain4j.exception.HttpException
                })
            }
            assertTrue(requests.isNotEmpty())
        }
    }

    private fun message(role: String, text: String) = buildJsonObject { put("role", role); put("content", text) }

    private data class Request(val path: String, val body: JsonObject, val session: String, val userAgent: String, val apiKey: String)

    private suspend fun withServer(
        toolResponse: Boolean = false,
        status: Int = 200,
        block: suspend (String, MutableList<Request>) -> Unit,
    ) {
        val requests = java.util.Collections.synchronizedList(mutableListOf<Request>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            requests += Request(exchange.requestURI.path,
                Json.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject,
                exchange.requestHeaders.getFirst("x-opencode-session").orEmpty(),
                exchange.requestHeaders.getFirst("User-Agent").orEmpty(), exchange.requestHeaders.getFirst("x-api-key").orEmpty())
            val content = if (toolResponse) """[{"type":"tool_use","id":"call_1","name":"lookup","input":{"query":"test"}}]"""
                else """[{"type":"text","text":"OK"}]"""
            val body = if (status == 200) """{"id":"msg_test","type":"message","role":"assistant","model":"minimax-m2.7","content":$content,"stop_reason":"${if (toolResponse) "tool_use" else "end_turn"}","stop_sequence":null,"usage":{"input_tokens":5,"output_tokens":3}}"""
                else """{"type":"error","error":{"type":"permission_error","message":"sensitive provider body"}}"""
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { block("http://127.0.0.1:${server.address.port}/zen/go/v1", requests) }
        finally { server.stop(0) }
    }
}
