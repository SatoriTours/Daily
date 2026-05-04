# Hybrid Book Intelligence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users search a book title, choose a reliable candidate, add it, and automatically generate concise viewpoint cards so the reading module becomes faster and easier to use.

**Architecture:** Add a focused shared `BookIntelligenceService` that produces structured book candidates and viewpoint drafts using existing AI services, with remote-MCP/source hooks and AI fallback for local-command MCP configs that Android cannot execute. Update repository insertion to return the inserted book id, then update the book search UI/ViewModel and navigation result handling so `添加并分析` returns to the reading tab with the new book selected.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Koin, Jetpack Compose Material 3, Kotlin coroutines/StateFlow, kotlin.test.

---

## File Structure

- Create `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookIntelligenceService.kt`: structured prompts, JSON parsing, AI-backed search, viewpoint generation, and source fallback policy.
- Create `shared/src/commonTest/kotlin/com/dailysatori/service/book/BookIntelligenceServiceTest.kt`: tests JSON parsing, prompt constraints, and fallback policy.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookSearchService.kt`: add `sourceSummary` to `BookSearchResult` with a default value.
- Modify `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`: add `selectLastInsertedBookId` query.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/data/repository/BookRepository.kt`: add `insertAndReturnId(...)` while preserving existing `insert(...)`.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`: register `BookIntelligenceService`.
- Modify `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`: inject `BookIntelligenceService` and `BookViewpointRepository` into `BookSearchViewModel`.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookSearchViewModel.kt`: drive search, add-and-analyze progress, persistence, partial success, and retry state.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookSearchScreen.kt`: update CTA, progress UI, source summary, and completion callback.
- Modify `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`: pass book-add result back to `HomeScreen`.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`: preserve/activate reading tab and forward selected book id to `BooksScreen`.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt` and `BooksViewModel.kt`: select newly added book and expose retry generation entry when a selected book has no viewpoints.
- Add app unit tests under `app/src/test/kotlin/com/dailysatori/ui/feature/book/` for search state text helpers and primary CTA text.

## Commit Policy

The user has not asked for commits. Do not commit unless explicitly asked.

---

### Task 1: Shared Book Intelligence Parsing And Prompts

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookIntelligenceService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookSearchService.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/book/BookIntelligenceServiceTest.kt`

- [ ] **Step 1: Write failing shared tests**

Create `shared/src/commonTest/kotlin/com/dailysatori/service/book/BookIntelligenceServiceTest.kt`:

```kotlin
package com.dailysatori.service.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookIntelligenceServiceTest {
    @Test
    fun parsesBookCandidatesFromJsonArray() {
        val json = """
            [
              {"title":"穷查理宝典","author":"查理·芒格","category":"投资","introduction":"芒格思想合集","isbn":"","coverUrl":"https://img.example/a.jpg","sourceSummary":"来自公开书评"},
              {"title":"","author":"无效"}
            ]
        """.trimIndent()

        val results = parseBookCandidateJson(json)

        assertEquals(1, results.size)
        assertEquals("穷查理宝典", results.first().title)
        assertEquals("查理·芒格", results.first().author)
        assertEquals("投资", results.first().category)
        assertEquals("来自公开书评", results.first().sourceSummary)
    }

    @Test
    fun parsesBookCandidatesFromFencedJson() {
        val json = """
            ```json
            [{"title":"原则","author":"Ray Dalio","introduction":"原则说明"}]
            ```
        """.trimIndent()

        val results = parseBookCandidateJson(json)

        assertEquals(1, results.size)
        assertEquals("原则", results.first().title)
    }

    @Test
    fun parsesValidViewpointDraftsOnly() {
        val json = """
            [
              {"title":"复利来自长期主义","content":"长期投入会放大优势。","example":"持续阅读和复盘会积累判断力。"},
              {"title":"缺少解释","content":"","example":"无效"}
            ]
        """.trimIndent()

        val drafts = parseBookViewpointJson(json)

        assertEquals(1, drafts.size)
        assertEquals("复利来自长期主义", drafts.first().title)
        assertEquals("长期投入会放大优势。", drafts.first().content)
        assertEquals("持续阅读和复盘会积累判断力。", drafts.first().example)
    }

    @Test
    fun viewpointPromptRequiresTenStructuredCards() {
        val prompt = buildBookViewpointPrompt(
            title = "穷查理宝典",
            author = "查理·芒格",
            introduction = "投资与人生智慧",
            sourceNotes = "公开资料摘要",
        )

        assertTrue(prompt.contains("10"))
        assertTrue(prompt.contains("title"))
        assertTrue(prompt.contains("content"))
        assertTrue(prompt.contains("example"))
        assertTrue(prompt.contains("只返回 JSON 数组"))
    }

    @Test
    fun androidDoesNotUseLocalCommandMcpAsCallableSource() {
        assertFalse(isAndroidCallableMcpSource("local"))
        assertFalse(isAndroidCallableMcpSource("stdio"))
        assertTrue(isAndroidCallableMcpSource("remote"))
        assertTrue(isAndroidCallableMcpSource("http"))
    }
}
```

