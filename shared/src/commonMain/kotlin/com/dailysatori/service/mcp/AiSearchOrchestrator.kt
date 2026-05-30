package com.dailysatori.service.mcp

data class AiSearchPlan(
    val searchMemory: Boolean = true,
    val searchDiaries: Boolean = false,
    val searchArticles: Boolean = false,
    val searchBooks: Boolean = false,
    val searchBookViewpoints: Boolean = false,
    val useSqlStatsPath: Boolean = false,
    val allowExternalContext: Boolean = false,
    val keywords: List<String> = emptyList(),
    val timeIntent: AiSearchTimeIntent? = null,
)

sealed class AiSearchTimeIntent {
    data class Date(val value: String) : AiSearchTimeIntent()
    data class Month(val value: String) : AiSearchTimeIntent()
    data class RecentDays(val days: Int) : AiSearchTimeIntent()
}

data class AiSearchEvidence(
    val result: McpSearchResult,
    val searchableText: String,
    val evidenceOnly: Boolean = false,
    val fromMemory: Boolean = false,
)

data class AiSearchResult(
    val plan: AiSearchPlan,
    val evidence: List<AiSearchEvidence>,
    val references: List<McpSearchResult>,
    val evidencePrompt: String?,
)

fun analyzeAiSearchQuery(query: String): AiSearchPlan {
    val diary = query.containsAny("日记", "写过", "心情", "情绪", "今天", "昨天", "前天", "某天", "那天")
    val article = query.containsAny("文章", "收藏", "新闻", "链接", "读过", "保存", "网页")
    val book = query.containsAny("书", "读书", "观点", "笔记", "摘录", "作者")
    val memory = query.containsAny("我之前", "有没有提过", "记得吗", "找一下", "什么线索")
    val stats = query.containsAny("多少", "多久", "最多", "频率", "趋势", "最近几天", "最近几月")
    val external = query.containsAny("是什么", "怎么说", "最新", "背景", "解释", "网上")
    val broad = memory && !diary && !article && !book
    return AiSearchPlan(
        searchMemory = query.isNotBlank(),
        searchDiaries = diary || broad,
        searchArticles = article || broad,
        searchBooks = book || broad,
        searchBookViewpoints = book || broad,
        useSqlStatsPath = stats,
        allowExternalContext = external,
        keywords = extractAiSearchKeywords(query),
        timeIntent = detectAiSearchTimeIntent(query),
    )
}

fun extractAiSearchKeywords(query: String): List<String> {
    val fillers = listOf("帮我", "找一下", "我之前", "有没有", "之前", "相关", "内容", "什么", "哪些", "一下", "写过", "日记", "文章", "读书", "吗", "的", "对", "里")
    var cleaned = query.trim()
    fillers.forEach { cleaned = cleaned.replace(it, " ") }
    val tokens = Regex("[A-Za-z0-9][A-Za-z0-9_-]*|[\\u4e00-\\u9fff]{2,}")
        .findAll(cleaned)
        .map { it.value.trim() }
        .filter { it.isNotBlank() }
        .toList()
    val expanded = tokens.flatMap { token ->
        if (token.any { it in '\u4e00'..'\u9fff' } && token.length > 4) {
            listOf(token, token.takeLast(2)) + token.windowed(4, 1, partialWindows = false) + token.windowed(2, 1, partialWindows = false)
        } else {
            listOf(token)
        }
    }
    return expanded
        .filterNot { it in fillers || it.length < 2 }
        .distinct()
        .take(5)
        .ifEmpty { listOf(query.trim()).filter { it.isNotBlank() } }
}

fun detectAiSearchTimeIntent(query: String): AiSearchTimeIntent? = when {
    Regex("\\d{4}-\\d{2}-\\d{2}").containsMatchIn(query) -> {
        AiSearchTimeIntent.Date(Regex("\\d{4}-\\d{2}-\\d{2}").find(query)!!.value)
    }
    Regex("\\d{4}-\\d{2}").containsMatchIn(query) -> {
        AiSearchTimeIntent.Month(Regex("\\d{4}-\\d{2}").find(query)!!.value)
    }
    query.contains("最近一周") -> AiSearchTimeIntent.RecentDays(7)
    query.contains("最近一个月") -> AiSearchTimeIntent.RecentDays(30)
    query.contains("最近") || query.contains("最近几天") -> AiSearchTimeIntent.RecentDays(7)
    else -> null
}

fun rankAiSearchEvidence(
    evidence: List<AiSearchEvidence>,
    keywords: List<String>,
    primaryTypes: Set<String>,
    nowDate: String = kotlinx.datetime.Clock.System.now().toString().take(10),
): List<AiSearchEvidence> = evidence
    .map { item -> item.copy(result = item.result.copy(matchReason = matchReason(item, keywords))) to scoreEvidence(item, keywords, primaryTypes, nowDate) }
    .sortedWith(compareByDescending<Pair<AiSearchEvidence, Int>> { it.second }.thenByDescending { it.first.result.createdAt.orEmpty() })
    .map { it.first }
    .distinctBy { it.result.type to it.result.id }

private fun scoreEvidence(
    item: AiSearchEvidence,
    keywords: List<String>,
    primaryTypes: Set<String>,
    nowDate: String,
): Int {
    val title = item.result.title.lowercase()
    val body = item.searchableText.lowercase()
    val matched = keywords.map { it.lowercase() }.filter { it.isNotBlank() }
    var score = 0
    if (matched.any { title.contains(it) }) score += 5
    if (matched.any { body.contains(it) }) score += 3
    if (item.fromMemory && !item.evidenceOnly) score += 2
    if (item.result.isFavorite == true) score += 2
    if (item.result.createdAt != null && item.result.createdAt.take(7) >= nowDate.take(7)) score += 1
    if (item.result.type in primaryTypes) score += 3
    return score
}

private fun matchReason(item: AiSearchEvidence, keywords: List<String>): String? {
    val text = "${item.result.title} ${item.searchableText}".lowercase()
    val hits = keywords.filter { it.isNotBlank() && text.contains(it.lowercase()) }.distinct().take(3)
    return hits.takeIf { it.isNotEmpty() }?.joinToString(prefix = "命中：")
}

private fun String.containsAny(vararg tokens: String): Boolean = tokens.any { contains(it) }
