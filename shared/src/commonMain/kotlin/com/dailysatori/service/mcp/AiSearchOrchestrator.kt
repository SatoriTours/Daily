package com.dailysatori.service.mcp

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.shared.db.Memory_entry

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

fun buildAiSearchEvidencePrompt(query: String, evidence: List<AiSearchEvidence>): String {
    val sufficiency = when (evidence.size) {
        0 -> "无相关记录"
        1, 2 -> "少量相关记录"
        else -> "可用于总结的多条记录"
    }
    val body = evidence.take(12).joinToString("\n") { item ->
        val result = item.result
        val type = searchResultTypeLabel(result.type)
        val key = "${result.type}_${result.id}"
        val date = result.createdAt?.takeIf { it.isNotBlank() } ?: "无日期"
        val reason = result.matchReason?.let { "｜$it" }.orEmpty()
        "[$key] $type｜${result.title}｜$date$reason｜${item.searchableText.take(240)}"
    }
    return """用户问题：$query

证据充足度：$sufficiency

已找到的本地证据：
$body

请只能基于上述证据回答；证据不足时明确说明不足；不要编造事实；结尾用 <!-- refs: ... --> 标注可打开引用。""".trimIndent()
}

fun buildAiSearchFallbackAnswer(query: String, rankedResults: List<McpSearchResult>): String {
    if (rankedResults.isEmpty()) return "在您的数据中没有找到相关信息。"
    val typeNames = rankedResults.map { searchResultTypeLabel(it.type) }.distinct().joinToString("、")
    val sparse = if (rankedResults.size <= 2) "\n\n我只找到少量相关记录，结论可能不完整。" else ""
    val top = rankedResults.take(3).joinToString("\n") { result ->
        val reason = result.matchReason?.let { "（$it）" }.orEmpty()
        val summary = result.summary?.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
        "- **${result.title}**$reason$summary"
    }
    return """## 结论
找到 ${rankedResults.size} 条相关内容，来源包括：$typeNames。$sparse

## 重点内容
$top

## 可继续查看
下面的引用卡片可以继续打开核对原文。""".trimIndent()
}

fun aiSearchUserContentForQuery(query: String, localSearch: AiSearchResult): String =
    if (localSearch.plan.useSqlStatsPath) query else localSearch.evidencePrompt ?: query