- [ ] **Step 2: Run tests and verify red**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.book.BookIntelligenceServiceTest"`

Expected: FAIL with unresolved references such as `parseBookCandidateJson`, `BookViewpointDraft`, and `buildBookViewpointPrompt`.

- [ ] **Step 3: Add `sourceSummary` to book search result**

Modify `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookSearchService.kt`:

```kotlin
@Serializable
data class BookSearchResult(
    val title: String,
    val author: String = "",
    val category: String = "",
    val introduction: String = "",
    val isbn: String = "",
    val coverUrl: String = "",
    val sourceSummary: String = "",
)
```

Keep `WebSearchEngine.parseResults()` valid by not passing `sourceSummary`; the default value preserves compatibility.

- [ ] **Step 4: Implement parsing and prompt helpers**

Create `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookIntelligenceService.kt` with these top-level models/helpers first:

```kotlin
package com.dailysatori.service.book

import com.dailysatori.data.repository.McpServerRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
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

private val bookIntelligenceJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun isAndroidCallableMcpSource(transport: String): Boolean =
    transport.equals("remote", ignoreCase = true) ||
        transport.equals("http", ignoreCase = true) ||
        transport.equals("streamable-http", ignoreCase = true)

fun parseBookCandidateJson(response: String): List<BookSearchResult> {
    val array = parseJsonArray(response) ?: return emptyList()
    return array.mapNotNull { item ->
        val obj = item.jsonObject
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
        val obj = item.jsonObject
        val title = obj.stringValue("title").trim()
        val content = obj.stringValue("content").trim()
        val example = obj.stringValue("example").trim()
        if (title.isBlank() || content.isBlank() || example.isBlank()) return@mapNotNull null
        BookViewpointDraft(title = title, content = content, example = example)
    }.take(10)
}

fun buildBookCandidatePrompt(query: String, sourceNotes: String): String =
    """你是一个可靠的书籍资料整理助手。用户搜索："$query"。

请根据以下公开资料线索整理候选书籍。不要编造不存在的书。

资料线索：
$sourceNotes

请只返回 JSON 数组，每个对象包含：
- title: 书名
- author: 作者
- category: 分类
- introduction: 200字以内简介
- isbn: ISBN，没有则为空字符串
- coverUrl: 封面 URL，没有则为空字符串
- sourceSummary: 可信来源摘要，说明你依据了哪些资料

只返回 JSON 数组，不要 Markdown，不要解释。""".trimIndent()

fun buildBookViewpointPrompt(
    title: String,
    author: String,
    introduction: String,
    sourceNotes: String,
): String =
    """你是一个帮助用户快速读懂一本书的读书助手。

书名：$title
作者：$author
简介：$introduction

公开资料线索：
$sourceNotes

请提炼这本书最重要的 10 个观点。每个观点都必须帮助用户更快理解和应用这本书。

只返回 JSON 数组，每个对象包含：
- title: 一句话观点，短而有记忆点
- content: 观点解释，说明这个观点为什么重要
- example: 案例或应用场景，让用户知道怎么用

只返回 JSON 数组，不要 Markdown，不要解释。""".trimIndent()

private fun parseJsonArray(response: String): JsonArray? {
    val cleaned = response.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    return try {
        bookIntelligenceJson.parseToJsonElement(cleaned).jsonArray
    } catch (_: Exception) {
        null
    }
}

private fun JsonObject.stringValue(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull ?: ""
```

