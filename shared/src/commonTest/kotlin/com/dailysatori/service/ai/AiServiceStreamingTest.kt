package com.dailysatori.service.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class AiServiceStreamingTest {
    @Test
    fun streamingRequestSetsStreamTrueAndKeepsTools() {
        val request = buildOpenAiChatCompletionRequest(
            modelName = "gpt-test",
            messages = listOf(buildJsonObject {
                put("role", JsonPrimitive("user"))
                put("content", JsonPrimitive("你好"))
            }),
            tools = listOf(buildJsonObject { put("type", JsonPrimitive("function")) }),
            temperature = 0.7,
            stream = true,
        ).toString()

        assertTrue(request.contains("\"stream\":true"))
        assertTrue(request.contains("\"tools\""))
        assertTrue(request.contains("\"tool_choice\":\"auto\""))
    }

    @Test
    fun parsesOpenAiStreamingContentChunks() {
        val lines = listOf(
            "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}",
            "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}",
            "data: [DONE]",
        )

        assertEquals(listOf("你", "好"), lines.mapNotNull(::parseOpenAiStreamingContentChunk))
    }

    @Test
    fun ignoresMalformedStreamingLines() {
        assertEquals(null, parseOpenAiStreamingContentChunk("event: ping"))
        assertEquals(null, parseOpenAiStreamingContentChunk("data: not-json"))
        assertEquals(null, parseOpenAiStreamingContentChunk("data: [DONE]"))
    }
}
