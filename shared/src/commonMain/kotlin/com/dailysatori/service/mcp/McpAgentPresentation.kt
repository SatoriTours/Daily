package com.dailysatori.service.mcp

fun searchResultTypeLabel(type: String): String = when (type) {
    "article" -> "文章"
    "diary" -> "日记"
    "book" -> "书籍"
    else -> "内容"
}

enum class SearchResultOpenTarget {
    Article,
    Diary,
    Book,
}

fun searchResultOpenTarget(type: String): SearchResultOpenTarget? = when (type) {
    "article" -> SearchResultOpenTarget.Article
    "diary" -> SearchResultOpenTarget.Diary
    "book" -> SearchResultOpenTarget.Book
    else -> null
}

fun canOpenSearchResult(type: String): Boolean = searchResultOpenTarget(type) != null

fun orderedDiaryIndexFromQuery(query: String): Int? {
    if (!query.contains("日记")) return null
    val normalized = query.replace("倒数第", "第")
    return when {
        normalized.contains("第二近") || normalized.contains("第二篇") || normalized.contains("第二个") -> 1
        normalized.contains("第三近") || normalized.contains("第三篇") || normalized.contains("第三个") -> 2
        normalized.contains("第四近") || normalized.contains("第四篇") || normalized.contains("第四个") -> 3
        normalized.contains("第五近") || normalized.contains("第五篇") || normalized.contains("第五个") -> 4
        else -> null
    }
}

fun preciseSearchResultsForQuery(
    query: String,
    results: List<McpSearchResult>,
): List<McpSearchResult> {
    val orderedDiaryIndex = orderedDiaryIndexFromQuery(query) ?: return results
    val diaries = results.filter { it.type == "diary" }
    return diaries.getOrNull(orderedDiaryIndex)?.let { listOf(it) } ?: results
}

fun aiSummaryRetryAttempts(): List<Int> = listOf(1, 2, 3)

fun buildFallbackAnswer(query: String, results: List<McpSearchResult>): String {
    if (results.isEmpty()) return "在您的数据中没有找到相关信息。"
    buildSecondLatestDiaryAnswer(query, results)?.let { return it }
    val preview = results.take(3).joinToString("\n") { result ->
        val summary = result.summary?.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
        "- **${result.title}**$summary"
    }
    return """## 结论
根据已查到的数据，找到 ${results.size} 条相关内容。

## 重点内容
$preview

## 可继续查看
下面的来源卡片可以继续打开查看详情。"""
}

private fun buildSecondLatestDiaryAnswer(query: String, results: List<McpSearchResult>): String? {
    if (!query.contains("倒数第二")) return null
    val diaries = results.filter { it.type == "diary" }
    if (diaries.size < 2) return null
    val diary = diaries[1]
    val date = diary.createdAt?.takeIf { it.isNotBlank() } ?: diary.title
    val summary = diary.summary?.takeIf { it.isNotBlank() }?.let { "\n- 内容摘要：$it" }.orEmpty()
    return """## 结论
倒数第二篇日记是 **$date** 的日记。

## 重点内容
- 标题：${diary.title}$summary

## 可继续查看
下面的日记引用可以帮助你核对原始记录。"""
}