- [ ] **Step 5: Run tests and verify green**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.book.BookIntelligenceServiceTest"`

Expected: PASS.

---

### Task 2: Repository Insert Id And Intelligence Service Runtime

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq:243-259`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/BookRepository.kt:22-32`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookIntelligenceService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt:23-24,79-82`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/BookRepositoryInsertApiTest.kt`

- [ ] **Step 1: Write compile-smoke test for insert id API**

Create `shared/src/commonTest/kotlin/com/dailysatori/data/repository/BookRepositoryInsertApiTest.kt`:

```kotlin
package com.dailysatori.data.repository

import kotlin.test.Test
import kotlin.test.assertTrue

class BookRepositoryInsertApiTest {
    @Test
    fun repositoryExposesInsertAndReturnIdApi() {
        assertTrue(::insertAndReturnIdSignatureCompiles.name.isNotBlank())
    }

    private fun insertAndReturnIdSignatureCompiles(repository: BookRepository): Long =
        repository.insertAndReturnId(
            title = "测试书",
            author = "作者",
            category = "分类",
            coverImage = "",
            introduction = "简介",
        )
}
```

- [ ] **Step 2: Run test and verify red**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.data.repository.BookRepositoryInsertApiTest"`

Expected: FAIL because `insertAndReturnId` does not exist.

- [ ] **Step 3: Add SQLDelight last inserted id query**

In `DailySatori.sq`, after `insertBook` add:

```sql
selectLastInsertedBookId:
SELECT last_insert_rowid();
```

- [ ] **Step 4: Add repository insert method**

In `BookRepository.kt`, add:

```kotlin
fun insertAndReturnId(
    title: String,
    author: String,
    category: String,
    coverImage: String,
    introduction: String,
    hasUpdate: Long = 0,
): Long {
    insert(title, author, category, coverImage, introduction, hasUpdate)
    return q.selectLastInsertedBookId().executeAsOne()
}
```

- [ ] **Step 5: Add runtime methods to `BookIntelligenceService`**

Append this class to `BookIntelligenceService.kt` below helpers:

```kotlin
class BookIntelligenceService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val bookSearchService: BookSearchService,
    private val mcpServerRepository: McpServerRepository,
) {
    suspend fun searchBooks(query: String): List<BookSearchResult> {
        val sourceNotes = collectSourceNotes(query)
        val aiResponse = completeWithDefaultAi(
            prompt = query,
            systemPrompt = buildBookCandidatePrompt(query, sourceNotes),
        ) ?: return bookSearchService.search(query)
        return parseBookCandidateJson(aiResponse).ifEmpty { bookSearchService.search(query) }
    }

    suspend fun generateViewpoints(book: BookSearchResult): List<BookViewpointDraft> {
        val sourceNotes = collectSourceNotes("${book.title} ${book.author} 核心观点 书评 目录")
        val aiResponse = completeWithDefaultAi(
            prompt = book.title,
            systemPrompt = buildBookViewpointPrompt(
                title = book.title,
                author = book.author,
                introduction = book.introduction,
                sourceNotes = sourceNotes,
            ),
        ) ?: return emptyList()
        return parseBookViewpointJson(aiResponse)
    }

    private suspend fun collectSourceNotes(query: String): String {
        val remoteMcpNames = mcpServerRepository.getEnabled()
            .filter { isAndroidCallableMcpSource(it.template_type.ifBlank { it.config_json }) || it.server_url.startsWith("http") }
            .joinToString { it.name }
        val webResults = bookSearchService.search(query).take(5).joinToString("\n") { result ->
            "- ${result.title} ${result.author}: ${result.introduction.take(300)}"
        }
        val mcpNote = if (remoteMcpNames.isBlank()) {
            "未发现 Android 可直接调用的远程 MCP，使用 AI 与内置网络搜索兜底。"
        } else {
            "可用远程 MCP：$remoteMcpNames。当前版本优先使用内置网络搜索和 AI 结构化，远程 MCP 调用失败时兜底。"
        }
        return "$mcpNote\n$webResults".trim()
    }

    private suspend fun completeWithDefaultAi(prompt: String, systemPrompt: String): String? {
        val config = aiConfigService.getDefaultConfig() ?: return null
        if (config.api_token.isBlank()) return null
        return try {
            aiService.complete(
                prompt = prompt,
                apiAddress = config.api_address,
                apiToken = config.api_token,
                modelName = config.model_name,
                provider = config.provider,
                systemPrompt = systemPrompt,
            )
        } catch (_: Exception) {
            null
        }
    }
}
```

