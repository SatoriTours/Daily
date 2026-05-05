package com.dailysatori.service.mcp

import com.dailysatori.shared.db.Mcp_server
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val MCP_PROTOCOL_VERSION = "2025-06-18"

class RemoteMcpClient(private val client: HttpClient) {
    private var requestId = 1

    suspend fun collectSourceNotes(servers: List<Mcp_server>, query: String): String =
        servers.mapNotNull { server -> callSearchTool(server, query) }
            .take(3)
            .joinToString("\n")

    suspend fun collectWebSearchNotes(servers: List<Mcp_server>, query: String): String =
        servers.filter { it.enabled == 1L && it.server_url.startsWith("http") }
            .mapNotNull { server -> callWebSearchTool(server, query) }
            .take(3)
            .joinToString("\n")

    private suspend fun callSearchTool(server: Mcp_server, query: String): String? = try {
        val sessionId = initialize(server)
        sendInitialized(server, sessionId)
        val tool = listTools(server, sessionId).firstOrNull {
            isLikelyBookSearchTool(it.name, it.description)
        } ?: return null
        val result = postJsonRpc(
            server = server,
            sessionId = sessionId,
            method = "tools/call",
            params = buildJsonObject {
                put("name", JsonPrimitive(tool.name))
                put("arguments", buildMcpToolArguments(tool.inputSchema, query))
            },
        )
        val text = extractMcpTextContent(result.body).take(1200)
        if (text.isBlank()) null else "远程 MCP ${server.name}/${tool.name}: $text"
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun callWebSearchTool(server: Mcp_server, query: String): String? = try {
        val sessionId = initialize(server)
        sendInitialized(server, sessionId)
        val tool = listTools(server, sessionId).firstOrNull {
            isLikelyWebSearchTool(it.name, it.description)
        } ?: return null
        val result = postJsonRpc(
            server = server,
            sessionId = sessionId,
            method = "tools/call",
            params = buildJsonObject {
                put("name", JsonPrimitive(tool.name))
                put("arguments", buildMcpToolArguments(tool.inputSchema, query))
            },
        )
        val text = extractMcpTextContent(result.body).take(2000)
        if (text.isBlank()) null else "外部 MCP ${server.name}/${tool.name}: $text"
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun initialize(server: Mcp_server): String? {
        val response = postJsonRpc(
            server = server,
            sessionId = null,
            method = "initialize",
            params = buildJsonObject {
                put("protocolVersion", JsonPrimitive(MCP_PROTOCOL_VERSION))
                put("capabilities", JsonObject(emptyMap()))
                put("clientInfo", buildJsonObject {
                    put("name", JsonPrimitive("daily-satori"))
                    put("version", JsonPrimitive("1.0"))
                })
            },
        )
        return response.sessionId ?: extractMcpSessionId(response.body)
    }

    private suspend fun sendInitialized(server: Mcp_server, sessionId: String?) {
        postJsonRpc(
            server = server,
            sessionId = sessionId,
            method = "notifications/initialized",
            params = JsonObject(emptyMap()),
            hasId = false,
        )
    }

    private suspend fun listTools(server: Mcp_server, sessionId: String?): List<RemoteMcpTool> {
        val response = postJsonRpc(server, sessionId, "tools/list", JsonObject(emptyMap()))
        val root = parseMcpJsonResponse(response.body) ?: return emptyList()
        return root["result"]?.jsonObject?.get("tools")?.jsonArray.orEmpty().mapNotNull { item ->
            val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
            val name = obj.stringValue("name")
            if (name.isBlank()) return@mapNotNull null
            RemoteMcpTool(
                name = name,
                description = obj.stringValue("description"),
                inputSchema = obj["inputSchema"]?.asJsonObjectOrNull() ?: JsonObject(emptyMap()),
            )
        }
    }

    private suspend fun postJsonRpc(
        server: Mcp_server,
        sessionId: String?,
        method: String,
        params: JsonObject,
        hasId: Boolean = true,
    ): McpHttpResponse {
        val id = requestId++
        val response = client.post(server.server_url) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            header("MCP-Protocol-Version", MCP_PROTOCOL_VERSION)
            header("Mcp-Method", method)
            if (sessionId != null) header("MCP-Session-Id", sessionId)
            if (server.api_key.isNotBlank()) bearerAuth(server.api_key)
            setBody(buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                if (hasId) put("id", JsonPrimitive(id))
                put("method", JsonPrimitive(method))
                put("params", params)
            }.toString())
        }
        return McpHttpResponse(
            body = response.bodyAsText(),
            sessionId = response.headers["Mcp-Session-Id"] ?: response.headers["MCP-Session-Id"],
        )
    }
}

private data class McpHttpResponse(
    val body: String,
    val sessionId: String?,
)

data class RemoteMcpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

fun isLikelyBookSearchTool(name: String, description: String): Boolean {
    val text = "$name $description".lowercase()
    val canSearch = listOf("search", "query", "find", "web").any { it in text }
    val relevant = listOf("book", "books", "isbn", "web", "google", "wiki").any { it in text }
    return canSearch && relevant
}

fun isLikelyWebSearchTool(name: String, description: String): Boolean {
    val text = "$name $description".lowercase()
    val canSearch = listOf("search", "query", "find", "web", "reader", "read").any { it in text }
    val relevant = listOf("web", "internet", "page", "url", "search", "reader").any { it in text }
    val irrelevant = listOf("weather", "balance", "model", "image", "video", "audio").any { it in text }
    return canSearch && relevant && !irrelevant
}

fun buildMcpToolArguments(inputSchema: JsonObject, query: String): JsonObject {
    val properties = inputSchema["properties"]?.jsonObject ?: return buildJsonObject { put("query", JsonPrimitive(query)) }
    val queryKey = listOf("query", "q", "keyword", "keywords", "search", "text")
        .firstOrNull { properties.containsKey(it) } ?: properties.keys.firstOrNull() ?: "query"
    return buildJsonObject {
        put(queryKey, JsonPrimitive(query))
        if (properties.containsKey("limit")) put("limit", JsonPrimitive(5))
    }
}

fun extractMcpTextContent(response: String): String {
    val root = parseMcpJsonResponse(response) ?: return ""
    val content = root["result"]?.jsonObject?.get("content")?.jsonArray ?: return root.toString()
    return content.mapNotNull { item ->
        val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
        obj.stringValue("text").ifBlank { obj.toString() }
    }.joinToString("\n")
}

fun parseMcpJsonResponse(response: String): JsonObject? {
    val jsonText = response.lines()
        .firstOrNull { it.trimStart().startsWith("data:") }
        ?.substringAfter("data:")
        ?.trim()
        ?: response.trim()
    return runCatching { Json.parseToJsonElement(jsonText).jsonObject }.getOrNull()
}

private fun extractMcpSessionId(response: String): String? =
    parseMcpJsonResponse(response)?.get("result")?.jsonObject?.get("sessionId")?.jsonPrimitive?.contentOrNull

private fun JsonObject.stringValue(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull ?: ""

private fun JsonElement.asJsonObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
