# AI Chat Search Orchestrator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve AI chat so normal questions automatically search relevant local evidence and return clickable references without a separate top-bar memory search.

**Architecture:** Add a deterministic `AiSearchOrchestrator` in shared code that plans intents, extracts keywords, recalls local evidence, ranks results, and builds evidence prompts before AI synthesis. Keep the existing MCP tool loop available for statistics and hybrid questions, and upgrade references so book viewpoints are first-class clickable results.

**Tech Stack:** Kotlin Multiplatform shared module, Android Jetpack Compose UI, SQLDelight repositories, existing MCP agent service, Gradle unit tests.

---

## File Structure

- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/AiSearchOrchestrator.kt`
  - Pure planner/ranker/evidence utilities plus repository-backed orchestration.
- Create: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/AiSearchOrchestratorTest.kt`
  - Unit tests for intent detection, keyword extraction, ranking, evidence prompt, reference fallback, and memory conversion helper logic.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt`
  - Inject and call `AiSearchOrchestrator` before normal model/tool processing; use fallback results when AI fails or refs are missing.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentPresentation.kt`
  - Add `book_viewpoint` label/open target and reference fallback helper.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpSearchResultPersistence.kt`
  - Persist `matchReason` and new result type without breaking old saved messages.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolResultFormatter.kt`
  - Extract book note results as `book_viewpoint` instead of overloaded `book`.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
  - Register `AiSearchOrchestrator` and pass it to `McpAgentService`.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt`
  - Remove top-bar memory search action and `MemorySearchSheet` entry point.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt`
  - Update `aiChatShowsMemorySearchAction()` to `false`.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailViewModel.kt`
  - Add explicit `BookViewpoint` open target handling.
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`
  - Assert top-bar memory search is removed.
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpAgentPresentationTest.kt`
  - Assert `book_viewpoint` label/open target and reference fallback.
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpSearchResultPersistenceTest.kt`
  - Assert `matchReason` and `book_viewpoint` round trip.

## Task 1: First-Class Book Viewpoint References

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentPresentation.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpSearchResultPersistence.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolResultFormatter.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailViewModel.kt`
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpAgentPresentationTest.kt`
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpSearchResultPersistenceTest.kt`

- [ ] **Step 1: Write failing presentation tests for book viewpoint references**

Add to `McpAgentPresentationTest`:

```kotlin
@Test
fun mapsBookViewpointSearchResultToReadableLabelAndOpenTarget() {
    assertEquals("读书笔记", searchResultTypeLabel("book_viewpoint"))
    assertEquals(true, canOpenSearchResult("book_viewpoint"))
    assertEquals(SearchResultOpenTarget.BookViewpoint, searchResultOpenTarget("book_viewpoint"))
}

@Test
fun fallsBackToRankedReferencesWhenAiRefsAreMissingOrInvalid() {
    val ranked = listOf(
        McpSearchResult(1, "article", "文章", "摘要", "2026-05-30"),
        McpSearchResult(2, "diary", "日记", "片段", "2026-05-29"),
    )

    assertEquals(ranked, referencesForAnswer("没有 refs", ranked))
    assertEquals(ranked, referencesForAnswer("回答\n<!-- refs: article_999 -->", ranked))
    assertEquals(listOf(ranked[0]), referencesForAnswer("回答\n<!-- refs: article_1 -->", ranked))
}
```

- [ ] **Step 2: Write failing persistence test for match reason and book viewpoint type**

Update the first result in `McpSearchResultPersistenceTest.encodesAndDecodesSearchResultsForChatPersistence()` to include `matchReason` and add a `book_viewpoint` result:

```kotlin
McpSearchResult(
    id = 42L,
    type = "diary",
    title = "最近一篇日记",
    summary = "今天记录了重要想法",
    createdAt = "2026-05-03",
    tags = listOf("生活", "想法"),
    isFavorite = null,
    matchReason = "命中：想法",
),
McpSearchResult(
    id = 99L,
    type = "book_viewpoint",
    title = "长期主义",
    summary = "读书笔记片段",
    createdAt = null,
    matchReason = "命中：长期主义",
),
```

- [ ] **Step 3: Run tests and verify failure**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.mcp.McpAgentPresentationTest.mapsBookViewpointSearchResultToReadableLabelAndOpenTarget" --tests "com.dailysatori.service.mcp.McpAgentPresentationTest.fallsBackToRankedReferencesWhenAiRefsAreMissingOrInvalid" --tests "com.dailysatori.service.mcp.McpSearchResultPersistenceTest.encodesAndDecodesSearchResultsForChatPersistence"
```

