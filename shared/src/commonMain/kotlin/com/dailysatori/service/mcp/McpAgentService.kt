package com.dailysatori.service.mcp

import co.touchlab.kermit.Logger
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.book.BookSearchResult
import kotlinx.serialization.json.*

data class McpToolResult(val success: Boolean, val data: JsonObject? = null)
data class McpAgentResult(val answer: String, val searchResults: List<McpSearchResult>)
data class McpSearchResult(
    val id: Long,
    val type: String,
    val title: String,
    val summary: String?,
    val createdAt: String?,
    val tags: List<String>? = null,
    val isFavorite: Boolean? = null,
)

class McpAgentService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val toolRegistry: McpToolRegistry,
) {
    private val log = Logger.withTag("MCPAgent")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private const val MAX_TOOL_CALL_ROUNDS = 5
    }

    suspend fun processQuery(
        query: String,
        onStep: (String, String) -> Unit,
    ): McpAgentResult {
        val collectedResults = mutableListOf<McpSearchResult>()
        var currentStepName: String? = null

        fun updateStep(stepName: String, status: String) {
            if (currentStepName != null && currentStepName != stepName) {
                onStep(currentStepName!!, "completed")
            }
            currentStepName = stepName
            onStep(stepName, status)
        }

        fun completeStep() {
            if (currentStepName != null) onStep(currentStepName!!, "completed")
            onStep("完成", "completed")
        }

        return try {
            val config = aiConfigService.getDefaultConfig()
            if (config == null || config.api_address.isBlank() || config.api_token.isBlank()) {
                return McpAgentResult(
                    answer = buildErrorResponse("AI 服务未配置，请先在设置中配置 AI 接口"),
                    searchResults = emptyList(),
                )
            }

            updateStep("正在理解您的问题...", "processing")

            val messages = mutableListOf<JsonObject>()
            messages.add(buildJsonObject {
                put("role", "system")
                put("content", buildSystemPrompt())
            })

            messages.add(buildJsonObject {
                put("role", "user")
                put("content", query)
            })

            val tools = toolRegistry.buildToolDefinitions()
            val apiUrl = config.api_address.trimEnd('/')
            val apiToken = config.api_token
            val modelName = config.model_name
            val provider = config.provider
            var finalAnswer: String? = null

            for (round in 0 until MAX_TOOL_CALL_ROUNDS) {
                val response = aiService.chatCompletion(
                    messages = messages,
                    apiAddress = apiUrl,
                    apiToken = apiToken,
                    modelName = modelName,
                    provider = provider,
                    tools = tools,
                    temperature = 0.7,
                ) ?: return McpAgentResult(
                    answer = buildErrorResponse("AI 请求失败，请稍后重试"),
                    searchResults = collectedResults,
                )

                val message = response["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonObject
                val toolCalls = message?.get("tool_calls")?.jsonArray

                if (message == null) { completeStep(); break }

                if (toolCalls != null && toolCalls.isNotEmpty()) {
                    updateStep("正在查询数据...", "processing")
                    messages.add(buildAssistantMessage(message, toolCalls))
                    executeToolCalls(toolCalls, messages, collectedResults)
                    updateStep("正在生成回答...", "processing")
                } else {
                    finalAnswer = message["content"]?.jsonPrimitive?.contentOrNull
                    completeStep()
                    break
                }
            }

            if (finalAnswer == null) {
                updateStep("正在整理答案...", "processing")
                finalAnswer = fetchFinalAnswer(messages, apiUrl, apiToken, modelName, provider)
                completeStep()
            }

            val filteredResults = filterRelevantResults(collectedResults, finalAnswer ?: "")
            val cleanAnswer = removeRefsTag(finalAnswer ?: buildErrorResponse("无法生成回答"))
            McpAgentResult(answer = cleanAnswer, searchResults = filteredResults)
        } catch (e: Exception) {
            log.e(e) { "MCP Agent processing failed" }
            if (currentStepName != null) onStep(currentStepName!!, "error")
            onStep("处理失败", "error")
            McpAgentResult(
                answer = buildErrorResponse("处理失败: ${e.message}"),
                searchResults = collectedResults,
            )
        }
    }

    private fun buildAssistantMessage(message: JsonObject, toolCalls: kotlinx.serialization.json.JsonArray): JsonObject =
        buildJsonObject {
            put("role", "assistant")
            put("content", message["content"]?.jsonPrimitive?.contentOrNull ?: "")
            put("tool_calls", toolCalls)
        }

    private suspend fun executeToolCalls(
        toolCalls: kotlinx.serialization.json.JsonArray,
        messages: MutableList<JsonObject>,
        collectedResults: MutableList<McpSearchResult>,
    ) {
        for (toolCall in toolCalls) {
            val tc = toolCall.jsonObject
            val function = tc["function"]?.jsonObject
            val toolName = function?.get("name")?.jsonPrimitive?.contentOrNull ?: continue
            val arguments = function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
            val toolCallId = tc["id"]?.jsonPrimitive?.contentOrNull ?: ""

            val toolResult = toolRegistry.executeTool(toolName, arguments)
            collectedResults.addAll(extractSearchResults(toolName, toolResult))

            val resultContent = toolResult.data?.toString() ?: buildJsonObject {
                put("success", toolResult.success); put("error", "unknown")
            }.toString()

            messages.add(buildJsonObject {
                put("role", "tool")
                put("tool_call_id", toolCallId)
                put("content", resultContent)
            })
        }
    }

    private suspend fun fetchFinalAnswer(
        messages: MutableList<JsonObject>,
        apiUrl: String,
        apiToken: String,
        modelName: String,
        provider: String,
    ): String? {
        val response = aiService.chatCompletion(
            messages = messages, apiAddress = apiUrl, apiToken = apiToken,
            modelName = modelName, provider = provider, temperature = 0.7,
        )
        return response?.let {
            it["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.contentOrNull
        }
    }

    private fun buildSystemPrompt(): String {
        val now = kotlinx.datetime.Clock.System.now()
        val epochMs = now.toEpochMilliseconds()
        val today = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs).toString().substring(0, 10)
        val yesterday = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs - 86400000).toString().substring(0, 10)
        val beforeYesterday = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs - 172800000).toString().substring(0, 10)
        val currentTime = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs).toString().substring(0, 19)

        return """你是一个智能助手，专门帮助用户从他们的个人数据中查找和总结信息。用户的数据包括：
- **日记**: 用户的个人日记记录
- **文章**: 用户收藏的网页文章
- **书籍**: 用户添加的书籍和读书笔记
- **记忆**: 用户的记忆库，包含核心偏好、内容摘要和对话关键信息

## 核心规则

**你只能基于用户的个人数据来回答问题，不要使用你的通用知识来回答。**
同时优先在记忆库中搜索相关信息。记忆库包含你的核心偏好、所有内容的AI摘要和之前对话的关键信息。

当用户提问时，你必须：
1. **首先使用搜索工具**查找用户数据中的相关内容
2. **优先使用 search_memory 工具**在记忆库中搜索
3. **基于搜索结果**来生成回答
4. 如果没有找到相关内容，告知用户"在您的数据中没有找到相关信息"

**禁止行为**：
- 不要直接用你的知识回答问题
- 不要跳过搜索步骤直接给答案
- 不要编造用户数据中不存在的内容

## 工具使用指南

### 日记相关
- `get_latest_diary`: 获取最新的日记
- `get_diary_by_date`: 获取指定日期的日记，日期格式为 YYYY-MM-DD
- `search_diary_by_content`: 按关键词搜索日记内容
- `get_diary_by_tag`: 按标签获取日记
- `get_diary_count`: 获取日记总数

### 文章相关
- `get_latest_articles`: 获取最新收藏的文章
- `search_articles`: 按关键词搜索文章
- `get_favorite_articles`: 获取标记为喜爱的文章
- `get_article_count`: 获取文章总数

### 书籍相关
- `get_latest_books`: 获取最新添加的书籍
- `search_books`: 按书名、作者或分类搜索书籍
- `search_book_notes`: 按关键词搜索读书笔记
- `get_book_viewpoints`: 获取指定书籍的读书笔记
- `get_book_count`: 获取书籍总数

### 综合
- `get_statistics`: 获取应用数据统计

### 记忆相关
- `search_memory`: 搜索你的记忆库（包含核心偏好、内容摘要、对话记忆）。可用于查找你的偏好、过去的内容要点等
- `get_memory_source`: 获取指定来源的完整记忆内容，可按 source_type (article/diary/book/book_viewpoint/chat) 和 source_id 查询

## 日期处理规则
- "今天" → "$today"
- "昨天" → "$yesterday"
- "前天" → "$beforeYesterday"

## 回答格式要求
1. 用自然语言总结，不要返回原始 JSON
2. 重要信息用 **加粗**
3. 无结果时友好告知
4. 在回答末尾用特定格式标注引用来源：
```
<!-- refs: article_123, diary_456, book_789 -->
```
如果没有引用任何内容，标注 `<!-- refs: none -->`

当前时间: $currentTime
"""
    }

    private fun extractSearchResults(toolName: String, result: McpToolResult): List<McpSearchResult> {
        if (!result.success || result.data == null) return emptyList()
        val data = result.data

        fun jsonString(obj: JsonObject, key: String): String? =
            obj[key]?.jsonPrimitive?.contentOrNull
        fun jsonLong(obj: JsonObject, key: String): Long? =
            obj[key]?.jsonPrimitive?.longOrNull
        fun jsonBool(obj: JsonObject, key: String): Boolean? =
            obj[key]?.jsonPrimitive?.booleanOrNull

        return when {
            toolName.contains("diary") -> extractDiaryResults(data, ::jsonString, ::jsonLong, ::jsonBool)
            toolName.contains("article") -> extractArticleResults(data, ::jsonString, ::jsonLong, ::jsonBool)
            toolName.contains("book") && !toolName.contains("note") -> extractBookResults(data, ::jsonString, ::jsonLong)
            toolName.contains("book") && toolName.contains("note") -> extractNoteResults(data, ::jsonString, ::jsonLong)
            else -> emptyList()
        }
    }

    private fun extractDiaryResults(
        data: JsonObject,
        jsonString: (JsonObject, String) -> String?,
        jsonLong: (JsonObject, String) -> Long?,
        jsonBool: (JsonObject, String) -> Boolean?,
    ): List<McpSearchResult> {
        val diaries = data["diaries"]?.jsonArray ?: return emptyList()
        return diaries.mapNotNull { item ->
            val d = item.jsonObject
            val tags: List<String>? = when (val t = d["tags"]) {
                is kotlinx.serialization.json.JsonPrimitive -> t.contentOrNull?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                is kotlinx.serialization.json.JsonArray -> t.mapNotNull { it.jsonPrimitive.contentOrNull }
                else -> null
            }
            McpSearchResult(
                id = jsonLong(d, "id") ?: return@mapNotNull null,
                type = "diary",
                title = generateDiaryTitle(d),
                summary = truncateNullable(jsonString(d, "content"), 100),
                createdAt = jsonString(d, "createdAt"),
                tags = tags,
            )
        }
    }

    private fun extractArticleResults(
        data: JsonObject,
        jsonString: (JsonObject, String) -> String?,
        jsonLong: (JsonObject, String) -> Long?,
        jsonBool: (JsonObject, String) -> Boolean?,
    ): List<McpSearchResult> {
        val articles = data["articles"]?.jsonArray ?: return emptyList()
        return articles.mapNotNull { item ->
            val a = item.jsonObject
            McpSearchResult(
                id = jsonLong(a, "id") ?: return@mapNotNull null,
                type = "article",
                title = jsonString(a, "title") ?: "未知标题",
                summary = truncateNullable(jsonString(a, "content"), 100),
                createdAt = jsonString(a, "createdAt"),
                isFavorite = jsonBool(a, "isFavorite") ?: (jsonLong(a, "isFavorite") == 1L),
            )
        }
    }

    private fun extractBookResults(
        data: JsonObject,
        jsonString: (JsonObject, String) -> String?,
        jsonLong: (JsonObject, String) -> Long?,
    ): List<McpSearchResult> {
        val books = data["books"]?.jsonArray ?: return emptyList()
        return books.mapNotNull { item ->
            val b = item.jsonObject
            McpSearchResult(
                id = jsonLong(b, "id") ?: return@mapNotNull null,
                type = "book",
                title = jsonString(b, "title") ?: "未知书名",
                summary = jsonString(b, "author"),
                createdAt = jsonString(b, "createdAt"),
            )
        }
    }

    private fun extractNoteResults(
        data: JsonObject,
        jsonString: (JsonObject, String) -> String?,
        jsonLong: (JsonObject, String) -> Long?,
    ): List<McpSearchResult> {
        val notes = data["notes"]?.jsonArray ?: return emptyList()
        return notes.mapNotNull { item ->
            val n = item.jsonObject
            McpSearchResult(
                id = jsonLong(n, "id") ?: return@mapNotNull null,
                type = "book",
                title = jsonString(n, "bookTitle") ?: "未知书籍",
                summary = truncateNullable(jsonString(n, "title"), 100),
                createdAt = null,
            )
        }
    }

    private fun filterRelevantResults(
        results: List<McpSearchResult>,
        answer: String,
    ): List<McpSearchResult> {
        if (results.isEmpty() || answer.isEmpty()) return results

        val refsMatch = Regex("<!--\\s*refs:\\s*([^>]+)\\s*-->").find(answer)
        if (refsMatch == null) return filterByTitleMatch(results, answer)

        val refsContent = refsMatch.groupValues[1].trim()
        if (refsContent.lowercase() == "none") return emptyList()
        if (refsContent.isEmpty()) return filterByTitleMatch(results, answer)

        val referencedIds = parseReferencedIds(refsContent)
        if (referencedIds.values.sumOf { it.size } == 0) {
            return filterByTitleMatch(results, answer)
        }

        val filtered = results.filter { r ->
            when (r.type) {
                "article" -> referencedIds["article"]?.contains(r.id) == true
                "diary" -> referencedIds["diary"]?.contains(r.id) == true
                "book" -> referencedIds["book"]?.contains(r.id) == true
                else -> false
            }
        }

        return if (filtered.isEmpty() && results.isNotEmpty()) filterByTitleMatch(results, answer)
        else filtered
    }

    private fun parseReferencedIds(refsContent: String): Map<String, MutableSet<Long>> {
        val ids = mutableMapOf(
            "article" to mutableSetOf<Long>(),
            "diary" to mutableSetOf<Long>(),
            "book" to mutableSetOf<Long>(),
        )
        for (ref in refsContent.split(",").map { it.trim() }) {
            val match = Regex("(article|diary|book)_(\\d+)").find(ref) ?: continue
            val type = match.groupValues[1]
            val id = match.groupValues[2].toLongOrNull() ?: continue
            ids[type]?.add(id)
        }
        return ids
    }

    private fun filterByTitleMatch(results: List<McpSearchResult>, answer: String): List<McpSearchResult> {
        val answerLower = answer.lowercase()
        return results.filter { result ->
            val keywords = result.title
                .replace(Regex("[^\\w\\u4e00-\\u9fa5]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length >= 2 }
            keywords.any { answerLower.contains(it.lowercase()) }
        }
    }

    private fun removeRefsTag(answer: String): String =
        answer.replace(Regex("\\n*<!--\\s*refs:[^>]*-->\\s*$"), "").trim()

    private fun buildErrorResponse(message: String): String =
        """😔 **出现问题**

$message

**建议**:
- 检查网络连接
- 确保 AI 服务配置正确
- 稍后重试"""

    private fun generateDiaryTitle(data: JsonObject): String {
        val createdAt = data["createdAt"]?.jsonPrimitive?.contentOrNull ?: return "日记"
        return try {
            val parts = createdAt.substring(0, 10).split("-")
            if (parts.size == 3) "${parts[0]}年${parts[1]}月${parts[2]}日的日记" else "日记"
        } catch (_: Exception) { "日记" }
    }

    private fun truncateNullable(text: String?, maxLen: Int): String? {
        if (text.isNullOrEmpty()) return null
        return if (text.length <= maxLen) text else text.substring(0, maxLen) + "..."
    }

    suspend fun searchBookOnline(query: String): List<BookSearchResult> {
        val config = aiConfigService.getDefaultConfig() ?: return emptyList()
        if (config.api_token.isBlank()) return emptyList()
        val systemPrompt = buildBookSearchPrompt(query)
        val response = aiService.complete(
            prompt = query,
            apiAddress = config.api_address,
            apiToken = config.api_token,
            modelName = config.model_name,
            provider = config.provider,
            systemPrompt = systemPrompt,
        )
        return parseBookSearchResults(response)
    }

    private fun buildBookSearchPrompt(query: String): String =
        """你是一个书籍搜索引擎。用户想了解关于"$query"的书籍信息。
请以 JSON 数组格式返回搜索结果，每个元素包含以下字段：
- title: 书名（字符串）
- author: 作者（字符串）
- introduction: 内容简介，200字以内（字符串）
- coverUrl: 封面图片URL，如果没有则为空字符串

只返回 JSON 数组，不要其他文字。示例格式：
[{"title":"书籍名称","author":"作者名","introduction":"内容简介...","coverUrl":""}]"""

    private fun parseBookSearchResults(response: String): List<BookSearchResult> {
        if (response.isBlank()) return emptyList()
        return try {
            val cleaned = response.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val array = json.parseToJsonElement(cleaned).jsonArray
            array.map { el ->
                val obj = el.jsonObject
                BookSearchResult(
                    title = obj["title"]?.jsonPrimitive?.content ?: "",
                    author = obj["author"]?.jsonPrimitive?.content ?: "",
                    introduction = obj["introduction"]?.jsonPrimitive?.content ?: "",
                    coverUrl = obj["coverUrl"]?.jsonPrimitive?.content ?: "",
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}
