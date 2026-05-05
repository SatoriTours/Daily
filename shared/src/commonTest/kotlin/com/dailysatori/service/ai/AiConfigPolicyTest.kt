package com.dailysatori.service.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AiConfigPolicyTest {
    @Test
    fun buildsDisplayNameFromProviderAndModelWithoutStoredName() {
        assertEquals(
            "OpenAI / GPT-5.5 (旗舰)",
            aiConfigDisplayName(provider = "openai", modelName = "gpt-5.5"),
        )
    }

    @Test
    fun fallsBackToRawProviderAndModelForCustomConfig() {
        assertEquals(
            "custom / llama3.2",
            aiConfigDisplayName(provider = "custom", modelName = "llama3.2"),
        )
    }

    @Test
    fun onlyNonDefaultAiConfigsCanBeDeleted() {
        assertEquals(false, canDeleteAiConfig(isDefault = 1L))
        assertEquals(true, canDeleteAiConfig(isDefault = 0L))
    }

    @Test
    fun openAiChatRequestPreservesReasoningContentInMessages() {
        val message = buildJsonObject {
            put("role", JsonPrimitive("assistant"))
            put("content", JsonPrimitive(""))
            put("reasoning_content", JsonPrimitive("thinking trace"))
        }

        val request = buildOpenAiChatCompletionRequest(
            modelName = "deepseek-reasoner",
            messages = listOf(message),
            tools = emptyList(),
            temperature = 0.7,
        )

        val requestMessage = request["messages"]?.jsonArray?.firstOrNull()?.jsonObject
        assertSame(message, requestMessage)
        assertEquals("thinking trace", requestMessage?.get("reasoning_content")?.jsonPrimitive?.content)
    }

    @Test
    fun aiChatRequestsUseLongTimeoutForToolSummaries() {
        assertEquals(120_000L, aiChatRequestTimeoutMillis())
    }
}