Expected: FAIL because `BookViewpoint`, `referencesForAnswer`, and `matchReason` do not exist.

- [ ] **Step 4: Add `matchReason` to `McpSearchResult`**

In `McpAgentService.kt`, change `McpSearchResult` to:

```kotlin
data class McpSearchResult(
    val id: Long,
    val type: String,
    val title: String,
    val summary: String?,
    val createdAt: String?,
    val tags: List<String>? = null,
    val isFavorite: Boolean? = null,
    val matchReason: String? = null,
)
```

- [ ] **Step 5: Implement book viewpoint presentation and fallback refs**

In `McpAgentPresentation.kt`, update the target enum and helpers:

```kotlin
fun searchResultTypeLabel(type: String): String = when (type) {
    "article" -> "文章"
    "diary" -> "日记"
    "book" -> "书籍"
    "book_viewpoint" -> "读书笔记"
    else -> "内容"
}

enum class SearchResultOpenTarget {
    Article,
    Diary,
    Book,
    BookViewpoint,
}

fun searchResultOpenTarget(type: String): SearchResultOpenTarget? = when (type) {
    "article" -> SearchResultOpenTarget.Article
    "diary" -> SearchResultOpenTarget.Diary
    "book" -> SearchResultOpenTarget.Book
    "book_viewpoint" -> SearchResultOpenTarget.BookViewpoint
    else -> null
}

fun referencesForAnswer(answer: String, rankedResults: List<McpSearchResult>, limit: Int = 8): List<McpSearchResult> {
    val refsMatch = Regex("<!--\\s*refs:\\s*([^>]+)\\s*-->").find(answer)
    val openable = rankedResults.filter { canOpenSearchResult(it.type) }
    if (refsMatch == null) return openable.take(limit)
    val refs = refsMatch.groupValues[1].trim()
    if (refs.equals("none", ignoreCase = true)) return emptyList()
    val byKey = openable.associateBy { "${it.type}_${it.id}" }
    val selected = refs.split(',').map { it.trim() }.mapNotNull(byKey::get)
    return selected.takeIf { it.isNotEmpty() } ?: openable.take(limit)
}
```

- [ ] **Step 6: Persist `matchReason`**

In `McpSearchResultPersistence.kt`, add encode/decode support:

```kotlin
result.matchReason?.let { put("matchReason", it) }
```

and in decode:

```kotlin
matchReason = obj.string("matchReason"),
```

- [ ] **Step 7: Extract note results as `book_viewpoint`**

In `McpToolResultFormatter.kt`, update `extractNoteResults`:

```kotlin
McpSearchResult(
    id = jsonLong(n, "id") ?: return@mapNotNull null,
    type = "book_viewpoint",
    title = jsonString(n, "bookTitle") ?: "未知书籍",
    summary = truncateNullable(jsonString(n, "title"), 100),
    createdAt = null,
)
```

- [ ] **Step 8: Open book viewpoints explicitly**

In `AiReferenceDetailViewModel.kt`, update `load()`:

```kotlin
_state.value = when (searchResultOpenTarget(result.type)) {
    SearchResultOpenTarget.Article -> loadArticle(result.id)
    SearchResultOpenTarget.Diary -> loadDiary(result.id)
    SearchResultOpenTarget.Book -> loadBook(result.id)
    SearchResultOpenTarget.BookViewpoint -> loadBookViewpoint(result.id)
    else -> AiReferenceDetailState(error = MISSING_CONTENT_MESSAGE)
}
```

Add:

```kotlin
private fun loadBookViewpoint(id: Long): AiReferenceDetailState {
    val viewpoint = viewpointRepo.getById(id)
    return if (viewpoint == null) {
        AiReferenceDetailState(error = MISSING_CONTENT_MESSAGE)
    } else {
        AiReferenceDetailState(book = bookRepo.getById(viewpoint.book_id), viewpoint = viewpoint)
    }
}
```

- [ ] **Step 9: Run tests and commit**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.mcp.McpAgentPresentationTest" --tests "com.dailysatori.service.mcp.McpSearchResultPersistenceTest"
```

Expected: PASS.

Commit:

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentPresentation.kt shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpSearchResultPersistence.kt shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolResultFormatter.kt app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailViewModel.kt shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpAgentPresentationTest.kt shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpSearchResultPersistenceTest.kt
git commit -m "feat: support book viewpoint AI references"
```

## Task 2: Pure Search Planning, Keywords, Time, Ranking

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/AiSearchOrchestrator.kt`
- Create: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/AiSearchOrchestratorTest.kt`

- [ ] **Step 1: Write failing pure function tests**

Create `AiSearchOrchestratorTest.kt` with:

```kotlin
package com.dailysatori.service.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiSearchOrchestratorTest {
    @Test
    fun detectsSearchIntentsFromChineseQuery() {
        val diary = analyzeAiSearchQuery("我之前写过焦虑的日记吗")
        assertTrue(diary.searchMemory)
        assertTrue(diary.searchDiaries)
        assertFalse(diary.searchArticles)

        val article = analyzeAiSearchQuery("我收藏过哪些 AI 文章")
        assertTrue(article.searchMemory)
        assertTrue(article.searchArticles)

        val book = analyzeAiSearchQuery("读书笔记里有没有长期主义")
        assertTrue(book.searchBooks)
        assertTrue(book.searchBookViewpoints)
    }

    @Test
    fun genericRecallSearchesAllLocalContent() {
        val plan = analyzeAiSearchQuery("我之前有没有提过工作节奏")
        assertTrue(plan.searchMemory)
        assertTrue(plan.searchDiaries)
        assertTrue(plan.searchArticles)
        assertTrue(plan.searchBooks)
        assertTrue(plan.searchBookViewpoints)
    }

    @Test
    fun extractsUsefulKeywordsAndDropsFillers() {
        val keywords = extractAiSearchKeywords("帮我找一下我之前有没有写过对工作节奏焦虑的日记")
        assertTrue("工作节奏" in keywords || "工作" in keywords)
        assertTrue("焦虑" in keywords)
        assertFalse("帮我" in keywords)
        assertTrue(keywords.size in 2..5)
    }

    @Test
    fun detectsSimpleTimeIntent() {
        assertEquals(AiSearchTimeIntent.RecentDays(7), detectAiSearchTimeIntent("最近一周我写过什么"))
        assertEquals(AiSearchTimeIntent.Month("2026-05"), detectAiSearchTimeIntent("2026-05 的日记"))
        assertEquals(AiSearchTimeIntent.Date("2026-05-30"), detectAiSearchTimeIntent("2026-05-30 写了什么"))
    }

    @Test
    fun ranksIntentTitleFavoriteAndRecentMatchesHigher() {
        val oldArticle = AiSearchEvidence(
            result = McpSearchResult(1, "article", "普通文章", "AI", "2020-01-01"),
            searchableText = "AI",
        )
        val favoriteTitle = AiSearchEvidence(
            result = McpSearchResult(2, "article", "AI 搜索文章", "摘要", "2026-05-30", isFavorite = true),
            searchableText = "AI 搜索文章 摘要",
        )

        val ranked = rankAiSearchEvidence(
            evidence = listOf(oldArticle, favoriteTitle),
            keywords = listOf("AI"),
            primaryTypes = setOf("article"),
            nowDate = "2026-05-30",
        )

        assertEquals(2L, ranked.first().result.id)
        assertTrue(ranked.first().result.matchReason.orEmpty().contains("AI"))
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.mcp.AiSearchOrchestratorTest"
```

Expected: FAIL because the file/functions do not exist.

- [ ] **Step 3: Implement pure orchestrator types and functions**

Create `AiSearchOrchestrator.kt` with these top-level types/functions first:

```kotlin
package com.dailysatori.service.mcp

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.MemoryRepository

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
```

Then add `analyzeAiSearchQuery`, `extractAiSearchKeywords`, `detectAiSearchTimeIntent`, and `rankAiSearchEvidence`:

```kotlin
fun analyzeAiSearchQuery(query: String): AiSearchPlan {
    val keywords = extractAiSearchKeywords(query)
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
        keywords = keywords,
        timeIntent = detectAiSearchTimeIntent(query),
    )
}

fun extractAiSearchKeywords(query: String): List<String> {
    val fillers = listOf("帮我", "找一下", "有没有", "之前", "相关", "内容", "什么", "哪些", "一下", "吗", "的")
    var cleaned = query.trim()
    fillers.forEach { cleaned = cleaned.replace(it, " ") }
    val raw = Regex("[A-Za-z0-9][A-Za-z0-9_-]*|[\\u4e00-\\u9fff]{2,}")
        .findAll(cleaned)
        .map { it.value.trim() }
        .filter { it.isNotBlank() }
        .toMutableList()
    val expanded = raw.flatMap { token ->
        if (token.any { it in '\u4e00'..'\u9fff' } && token.length > 4) {
            listOf(token) + token.windowed(4, 2, partialWindows = false) + token.windowed(2, 2, partialWindows = false)
        } else listOf(token)
    }
    return expanded.distinct().take(5).ifEmpty { listOf(query.trim()).filter { it.isNotBlank() } }
}

fun detectAiSearchTimeIntent(query: String): AiSearchTimeIntent? = when {
    Regex("\\d{4}-\\d{2}-\\d{2}").containsMatchIn(query) -> AiSearchTimeIntent.Date(Regex("\\d{4}-\\d{2}-\\d{2}").find(query)!!.value)
    Regex("\\d{4}-\\d{2}").containsMatchIn(query) -> AiSearchTimeIntent.Month(Regex("\\d{4}-\\d{2}").find(query)!!.value)
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

private fun scoreEvidence(item: AiSearchEvidence, keywords: List<String>, primaryTypes: Set<String>, nowDate: String): Int {
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
```

- [ ] **Step 4: Run pure tests and commit**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.mcp.AiSearchOrchestratorTest"
```

Expected: PASS.

Commit:

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/mcp/AiSearchOrchestrator.kt shared/src/commonTest/kotlin/com/dailysatori/service/mcp/AiSearchOrchestratorTest.kt
git commit -m "feat: add AI chat search planning"
```

## Task 3: Repository-Backed Evidence Recall And Prompt Building

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/AiSearchOrchestrator.kt`
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/AiSearchOrchestratorTest.kt`

- [ ] **Step 1: Add tests for prompt and reference fallback helpers**

Append to `AiSearchOrchestratorTest`:

```kotlin
@Test
fun buildsEvidencePromptWithSufficiencyAndOpenableRefsOnly() {
    val evidence = listOf(
        AiSearchEvidence(McpSearchResult(1, "article", "AI 文章", "摘要", "2026-05-30", matchReason = "命中：AI"), "AI 摘要"),
        AiSearchEvidence(McpSearchResult(2, "core_memory", "偏好", "喜欢结构化", null, matchReason = "命中：结构化"), "喜欢结构化", evidenceOnly = true),
    )

    val prompt = buildAiSearchEvidencePrompt("我收藏过哪些 AI 文章", evidence)

    assertTrue(prompt.contains("用户问题：我收藏过哪些 AI 文章"))
    assertTrue(prompt.contains("证据充足度：少量相关记录"))
    assertTrue(prompt.contains("[article_1]"))
    assertTrue(prompt.contains("[core_memory_2]"))
    assertTrue(prompt.contains("只能基于上述证据回答"))
}

@Test
fun fallbackAnswerMentionsCountTypesTopMatchesAndSparseEvidence() {
    val answer = buildAiSearchFallbackAnswer(
        query = "我之前写过焦虑吗",
        rankedResults = listOf(McpSearchResult(1, "diary", "日记", "片段", "2026-05-30", matchReason = "命中：焦虑")),
    )

    assertTrue(answer.contains("找到 1 条相关内容"))
    assertTrue(answer.contains("我只找到少量相关记录"))
    assertTrue(answer.contains("日记"))
    assertTrue(answer.contains("命中：焦虑"))
}
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.mcp.AiSearchOrchestratorTest.buildsEvidencePromptWithSufficiencyAndOpenableRefsOnly" --tests "com.dailysatori.service.mcp.AiSearchOrchestratorTest.fallbackAnswerMentionsCountTypesTopMatchesAndSparseEvidence"
```

