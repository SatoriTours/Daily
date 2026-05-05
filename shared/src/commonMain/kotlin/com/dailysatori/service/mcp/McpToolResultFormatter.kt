package com.dailysatori.service.mcp

import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.Book
import com.dailysatori.shared.db.Diary
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal fun successResult(vararg pairs: Pair<String, JsonElement>): McpToolResult {
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

internal fun errorResult(message: String): McpToolResult {
    val obj = buildJsonObject { put("success", false); put("error", message) }
    return McpToolResult(false, obj)
}

internal fun diaryListToJson(diaries: List<Diary>): JsonArray = JsonArray(diaries.map { diary ->
    buildJsonObject {
        put("id", diary.id)
        put("content", truncate(diary.content, 500))
        put("tags", diary.tags ?: "")
        put("mood", diary.mood ?: "")
        put("createdAt", formatDate(diary.created_at))
    }
})

internal fun articleListToJson(articles: List<Article>): JsonArray = JsonArray(articles.map { article ->
    buildJsonObject {
        put("id", article.id)
        put("title", article.ai_title ?: article.title ?: "无标题")
        put("content", truncate(article.ai_content ?: "", 800))
        put("comment", article.comment ?: "")
        put("url", article.url ?: "")
        put("isFavorite", article.is_favorite ?: 0L)
        put("createdAt", formatDate(article.created_at))
    }
})

internal fun bookListToJson(books: List<Book>): JsonArray = JsonArray(books.map { book ->
    buildJsonObject {
        put("id", book.id)
        put("title", book.title)
        put("author", book.author)
        put("category", book.category)
        put("createdAt", formatDate(book.created_at))
    }
})

internal fun truncate(text: String, maxLen: Int): String =
    if (text.length <= maxLen) text else text.substring(0, maxLen) + "..."

internal fun extractMcpSearchResults(toolName: String, result: McpToolResult): List<McpSearchResult> {
    if (!result.success || result.data == null) return emptyList()
    val data = result.data

    fun jsonString(obj: JsonObject, key: String): String? =
        obj[key]?.jsonPrimitive?.contentOrNull
    fun jsonLong(obj: JsonObject, key: String): Long? =
        obj[key]?.jsonPrimitive?.longOrNull
    fun jsonBool(obj: JsonObject, key: String): Boolean? =
        obj[key]?.jsonPrimitive?.booleanOrNull

    return when {
        toolName.contains("diary") -> extractDiaryResults(data, ::jsonString, ::jsonLong)
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
): List<McpSearchResult> {
    val diaries = data["diaries"]?.jsonArray ?: return emptyList()
    return diaries.mapNotNull { item ->
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

internal fun filterRelevantMcpResults(
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

internal fun removeMcpRefsTag(answer: String): String =
    answer.replace(Regex("\\n*<!--\\s*refs:[^>]*-->\\s*$"), "").trim()

internal fun buildMcpErrorResponse(message: String): String =
    """😔 **出现问题**

$message

**建议**:
- 检查网络连接
- 确保 AI 服务配置正确
- 稍后重试"""

private fun formatDate(timestampMs: Long): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(timestampMs)
    return instant.toString().substring(0, 10)
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
