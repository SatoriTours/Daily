# WeRead AI Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate book viewpoints from the default AI model only when WeRead returns a searchable book but lacks enough material for reliable WeRead-derived viewpoint cards.

**Architecture:** Keep WeRead as the only search source. Wrap viewpoint generation in a source-aware result so the shared service can choose WeRead or AI fallback while the UI only displays a transparent completion notice. Put fallback logic inside `WeReadSkillService` so `BookSearchViewModel` stays thin.

**Tech Stack:** Kotlin Multiplatform, Ktor `HttpClient`, SQLDelight repositories, Koin DI, kotlinx.serialization JSON, Android Compose UI, Gradle unit tests.

---

## File Structure

- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookIntelligenceService.kt`: add `BookViewpointGenerationResult` and `BookViewpointSource`, update the facade/interface return type.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt`: add AI fallback prompt/parser, sufficiency checks, source marking, and missing AI config error.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`: inject `AiConfigService` and `AiService` into `WeReadSkillService`.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookSearchViewModel.kt`: consume generation wrapper and display AI source notice.
- Modify `shared/src/commonTest/kotlin/com/dailysatori/service/book/WeReadSkillServiceTest.kt`: cover sufficiency, AI parsing, missing config, and source marking.
- Modify `shared/src/commonTest/kotlin/com/dailysatori/service/book/BookIntelligenceServiceTest.kt`: update facade fake source to return the wrapper.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchUiTextTest.kt`: cover AI completion notice text.

## Task 1: Add Source-Aware Viewpoint Result

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookIntelligenceService.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/book/BookIntelligenceServiceTest.kt`

- [ ] **Step 1: Write the failing facade test**

Replace the expected viewpoint result in `BookIntelligenceServiceTest.generateViewpointsDelegatesToSource` with:

```kotlin
val expected = BookViewpointGenerationResult(
    drafts = listOf(BookViewpointDraft(title = "判断", content = "解释", example = "案例")),
    source = BookViewpointSource.WeRead,
)
val source = FakeBookIntelligenceSource(viewpoints = expected)
val service = BookIntelligenceService(source)

val results = service.generateViewpoints(book)

assertEquals(expected, results)
assertEquals(book, source.viewpointBook)
```

Update the fake source constructor and method to:

```kotlin
private class FakeBookIntelligenceSource(
    private val searchResults: List<BookSearchResult> = emptyList(),
    private val viewpoints: BookViewpointGenerationResult = BookViewpointGenerationResult(emptyList(), BookViewpointSource.WeRead),
) : BookIntelligenceSource {
    var searchQuery: String? = null
        private set
    var viewpointBook: BookSearchResult? = null
        private set

    override suspend fun searchBooks(query: String): List<BookSearchResult> {
        searchQuery = query
        return searchResults
    }

    override suspend fun generateViewpoints(book: BookSearchResult): BookViewpointGenerationResult {
        viewpointBook = book
        return viewpoints
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.book.BookIntelligenceServiceTest.generateViewpointsDelegatesToSource"`

Expected: FAIL because `BookViewpointGenerationResult` and `BookViewpointSource` do not exist and `generateViewpoints` still returns `List<BookViewpointDraft>`.

- [ ] **Step 3: Implement the wrapper and interface return type**

In `BookIntelligenceService.kt`, add after `BookViewpointDraft`:

```kotlin
enum class BookViewpointSource { WeRead, AiFallback }

data class BookViewpointGenerationResult(
    val drafts: List<BookViewpointDraft>,
    val source: BookViewpointSource,
)
```

Change `BookIntelligenceSource` to:

```kotlin
interface BookIntelligenceSource {
    suspend fun searchBooks(query: String): List<BookSearchResult>
    suspend fun generateViewpoints(book: BookSearchResult): BookViewpointGenerationResult
}
```

Change the facade method to:

```kotlin
suspend fun generateViewpoints(book: BookSearchResult): BookViewpointGenerationResult =
    weReadSkillService.generateViewpoints(book)
```

Temporarily update `WeReadSkillService.generateViewpoints` to wrap the existing drafts:

```kotlin
override suspend fun generateViewpoints(book: BookSearchResult): BookViewpointGenerationResult {
    val bookId = book.sourceUrl.extractWeReadBookId()
        ?: searchBooks("${book.title} ${book.author}".trim(), limit = 1).firstOrNull()?.sourceUrl?.extractWeReadBookId()
        ?: throw WeReadSkillException(WeReadSkillErrorType.NoResults, "微信读书未找到相关书籍")
    val info = parseWeReadBookInfo(callGateway("/book/info", mapOf("bookId" to bookId)))
    val chapters = parseWeReadChapters(callGateway("/book/chapterinfo", mapOf("bookId" to bookId)))
    val reviews = parseWeReadReviews(
        callGateway("/review/list", mapOf("bookId" to bookId, "reviewListType" to 1, "count" to 10)),
    )
    return BookViewpointGenerationResult(
        drafts = buildWeReadViewpointDrafts(info, chapters, reviews),
        source = BookViewpointSource.WeRead,
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.book.BookIntelligenceServiceTest.generateViewpointsDelegatesToSource"`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/book/BookIntelligenceService.kt shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt shared/src/commonTest/kotlin/com/dailysatori/service/book/BookIntelligenceServiceTest.kt
git commit -m "feat: track book viewpoint source"
```

## Task 2: Add AI Fallback Parsing And Prompt Contracts

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/book/WeReadSkillServiceTest.kt`

- [ ] **Step 1: Write failing parser and prompt tests**

Append these tests to `WeReadSkillServiceTest`:

```kotlin
@Test
fun parsesDetailedAiFallbackViewpointsOnly() {
    val json = """
        [
          {"title":"待上架书也要先界定真实问题。","content":"当一本书还没有完整目录和书评时，观点生成不能假装拥有微信读书材料，而应基于书名、作者、简介和分类先界定读者可能面对的真实问题。这样生成的内容虽然来自 AI，但仍然围绕已知元数据展开，避免把不存在的章节或读者评价写成事实。","example":"例如一位读者想添加一本待上架的供应链新书，微信读书只返回书名、作者和一句简介。系统没有编造目录，而是把简介里的产业协同作为主题，让 AI 生成一个具体场景：采购、仓储和销售团队因为预测口径不同导致缺货，再说明如何用统一指标协调下一步动作。"},
          {"title":"太短","content":"短","example":"短"}
        ]
    """.trimIndent()

    val drafts = parseAiFallbackViewpointJson(json)

    assertEquals(1, drafts.size)
    assertEquals("待上架书也要先界定真实问题。", drafts.first().title)
}

@Test
fun buildsAiFallbackPromptWithDisclosureAndJsonContract() {
    val prompt = buildAiFallbackViewpointPrompt(
        book = BookSearchResult(
            title = "供应链架构师",
            author = "施云",
            category = "管理",
            introduction = "供应链是一套端到端的系统能力。",
        ),
        info = WeReadBookInfo(bookId = "123", title = "供应链架构师", author = "施云", intro = "端到端供应链。"),
        chapters = listOf(WeReadChapter(chapterUid = 1, chapterIdx = 1, title = "战略到运营")),
        reviews = emptyList(),
    )

    assertTrue(prompt.contains("只返回 JSON 数组"))
    assertTrue(prompt.contains("10 个对象"))
    assertTrue(prompt.contains("AI 生成"))
    assertTrue(prompt.contains("不能声称来自微信读书书评或原文"))
    assertTrue(prompt.contains("供应链架构师"))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.book.WeReadSkillServiceTest"`

Expected: FAIL because `parseAiFallbackViewpointJson` and `buildAiFallbackViewpointPrompt` do not exist.

- [ ] **Step 3: Add prompt and parser implementation**

In `WeReadSkillService.kt`, add public functions near `buildWeReadViewpointDrafts`:

```kotlin
fun buildAiFallbackViewpointPrompt(
    book: BookSearchResult,
    info: WeReadBookInfo,
    chapters: List<WeReadChapter>,
    reviews: List<WeReadReview>,
): String {
    val title = info.title.ifBlank { book.title }
    val author = info.author.ifBlank { book.author }
    val intro = info.intro.ifBlank { book.introduction }
    val chapterText = chapters.take(8).joinToString("、") { it.title }
    val reviewText = reviews.take(3).joinToString("\n") { it.content }
    return """
        请为一本微信读书已返回但资料不足的书生成 10 个观点卡片。
        这些观点是 AI 生成，不能声称来自微信读书书评或原文。

        书名：$title
        作者：$author
        分类：${book.category.ifBlank { info.category }}
        简介：$intro
        可用目录：$chapterText
        可用书评：$reviewText

        只返回 JSON 数组，不要 Markdown、解释或额外文本。
        数组必须包含 10 个对象，每个对象必须包含字段：title、content、example。
        title 必须是完整判断句。
        content 至少 80 个中文字符，说明观点的原因、边界和判断标准。
        example 至少 100 个中文字符，必须是具体场景，写清人物或组织、问题、动作和结果。
    """.trimIndent()
}

fun parseAiFallbackViewpointJson(response: String): List<BookViewpointDraft> {
    val array = runCatching { weReadJson.parseToJsonElement(extractJsonArray(response)).jsonArray }.getOrNull()
        ?: return emptyList()
    return array.mapNotNull { item ->
        val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
        val title = obj.stringValue("title").trim()
        val content = obj.stringValue("content").trim()
        val example = obj.stringValue("example").trim()
        if (title.isBlank() || content.length < 80 || example.length < 100) return@mapNotNull null
        BookViewpointDraft(title = title, content = content, example = example)
    }.take(10)
}

private fun extractJsonArray(response: String): String {
    val trimmed = response.trim()
    val unfenced = if (trimmed.startsWith("```")) {
        trimmed.lines()
            .drop(1)
            .dropLastWhile { it.trim().startsWith("```") || it.isBlank() }
            .joinToString("\n")
            .trim()
    } else {
        trimmed
    }
    val start = unfenced.indexOf('[')
    val end = unfenced.lastIndexOf(']')
    if (start < 0 || end < start) return "[]"
    return unfenced.substring(start, end + 1)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.book.WeReadSkillServiceTest"`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt shared/src/commonTest/kotlin/com/dailysatori/service/book/WeReadSkillServiceTest.kt
git commit -m "feat: parse AI book viewpoints"
```

## Task 3: Add Sufficiency Checks And AI Fallback Service Flow

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/book/WeReadSkillServiceTest.kt`

- [ ] **Step 1: Write failing sufficiency tests**

Append these tests to `WeReadSkillServiceTest`:

```kotlin
@Test
fun detectsSparseWeReadMaterialAsInsufficient() {
    val info = WeReadBookInfo(bookId = "123", title = "待上架新书", intro = "")
    val drafts = buildWeReadViewpointDrafts(info, chapters = emptyList(), reviews = emptyList())

    assertEquals(false, hasSufficientWeReadMaterial(info, emptyList(), emptyList(), drafts))
}

@Test
fun detectsConcreteWeReadMaterialAsSufficient() {
    val info = WeReadBookInfo(
        bookId = "123",
        title = "三体",
        author = "刘慈欣",
        intro = "人类文明第一次面对宇宙级不确定性，个体选择与集体命运被放到同一个坐标系里审视。",
    )
    val chapters = listOf(WeReadChapter(chapterUid = 1, chapterIdx = 1, title = "科学边界"))
    val reviews = listOf(WeReadReview("这本书真正震撼人的地方，是把文明选择写成每个人都能感受到的压力。"))
    val drafts = buildWeReadViewpointDrafts(info, chapters, reviews)

    assertEquals(true, hasSufficientWeReadMaterial(info, chapters, reviews, drafts))
}

@Test
fun missingAiConfigUsesDedicatedErrorMessage() {
    val error = assertFailsWith<WeReadSkillException> { requireAiFallbackConfig(null) }

    assertEquals(WeReadSkillErrorType.RemoteFailure, error.type)
    assertEquals("微信读书资料不足，请先配置默认 AI 模型后重试", error.message)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.book.WeReadSkillServiceTest"`

Expected: FAIL because `hasSufficientWeReadMaterial` and `requireAiFallbackConfig` do not exist.

- [ ] **Step 3: Add dependencies and sufficiency helpers**

In `WeReadSkillService.kt`, add imports:

```kotlin
import com.dailysatori.shared.db.Ai_config
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
```

Change the constructor to:

```kotlin
class WeReadSkillService(
    private val client: HttpClient,
    private val settingRepository: SettingRepository,
    private val secretCipher: SecretCipher,
    private val aiConfigService: AiConfigService,
    private val aiService: AiService,
) : BookIntelligenceSource {
```

Add helpers:

```kotlin
fun hasSufficientWeReadMaterial(
    info: WeReadBookInfo,
    chapters: List<WeReadChapter>,
    reviews: List<WeReadReview>,
    drafts: List<BookViewpointDraft>,
): Boolean {
    val materialLength = info.intro.length + chapters.sumOf { it.title.length } + reviews.sumOf { it.content.length }
    return info.title.isNotBlank() &&
        materialLength >= 40 &&
        drafts.size >= 10 &&
        drafts.all { it.content.length >= 80 && it.example.length >= 100 }
}

fun requireAiFallbackConfig(config: Ai_config?): Ai_config {
    val value = config ?: throw WeReadSkillException(
        WeReadSkillErrorType.RemoteFailure,
        "微信读书资料不足，请先配置默认 AI 模型后重试",
    )
    if (value.api_token.isBlank()) {
        throw WeReadSkillException(
            WeReadSkillErrorType.RemoteFailure,
            "微信读书资料不足，请先配置默认 AI 模型后重试",
        )
    }
    return value
}
```

Update `SharedModule.kt` registration:

```kotlin
single { WeReadSkillService(get(), get(), get(), get(), get()) }
```

- [ ] **Step 4: Implement fallback flow**

Replace `generateViewpoints` in `WeReadSkillService.kt` with:

```kotlin
override suspend fun generateViewpoints(book: BookSearchResult): BookViewpointGenerationResult {
    val bookId = book.sourceUrl.extractWeReadBookId()
        ?: searchBooks("${book.title} ${book.author}".trim(), limit = 1).firstOrNull()?.sourceUrl?.extractWeReadBookId()
        ?: throw WeReadSkillException(WeReadSkillErrorType.NoResults, "微信读书未找到相关书籍")
    val info = parseWeReadBookInfo(callGateway("/book/info", mapOf("bookId" to bookId))).withSearchFallback(book, bookId)
    val chapters = parseWeReadChapters(callGateway("/book/chapterinfo", mapOf("bookId" to bookId)))
    val reviews = parseWeReadReviews(
        callGateway("/review/list", mapOf("bookId" to bookId, "reviewListType" to 1, "count" to 10)),
    )
    val weReadDrafts = buildWeReadViewpointDrafts(info, chapters, reviews)
    if (hasSufficientWeReadMaterial(info, chapters, reviews, weReadDrafts)) {
        return BookViewpointGenerationResult(weReadDrafts, BookViewpointSource.WeRead)
    }
    return generateAiFallbackViewpoints(book, info, chapters, reviews)
}
```

Add private helpers:

```kotlin
private suspend fun generateAiFallbackViewpoints(
    book: BookSearchResult,
    info: WeReadBookInfo,
    chapters: List<WeReadChapter>,
    reviews: List<WeReadReview>,
): BookViewpointGenerationResult {
    val config = requireAiFallbackConfig(aiConfigService.getDefaultConfig())
    val response = try {
        aiService.complete(
            prompt = info.title.ifBlank { book.title },
            apiAddress = config.api_address,
            apiToken = config.api_token,
            modelName = config.model_name,
            provider = config.provider,
            systemPrompt = buildAiFallbackViewpointPrompt(book, info, chapters, reviews),
            temperature = 0.5,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw WeReadSkillException(WeReadSkillErrorType.RemoteFailure, "AI 观点生成失败，请稍后重试", error)
    }
    val drafts = parseAiFallbackViewpointJson(response)
    if (drafts.size < 10) {
        throw WeReadSkillException(WeReadSkillErrorType.RemoteFailure, "AI 观点生成失败，请稍后重试")
    }
    return BookViewpointGenerationResult(drafts, BookViewpointSource.AiFallback)
}

private fun WeReadBookInfo.withSearchFallback(book: BookSearchResult, bookId: String): WeReadBookInfo = copy(
    bookId = this.bookId.ifBlank { bookId },
    title = title.ifBlank { book.title },
    author = author.ifBlank { book.author },
    intro = intro.ifBlank { book.introduction },
    category = category.ifBlank { book.category },
)
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.book.WeReadSkillServiceTest"`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt shared/src/commonTest/kotlin/com/dailysatori/service/book/WeReadSkillServiceTest.kt
git commit -m "feat: fallback to AI for sparse WeRead books"
```

## Task 4: Show AI Fallback Disclosure In Book Add UI

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookSearchViewModel.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchUiTextTest.kt`

