# WeRead Book Skill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the reading page's book search and core-viewpoint generation with the official WeRead Skill gateway, with only a WeRead API Key setting exposed to users.

**Architecture:** Add a focused `WeReadSkillService` in `shared` that calls the WeRead Agent API Gateway and maps responses into the existing `BookSearchResult` and `BookViewpointDraft` UI contracts. Keep `BookIntelligenceService` as the public facade for current ViewModels, but delegate only to `WeReadSkillService` with no legacy fallback. Add a small settings screen for storing `weread_api_key` in the existing `setting` table.

**Tech Stack:** Kotlin Multiplatform, Ktor `HttpClient`, kotlinx.serialization JSON, SQLDelight `setting` table, Koin DI, Jetpack Compose Material 3, kotlin.test.

---

## File Structure

- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt`
  - Owns WeRead gateway constants, request helpers, response parsers, error type, and deterministic viewpoint drafting from WeRead book info, chapters, and public reviews.
- Create: `shared/src/commonTest/kotlin/com/dailysatori/service/book/WeReadSkillServiceTest.kt`
  - Unit tests for response parsing, error detection, request body shape, missing-key behavior, and viewpoint drafting.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookIntelligenceService.kt`
  - Replace AI, web search, and generic MCP dependencies with `WeReadSkillService` delegation.
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/book/BookIntelligenceServiceTest.kt`
  - Remove or update tests asserting old AI/Douban fallback behavior; keep pure parser tests that are still used only if functions remain.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
  - Add `SettingKeys.weReadApiKey`.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
  - Register `WeReadSkillService`; update `BookIntelligenceService` construction.
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsViewModel.kt`
  - Loads, saves, and clears the WeRead API Key from `SettingRepository`.
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsScreen.kt`
  - Compose UI with one password-style API Key field, save/clear buttons, and status message.
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsTextTest.kt`
  - Tests user-facing labels and masked-key helper text.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`
  - Add `WE_READ` page and a settings row under “AI 与服务”.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookSearchViewModel.kt`
  - Map `WeReadSkillException` to required user-facing messages.

Do not modify `DailySatori.sq`; `setting` is key-value storage and `weread_api_key` needs no schema migration.

## Constants From Official WeRead Skill

- Gateway: `https://i.weread.qq.com/api/agent/gateway`
- Header: `Authorization: Bearer <api key>`
- Header: `Content-Type: application/json`
- Required body field: `skill_version: "1.0.3"`
- Search API: `api_name: "/store/search"`, `keyword`, `scope: 10`, `count`
- Book info API: `api_name: "/book/info"`, `bookId`
- Chapter API: `api_name: "/book/chapterinfo"`, `bookId`
- Review API: `api_name: "/review/list"`, `bookId`, `reviewListType: 1`, `count`

---

### Task 1: WeRead Parser And Viewpoint Unit Tests

**Files:**
- Create: `shared/src/commonTest/kotlin/com/dailysatori/service/book/WeReadSkillServiceTest.kt`

- [ ] **Step 1: Write parser and message tests**

Create `shared/src/commonTest/kotlin/com/dailysatori/service/book/WeReadSkillServiceTest.kt` with:

```kotlin
package com.dailysatori.service.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WeReadSkillServiceTest {
    @Test
    fun buildsGatewayBodyWithFlatParametersAndSkillVersion() {
        val body = buildWeReadGatewayBody(
            apiName = "/store/search",
            params = mapOf("keyword" to "三体", "scope" to 10, "count" to 5),
        ).toString()

        assertTrue(body.contains("\"api_name\":\"/store/search\""))
        assertTrue(body.contains("\"skill_version\":\"1.0.3\""))
        assertTrue(body.contains("\"keyword\":\"三体\""))
        assertTrue(body.contains("\"scope\":10"))
        assertTrue(body.contains("\"count\":5"))
        assertTrue(!body.contains("\"params\""))
    }

    @Test
    fun parsesSearchResultsFromWeReadV3Response() {
        val json = """
            {
              "errcode": 0,
              "results": [
                {
                  "title": "电子书",
                  "scope": 10,
                  "books": [
                    {
                      "readingCount": 153000,
                      "newRating": 92,
                      "newRatingCount": 12000,
                      "bookInfo": {
                        "bookId": "3300045871",
                        "title": "三体",
                        "author": "刘慈欣",
                        "cover": "https://res.weread.qq.com/cover.jpg",
                        "intro": "文化大革命如火如荼进行的同时，军方探寻外星文明的绝秘计划取得突破。",
                        "category": "科幻小说",
                        "isbn": "9787536692930"
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val results = parseWeReadSearchResults(json)

        assertEquals(1, results.size)
        assertEquals("三体", results.first().title)
        assertEquals("刘慈欣", results.first().author)
        assertEquals("科幻小说", results.first().category)
        assertEquals("9787536692930", results.first().isbn)
        assertEquals("https://res.weread.qq.com/cover.jpg", results.first().coverUrl)
        assertEquals("微信读书 9.2 分，153000 人在读，12000 人评分", results.first().sourceSummary)
        assertEquals("weread://reading?bId=3300045871", results.first().sourceUrl)
    }

    @Test
    fun throwsChineseErrorWhenGatewayReturnsErrcode() {
        val error = assertFailsWith<WeReadSkillException> {
            parseWeReadSearchResults("""{"errcode":1001,"errmsg":"invalid api key"}""")
        }

        assertEquals(WeReadSkillErrorType.RemoteFailure, error.type)
        assertEquals("invalid api key", error.message)
    }

    @Test
    fun parsesBookInfoChaptersAndReviewsIntoViewpoints() {
        val infoJson = """
            {
              "errcode": 0,
              "bookId": "3300045871",
              "title": "三体",
              "author": "刘慈欣",
              "intro": "人类文明第一次面对宇宙级不确定性，个体选择与集体命运被放到同一个坐标系里审视。",
              "category": "科幻小说",
              "newRating": 92,
              "newRatingCount": 12000
            }
        """.trimIndent()
        val chaptersJson = """
            {
              "errcode": 0,
              "chapters": [
                {"chapterUid": 1, "chapterIdx": 1, "title": "科学边界", "level": 1, "wordCount": 12000},
                {"chapterUid": 2, "chapterIdx": 2, "title": "三体问题", "level": 1, "wordCount": 18000},
                {"chapterUid": 3, "chapterIdx": 3, "title": "黑暗森林", "level": 1, "wordCount": 20000}
              ]
            }
        """.trimIndent()
        val reviewsJson = """
            {
              "errcode": 0,
              "reviews": [
                {"review":{"review":{"content":"这本书真正震撼人的地方，是把文明选择写成每个人都能感受到的压力。", "star": 100}}},
                {"review":{"review":{"content":"它让读者意识到技术乐观和生存恐惧会同时存在。", "star": 80}}}
              ]
            }
        """.trimIndent()

        val drafts = buildWeReadViewpointDrafts(
            info = parseWeReadBookInfo(infoJson),
            chapters = parseWeReadChapters(chaptersJson),
            reviews = parseWeReadReviews(reviewsJson),
        )

        assertEquals(10, drafts.size)
        assertTrue(drafts.first().title.contains("三体"))
        assertTrue(drafts.first().content.length >= 80)
        assertTrue(drafts.first().example.length >= 100)
        assertTrue(drafts.any { it.content.contains("科学边界") || it.content.contains("三体问题") })
        assertTrue(drafts.any { it.content.contains("文明选择") || it.example.contains("文明选择") })
    }

    @Test
    fun missingApiKeyUsesDedicatedErrorType() {
        val error = assertFailsWith<WeReadSkillException> { requireWeReadApiKey("   ") }

        assertEquals(WeReadSkillErrorType.MissingApiKey, error.type)
        assertEquals("请先在设置中配置微信读书 API Key", weReadUserMessage(error))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.service.book.WeReadSkillServiceTest"`

Expected: FAIL with unresolved references such as `buildWeReadGatewayBody`, `parseWeReadSearchResults`, and `WeReadSkillException`.

---

### Task 2: WeRead Skill Service Implementation

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`

- [ ] **Step 1: Add the setting key**

Modify `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt` inside `object SettingKeys`:

```kotlin
    const val weReadApiKey = "weread_api_key"
```

- [ ] **Step 2: Implement WeRead service and pure parsers**

Create `shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt` with:

```kotlin
package com.dailysatori.service.book

