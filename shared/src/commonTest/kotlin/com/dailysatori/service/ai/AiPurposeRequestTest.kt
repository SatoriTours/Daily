package com.dailysatori.service.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class AiPurposeRequestTest {
    @Test fun deepPrivateCompletionEnablesThinkingInsteadOfTheLegacyFastDefault() = runBlocking {
        withAi { ai, requests ->
            ai.completePrivate("synthetic private source", "https://example.com/v1", "test-token", "deepseek-v4-pro",
                "deepseek", "Summarize", purpose = AiPurpose.REFLECTION)
            val body = requests.single()
            assertEquals("enabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals("high", body["reasoning_effort"]!!.jsonPrimitive.content)
        }
    }

    @Test fun externalGoDeepSeekRequestsAreEconomicalButOtherGoModelsKeepNativeDefaults() = runBlocking {
        withAi { ai, requests ->
            ai.complete("article", "https://example.com/v1", "test-token", "deepseek-v4-flash", "opencode-go",
                purpose = AiPurpose.EXTERNAL_CONTENT)
            ai.complete("article", "https://example.com/v1", "test-token", "kimi-k3", "opencode-go",
                purpose = AiPurpose.REFLECTION)
            ai.complete("article", "https://example.com/v1", "test-token", "custom-model", "openai",
                purpose = AiPurpose.REFLECTION)
            assertEquals("disabled", requests.first()["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            requests.drop(1).forEach { body -> assertNull(body["thinking"]); assertNull(body["reasoning_effort"]) }
        }
    }

    @Test fun streamingReflectionUsesTheSameDeepPolicyAndDoesNotPublishReasoningChunks() = runBlocking {
        val body = "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"private reasoning\"}}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{\"content\":\"final answer\"}}]}\n\ndata: [DONE]\n\n"
        withAi(body) { ai, requests ->
            val chunks = mutableListOf<String>()
            ai.chatCompletionStreaming(emptyList(), "https://example.com/v1", "test-token", "deepseek-v4-pro", "opencode-go",
                purpose = AiPurpose.REFLECTION, onChunk = { chunks += it })
            assertEquals(listOf("final answer"), chunks)
            assertEquals("enabled", requests.single()["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals("high", requests.single()["reasoning_effort"]!!.jsonPrimitive.content)
        }
    }

    @Test fun ordinaryDefaultCallsDoNotOptIntoNewPurposePolicies() = runBlocking {
        withAi { ai, requests ->
            ai.complete("small helper", "https://example.com/v1", "test-token", "deepseek-v4-flash", "deepseek")
            assertNull(requests.single()["thinking"])
            assertNull(requests.single()["reasoning_effort"])
        }
    }

    private suspend fun withAi(
        response: String = """{"choices":[{"message":{"content":"OK"},"finish_reason":"stop"}]}""",
        test: suspend (AiService, List<JsonObject>) -> Unit,
    ) {
        val requests = mutableListOf<JsonObject>()
        HttpClient(MockEngine { request ->
            requests += Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            respond(response)
        }).use { test(AiService(it), requests) }
    }
}
