package com.dailysatori.service.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AiServiceGoTest {
    private val base = "https://opencode.ai/zen/go/v1"
    private val message = buildJsonObject { put("role", "user"); put("content", "Hi") }

    @Test
    fun goCompletionPrivateChatAndStreamingUseTheSameSessionAndHonestClientHeaders() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        HttpClient(MockEngine { request ->
            requests += request
            val stream = (request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString().contains("\"stream\":true")
            respond(
                if (stream) "data: {\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}\n\ndata: [DONE]\n\n"
                else """{"choices":[{"message":{"role":"assistant","content":"OK"}}]}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, if (stream) "text/event-stream" else ContentType.Application.Json.toString()),
            )
        }).use { client ->
            val ai = AiService(client)
            withAiRequestSession("test-conversation") {
                assertEquals("OK", ai.complete("Hi", base, "key", "glm-5.2", "opencode-go"))
                assertEquals("OK", ai.completePrivate("Hi", base, "key", "glm-5.2", "opencode-go", "Private"))
                ai.chatCompletion(listOf(message), base, "key", "glm-5.2", "opencode-go")
                val chunks = mutableListOf<String>()
                ai.chatCompletionStreaming(listOf(message), base, "key", "glm-5.2", "opencode-go", onChunk = { chunks += it })
                assertEquals(listOf("OK"), chunks)
            }
            assertEquals(4, requests.size)
            requests.forEach {
                assertEquals("$base/chat/completions", it.url.toString())
                assertEquals("test-conversation", it.headers["x-opencode-session"])
                assertTrue(it.headers[HttpHeaders.UserAgent].orEmpty().startsWith("DailySatori/"))
                assertEquals("Bearer key", it.headers[HttpHeaders.Authorization])
            }
            ai.complete("Hi", base, "key", "glm-5.2", "opencode-go")
            assertNotEquals("test-conversation", requests.last().headers["x-opencode-session"])
        }
    }

    @Test
    fun unsupportedModelsAreRejectedBeforeAnyNetworkRequest() = runBlocking {
        var calls = 0
        HttpClient(MockEngine { calls++; error("Should not send") }).use { client ->
            val ai = AiService(client)
            listOf("unknown", "grok-4.7").forEach { model ->
                assertTrue(ai.testConnection(base, "key", model, "opencode-go").isFailure)
                assertFailsWith<IllegalArgumentException> { ai.chatCompletion(listOf(message), base, "key", model, "opencode-go") }
            }
            assertEquals(0, calls)
        }
    }

    @Test
    fun ordinaryProvidersNeverReceiveGoHeadersAndGoErrorsDoNotExposeBodies() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        HttpClient(MockEngine {
            requests += it
            respond("private diary and secret key", HttpStatusCode.Forbidden)
        }).use { client ->
            val result = AiService(client).testConnection(base, "key", "glm-5.2", "opencode-go")
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("403"))
            assertTrue(!result.exceptionOrNull()?.message.orEmpty().contains("private diary"))
            AiService(client).testConnection("https://native/v1", "key", "test", "openai")
            assertEquals(null, requests.last().headers["x-opencode-session"])
        }
    }

    @Test
    fun malformedSuccessBodiesCannotLeakPrivateContentInExceptions() = runBlocking {
        HttpClient(MockEngine {
            respond("secret-diary is not JSON", HttpStatusCode.OK)
        }).use { client ->
            val error = AiService(client).testConnection(base, "key", "glm-5.2", "opencode-go").exceptionOrNull()!!
            assertTrue(generateSequence(error) { it.cause }.take(8).none { it.toString().contains("secret-diary") })
        }
    }

    @Test
    fun sessionScopesReuseParentButIsolateConcurrentIndependentTasks() = runBlocking {
        val first = withAiRequestSession { currentAiRequestSessionId() }
        val second = withAiRequestSession { currentAiRequestSessionId() }
        assertNotEquals(first, second)
        withAiRequestSession("parent") {
            assertEquals("parent", withAiRequestSession { currentAiRequestSessionId() })
            coroutineScope {
                val a = async { withAiRequestSession("a") { currentAiRequestSessionId() } }
                val b = async { withAiRequestSession("b") { currentAiRequestSessionId() } }
                assertEquals("a", a.await())
                assertEquals("b", b.await())
            }
        }
    }
}
