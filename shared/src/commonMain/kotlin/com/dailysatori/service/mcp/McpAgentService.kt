package com.dailysatori.service.mcp

import co.touchlab.kermit.Logger
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.book.BookSearchResult
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.Book
import com.dailysatori.shared.db.Book_viewpoint
import com.dailysatori.shared.db.Diary
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
    private val articleRepo: ArticleRepository,
    private val diaryRepo: DiaryRepository,
    private val bookRepo: BookRepository,
    private val viewpointRepo: BookViewpointRepository,
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

            val tools = buildToolDefinitions()
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
                )

                if (response == null) {
                    return McpAgentResult(
                        answer = buildErrorResponse("AI 请求失败，请稍后重试"),
                        searchResults = collectedResults,
                    )
                }

                val choice = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                val message = choice?.get("message")?.jsonObject
                val toolCalls = message?.get("tool_calls")?.jsonArray

                if (message == null) {
                    completeStep()
                    break
                }

                if (toolCalls != null && toolCalls.isNotEmpty()) {
                    updateStep("正在查询数据...", "processing")

                    messages.add(buildJsonObject {
                        put("role", "assistant")
                        put("content", message["content"]?.jsonPrimitive?.contentOrNull ?: "")
                        put("tool_calls", toolCalls)
                    })

                    for (toolCall in toolCalls) {
                        val tc = toolCall.jsonObject
                        val function = tc["function"]?.jsonObject
                        val toolName = function?.get("name")?.jsonPrimitive?.contentOrNull ?: continue
                        val arguments = function["arguments"]?.jsonPrimitive?.contentOrNull ?: "{}"
                        val toolCallId = tc["id"]?.jsonPrimitive?.contentOrNull ?: ""

                        val toolResult = executeTool(toolName, arguments)
                        collectedResults.addAll(extractSearchResults(toolName, toolResult))

                        val resultContent = toolResult.data?.toString() ?: buildJsonObject {
                            put("success", toolResult.success)
                            put("error", "unknown")
                        }.toString()

                        messages.add(buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", toolCallId)
                            put("content", resultContent)
                        })
                    }
                    updateStep("正在生成回答...", "processing")
                } else {
                    finalAnswer = message["content"]?.jsonPrimitive?.contentOrNull
                    completeStep()
                    break
                }
            }

            if (finalAnswer == null) {
                updateStep("正在整理答案...", "processing")
                val response = aiService.chatCompletion(
                    messages = messages,
                    apiAddress = apiUrl,
                    apiToken = apiToken,
                    modelName = modelName,
                    provider = provider,
                    temperature = 0.7,
                )
                finalAnswer = response?.let {
                    val choice = it["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                    val msg = choice?.get("message")?.jsonObject
                    msg?.get("content")?.jsonPrimitive?.contentOrNull
                }
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

    private fun buildSystemPrompt(): String {
        val now = kotlinx.datetime.Clock.System.now()
        val today = kotlinx.datetime.Instant.fromEpochMilliseconds(
            now.toEpochMilliseconds()
        ).toString().substring(0, 10)
        val yesterday = kotlinx.datetime.Instant.fromEpochMilliseconds(
            now.toEpochMilliseconds() - 86400000
        ).toString().substring(0, 10)
        val beforeYesterday = kotlinx.datetime.Instant.fromEpochMilliseconds(
            now.toEpochMilliseconds() - 172800000
        ).toString().substring(0, 10)
        val currentTime = kotlinx.datetime.Instant.fromEpochMilliseconds(
            now.toEpochMilliseconds()
        ).toString().substring(0, 19)

        return """你是一个智能助手，专门帮助用户从他们的个人数据中查找和总结信息。用户的数据包括：
- **日记**: 用户的个人日记记录
- **文章**: 用户收藏的网页文章
- **书籍**: 用户添加的书籍和读书笔记

## 核心规则

**你只能基于用户的个人数据来回答问题，不要使用你的通用知识来回答。**

当用户提问时，你必须：
1. **首先使用搜索工具**查找用户数据中的相关内容
2. **基于搜索结果**来生成回答
3. 如果没有找到相关内容，告知用户"在您的数据中没有找到相关信息"

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

    private fun buildToolDefinitions(): List<JsonObject> = listOf(
        buildTool("get_latest_diary", "获取最新的日记条目", mapOf(
            "limit" to buildParam("integer", "返回的日记数量，默认为5，最大为20"),
        )),
        buildTool("get_diary_by_date", "获取指定日期的日记", mapOf(
            "date" to buildParam("string", "日期，格式为YYYY-MM-DD或相对日期如today,yesterday"),
        ), listOf("date")),
        buildTool("search_diary_by_content", "按内容关键词搜索日记", mapOf(
            "keyword" to buildParam("string", "搜索关键词，多个关键词用逗号分隔"),
            "limit" to buildParam("integer", "返回的最大数量，默认为20"),
        ), listOf("keyword")),
        buildTool("get_diary_by_tag", "获取指定标签的日记", mapOf(
            "tag" to buildParam("string", "标签名称"),
            "limit" to buildParam("integer", "返回的最大数量，默认为10"),
        ), listOf("tag")),
        buildTool("get_diary_count", "获取日记的总数量"),
        buildTool("get_latest_articles", "获取最新收藏的文章", mapOf(
            "limit" to buildParam("integer", "返回的文章数量，默认为5"),
        )),
        buildTool("search_articles", "按关键词搜索文章", mapOf(
            "keyword" to buildParam("string", "搜索关键词，多个关键词用逗号分隔"),
            "limit" to buildParam("integer", "返回的最大数量，默认为20"),
        ), listOf("keyword")),
        buildTool("get_favorite_articles", "获取标记为喜爱的文章", mapOf(
            "limit" to buildParam("integer", "返回的最大数量，默认为10"),
        )),
        buildTool("get_article_count", "获取文章的总数量"),
        buildTool("get_latest_books", "获取最新添加的书籍", mapOf(
            "limit" to buildParam("integer", "返回的书籍数量，默认为5"),
        )),
        buildTool("search_books", "按标题、作者或分类搜索书籍", mapOf(
            "keyword" to buildParam("string", "搜索关键词，多个关键词用逗号分隔"),
            "limit" to buildParam("integer", "返回的最大数量，默认为15"),
        ), listOf("keyword")),
        buildTool("search_book_notes", "搜索读书笔记内容", mapOf(
            "keyword" to buildParam("string", "搜索关键词，多个关键词用逗号分隔"),
            "limit" to buildParam("integer", "返回的最大数量，默认为20"),
        ), listOf("keyword")),
        buildTool("get_book_viewpoints", "获取书籍的读书笔记/观点", mapOf(
            "book_id" to buildParam("integer", "书籍ID"),
        ), listOf("book_id")),
        buildTool("get_book_count", "获取书籍的总数量"),
        buildTool("get_statistics", "获取应用的综合统计信息"),
    )

    private fun buildTool(
        name: String,
        description: String,
        properties: Map<String, JsonObject> = emptyMap(),
        required: List<String> = emptyList(),
    ): JsonObject = buildJsonObject {
        put("type", "function")
        put("function", buildJsonObject {
            put("name", name)
            put("description", description)
            put("parameters", buildJsonObject {
                put("type", "object")
                put("properties", JsonObject(properties))
                put("required", JsonArray(required.map { JsonPrimitive(it) }))
            })
        })
    }

    private fun buildParam(type: String, description: String): JsonObject = buildJsonObject {
        put("type", type)
        put("description", description)
    }

    private fun executeTool(toolName: String, argumentsStr: String): McpToolResult {
        val args = try {
            json.parseToJsonElement(argumentsStr).jsonObject
        } catch (_: Exception) {
            JsonObject(emptyMap())
        }

        return try {
            when (toolName) {
                "get_latest_diary" -> getLatestDiary(args)
                "get_diary_by_date" -> getDiaryByDate(args)
                "search_diary_by_content" -> searchDiary(args)
                "get_diary_by_tag" -> getDiaryByTag(args)
                "get_diary_count" -> getDiaryCount()
                "get_latest_articles" -> getLatestArticles(args)
                "search_articles" -> searchArticles(args)
                "get_favorite_articles" -> getFavoriteArticles(args)
                "get_article_count" -> getArticleCount()
                "get_latest_books" -> getLatestBooks(args)
                "search_books" -> searchBooks(args)
                "search_book_notes" -> searchBookNotes(args)
                "get_book_viewpoints" -> getBookViewpoints(args)
                "get_book_count" -> getBookCount()
                "get_statistics" -> getStatistics()
                else -> errorResult("未知工具: $toolName")
            }
        } catch (e: Exception) {
            log.e(e) { "Tool execution failed: $toolName" }
            errorResult("工具执行失败: ${e.message}")
        }
    }

    private fun getLatestDiary(args: JsonObject): McpToolResult {
        val limit = intParam(args, "limit", 5)
        val diaries = diaryRepo.getLatestSync(limit)
        return successResult("diaries" to diaryListToJson(diaries))
    }

    private fun getDiaryByDate(args: JsonObject): McpToolResult {
        val dateStr = stringParam(args, "date") ?: return errorResult("缺少参数: date")
        val date = parseDate(dateStr) ?: return errorResult("无效日期格式，请使用 YYYY-MM-DD")
        val startMs = date.toEpochMilliseconds()
        val endMs = startMs + 86400000
        val diaries = diaryRepo.getByDateRangeSync(startMs, endMs)
        return successResult(
            "date" to JsonPrimitive(dateStr),
            "diaries" to diaryListToJson(diaries),
        )
    }

    private fun searchDiary(args: JsonObject): McpToolResult {
        val keyword = stringParam(args, "keyword") ?: return errorResult("缺少参数: keyword")
        val limit = intParam(args, "limit", 20)
        val results = searchWithKeywords(keyword) { kw -> diaryRepo.searchSync(kw) }
        return successResult(
            "keyword" to JsonPrimitive(keyword),
            "diaries" to diaryListToJson(results.take(limit)),
        )
    }

    private fun getDiaryByTag(args: JsonObject): McpToolResult {
        val tag = stringParam(args, "tag") ?: return errorResult("缺少参数: tag")
        val limit = intParam(args, "limit", 10)
        val allDiaries = diaryRepo.getAllSync()
        val filtered = allDiaries.filter { diary ->
            diary.tags?.split(",")?.map { it.trim() }?.any { it.equals(tag, ignoreCase = true) } == true
        }
        return successResult(
            "tag" to JsonPrimitive(tag),
            "diaries" to diaryListToJson(filtered.take(limit)),
        )
    }

    private fun getDiaryCount(): McpToolResult =
        successResult("count" to JsonPrimitive(diaryRepo.count()))

    private fun getLatestArticles(args: JsonObject): McpToolResult {
        val limit = intParam(args, "limit", 5)
        val articles = articleRepo.getLatestSync(limit)
        return successResult("articles" to articleListToJson(articles))
    }

    private fun searchArticles(args: JsonObject): McpToolResult {
        val keyword = stringParam(args, "keyword") ?: return errorResult("缺少参数: keyword")
        val limit = intParam(args, "limit", 20)
        val results = searchWithKeywords(keyword) { kw -> articleRepo.searchSync(kw) }
        return successResult(
            "keyword" to JsonPrimitive(keyword),
            "articles" to articleListToJson(results.take(limit)),
        )
    }

    private fun getFavoriteArticles(args: JsonObject): McpToolResult {
        val limit = intParam(args, "limit", 10)
        val articles = articleRepo.getFavoritesSync()
        return successResult("articles" to articleListToJson(articles.take(limit)))
    }

    private fun getArticleCount(): McpToolResult =
        successResult("count" to JsonPrimitive(articleRepo.count()))

    private fun getLatestBooks(args: JsonObject): McpToolResult {
        val limit = intParam(args, "limit", 5)
        val books = bookRepo.getAllSync()
        return successResult("books" to bookListToJson(books.take(limit)))
    }

    private fun searchBooks(args: JsonObject): McpToolResult {
        val keyword = stringParam(args, "keyword") ?: return errorResult("缺少参数: keyword")
        val limit = intParam(args, "limit", 15)
        val results = searchWithKeywords(keyword) { kw -> bookRepo.searchSync(kw) }
        return successResult(
            "keyword" to JsonPrimitive(keyword),
            "books" to bookListToJson(results.take(limit)),
        )
    }

    private fun searchBookNotes(args: JsonObject): McpToolResult {
        val keyword = stringParam(args, "keyword") ?: return errorResult("缺少参数: keyword")
        val limit = intParam(args, "limit", 20)
        val results = searchWithKeywords(keyword) { kw -> viewpointRepo.searchByContentSync(kw) }
        val booksMap = bookRepo.getAllSync().associateBy { it.id }
        val notesJson = JsonArray(results.take(limit).map { vp ->
            val book = booksMap[vp.book_id]
            buildJsonObject {
                put("id", vp.id)
                put("title", vp.title)
                put("content", truncate(vp.content, 500))
                put("bookId", vp.book_id)
                put("bookTitle", book?.title ?: "未知书籍")
                put("bookAuthor", book?.author ?: "")
            }
        })
        return successResult(
            "keyword" to JsonPrimitive(keyword),
            "notes" to notesJson,
        )
    }

    private fun getBookViewpoints(args: JsonObject): McpToolResult {
        val bookId = longParam(args, "book_id") ?: return errorResult("缺少参数: book_id")
        val book = bookRepo.getById(bookId) ?: return errorResult("未找到书籍: $bookId")
        val viewpoints = viewpointRepo.getByBookSync(bookId)
        return successResult(
            "book" to buildJsonObject {
                put("id", book.id)
                put("title", book.title)
                put("author", book.author)
            },
            "viewpoints" to JsonArray(viewpoints.map { vp ->
                buildJsonObject {
                    put("id", vp.id)
                    put("title", vp.title)
                    put("content", truncate(vp.content, 500))
                }
            }),
        )
    }

    private fun getBookCount(): McpToolResult =
        successResult("count" to JsonPrimitive(bookRepo.count()))

    private fun getStatistics(): McpToolResult = successResult(
        "statistics" to buildJsonObject {
            put("articles", articleRepo.count())
            put("diaries", diaryRepo.count())
            put("books", bookRepo.count())
        },
    )

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
            toolName.contains("diary") -> {
                val diaries = data["diaries"]?.jsonArray ?: return emptyList()
                diaries.mapNotNull { item ->
                    val d = item.jsonObject
                    val tags: List<String>? = when (val t = d["tags"]) {
                        is JsonPrimitive -> t.contentOrNull?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                        is JsonArray -> t.mapNotNull { it.jsonPrimitive.contentOrNull }
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
            toolName.contains("article") -> {
                val articles = data["articles"]?.jsonArray ?: return emptyList()
                articles.mapNotNull { item ->
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
            toolName.contains("book") && !toolName.contains("note") -> {
                val books = data["books"]?.jsonArray ?: return emptyList()
                books.mapNotNull { item ->
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
            toolName.contains("book") && toolName.contains("note") -> {
                val notes = data["notes"]?.jsonArray ?: return emptyList()
                notes.mapNotNull { item ->
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
            else -> emptyList()
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

        val referencedIds = mutableMapOf<String, MutableSet<Long>>(
            "article" to mutableSetOf(),
            "diary" to mutableSetOf(),
            "book" to mutableSetOf(),
        )

        for (ref in refsContent.split(",").map { it.trim() }) {
            val match = Regex("(article|diary|book)_(\\d+)").find(ref) ?: continue
            val type = match.groupValues[1]
            val id = match.groupValues[2].toLongOrNull() ?: continue
            referencedIds[type]?.add(id)
        }

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

    private fun successResult(vararg pairs: Pair<String, JsonElement>): McpToolResult {
        val obj = buildJsonObject {
            put("success", true)
            for ((key, value) in pairs) {
                put(key, value)
            }
            val countFields = setOf("diaries", "articles", "books", "viewpoints", "notes")
            for ((key, value) in pairs) {
                if (key in countFields && value is JsonArray) {
                    put("count", value.size)
                }
            }
        }
        return McpToolResult(true, obj)
    }

    private fun errorResult(message: String): McpToolResult {
        val obj = buildJsonObject {
            put("success", false)
            put("error", message)
        }
        return McpToolResult(false, obj)
    }

    private fun diaryToJson(diary: Diary): JsonObject = buildJsonObject {
        put("id", diary.id)
        put("content", truncate(diary.content, 500))
        put("tags", diary.tags ?: "")
        put("mood", diary.mood ?: "")
        put("createdAt", formatDate(diary.created_at))
    }

    private fun diaryListToJson(diaries: List<Diary>): JsonArray =
        JsonArray(diaries.map { diaryToJson(it) })

    private fun articleToJson(article: Article): JsonObject = buildJsonObject {
        put("id", article.id)
        put("title", article.ai_title ?: article.title ?: "无标题")
        put("content", truncate(article.ai_content ?: article.content ?: "", 800))
        put("comment", article.comment ?: "")
        put("url", article.url ?: "")
        put("isFavorite", article.is_favorite ?: 0L)
        put("createdAt", formatDate(article.created_at))
    }

    private fun articleListToJson(articles: List<Article>): JsonArray =
        JsonArray(articles.map { articleToJson(it) })

    private fun bookToJson(book: Book): JsonObject = buildJsonObject {
        put("id", book.id)
        put("title", book.title)
        put("author", book.author)
        put("category", book.category)
        put("createdAt", formatDate(book.created_at))
    }

    private fun bookListToJson(books: List<Book>): JsonArray =
        JsonArray(books.map { bookToJson(it) })

    private fun generateDiaryTitle(data: JsonObject): String {
        val createdAt = data["createdAt"]?.jsonPrimitive?.contentOrNull ?: return "日记"
        return try {
            val parts = createdAt.substring(0, 10).split("-")
            if (parts.size == 3) "${parts[0]}年${parts[1]}月${parts[2]}日的日记" else "日记"
        } catch (_: Exception) { "日记" }
    }

    private fun formatDate(timestampMs: Long): String {
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(timestampMs)
        return instant.toString().substring(0, 10)
    }

    private fun truncate(text: String, maxLen: Int): String =
        if (text.length <= maxLen) text else text.substring(0, maxLen) + "..."

    private fun truncateNullable(text: String?, maxLen: Int): String? {
        if (text.isNullOrEmpty()) return null
        return if (text.length <= maxLen) text else text.substring(0, maxLen) + "..."
    }

    private fun intParam(args: JsonObject, key: String, default: Int): Int {
        return args[key]?.jsonPrimitive?.intOrNull ?: default
    }

    private fun stringParam(args: JsonObject, key: String): String? {
        return args[key]?.jsonPrimitive?.contentOrNull
    }

    private fun longParam(args: JsonObject, key: String): Long? {
        return args[key]?.jsonPrimitive?.longOrNull
    }

    private fun parseDate(dateStr: String): kotlinx.datetime.Instant? {
        return try {
            when (dateStr.lowercase()) {
                "today" -> kotlinx.datetime.Clock.System.now()
                "yesterday" -> kotlinx.datetime.Instant.fromEpochMilliseconds(
                    kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - 86400000
                )
                "beforeyesterday", "before_yesterday" -> kotlinx.datetime.Instant.fromEpochMilliseconds(
                    kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - 172800000
                )
                else -> {
                    val dateStr10 = dateStr.substring(0, 10)
                    val parts = dateStr10.split("-")
                    if (parts.size == 3) {
                        kotlinx.datetime.Instant.parse("${dateStr10}T00:00:00Z")
                    } else null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> searchWithKeywords(keyword: String, searcher: (String) -> List<T>): List<T> {
        val keywords = keyword.split(Regex("[\\s,，]+"))
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && (containsChinese(it) || it.length >= 2) }

        if (keywords.isEmpty()) return emptyList()

        val resultMap = mutableMapOf<Int, T>()
        for (kw in keywords) {
            for (item in searcher(kw)) {
                val id = getItemId(item as Any)
                if (!resultMap.containsKey(id)) resultMap[id] = item
            }
        }

        return resultMap.values.toList().sortedByDescending { getItemTimestamp(it as Any) }
    }

    private fun containsChinese(text: String): Boolean =
        Regex("[\\u4e00-\\u9fa5]").containsMatchIn(text)

    private fun getItemId(item: Any): Int = when (item) {
        is Article -> item.id.toInt()
        is Diary -> item.id.toInt()
        is Book -> item.id.toInt()
        is Book_viewpoint -> item.id.toInt()
        else -> item.hashCode()
    }

    private fun getItemTimestamp(item: Any): Long = when (item) {
        is Article -> item.created_at
        is Diary -> item.created_at
        is Book -> item.created_at
        is Book_viewpoint -> item.created_at
        else -> 0L
    }

    suspend fun searchBookOnline(query: String): List<BookSearchResult> {
        val config = aiConfigService.getDefaultConfig()
            ?: return emptyList()
        if (config.api_token.isBlank()) return emptyList()

        val provider = config.provider

        val systemPrompt = """你是一个书籍搜索引擎。用户想了解关于"$query"的书籍信息。
请以 JSON 数组格式返回搜索结果，每个元素包含以下字段：
- title: 书名（字符串）
- author: 作者（字符串）
- introduction: 内容简介，200字以内（字符串）
- coverUrl: 封面图片URL，如果没有则为空字符串

只返回 JSON 数组，不要其他文字。示例格式：
[{"title":"书籍名称","author":"作者名","introduction":"内容简介...","coverUrl":""}]"""

        val response = aiService.complete(
            prompt = query,
            apiAddress = config.api_address,
            apiToken = config.api_token,
            modelName = config.model_name,
            provider = provider,
            systemPrompt = systemPrompt,
        )

        return parseBookSearchResults(response)
    }

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
        } catch (_: Exception) {
            emptyList()
        }
    }
}
