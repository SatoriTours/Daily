package com.dailysatori.service.mcp

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.Book
import com.dailysatori.shared.db.Diary
import kotlinx.serialization.json.*

class McpToolRegistry(
    private val diaryRepo: DiaryRepository,
    private val articleRepo: ArticleRepository,
    private val bookRepo: BookRepository,
    private val viewpointRepo: BookViewpointRepository,
    private val memoryRepo: MemoryRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun buildToolDefinitions(): List<JsonObject> = listOf(
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
        buildTool("search_memory", "搜索记忆库中的内容。记忆分为三种类型：core(核心偏好/事实)、content(从日记/文章/书中提取的摘要)、chat(对话中提取的关键信息)", mapOf(
            "query" to buildParam("string", "搜索关键词"),
            "type" to buildParam("string", "记忆类型过滤: core, content, chat，不传则搜索全部"),
            "limit" to buildParam("integer", "返回的最大数量，默认为10"),
        ), listOf("query")),
        buildTool("get_memory_source", "获取指定来源的记忆内容", mapOf(
            "source_type" to buildParam("string", "来源类型: article, diary, book, book_viewpoint, chat"),
            "source_id" to buildParam("integer", "来源ID"),
        ), listOf("source_type", "source_id")),
    )

    fun executeTool(toolName: String, argumentsStr: String): McpToolResult {
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
                "search_memory" -> searchMemory(args)
                "get_memory_source" -> getMemorySource(args)
                else -> errorResult("未知工具: $toolName")
            }
        } catch (e: Exception) {
            errorResult("工具执行失败: ${e.message}")
        }
    }

    private fun buildTool(
        name: String,
        description: String,
        properties: Map<String, JsonObject> = emptyMap(),
        required: List<String> = emptyList(),
    ): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("function"))
        put("function", buildJsonObject {
            put("name", JsonPrimitive(name))
            put("description", JsonPrimitive(description))
            put("parameters", buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", JsonObject(properties))
                put("required", JsonArray(required.map { JsonPrimitive(it) }))
            })
        })
    }

    private fun buildParam(type: String, description: String): JsonObject = buildJsonObject {
        put("type", JsonPrimitive(type))
        put("description", JsonPrimitive(description))
    }

    // --- Tool implementations ---

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
        return successResult("keyword" to JsonPrimitive(keyword), "notes" to notesJson)
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

    private fun searchMemory(args: JsonObject): McpToolResult {
        val query = stringParam(args, "query") ?: return errorResult("缺少query参数")
        val type = stringParam(args, "type")
        val limit = intParam(args, "limit", 10)
        val results = if (type != null) {
            memoryRepo.search(query, limit.toLong()).filter { it.type == type }
        } else {
            memoryRepo.search(query, limit.toLong())
        }
        if (results.isEmpty()) {
            return successResult("message" to JsonPrimitive("未找到相关记忆"))
        }
        return successResult("results" to JsonArray(results.take(limit).map { entry ->
            buildJsonObject {
                put("id", entry.id)
                put("type", entry.type)
                put("source_type", entry.source_type ?: "")
                put("title", entry.title)
                put("content", entry.content.take(500))
                put("tags", entry.tags ?: "")
            }
        }))
    }

    private fun getMemorySource(args: JsonObject): McpToolResult {
        val sourceType = stringParam(args, "source_type") ?: return errorResult("缺少source_type参数")
        val sourceId = longParam(args, "source_id") ?: return errorResult("缺少source_id参数")
        val entry = memoryRepo.getBySource(sourceType, sourceId)
        if (entry == null) {
            return successResult("message" to JsonPrimitive("未找到相关记忆"))
        }
        return successResult("memory" to buildJsonObject {
            put("id", entry.id)
            put("type", entry.type)
            put("title", entry.title)
            put("content", entry.content)
            put("tags", entry.tags ?: "")
        })
    }

    // --- Helpers ---

    private fun successResult(vararg pairs: Pair<String, JsonElement>): McpToolResult {
        val obj = buildJsonObject {
            put("success", true)
            for ((key, value) in pairs) { put(key, value) }
            val countFields = setOf("diaries", "articles", "books", "viewpoints", "notes")
            for ((key, value) in pairs) {
                if (key in countFields && value is JsonArray) put("count", value.size)
            }
        }
        return McpToolResult(true, obj)
    }

    private fun errorResult(message: String): McpToolResult {
        val obj = buildJsonObject { put("success", false); put("error", message) }
        return McpToolResult(false, obj)
    }

    private fun intParam(args: JsonObject, key: String, default: Int): Int =
        args[key]?.jsonPrimitive?.intOrNull ?: default

    private fun stringParam(args: JsonObject, key: String): String? =
        args[key]?.jsonPrimitive?.contentOrNull

    private fun longParam(args: JsonObject, key: String): Long? =
        args[key]?.jsonPrimitive?.longOrNull

    private fun diaryListToJson(diaries: List<Diary>): JsonArray = JsonArray(diaries.map { diary ->
        buildJsonObject {
            put("id", diary.id)
            put("content", truncate(diary.content, 500))
            put("tags", diary.tags ?: "")
            put("mood", diary.mood ?: "")
            put("createdAt", formatDate(diary.created_at))
        }
    })

    private fun articleListToJson(articles: List<Article>): JsonArray = JsonArray(articles.map { article ->
        buildJsonObject {
            put("id", article.id)
            put("title", article.ai_title ?: article.title ?: "无标题")
            put("content", truncate(article.ai_content ?: article.content ?: "", 800))
            put("comment", article.comment ?: "")
            put("url", article.url ?: "")
            put("isFavorite", article.is_favorite ?: 0L)
            put("createdAt", formatDate(article.created_at))
        }
    })

    private fun bookListToJson(books: List<Book>): JsonArray = JsonArray(books.map { book ->
        buildJsonObject {
            put("id", book.id)
            put("title", book.title)
            put("author", book.author)
            put("category", book.category)
            put("createdAt", formatDate(book.created_at))
        }
    })

    private fun formatDate(timestampMs: Long): String {
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(timestampMs)
        return instant.toString().substring(0, 10)
    }

    private fun truncate(text: String, maxLen: Int): String =
        if (text.length <= maxLen) text else text.substring(0, maxLen) + "..."

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
                    if (parts.size == 3) kotlinx.datetime.Instant.parse("${dateStr10}T00:00:00Z")
                    else null
                }
            }
        } catch (_: Exception) { null }
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
        is com.dailysatori.shared.db.Book_viewpoint -> item.id.toInt()
        else -> item.hashCode()
    }

    private fun getItemTimestamp(item: Any): Long = when (item) {
        is Article -> item.created_at
        is Diary -> item.created_at
        is Book -> item.created_at
        is com.dailysatori.shared.db.Book_viewpoint -> item.created_at
        else -> 0L
    }
}