If `Mcp_server` generated properties differ, inspect generated usage in existing code and adjust field names. Keep API keys out of logs.

- [ ] **Step 6: Register service in Koin**

In `SharedModule.kt`, import `BookIntelligenceService` and register after `BookSearchService`:

```kotlin
single { BookIntelligenceService(get(), get(), get(), get()) }
```

- [ ] **Step 7: Run verification**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.data.repository.BookRepositoryInsertApiTest"`

Expected: PASS.

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:compileDebugKotlinAndroid`

Expected: PASS.

---

### Task 3: Add-And-Analyze ViewModel Flow

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookSearchViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt:77-82`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchStateTest.kt`

- [ ] **Step 1: Write state helper tests**

Create `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchStateTest.kt`:

```kotlin
package com.dailysatori.ui.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals

class BookSearchStateTest {
    @Test
    fun formatsPartialViewpointMessage() {
        assertEquals("已生成 6 个观点，可稍后重试补全", bookAnalysisPartialMessage(6))
    }

    @Test
    fun formatsAnalysisFailureMessage() {
        assertEquals("分析失败，可重新生成观点", bookAnalysisFailureMessage())
    }
}
```

- [ ] **Step 2: Run tests and verify red**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.BookSearchStateTest"`

Expected: FAIL because helper functions do not exist.

- [ ] **Step 3: Update `BookSearchState` and ViewModel dependencies**

In `BookSearchViewModel.kt`, replace dependencies with:

```kotlin
class BookSearchViewModel(
    private val bookIntelligenceService: BookIntelligenceService,
    private val bookRepo: BookRepository,
    private val viewpointRepo: BookViewpointRepository,
) : ViewModel() {
```

Update `BookSearchState`:

```kotlin
data class BookSearchState(
    val query: String = "",
    val results: List<BookSearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val isAnalyzing: Boolean = false,
    val analysisStep: String = "",
    val analysisMessage: String? = null,
    val addedBookTitle: String? = null,
    val addedBookId: Long? = null,
    val error: String? = null,
)
```

Add helpers at file bottom:

```kotlin
fun bookAnalysisPartialMessage(count: Int): String = "已生成 $count 个观点，可稍后重试补全"

fun bookAnalysisFailureMessage(): String = "分析失败，可重新生成观点"
```

- [ ] **Step 4: Update search method**

Replace `mcpAgentService.searchBookOnline(query)` with:

```kotlin
val results = bookIntelligenceService.searchBooks(query)
```

Set no-result error to:

```kotlin
"未找到可靠书籍资料，请换个关键词再试"
```

- [ ] **Step 5: Replace add with add-and-analyze**

Replace `addBook(result)` with:

```kotlin
fun addAndAnalyzeBook(result: BookSearchResult) {
    viewModelScope.launch(Dispatchers.IO) {
        var insertedBookId: Long? = null
        try {
            _state.update {
                it.copy(
                    isAnalyzing = true,
                    analysisStep = "正在搜索书籍资料",
                    analysisMessage = null,
                    error = null,
                )
            }
            val bookId = bookRepo.insertAndReturnId(
                title = result.title,
                author = result.author,
                category = result.category,
                coverImage = result.coverUrl,
                introduction = result.introduction,
            )
            insertedBookId = bookId
            _state.update { it.copy(analysisStep = "正在提炼核心观点") }
            val viewpoints = bookIntelligenceService.generateViewpoints(result)
            _state.update { it.copy(analysisStep = "正在生成观点卡片") }
            viewpoints.forEach { draft ->
                viewpointRepo.insert(
                    bookId = bookId,
                    title = draft.title,
                    content = draft.content,
                    example = draft.example,
                )
            }
            val message = if (viewpoints.size in 1..9) bookAnalysisPartialMessage(viewpoints.size) else null
            _state.update {
                it.copy(
                    isAnalyzing = false,
                    analysisStep = "",
                    analysisMessage = message,
                    addedBookTitle = result.title,
                    addedBookId = bookId,
                )
            }
        } catch (_: Exception) {
            _state.update {
                it.copy(
                    isAnalyzing = false,
                    analysisStep = "",
                    analysisMessage = if (insertedBookId != null) bookAnalysisFailureMessage() else null,
                    addedBookTitle = if (insertedBookId != null) result.title else null,
                    addedBookId = insertedBookId,
                    error = if (insertedBookId == null) "添加失败" else null,
                )
            }
        }
    }
}
```

- [ ] **Step 6: Update DI**

In `ViewModelModule.kt`, remove `McpAgentService` usage from `BookSearchViewModel` and inject:

```kotlin
BookSearchViewModel(
    bookIntelligenceService = get(),
    bookRepo = get<BookRepository>(),
    viewpointRepo = get<BookViewpointRepository>(),
)
```

- [ ] **Step 7: Run tests**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.BookSearchStateTest"`

Expected: PASS.

---

### Task 4: Search UI, Completion Callback, And Book Selection Result

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookSearchScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksViewModel.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchUiTextTest.kt`

- [ ] **Step 1: Write UI text tests**

Create `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchUiTextTest.kt`:

```kotlin
package com.dailysatori.ui.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals

class BookSearchUiTextTest {
    @Test
    fun primaryBookActionAddsAndAnalyzes() {
        assertEquals("添加并分析", bookSearchPrimaryActionText(isAnalyzing = false))
        assertEquals("分析中...", bookSearchPrimaryActionText(isAnalyzing = true))
    }
}
```

- [ ] **Step 2: Run test and verify red**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.BookSearchUiTextTest"`

Expected: FAIL because `bookSearchPrimaryActionText` does not exist.

- [ ] **Step 3: Update `BookSearchScreen` API and UI**

Change signature:

```kotlin
fun BookSearchScreen(
    onBack: () -> Unit = {},
    onBookAdded: (Long) -> Unit = {},
)
```

Add helper near bottom:

```kotlin
fun bookSearchPrimaryActionText(isAnalyzing: Boolean): String = if (isAnalyzing) "分析中..." else "添加并分析"
```

In result list, replace `onAdd = { viewModel.addBook(result) }` with:

```kotlin
onAdd = { viewModel.addAndAnalyzeBook(result) },
isAnalyzing = state.isAnalyzing,
```

Update `SearchResultItem` signature:

```kotlin
private fun SearchResultItem(
    result: BookSearchResult,
    isAnalyzing: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
)
```

In the button:

```kotlin
FilledTonalButton(
    onClick = onAdd,
    enabled = !isAnalyzing,
    contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.xs),
) {
    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
    Spacer(modifier = Modifier.width(Spacing.xxs))
    Text(bookSearchPrimaryActionText(isAnalyzing))
}
```

Show source summary under introduction:

```kotlin
if (result.sourceSummary.isNotBlank()) {
    Spacer(modifier = Modifier.height(Spacing.xs))
    Text(
        result.sourceSummary,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.primary,
    )
}
```

Show progress if analyzing:

```kotlin
if (state.isAnalyzing) {
    Text(
        text = state.analysisStep.ifBlank { "正在分析书籍" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
    )
}
```

React to completion:

```kotlin
LaunchedEffect(state.addedBookId) {
    state.addedBookId?.let(onBookAdded)
}
```

- [ ] **Step 4: Pass result through navigation**

In `NavHost.kt`, define key near constants:

```kotlin
private const val SELECTED_BOOK_ID_KEY = "selectedBookId"
```

In `composable<HomeRoute>` collect selected book id:

```kotlin
val selectedBookId = it.savedStateHandle.getStateFlow<Long?>(SELECTED_BOOK_ID_KEY, null).collectAsState()
HomeScreen(
    selectedBookId = selectedBookId.value,
    onSelectedBookConsumed = { it.savedStateHandle[SELECTED_BOOK_ID_KEY] = null },
    ...
)
```

Add these imports:

```kotlin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
```

In `BookSearchScreen` route:

```kotlin
BookSearchScreen(
    onBack = { navController.popBackStack() },
    onBookAdded = { bookId ->
        navController.previousBackStackEntry?.savedStateHandle?.set(SELECTED_BOOK_ID_KEY, bookId)
        navController.popBackStack()
    },
)
```

- [ ] **Step 5: Forward selected book to Books tab**

In `HomeScreen.kt`, update signature:

```kotlin
fun HomeScreen(
    onArticleClick: (Long) -> Unit = {},
    onBookSearchClick: () -> Unit = {},
    onAiArticleClick: (Long) -> Unit = {},
    selectedBookId: Long? = null,
    onSelectedBookConsumed: () -> Unit = {},
)
```

When `selectedBookId != null`, set selected tab to Books:

```kotlin
LaunchedEffect(selectedBookId) {
    if (selectedBookId != null) selectedIndex = 2
}
```

Pass to Books:

```kotlin
2 -> BooksScreen(
    onSearchClick = onBookSearchClick,
    selectedBookId = selectedBookId,
    onSelectedBookConsumed = onSelectedBookConsumed,
)
```

- [ ] **Step 6: Select new book in Books screen/viewmodel**

In `BooksScreen.kt`, update signature:

```kotlin
fun BooksScreen(
    onSearchClick: () -> Unit = {},
    selectedBookId: Long? = null,
    onSelectedBookConsumed: () -> Unit = {},
)
```

Add effect after state collection:

```kotlin
LaunchedEffect(selectedBookId) {
    selectedBookId?.let {
        viewModel.selectBook(it)
        onSelectedBookConsumed()
    }
}
```

In `BooksViewModel.kt`, keep `selectBook(bookId)` as the existing API and do not add new state for this step.

- [ ] **Step 7: Run UI tests and compile**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.BookSearchUiTextTest"`

Expected: PASS.

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`

Expected: PASS.

---

### Task 5: Retry Entry And Final Verification

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksViewModel.kt`

- [ ] **Step 1: Add retry affordance for empty selected book**

In `BooksScreen.kt`, when `state.viewpoints.isEmpty()` and `state.currentBookId != null`, show an `EmptyState` subtitle that tells the user to search/retry:

```kotlin
subtitle = "这本书还没有观点，点击搜索重新添加并分析"
```

Keep the top search icon as the retry path for this iteration. Do not add duplicate analysis logic to `BooksViewModel` unless the implementation already exposes a safe service path.

- [ ] **Step 2: Run full focused test set**

Run sequentially, not in parallel:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.book.BookIntelligenceServiceTest"
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.data.repository.BookRepositoryInsertApiTest"
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.BookSearchStateTest"
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.BookSearchUiTextTest"
```

Expected: each command reports `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run required compile**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Install and launch**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: install reports `Installed on 1 device`, launch reports `Starting: Intent { cmp=com.dailysatori/.MainActivity }`.

- [ ] **Step 5: Manual smoke checklist**

On device:

1. Open `读书` tab.
2. Tap search.
3. Search a known book title.
4. Confirm results show title, author, intro, and `添加并分析`.
5. Tap `添加并分析`.
6. Confirm progress step text appears.
7. Confirm app returns to reading tab after completion.
8. Confirm the added book is selected and viewpoint cards appear when generation succeeds.
9. If generation returns fewer than 10, confirm the partial count message is understandable.

- [ ] **Step 6: Check worktree status**

Run: `git status --short`

Expected: only files intentionally changed for this feature plus existing unrelated untracked spec/plan files are listed.