Expected: FAIL because prompt/fallback helpers do not exist.

- [ ] **Step 3: Implement prompt and fallback helpers**

Add to `AiSearchOrchestrator.kt`:

```kotlin
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
```

- [ ] **Step 4: Add repository-backed orchestrator class**

Append a minimal class that uses existing sync repository APIs:

```kotlin
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
        val primaryTypes = primaryTypesForPlan(plan)
        val ranked = rankAiSearchEvidence(evidence, keywords, primaryTypes).take(12)
        val refs = ranked.map { it.result }.filter { canOpenSearchResult(it.type) }.take(8)
        return AiSearchResult(
            plan = plan,
            evidence = ranked,
            references = refs,
            evidencePrompt = ranked.takeIf { it.isNotEmpty() }?.let { buildAiSearchEvidencePrompt(query, it) },
        )
    }

    private fun searchMemoryEvidence(keywords: List<String>): List<AiSearchEvidence> = keywords.flatMap { keyword ->
        memoryRepo.search(keyword, 10).map { memory ->
            AiSearchEvidence(
                result = McpSearchResult(memory.id, "core_memory", memory.title, memory.content.take(160), null),
                searchableText = listOf(memory.title, memory.content, memory.tags.orEmpty()).joinToString(" "),
                evidenceOnly = true,
                fromMemory = true,
            )
        }
    }

    private fun searchDiaryEvidence(keywords: List<String>) = keywords.flatMap { keyword ->
        diaryRepo.searchSync(keyword).take(8).map { diary ->
            AiSearchEvidence(McpSearchResult(diary.id, "diary", diary.created_at.toString(), diary.content.take(160), diary.created_at.toString()), diary.content)
        }
    }

    private fun searchArticleEvidence(keywords: List<String>) = keywords.flatMap { keyword ->
        articleRepo.searchFavoriteFirstSync(keyword).take(8).map { article ->
            AiSearchEvidence(
                McpSearchResult(article.id, "article", article.ai_title ?: article.title ?: "无标题文章", article.ai_content?.take(160), article.created_at.toString(), isFavorite = article.is_favorite == 1L),
                listOf(article.title, article.ai_title, article.ai_content, article.ai_markdown_content).filterNotNull().joinToString(" "),
            )
        }
    }

    private fun searchBookEvidence(keywords: List<String>) = keywords.flatMap { keyword ->
        bookRepo.searchSync(keyword).take(5).map { book ->
            AiSearchEvidence(McpSearchResult(book.id, "book", book.title, book.author, book.created_at.toString()), "${book.title} ${book.author} ${book.introduction}")
        }
    }

    private fun searchBookViewpointEvidence(keywords: List<String>) = keywords.flatMap { keyword ->
        viewpointRepo.searchBookContent(keyword).take(8).map { row ->
            AiSearchEvidence(McpSearchResult(row.viewpointId, "book_viewpoint", row.bookTitle, row.title, null), "${row.bookTitle} ${row.title} ${row.content} ${row.example}")
        }
    }
}

private fun primaryTypesForPlan(plan: AiSearchPlan): Set<String> = buildSet {
    if (plan.searchDiaries) add("diary")
    if (plan.searchArticles) add("article")
    if (plan.searchBooks) add("book")
    if (plan.searchBookViewpoints) add("book_viewpoint")
}
```

- [ ] **Step 5: Run tests and commit**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.mcp.AiSearchOrchestratorTest"
```

Expected: PASS.

Commit:

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/mcp/AiSearchOrchestrator.kt shared/src/commonTest/kotlin/com/dailysatori/service/mcp/AiSearchOrchestratorTest.kt
git commit -m "feat: build AI chat local evidence prompts"
```