class AiSearchOrchestrator(
    private val memoryRepo: MemoryRepository,
    private val diaryRepo: DiaryRepository,
    private val articleRepo: ArticleRepository,
    private val bookRepo: BookRepository,
    private val viewpointRepo: BookViewpointRepository,
) {
    fun search(query: String): AiSearchResult {
        val plan = analyzeAiSearchQuery(query)
        val keywords = plan.keywords
        val evidence = mutableListOf<AiSearchEvidence>()
        runCatching { if (plan.searchMemory) evidence += searchMemoryEvidence(keywords) }
        runCatching { if (plan.searchDiaries) evidence += searchDiaryEvidence(keywords) }
        runCatching { if (plan.searchArticles) evidence += searchArticleEvidence(keywords) }
        runCatching { if (plan.searchBooks) evidence += searchBookEvidence(keywords) }
        runCatching { if (plan.searchBookViewpoints) evidence += searchBookViewpointEvidence(keywords) }
        val ranked = rankAiSearchEvidence(evidence, keywords, primaryTypesForPlan(plan)).take(12)
        val refs = ranked.map { it.result }.filter { canOpenSearchResult(it.type) }.take(8)
        return AiSearchResult(
            plan = plan,
            evidence = ranked,
            references = refs,
            evidencePrompt = ranked.takeIf { it.isNotEmpty() }?.let { buildAiSearchEvidencePrompt(query, it) },
        )
    }

    private fun searchMemoryEvidence(keywords: List<String>): List<AiSearchEvidence> = keywords.flatMap { keyword ->
        memoryRepo.search(keyword, 10).map { memory -> memory.toEvidence() }
    }

    private fun Memory_entry.toEvidence(): AiSearchEvidence = when (source_type) {
        "diary" -> diaryRepo.getById(source_id ?: -1)?.let { diary ->
            AiSearchEvidence(
                result = McpSearchResult(diary.id, "diary", formatAiSearchDate(diary.created_at), diary.content.take(160), formatAiSearchDate(diary.created_at)),
                searchableText = diary.content,
                fromMemory = true,
            )
        } ?: memoryOnlyEvidence()
        "article" -> articleRepo.getById(source_id ?: -1)?.let { article ->
            val title = article.ai_title ?: article.title ?: title
            AiSearchEvidence(
                result = McpSearchResult(article.id, "article", title, article.ai_content?.take(160), formatAiSearchDate(article.created_at), isFavorite = article.is_favorite == 1L),
                searchableText = listOf(article.title, article.ai_title, article.ai_content, article.ai_markdown_content).filterNotNull().joinToString(" "),
                fromMemory = true,
            )
        } ?: memoryOnlyEvidence()
        "book" -> bookRepo.getById(source_id ?: -1)?.let { book ->
            AiSearchEvidence(
                result = McpSearchResult(book.id, "book", book.title, book.author, formatAiSearchDate(book.created_at)),
                searchableText = "${book.title} ${book.author} ${book.introduction}",
                fromMemory = true,
            )
        } ?: memoryOnlyEvidence()
        "book_viewpoint" -> viewpointRepo.getById(source_id ?: -1)?.let { viewpoint ->
            val book = bookRepo.getById(viewpoint.book_id)
            AiSearchEvidence(
                result = McpSearchResult(viewpoint.id, "book_viewpoint", book?.title ?: viewpoint.title, viewpoint.content.take(160), null),
                searchableText = listOf(book?.title, viewpoint.title, viewpoint.content, viewpoint.example).filterNotNull().joinToString(" "),
                fromMemory = true,
            )
        } ?: memoryOnlyEvidence()
        else -> memoryOnlyEvidence()
    }

    private fun Memory_entry.memoryOnlyEvidence(): AiSearchEvidence = AiSearchEvidence(
        result = McpSearchResult(id, type, title, content.take(160), null),
        searchableText = listOf(title, content, tags.orEmpty()).joinToString(" "),
        evidenceOnly = true,
        fromMemory = true,
    )

    private fun searchDiaryEvidence(keywords: List<String>): List<AiSearchEvidence> = keywords.flatMap { keyword ->
        diaryRepo.searchSync(keyword).take(8).map { diary ->
            AiSearchEvidence(
                result = McpSearchResult(diary.id, "diary", formatAiSearchDate(diary.created_at), diary.content.take(160), formatAiSearchDate(diary.created_at)),
                searchableText = diary.content,
            )
        }
    }

    private fun searchArticleEvidence(keywords: List<String>): List<AiSearchEvidence> = keywords.flatMap { keyword ->
        articleRepo.searchFavoriteFirstSync(keyword).take(8).map { article ->
            AiSearchEvidence(
                result = McpSearchResult(
                    id = article.id,
                    type = "article",
                    title = article.ai_title ?: article.title ?: "无标题文章",
                    summary = article.ai_content?.take(160),
                    createdAt = formatAiSearchDate(article.created_at),
                    isFavorite = article.is_favorite == 1L,
                ),
                searchableText = listOf(article.title, article.ai_title, article.ai_content, article.ai_markdown_content).filterNotNull().joinToString(" "),
            )
        }
    }

    private fun searchBookEvidence(keywords: List<String>): List<AiSearchEvidence> = keywords.flatMap { keyword ->
        bookRepo.searchSync(keyword).take(5).map { book ->
            AiSearchEvidence(
                result = McpSearchResult(book.id, "book", book.title, book.author, formatAiSearchDate(book.created_at)),
                searchableText = "${book.title} ${book.author} ${book.introduction}",
            )
        }
    }

    private fun searchBookViewpointEvidence(keywords: List<String>): List<AiSearchEvidence> = keywords.flatMap { keyword ->
        viewpointRepo.searchBookContent(keyword).take(8).map { row ->
            AiSearchEvidence(
                result = McpSearchResult(row.viewpointId, "book_viewpoint", row.bookTitle, row.title, null),
                searchableText = "${row.bookTitle} ${row.title} ${row.content} ${row.example}",
            )
        }
    }
}

private fun primaryTypesForPlan(plan: AiSearchPlan): Set<String> = buildSet {
    if (plan.searchDiaries) add("diary")
    if (plan.searchArticles) add("article")
    if (plan.searchBooks) add("book")
    if (plan.searchBookViewpoints) add("book_viewpoint")
}

private fun formatAiSearchDate(timestampMs: Long): String =
    kotlinx.datetime.Instant.fromEpochMilliseconds(timestampMs).toString().take(10)

private fun String.containsAny(vararg tokens: String): Boolean = tokens.any { contains(it) }
