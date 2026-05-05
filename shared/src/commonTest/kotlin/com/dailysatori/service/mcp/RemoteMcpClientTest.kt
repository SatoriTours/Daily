package com.dailysatori.service.mcp

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteMcpClientTest {
    @Test
    fun detectsBookSearchTools() {
        assertTrue(isLikelyBookSearchTool("search_books", "Search books"))
        assertTrue(isLikelyBookSearchTool("web_search", "Search web pages"))
        assertFalse(isLikelyBookSearchTool("get_weather", "Weather forecast"))
    }

    @Test
    fun detectsGenericWebSearchTools() {
        assertTrue(isLikelyWebSearchTool("webSearchPrime", "Search web pages"))
        assertTrue(isLikelyWebSearchTool("webReader", "Read webpage content"))
        assertTrue(isLikelyWebSearchTool("search", "Search the internet"))
        assertFalse(isLikelyWebSearchTool("get_weather", "Weather forecast"))
    }

    @Test
    fun buildsQueryArgumentsFromSchema() {
        val schema = buildJsonObject {
            put("properties", buildJsonObject {
                put("query", buildJsonObject { put("type", JsonPrimitive("string")) })
                put("limit", buildJsonObject { put("type", JsonPrimitive("integer")) })
            })
        }

        val args = buildMcpToolArguments(schema, "原则")

        assertEquals("原则", args["query"]?.toString()?.trim('"'))
        assertEquals("5", args["limit"]?.toString())
    }

    @Test
    fun extractsTextContentFromToolCallResult() {
        val response = """
            {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"Book notes"}],"isError":false}}
        """.trimIndent()

        assertEquals("Book notes", extractMcpTextContent(response))
    }

    @Test
    fun extractsJsonFromSseResponse() {
        val response = """
            event: message
            data: {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"SSE notes"}]}}
        """.trimIndent()

        assertEquals("SSE notes", extractMcpTextContent(response))
    }
}