## Task 4: Wire Orchestrator Into MCP Agent

**Files:**
- Modify: `shared/src/main/kotlin/com/dailysatori/service/mcp/McpAgentService.kt`
- Modify: `shared/src/main/kotlin/com/dailysatori/di/SharedModule.kt`
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpAgentPresentationTest.kt`

- [ ] **Step 1: Add source-level wiring test**

Add to `McpAgentPresentationTest`:

```kotlin
@Test
fun mcpAgentUsesAiSearchOrchestratorBeforeToolLoop() {
    val service = java.io.File("src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt").readText()
    val di = java.io.File("src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt").readText()

    assertTrue(service.contains("private val aiSearchOrchestrator: AiSearchOrchestrator"))
    assertTrue(service.contains("val localSearch = aiSearchOrchestrator.search(query)"))
    assertTrue(service.contains("referencesForAnswer(cleanAnswer, localSearch.references"))
    assertTrue(di.contains("single { AiSearchOrchestrator(get(), get(), get(), get(), get()) }"))
    assertTrue(di.contains("McpAgentService(get(), get(), get(), get())"))
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.mcp.McpAgentPresentationTest.mcpAgentUsesAiSearchOrchestratorBeforeToolLoop"
```

Expected: FAIL because service and DI are not wired.

- [ ] **Step 3: Update DI**

In `SharedModule.kt`, add import:

```kotlin
import com.dailysatori.service.mcp.AiSearchOrchestrator
```

Register before `McpAgentService`:

```kotlin
single { AiSearchOrchestrator(get(), get(), get(), get(), get()) }
single { McpAgentService(get(), get(), get(), get()) }
```

- [ ] **Step 4: Update `McpAgentService` constructor and local search use**

Change constructor:

```kotlin
class McpAgentService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val toolRegistry: McpToolRegistry,
    private val aiSearchOrchestrator: AiSearchOrchestrator,
) {
```

At the start of `processQuery`, after `collectedResults` is created, add:

```kotlin
val localSearch = aiSearchOrchestrator.search(query)
collectedResults.addAll(localSearch.references)
```

When building user message content, replace:

```kotlin
put("content", query)
```

with:

```kotlin
put("content", localSearch.evidencePrompt ?: query)
```

When config is missing, before returning the config error, add:

```kotlin
if (localSearch.references.isNotEmpty()) {
    return McpAgentResult(
        answer = buildAiSearchFallbackAnswer(query, localSearch.references) + "\n\nAI 服务未配置，以上为本地搜索结果。",
        searchResults = localSearch.references,
    )
}
```

When final answer is built, replace the result filtering block with:

```kotlin
val cleanAnswer = privacyMasker.restore(removeMcpRefsTag(finalAnswer ?: buildFallbackAnswer(query, collectedResults)))
val filteredResults = filterRelevantMcpResults(collectedResults, finalAnswer ?: "")
val preciseResults = preciseSearchResultsForQuery(query, filteredResults)
val searchResults = referencesForAnswer(finalAnswer ?: cleanAnswer, preciseResults.ifEmpty { localSearch.references })
McpAgentResult(answer = cleanAnswer, searchResults = searchResults)
```

When request returns null and local refs exist, return:

```kotlin
answer = buildAiSearchFallbackAnswer(query, localSearch.references),
searchResults = localSearch.references,
```

- [ ] **Step 5: Run wiring test and commit**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.mcp.McpAgentPresentationTest.mcpAgentUsesAiSearchOrchestratorBeforeToolLoop"
```

Expected: PASS.

Commit:

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpAgentPresentationTest.kt
git commit -m "feat: use local evidence in AI chat"
```

## Task 5: Remove Top-Bar Memory Search Entry

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`

- [ ] **Step 1: Update failing UI regression test**

Change `topBarDoesNotExposeRefreshAction` in `AiChatUiStateTest` to:

```kotlin
@Test
fun topBarDoesNotExposeRefreshOrMemorySearchAction() {
    val screen = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt").readText()

    assertFalse(aiChatShowsRefreshAction())
    assertFalse(aiChatShowsMemorySearchAction())
    assertFalse(screen.contains("MemorySearchSheet("))
    assertFalse(screen.contains("showMemorySheet"))
    assertFalse(screen.contains("contentDescription = \"记忆搜索\""))
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest.topBarDoesNotExposeRefreshOrMemorySearchAction"
```

Expected: FAIL because the current screen still exposes memory search.

- [ ] **Step 3: Remove memory search from screen**

In `AiChatScreen.kt`:

- Remove `import androidx.compose.material.icons.filled.Search`.
- Remove `import androidx.compose.material3.Icon`.
- Remove `import androidx.compose.material3.IconButton` if no longer used.
- Remove `var showMemorySheet by remember { mutableStateOf(false) }`.
- Remove `actions = { IconButton(...) }` from `AppTopBar`.
- Remove the `if (showMemorySheet) { MemorySearchSheet(...) }` block.

The `AppTopBar` call should keep only title/navigation values:

```kotlin
AppTopBar(
    title = "AI 助手",
    showBack = false,
    myNavigationLabel = "我的",
    onMyNavigationClick = onMyClick,
)
```

In `AiChatViewModel.kt`, change:

```kotlin
fun aiChatShowsMemorySearchAction(): Boolean = false
```

- [ ] **Step 4: Run test and commit**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest.topBarDoesNotExposeRefreshOrMemorySearchAction"
```

Expected: PASS.

Commit:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt
git commit -m "fix: remove AI chat memory search action"
```

## Task 6: Final Verification

**Files:**
- No source changes expected unless verification exposes a defect.

- [ ] **Step 1: Run targeted shared tests**

Run:

```bash
./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.mcp.AiSearchOrchestratorTest" --tests "com.dailysatori.service.mcp.McpAgentPresentationTest" --tests "com.dailysatori.service.mcp.McpSearchResultPersistenceTest"
```

Expected: PASS.

- [ ] **Step 2: Run targeted app tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest"
```

Expected: PASS.

- [ ] **Step 3: Run required compile check**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Install and launch on connected device**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: install succeeds and activity starts. If no device is connected, record the blocker with `adb devices` output.

- [ ] **Step 5: Inspect diff and commit any verification fixes**

Run:

```bash
git status --short
git diff --stat
```

Expected: only intended AI chat search files are changed. Do not stage unrelated existing dirty files such as `DiaryCard.kt` or `MainContentRhythmTest.kt` unless the user explicitly asks.

If verification required fixes, commit them:

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/mcp/AiSearchOrchestrator.kt shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentService.kt shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpAgentPresentation.kt shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpSearchResultPersistence.kt shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolResultFormatter.kt shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailViewModel.kt shared/src/commonTest/kotlin/com/dailysatori/service/mcp/AiSearchOrchestratorTest.kt shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpAgentPresentationTest.kt shared/src/commonTest/kotlin/com/dailysatori/service/mcp/McpSearchResultPersistenceTest.kt app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt
git commit -m "fix: verify AI chat search orchestration"
```

## Plan Self-Review

Spec coverage:

- Deterministic local orchestration: Tasks 2 and 3.
- Existing SQL/tool loop remains available: Task 4.
- Evidence trust and original source preference: Task 3 structure and Task 2 ranking.
- Evidence sufficiency and fallback answer: Task 3.
- Keyword and time intent: Task 2.
- `book_viewpoint` first-class references: Task 1.
- Reference fallback for missing/invalid refs: Task 1 and Task 4.
- Remove top-bar memory search: Task 5.
- No schema change: all tasks use existing tables/repositories.
- Future enhancements are documented in the spec and intentionally left outside this implementation plan.

Forbidden-marker scan:

- No incomplete-marker text, vague edge-case instructions, or references to absent functions remain.

Type consistency:

- Plan uses `AiSearchOrchestrator`, `AiSearchPlan`, `AiSearchEvidence`, `AiSearchResult`, `AiSearchTimeIntent`, `McpSearchResult.matchReason`, `SearchResultOpenTarget.BookViewpoint`, and `referencesForAnswer` consistently across tasks.
