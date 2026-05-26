package com.dailysatori.service.mcp

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
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
    fun prefersSearchQueryArgumentWhenMcpSchemaProvidesIt() {
        val schema = buildJsonObject {
            put("properties", buildJsonObject {
                put("query", buildJsonObject { put("type", JsonPrimitive("string")) })
                put("search_query", buildJsonObject { put("type", JsonPrimitive("string")) })
            })
        }

        val args = buildMcpToolArguments(schema, "新书 观点")

        assertEquals("新书 观点", args["search_query"]?.toString()?.trim('"'))
        assertEquals(null, args["query"])
    }

    @Test
    fun ranksSearchToolsBeforeReaderToolsForPlainQueries() {
        val tools = listOf(
            RemoteMcpTool("webReader", "Read webpage content", buildJsonObject { }),
            RemoteMcpTool("webSearchPrime", "Search web pages", buildJsonObject { }),
        )

        val best = selectBestWebSearchTool(tools)

        assertEquals("webSearchPrime", best?.name)
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

    @Test
    fun strictToolCountRejectsJsonRpcError() {
        val response = """
            {"jsonrpc":"2.0","id":2,"error":{"code":-32601,"message":"Method not found"}}
        """.trimIndent()

        val error = assertFailsWith<IllegalStateException> { strictMcpToolCount(response) }

        assertEquals("Method not found", error.message)
    }

    @Test
    fun strictToolCountRejectsInvalidToolsListResponse() {
        val response = """
            {"jsonrpc":"2.0","id":2,"result":{}}
        """.trimIndent()

        val error = assertFailsWith<IllegalStateException> { strictMcpToolCount(response) }

        assertEquals("Invalid MCP tools/list response", error.message)
    }

    @Test
    fun strictToolCountReadsValidToolsListResponse() {
        val response = """
            {"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"search"},{"name":"read"}]}}
        """.trimIndent()

        assertEquals(2, strictMcpToolCount(response))
    }
}
