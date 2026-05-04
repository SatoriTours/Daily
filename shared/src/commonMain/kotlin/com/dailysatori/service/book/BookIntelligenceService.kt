package com.dailysatori.service.book

import com.dailysatori.data.repository.McpServerRepository
import com.dailysatori.shared.db.Ai_config
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.mcp.RemoteMcpClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class BookViewpointDraft(
    val title: String,
    val content: String,
    val example: String,
)

private val bookIntelligenceJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun isAndroidCallableMcpSource(transport: String): Boolean =
    transport.trim().lowercase() in setOf("remote", "http", "streamable-http")

fun parseBookCandidateJson(response: String): List<BookSearchResult> {
    val array = parseJsonArray(response) ?: return emptyList()
    return array.mapNotNull { item ->
        val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
        val title = obj.stringValue("title").trim()
        if (title.isBlank()) return@mapNotNull null
        BookSearchResult(
            title = title,
            author = obj.stringValue("author"),
            category = obj.stringValue("category"),
            introduction = obj.stringValue("introduction"),
            isbn = obj.stringValue("isbn"),
            coverUrl = obj.stringValue("coverUrl"),
            sourceSummary = obj.stringValue("sourceSummary"),
        )
    }
}

fun parseBookViewpointJson(response: String): List<BookViewpointDraft> {
    val array = parseJsonArray(response) ?: return emptyList()
    return array.mapNotNull { item ->
        val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
        val title = obj.stringValue("title").trim()
        val content = obj.stringValue("content").trim()
        val example = obj.stringValue("example").trim()
        if (title.isBlank() || content.isBlank() || example.isBlank()) return@mapNotNull null
        BookViewpointDraft(title = title, content = content, example = example)
    }.take(10)
}

fun buildBookCandidatePrompt(query: String, sourceNotes: String): String = """
    请基于以下检索需求生成候选书籍。
    查询：$query
    资料摘要：$sourceNotes

    只返回 JSON 数组，不要 Markdown、解释或额外文本。
    每个对象必须包含字段：title、author、category、introduction、isbn、coverUrl、sourceSummary。
    如果某个字段未知，使用空字符串。
""".trimIndent()

fun buildBookViewpointPrompt(
    title: String,
    author: String,
    introduction: String,
    sourceNotes: String,
): String = """
    请为以下书籍生成 10 张结构化观点卡片。
    书名：$title
    作者：$author
    简介：$introduction
    资料摘要：$sourceNotes

    只返回 JSON 数组，不要 Markdown、解释或额外文本。
    数组必须包含 10 个对象，每个对象必须包含字段：title、content、example。
""".trimIndent()

private fun parseJsonArray(response: String): JsonArray? = runCatching {
    bookIntelligenceJson.parseToJsonElement(extractJsonArray(response)).jsonArray
}.getOrNull()

private fun extractJsonArray(response: String): String {
    val trimmed = response.trim()
    val unfenced = if (trimmed.startsWith("```")) {
        trimmed.lines()
            .drop(1)
            .dropLastWhile { it.trim().startsWith("```") || it.isBlank() }
            .joinToString("\n")
            .trim()
    } else {
        trimmed
    }
    return unfenced.substring(unfenced.indexOf('['), unfenced.lastIndexOf(']') + 1)
}

private fun JsonObject.stringValue(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull ?: ""

private fun kotlinx.serialization.json.JsonElement.asJsonObjectOrNull(): JsonObject? =
    runCatching { jsonObject }.getOrNull()

private data class BookSourceNotes(
    val text: String,
    val webResults: List<BookSearchResult>,
)

class BookIntelligenceService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val bookSearchService: BookSearchService,
    private val mcpServerRepository: McpServerRepository,
    private val remoteMcpClient: RemoteMcpClient,
) {
    suspend fun searchBooks(query: String): List<BookSearchResult> {
        val config = aiConfigService.getDefaultConfig() ?: return bookSearchService.search(query)
        if (config.api_token.isBlank()) return bookSearchService.search(query)
        val sourceNotes = collectSourceNotes(query)
        val aiResponse = completeWithDefaultAi(
            config = config,
            prompt = query,
            systemPrompt = buildBookCandidatePrompt(query, sourceNotes.text),
        ) ?: return sourceNotes.webResults
        return parseBookCandidateJson(aiResponse).ifEmpty { sourceNotes.webResults }
    }

    suspend fun generateViewpoints(book: BookSearchResult): List<BookViewpointDraft> {
        val config = aiConfigService.getDefaultConfig() ?: return emptyList()
        if (config.api_token.isBlank()) return emptyList()
        val sourceNotes = collectSourceNotes("${book.title} ${book.author} 核心观点 书评 目录")
        val aiResponse = completeWithDefaultAi(
            config = config,
            prompt = book.title,
            systemPrompt = buildBookViewpointPrompt(
                title = book.title,
                author = book.author,
                introduction = book.introduction,
                sourceNotes = sourceNotes.text,
            ),
        ) ?: return emptyList()
        return parseBookViewpointJson(aiResponse)
    }

    private suspend fun collectSourceNotes(query: String): BookSourceNotes {
        val remoteServers = mcpServerRepository.getEnabled().filter {
            isAndroidCallableMcpSource(it.template_type.ifBlank { it.config_json }) ||
                it.server_url.startsWith("http")
        }
        val remoteNotes = remoteMcpClient.collectSourceNotes(remoteServers, query)
        val webResults = bookSearchService.search(query).take(5)
        val webResultNotes = webResults.joinToString("\n") { result ->
            "- ${result.title} ${result.author}: ${result.introduction.take(300)}"
        }
        val mcpNote = buildMcpSourceNote(remoteServers.isEmpty(), remoteNotes.isNotBlank(), remoteServers.joinToString { it.name })
        return BookSourceNotes("$mcpNote\n$remoteNotes\n$webResultNotes".trim(), webResults)
    }

    private fun buildMcpSourceNote(noRemoteServers: Boolean, hasRemoteNotes: Boolean, names: String): String = when {
        noRemoteServers -> "未发现 Android 可直接调用的远程 MCP，使用 AI 与内置网络搜索兜底。"
        hasRemoteNotes -> "已调用远程 MCP：$names。远程 MCP 失败时使用内置网络搜索兜底。"
        else -> "远程 MCP 未返回可用资料，使用 AI 与内置网络搜索兜底。"
    }

    private suspend fun completeWithDefaultAi(
        config: Ai_config,
        prompt: String,
        systemPrompt: String,
    ): String? {
        return try {
            aiService.complete(
                prompt = prompt,
                apiAddress = config.api_address,
                apiToken = config.api_token,
                modelName = config.model_name,
                provider = config.provider,
                systemPrompt = systemPrompt,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
}