import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val WEREAD_GATEWAY_URL = "https://i.weread.qq.com/api/agent/gateway"
private const val WEREAD_SKILL_VERSION = "1.0.3"

private val weReadJson = Json { ignoreUnknownKeys = true; isLenient = true }

enum class WeReadSkillErrorType { MissingApiKey, NoResults, RemoteFailure }

class WeReadSkillException(
    val type: WeReadSkillErrorType,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

data class WeReadBookInfo(
    val bookId: String,
    val title: String,
    val author: String,
    val intro: String,
    val category: String,
    val cover: String,
    val isbn: String,
    val rating: Int,
    val ratingCount: Int,
)

data class WeReadChapter(val title: String, val level: Int, val wordCount: Int)

data class WeReadReview(val content: String, val star: Int)

class WeReadSkillService(
    private val client: HttpClient,
    private val settingRepository: SettingRepository,
) {
    suspend fun searchBooks(query: String, limit: Int = 10): List<BookSearchResult> {
        val response = callGateway(
            apiName = "/store/search",
            params = mapOf("keyword" to query, "scope" to 10, "count" to limit),
        )
        val results = parseWeReadSearchResults(response)
        if (results.isEmpty()) throw WeReadSkillException(WeReadSkillErrorType.NoResults, "微信读书未找到相关书籍")
        return results
    }

    suspend fun generateViewpoints(book: BookSearchResult): List<BookViewpointDraft> {
        val bookId = book.sourceUrl.substringAfter("bId=", "").takeIf { it.isNotBlank() }
            ?: searchBooks(listOf(book.title, book.author).filter { it.isNotBlank() }.joinToString(" "), 1).firstOrNull()
                ?.sourceUrl?.substringAfter("bId=", "")
            ?: throw WeReadSkillException(WeReadSkillErrorType.NoResults, "微信读书未找到相关书籍")
        val info = parseWeReadBookInfo(callGateway("/book/info", mapOf("bookId" to bookId)))
        val chapters = parseWeReadChapters(callGateway("/book/chapterinfo", mapOf("bookId" to bookId)))
        val reviews = parseWeReadReviews(
            callGateway("/review/list", mapOf("bookId" to bookId, "reviewListType" to 1, "count" to 10)),
        )
        return buildWeReadViewpointDrafts(info, chapters, reviews)
    }

    private suspend fun callGateway(apiName: String, params: Map<String, Any>): String {
        val apiKey = requireWeReadApiKey(settingRepository.get(SettingKeys.weReadApiKey))
        return try {
            client.post(WEREAD_GATEWAY_URL) {
                bearerAuth(apiKey)
                contentType(ContentType.Application.Json)
                setBody(buildWeReadGatewayBody(apiName, params).toString())
            }.bodyAsText()
        } catch (error: CancellationException) {
            throw error
        } catch (error: WeReadSkillException) {
            throw error
        } catch (error: Exception) {
            throw WeReadSkillException(WeReadSkillErrorType.RemoteFailure, "微信读书服务调用失败，请稍后重试", error)
        }
    }
}

fun requireWeReadApiKey(value: String?): String {
    val key = value.orEmpty().trim()
    if (key.isBlank()) throw WeReadSkillException(WeReadSkillErrorType.MissingApiKey, "请先在设置中配置微信读书 API Key")
    return key
}

fun buildWeReadGatewayBody(apiName: String, params: Map<String, Any>): JsonObject = buildJsonObject {
    put("api_name", JsonPrimitive(apiName))
    put("skill_version", JsonPrimitive(WEREAD_SKILL_VERSION))
    params.forEach { (key, value) ->
        when (value) {
            is Int -> put(key, JsonPrimitive(value))
            is Long -> put(key, JsonPrimitive(value))
            is Boolean -> put(key, JsonPrimitive(value))
            else -> put(key, JsonPrimitive(value.toString()))
        }
    }
}

fun parseWeReadSearchResults(jsonText: String): List<BookSearchResult> {
    val root = parseWeReadRoot(jsonText)
    val groups = root["results"]?.jsonArray ?: return emptyList()
    return groups.flatMap { group ->
        group.jsonObject["books"]?.jsonArray.orEmpty().mapNotNull { item ->
            val obj = item.jsonObject
            val info = obj["bookInfo"]?.jsonObject ?: return@mapNotNull null
            val bookId = info.stringValue("bookId")
            val title = info.stringValue("title")
            if (bookId.isBlank() || title.isBlank()) return@mapNotNull null
            BookSearchResult(
                title = title,
                author = info.stringValue("author"),
                category = info.stringValue("category"),
                introduction = info.stringValue("intro"),
                isbn = info.stringValue("isbn"),
                coverUrl = info.stringValue("cover"),
                sourceSummary = formatWeReadSourceSummary(
                    rating = obj.intValue("newRating"),
                    readingCount = obj.intValue("readingCount"),
                    ratingCount = obj.intValue("newRatingCount"),
                ),
                sourceUrl = "weread://reading?bId=$bookId",
            )
        }
    }.distinctBy { it.sourceUrl }
}

fun parseWeReadBookInfo(jsonText: String): WeReadBookInfo {
    val root = parseWeReadRoot(jsonText)
    return WeReadBookInfo(
        bookId = root.stringValue("bookId"),
        title = root.stringValue("title"),
        author = root.stringValue("author"),
        intro = root.stringValue("intro"),
        category = root.stringValue("category"),
        cover = root.stringValue("cover"),
        isbn = root.stringValue("isbn"),
        rating = root.intValue("newRating"),
        ratingCount = root.intValue("newRatingCount"),
    )
}

fun parseWeReadChapters(jsonText: String): List<WeReadChapter> {
    val root = parseWeReadRoot(jsonText)
    return root["chapters"]?.jsonArray.orEmpty().mapNotNull { item ->
        val obj = item.jsonObject
        val title = obj.stringValue("title")
        if (title.isBlank()) null else WeReadChapter(title, obj.intValue("level"), obj.intValue("wordCount"))
    }
}

fun parseWeReadReviews(jsonText: String): List<WeReadReview> {
    val root = parseWeReadRoot(jsonText)
    return root["reviews"]?.jsonArray.orEmpty().mapNotNull { item ->
        val review = item.jsonObject["review"]?.jsonObject?.get("review")?.jsonObject ?: return@mapNotNull null
        val content = review.stringValue("content").stripHtml().trim()
        if (content.isBlank()) null else WeReadReview(content, review.intValue("star"))
    }
}

fun buildWeReadViewpointDrafts(
    info: WeReadBookInfo,
    chapters: List<WeReadChapter>,
    reviews: List<WeReadReview>,
): List<BookViewpointDraft> {
    val title = info.title.ifBlank { "这本书" }
    val intro = info.intro.ifBlank { "微信读书资料显示，这本书需要结合目录、读者点评和阅读场景来理解。" }
    val chapterText = chapters.take(6).joinToString("、") { it.title }.ifBlank { "核心章节" }
    val reviewText = reviews.firstOrNull()?.content ?: "读者反馈提醒我们把书中的概念转化为具体判断。"
    val seeds = listOf(
        "先用目录建立问题地图，再进入细节阅读。" to "目录中的 $chapterText 提供了理解《${title}》的主线。读者不应该把章节当成孤立材料，而要先判断每章在回答什么问题、解决什么冲突、推进什么结论。这样阅读时更容易区分作者的核心论证和辅助案例，也能避免只记住零散句子却说不清整本书的结构。",
        "把简介里的主题转化为自己的判断标准。" to "$intro 这类主题如果只停留在概念层面，很容易变成模糊感受。更有效的做法是把它转成可观察的问题：作者认为关键矛盾是什么，哪些行为会带来改变，哪些结果说明理解有效。判断标准清楚后，读者才能把书中的观点用于真实选择。",
        "读者点评能暴露一本书真正触发行动的地方。" to "$reviewText 点评的价值不只是评价好坏，而是提示哪些内容真正影响了读者的判断。阅读时可以把这些高频触发点标记出来，再回到原章节验证作者如何铺垫、证明和收束这个观点，从而形成比摘要更可靠的理解。",
        "核心观点要落到一个可执行的下一步。" to "读完《${title}》后，如果只留下赞同或震撼，知识很快会消散。读者应该选一个具体场景做实验：一次沟通、一个项目决策、一段学习安排或一次复盘，把书中的判断压缩成可以当天执行的动作。只有行动改变了，观点才真正进入自己的知识系统。",
        "评分和热度只能作为入口，不能替代独立判断。" to "微信读书评分和点评能帮助读者快速识别关注点，但它们不能替你完成理解。高分说明许多人觉得有价值，却不说明这本书适合当前问题。阅读时仍要回到自己的处境：我正在解决什么问题，哪些章节直接相关，哪些精彩内容暂时不必投入过多注意力。",
        "章节标题之间的关系比单章金句更重要。" to "《${title}》的目录提供了从问题到解释再到结论的路径。读者如果只摘抄单章金句，容易忽略作者如何连接前后概念。更好的方式是每读完一章写一句关系说明：这一章如何承接上一章，又为下一章准备了什么判断。这样才能读出整本书的推理骨架。",
        "公开点评要和原书内容交叉验证。" to "看到读者评价后，不要立刻接受其中的结论。可以把点评中的关键词带回目录和简介里检查：它是否来自作者的核心论证，还是读者自己的延伸理解。经过交叉验证的观点更适合进入笔记，因为它同时有文本依据和真实读者的使用反馈。",
        "一本书的价值取决于它改变了哪个具体问题。" to "《${title}》不是为了增加谈资而存在。读者需要在阅读前后各写一次问题定义：阅读前我以为问题是什么，阅读后我如何重新描述它。如果问题定义变清楚，即使没有立刻得到答案，这本书也已经改变了你的判断质量。",
        "阅读进度不等于理解进度。" to "即使已经读完大量章节，也可能只是完成了浏览。更可靠的理解进度，是能否用自己的话复述作者的主张、指出一个适用边界、举出一个反例或行动场景。用这种方式检查，《${title}》的阅读才不会只留下完成感。",
        "把书评、目录和简介合并成一张行动卡。" to "处理《${title}》时，可以把微信读书资料压缩为三行：简介说明主题，目录说明结构，点评说明触发点。最后再补一行自己的行动：我下一次会在哪个场景使用这个判断。这样的卡片足够短，可以复习，也足够具体，可以改变行为。",
    )
    return seeds.map { (viewpoint, content) ->
        BookViewpointDraft(
            title = "$title：$viewpoint",
            content = content,
            example = "例如一名读者准备把《${title}》用于下周的工作复盘。他没有只摘抄喜欢的句子，而是先看目录找出最相关的章节，再阅读微信读书里的推荐点评，确认其他读者被触动的原因。随后他把观点写成一个具体动作：复盘时先说明问题边界，再列出判断标准，最后选择一个下周能验证的小实验。会议结束后，团队不仅记住了书里的概念，也知道下一步该怎么做。",
        )
    }
}

fun weReadUserMessage(error: WeReadSkillException): String = when (error.type) {
    WeReadSkillErrorType.MissingApiKey -> "请先在设置中配置微信读书 API Key"
    WeReadSkillErrorType.NoResults -> "微信读书未找到相关书籍"
    WeReadSkillErrorType.RemoteFailure -> "微信读书服务调用失败，请稍后重试"
}

private fun parseWeReadRoot(jsonText: String): JsonObject {
    val root = runCatching { weReadJson.parseToJsonElement(jsonText).jsonObject }.getOrNull()
        ?: throw WeReadSkillException(WeReadSkillErrorType.RemoteFailure, "微信读书服务调用失败，请稍后重试")
    val errcode = root.intValue("errcode")
    if (errcode != 0) {
        throw WeReadSkillException(WeReadSkillErrorType.RemoteFailure, root.stringValue("errmsg").ifBlank { "微信读书服务调用失败，请稍后重试" })
    }
    return root
}

private fun formatWeReadSourceSummary(rating: Int, readingCount: Int, ratingCount: Int): String {
    val ratingText = if (rating > 0) "${rating / 10}.${rating % 10} 分" else "暂无评分"
    return "微信读书 $ratingText，$readingCount 人在读，$ratingCount 人评分"
}

private fun String.stripHtml(): String = replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")

private fun JsonObject.stringValue(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: ""

private fun JsonObject.intValue(key: String): Int = this[key]?.jsonPrimitive?.intOrNull ?: 0

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
```

- [ ] **Step 3: Run WeRead tests**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.service.book.WeReadSkillServiceTest"`

Expected: PASS.

---

### Task 3: Replace BookIntelligenceService Delegation

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookIntelligenceService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/book/BookIntelligenceServiceTest.kt`

- [ ] **Step 1: Add a facade test that documents no legacy fallback**

Append this test to `shared/src/commonTest/kotlin/com/dailysatori/service/book/BookIntelligenceServiceTest.kt`:

```kotlin
    @Test
    fun bookIntelligenceSourceCodeDelegatesToWeReadOnly() {
        val source = java.io.File("src/commonMain/kotlin/com/dailysatori/service/book/BookIntelligenceService.kt").readText()

        assertTrue(source.contains("private val weReadSkillService: WeReadSkillService"))
        assertTrue(source.contains("weReadSkillService.searchBooks(query)"))
        assertTrue(source.contains("weReadSkillService.generateViewpoints(book)"))
        assertFalse(source.contains("fallbackBookViewpoints(book)"))
        assertFalse(source.contains("bookSearchService.search"))
        assertFalse(source.contains("completeWithDefaultAi"))
        assertFalse(source.contains("collectSourceNotes"))
    }
```

- [ ] **Step 2: Run the facade test to verify it fails**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.service.book.BookIntelligenceServiceTest.bookIntelligenceSourceCodeDelegatesToWeReadOnly"`

Expected: FAIL because `BookIntelligenceService` still contains legacy dependencies and fallback code.

- [ ] **Step 3: Replace the service class implementation**

In `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookIntelligenceService.kt`, keep the top-level data classes and parser helpers that are still referenced by tests only if needed, but replace the `BookIntelligenceService` class body with:

```kotlin
class BookIntelligenceService(
    private val weReadSkillService: WeReadSkillService,
) {
    suspend fun searchBooks(query: String): List<BookSearchResult> =
        weReadSkillService.searchBooks(query)

    suspend fun generateViewpoints(book: BookSearchResult): List<BookViewpointDraft> =
        weReadSkillService.generateViewpoints(book)
}
```

Remove unused imports from `BookIntelligenceService.kt`, including `McpServerRepository`, `Ai_config`, `AiConfigService`, `AiService`, `RemoteMcpClient`, and `CancellationException` if no remaining code uses them.

- [ ] **Step 4: Update DI**

Modify `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt` imports:

```kotlin
import com.dailysatori.service.book.WeReadSkillService
```

Add registration near book services:

```kotlin
    single { WeReadSkillService(get(), get()) }
```

Change the BookIntelligenceService registration from:

```kotlin
    single { BookIntelligenceService(get(), get(), get(), get(), get()) }
```

to:

```kotlin
    single { BookIntelligenceService(get()) }
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.service.book.BookIntelligenceServiceTest"`

Expected: PASS after removing or updating any old tests that assert fallback behavior. Keep tests for pure functions only if those functions remain in the file.

---

### Task 4: Book Search Error Messages

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BookSearchViewModel.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchUiTextTest.kt`

- [ ] **Step 1: Add UI text tests**

Append to `BookSearchUiTextTest`:

```kotlin
    @Test
    fun mapsWeReadErrorsToRequiredMessages() {
        assertEquals(
            "请先在设置中配置微信读书 API Key",
            bookSearchFailureMessage(com.dailysatori.service.book.WeReadSkillException(
                com.dailysatori.service.book.WeReadSkillErrorType.MissingApiKey,
                "missing",
            )),
        )
        assertEquals(
            "微信读书未找到相关书籍",
            bookSearchFailureMessage(com.dailysatori.service.book.WeReadSkillException(
                com.dailysatori.service.book.WeReadSkillErrorType.NoResults,
                "none",
            )),
        )
        assertEquals(
            "微信读书服务调用失败，请稍后重试",
            bookSearchFailureMessage(com.dailysatori.service.book.WeReadSkillException(
                com.dailysatori.service.book.WeReadSkillErrorType.RemoteFailure,
                "remote",
            )),
        )
    }
```

- [ ] **Step 2: Run UI text test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.BookSearchUiTextTest.mapsWeReadErrorsToRequiredMessages"`

Expected: FAIL until `bookSearchFailureMessage` handles `WeReadSkillException`.

- [ ] **Step 3: Implement message mapping**

Add import in `BookSearchViewModel.kt`:

```kotlin
import com.dailysatori.service.book.WeReadSkillException
import com.dailysatori.service.book.weReadUserMessage
```

Replace `bookSearchFailureMessage` with:

```kotlin
fun bookSearchFailureMessage(error: Throwable): String = when (error) {
    is kotlinx.coroutines.TimeoutCancellationException -> bookSearchTimeoutMessage()
    is WeReadSkillException -> weReadUserMessage(error)
    else -> error.message ?: "搜索失败"
}
```

- [ ] **Step 4: Run UI text tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.BookSearchUiTextTest"`

Expected: PASS.

---

### Task 5: WeRead Settings Screen Tests And ViewModel

**Files:**
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsTextTest.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsViewModel.kt`

- [ ] **Step 1: Write text helper tests**

Create `app/src/test/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsTextTest.kt` with:

```kotlin
package com.dailysatori.ui.feature.settings.weread

import kotlin.test.Test
import kotlin.test.assertEquals

class WeReadSettingsTextTest {
    @Test
    fun masksSavedApiKeyInSubtitle() {
        assertEquals("未配置", weReadApiKeyStatus(""))
        assertEquals("已配置：wrk-****cdef", weReadApiKeyStatus("wrk-12345678abcdef"))
    }

    @Test
    fun exposesRequiredLabels() {
        assertEquals("微信读书", weReadSettingsTitle())
        assertEquals("保存", weReadSaveButtonText(false))
        assertEquals("保存中...", weReadSaveButtonText(true))
        assertEquals("清空", weReadClearButtonText())
        assertEquals("微信读书 API Key 已保存", weReadSavedMessage())
        assertEquals("微信读书 API Key 已清空", weReadClearedMessage())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.settings.weread.WeReadSettingsTextTest"`

Expected: FAIL with unresolved references.

- [ ] **Step 3: Implement ViewModel and helper text**

Create `app/src/main/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsViewModel.kt` with:

```kotlin
package com.dailysatori.ui.feature.settings.weread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeReadSettingsState(
    val apiKey: String = "",
    val savedApiKey: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
)

class WeReadSettingsViewModel(
    private val settingRepository: SettingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(WeReadSettingsState())
    val state: StateFlow<WeReadSettingsState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val key = settingRepository.get(SettingKeys.weReadApiKey).orEmpty()
            _state.update { it.copy(apiKey = key, savedApiKey = key, message = null) }
        }
    }

    fun updateApiKey(value: String) {
        _state.update { it.copy(apiKey = value, message = null) }
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, message = null) }
            val key = _state.value.apiKey.trim()
            settingRepository.upsert(SettingKeys.weReadApiKey, key)
            _state.update { it.copy(apiKey = key, savedApiKey = key, isSaving = false, message = weReadSavedMessage()) }
        }
    }

    fun clear() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, message = null) }
            settingRepository.delete(SettingKeys.weReadApiKey)
            _state.update { it.copy(apiKey = "", savedApiKey = "", isSaving = false, message = weReadClearedMessage()) }
        }
    }
}

fun weReadSettingsTitle(): String = "微信读书"

fun weReadApiKeyStatus(apiKey: String): String {
    val trimmed = apiKey.trim()
    if (trimmed.isBlank()) return "未配置"
    val suffix = trimmed.takeLast(4)
    return "已配置：wrk-****$suffix"
}

fun weReadSaveButtonText(isSaving: Boolean): String = if (isSaving) "保存中..." else "保存"

fun weReadClearButtonText(): String = "清空"

fun weReadSavedMessage(): String = "微信读书 API Key 已保存"

fun weReadClearedMessage(): String = "微信读书 API Key 已清空"
```

- [ ] **Step 4: Register ViewModel in existing Koin ViewModel module**

Find the app ViewModel Koin module by searching for `viewModel {` in `app/src/main/kotlin` and add:

```kotlin
viewModel { WeReadSettingsViewModel(get()) }
```

Add import:

```kotlin
import com.dailysatori.ui.feature.settings.weread.WeReadSettingsViewModel
```

- [ ] **Step 5: Run settings tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.settings.weread.WeReadSettingsTextTest"`

Expected: PASS.

---

### Task 6: WeRead Settings Compose UI And Navigation

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`

- [ ] **Step 1: Create settings screen**

Create `app/src/main/kotlin/com/dailysatori/ui/feature/settings/weread/WeReadSettingsScreen.kt` with:

```kotlin
package com.dailysatori.ui.feature.settings.weread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun WeReadSettingsScreen(onBack: () -> Unit) {
    val viewModel: WeReadSettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    AppScaffold(title = weReadSettingsTitle(), onBack = onBack) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                text = "配置微信读书 API Key 后，读书页会使用微信读书 Skill 搜书并提炼核心观点。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::updateApiKey,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                supportingText = { Text(weReadApiKeyStatus(state.savedApiKey)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Button(onClick = viewModel::save, enabled = !state.isSaving) {
                    Text(weReadSaveButtonText(state.isSaving))
                }
                OutlinedButton(onClick = viewModel::clear, enabled = !state.isSaving) {
                    Text(weReadClearButtonText())
                }
            }
            state.message?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
```

- [ ] **Step 2: Add navigation page**

Modify `SettingsScreen.kt` imports:

```kotlin
import androidx.compose.material.icons.filled.MenuBook
import com.dailysatori.ui.feature.settings.weread.WeReadSettingsScreen
```

Add enum value to `SettingsPage`:

```kotlin
    WE_READ,
```

Add branch in `SettingsScreen` page routing:

```kotlin
        SettingsPage.WE_READ -> WeReadSettingsScreen(onBack = { currentPage = SettingsPage.MAIN })
```

Add row in `AiServicesSection` after AI 配置:

```kotlin
        SettingsRow(Icons.Default.MenuBook, "微信读书", "配置微信读书 API Key", onClick = { onNavigate(SettingsPage.WE_READ) })
```

- [ ] **Step 3: Run app unit tests for settings text**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.settings.weread.WeReadSettingsTextTest"`

Expected: PASS.

---

### Task 7: Remove Legacy Book Source Usage From Runtime Path

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookSearchService.kt` only if unused imports or dead registration cause warnings.
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BookSearchUiTextTest.kt`

- [ ] **Step 1: Update stale UI test expectation for Douban action if needed**

If `BookSearchUiTextTest.buildsDoubanSearchUrlFromBookResult` or `bookResultDoubanActionDescription()` no longer matches UI after replacing source links with WeRead deep links, change the test to:

```kotlin
    @Test
    fun buildsSourceUrlFromWeReadBookResult() {
        val result = com.dailysatori.service.book.BookSearchResult(
            title = "三体",
            author = "刘慈欣",
            sourceUrl = "weread://reading?bId=3300045871",
        )

        assertEquals("weread://reading?bId=3300045871", doubanBookSearchUrl(result))
    }
```

Keep the helper name `doubanBookSearchUrl` for now to avoid a larger UI rename unless the compiler requires cleanup.

- [ ] **Step 2: Keep legacy services registered only if other features use them**

Do not delete `BookSearchService`, `DoubanSuggestSearchEngine`, or `WebSearchEngine` in this task. They can remain registered if compilation or other features still use them. The requirement is that `BookIntelligenceService` no longer calls them.

- [ ] **Step 3: Run targeted book tests**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.BookSearchUiTextTest"`

Expected: PASS.

---

### Task 8: Full Verification

**Files:**
- No code files unless verification exposes compile errors.

- [ ] **Step 1: Run shared tests touched by the change**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.service.book.*"`

Expected: PASS.

- [ ] **Step 2: Run app unit tests touched by the change**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.book.*" --tests "com.dailysatori.ui.feature.settings.weread.*"`

Expected: PASS.

- [ ] **Step 3: Run required compile check**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install to connected device**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: BUILD SUCCESSFUL and APK installed.

- [ ] **Step 5: Launch app**

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: App launches. Manually verify Settings -> 微信读书 is visible, API Key can be entered, and the read page shows a clear missing-key error if the key is empty.

---

## Self-Review

- Spec coverage: The plan adds API Key settings, implements a first-class WeRead service, delegates book search/viewpoints only to WeRead, removes runtime fallback, preserves local reading features, and verifies compile/install.
- Placeholder scan: The plan contains concrete code snippets and commands for each code-changing step, plus exact verification expectations.
- Type consistency: `WeReadSkillService`, `WeReadSkillException`, `WeReadSkillErrorType`, `weReadUserMessage`, `BookSearchResult`, and `BookViewpointDraft` signatures are consistent across tasks.
- Commit policy: This plan intentionally omits commit steps because the workspace instruction says commits are only allowed when explicitly requested.
