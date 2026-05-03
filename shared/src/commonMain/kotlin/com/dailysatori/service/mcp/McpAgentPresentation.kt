package com.dailysatori.service.mcp

fun searchResultTypeLabel(type: String): String = when (type) {
    "article" -> "文章"
    "diary" -> "日记"
    "book" -> "书籍"
    else -> "内容"
}

fun canOpenSearchResult(type: String): Boolean = type == "article"

fun buildFallbackAnswer(results: List<McpSearchResult>): String {
    if (results.isEmpty()) return "在您的数据中没有找到相关信息。"
    val preview = results.take(3).joinToString("\n") { result ->
        val summary = result.summary?.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
        "- **${result.title}**$summary"
    }
    return """## 结论
找到 ${results.size} 条相关内容。AI 总结暂时失败，先展示可确认的匹配结果。

## 重点内容
$preview

## 可继续查看
下面的来源卡片可以继续打开查看详情。"""
}