- [ ] **Step 1: Write failing UI text test**

Append to `BookSearchUiTextTest`:

```kotlin
@Test
fun appendsAiGeneratedDisclosureForFallbackViewpoints() {
    assertEquals("观点由 AI 生成，非微信读书内容", bookAiGeneratedDisclosure())
    assertEquals(
        "《待上架新书》已添加，10 个观点已生成（观点由 AI 生成，非微信读书内容）",
        bookAnalysisCompletionNotice("待上架新书", 10, com.dailysatori.service.book.BookViewpointSource.AiFallback),
    )
    assertEquals(
        "《三体》已添加，10 个观点已生成",
        bookAnalysisCompletionNotice("三体", 10, com.dailysatori.service.book.BookViewpointSource.WeRead),
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.BookSearchUiTextTest.appendsAiGeneratedDisclosureForFallbackViewpoints"`

Expected: FAIL because `bookAiGeneratedDisclosure` and the new overload/signature do not exist.

- [ ] **Step 3: Update ViewModel to use generation result source**

In `BookSearchViewModel.kt`, import:

```kotlin
import com.dailysatori.service.book.BookViewpointSource
```

Change the add flow from:

```kotlin
val viewpoints = bookIntelligenceService.generateViewpoints(result)
_state.update { it.copy(analysisStep = bookAnalysisGeneratingStep()) }
viewpoints.forEach { draft -> viewpointRepo.insert(bookId, draft.title, draft.content, draft.example) }
val message = bookAnalysisCompletionNotice(result.title, viewpoints.size)
```

to:

```kotlin
val generation = bookIntelligenceService.generateViewpoints(result)
_state.update { it.copy(analysisStep = bookAnalysisGeneratingStep()) }
generation.drafts.forEach { draft -> viewpointRepo.insert(bookId, draft.title, draft.content, draft.example) }
val message = bookAnalysisCompletionNotice(result.title, generation.drafts.size, generation.source)
```

Change the completion helpers to:

```kotlin
fun bookAiGeneratedDisclosure(): String = "观点由 AI 生成，非微信读书内容"

fun bookAnalysisCompletionNotice(
    title: String,
    count: Int,
    source: BookViewpointSource = BookViewpointSource.WeRead,
): String {
    val base = "《$title》已添加，$count 个观点已生成"
    return if (source == BookViewpointSource.AiFallback) "$base（${bookAiGeneratedDisclosure()}）" else base
}
```

- [ ] **Step 4: Run app UI text tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.BookSearchUiTextTest"`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/book/BookSearchViewModel.kt app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchUiTextTest.kt
git commit -m "feat: disclose AI book viewpoints"
```

## Task 5: Full Verification

**Files:**
- Verify only; no expected code edits unless failures reveal a defect.

- [ ] **Step 1: Run shared book tests**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.book.*"`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run app book/settings tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.*" --tests "com.dailysatori.ui.feature.settings.weread.*"`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run required compile check**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install and launch if a device is connected**

Run: `adb devices`

If a device is listed as `device`, run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: install command reports BUILD SUCCESSFUL and the start command prints `Starting: Intent { cmp=com.dailysatori/.MainActivity }`.

- [ ] **Step 5: Commit verification-only fixes if needed**

If verification required code changes, commit them:

```bash
git add <changed files>
git commit -m "fix: stabilize WeRead AI fallback"
```

If no files changed, do not create an empty commit.

## Self-Review

- Spec coverage: The plan keeps search WeRead-only, allows searchable pending books, adds AI fallback only for insufficient material, discloses AI-generated content, avoids schema changes, and documents backup behavior as unchanged.
- Placeholder scan: No TBD/TODO placeholders remain; each task has exact files, code snippets, commands, and expected results.
- Type consistency: `BookViewpointGenerationResult`, `BookViewpointSource.WeRead`, and `BookViewpointSource.AiFallback` are introduced before use in service and UI tasks.
