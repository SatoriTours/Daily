package com.dailysatori.service.book

import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.service.security.SecretCipherPrefix
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val WE_READ_GATEWAY = "https://i.weread.qq.com/api/agent/gateway"
private const val WE_READ_SKILL_VERSION = "1.0.3"

private val weReadJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

enum class WeReadSkillErrorType { MissingApiKey, NoResults, RemoteFailure }

class WeReadSkillException(
    val type: WeReadSkillErrorType,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

data class WeReadBookInfo(
    val bookId: String,
    val title: String,
    val author: String = "",
    val intro: String = "",
    val category: String = "",
    val rating: Int = 0,
    val ratingCount: Int = 0,
)

data class WeReadChapter(
    val chapterUid: Int,
    val chapterIdx: Int,
    val title: String,
    val level: Int = 0,
    val wordCount: Int = 0,
)

data class WeReadReview(
    val content: String,
    val star: Int = 0,
)

class WeReadSkillService(
    private val client: HttpClient,
    private val settingRepository: SettingRepository,
    private val secretCipher: SecretCipher,
) : BookIntelligenceSource {
    override suspend fun searchBooks(query: String): List<BookSearchResult> = searchBooks(query, limit = 10)

    suspend fun searchBooks(query: String, limit: Int): List<BookSearchResult> {
        val response = callGateway(
            apiName = "/store/search",
            params = mapOf("keyword" to query, "scope" to 10, "count" to limit),
        )
        return parseWeReadSearchResults(response).take(limit).ifEmpty {
            throw WeReadSkillException(WeReadSkillErrorType.NoResults, "微信读书未找到相关书籍")
        }
    }

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

    private suspend fun callGateway(apiName: String, params: Map<String, Any>): String {
        val apiKey = requireWeReadApiKey(readStoredWeReadApiKey())
        return try {
            client.post(WE_READ_GATEWAY) {
                contentType(ContentType.Application.Json)
                bearerAuth(apiKey)
                setBody(buildWeReadGatewayBody(apiName, params).toString())
            }.bodyAsText()
        } catch (error: CancellationException) {
            throw error
        } catch (error: WeReadSkillException) {
            throw error
        } catch (error: Exception) {
            throw WeReadSkillException(WeReadSkillErrorType.RemoteFailure, "微信读书服务暂时不可用", error)
        }
    }

    private fun readStoredWeReadApiKey(): String {
        val stored = settingRepository.get(SettingKeys.weReadApiKey).orEmpty()
        return resolveStoredWeReadApiKey(
            stored = stored,
            isEncrypted = secretCipher::isEncrypted,
            decrypt = secretCipher::decrypt,
            onPlaintext = { key -> settingRepository.upsert(SettingKeys.weReadApiKey, secretCipher.encrypt(key)) },
        )
    }
}

fun requireWeReadApiKey(value: String?): String = value?.trim()?.takeIf { it.isNotBlank() }
    ?: throw WeReadSkillException(WeReadSkillErrorType.MissingApiKey, "请先在设置中配置微信读书 API Key")

fun resolveStoredWeReadApiKey(
    stored: String,
    isEncrypted: (String) -> Boolean,
    decrypt: (String) -> String,
    onPlaintext: (String) -> Unit = {},
): String {
    val trimmed = stored.trim()
    if (trimmed.isBlank()) return ""
    if (isEncrypted(trimmed)) {
        val decrypted = decrypt(trimmed).trim()
        return if (decrypted == trimmed && trimmed.startsWith(SecretCipherPrefix)) "" else decrypted
    }
    onPlaintext(trimmed)
    return trimmed
}

fun buildWeReadGatewayBody(apiName: String, params: Map<String, Any>): JsonObject = buildJsonObject {
    put("api_name", JsonPrimitive(apiName))
    put("skill_version", JsonPrimitive(WE_READ_SKILL_VERSION))
    params.forEach { (key, value) -> put(key, value.toJsonPrimitive()) }
}

fun parseWeReadSearchResults(jsonText: String): List<BookSearchResult> {
    val root = parseWeReadRoot(jsonText)
    val results = root.arrayValue("results") ?: JsonArray(emptyList())
    return results.flatMap { section ->
        val sectionObj = section.asJsonObjectOrNull() ?: return@flatMap emptyList()
        val books = sectionObj.arrayValue("books") ?: JsonArray(listOf(section))
        books.mapNotNull { item -> parseWeReadSearchItem(item.asJsonObjectOrNull() ?: return@mapNotNull null) }
    }
}

fun parseWeReadBookInfo(jsonText: String): WeReadBookInfo {
    val root = parseWeReadRoot(jsonText)
    val info = root.objectValue("bookInfo") ?: root.objectValue("book") ?: root
    return WeReadBookInfo(
        bookId = info.stringValue("bookId"),
        title = info.stringValue("title"),
        author = info.stringValue("author"),
        intro = info.stringValue("intro"),
        category = info.stringValue("category"),
        rating = info.intValue("newRating"),
        ratingCount = info.intValue("newRatingCount"),
    )
}

fun parseWeReadChapters(jsonText: String): List<WeReadChapter> {
    val root = parseWeReadRoot(jsonText)
    val chapters = root.arrayValue("chapters") ?: root.arrayValue("updated") ?: JsonArray(emptyList())
    return chapters.mapNotNull { item ->
        val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
        val title = obj.stringValue("title").trim()
        if (title.isBlank()) return@mapNotNull null
        WeReadChapter(
            chapterUid = obj.intValue("chapterUid"),
            chapterIdx = obj.intValue("chapterIdx"),
            title = title,
            level = obj.intValue("level"),
            wordCount = obj.intValue("wordCount"),
        )
    }
}

fun parseWeReadReviews(jsonText: String): List<WeReadReview> {
    val root = parseWeReadRoot(jsonText)
    val reviews = root.arrayValue("reviews") ?: root.arrayValue("synckeys") ?: JsonArray(emptyList())
    return reviews.mapNotNull { item ->
        val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
        val review = obj.objectValue("review")?.objectValue("review")
            ?: obj.objectValue("review")
            ?: obj
        val content = review.stringValue("content").trim()
        if (content.isBlank()) return@mapNotNull null
        WeReadReview(content = content, star = review.intValue("star"))
    }
}

fun buildWeReadViewpointDrafts(
    info: WeReadBookInfo,
    chapters: List<WeReadChapter>,
    reviews: List<WeReadReview>,
): List<BookViewpointDraft> {
    val title = info.title.ifBlank { "这本书" }
    val author = info.author.ifBlank { "作者" }
    val intro = info.intro.ifBlank { "它提供了一套观察个人选择、组织行动与长期结果之间关系的框架。" }
    val chapterText = chapters.take(3).joinToString("、") { it.title }.ifBlank { "核心章节" }
    val reviewText = reviews.firstOrNull()?.content ?: "读者反馈强调它能把抽象问题转化为可感知的压力与选择。"
    val themes = listOf(
        "先识别系统压力，再评价个人选择",
        "把不确定性拆成可讨论的具体约束",
        "章节线索能帮助读者建立递进理解",
        "读者共鸣来自把宏大问题落到日常判断",
        "好观点需要同时解释风险和行动边界",
        "评价一本书要看它是否改变问题意识",
        "从作者结构里寻找反复出现的因果链",
        "把书评当作理解偏差的补充样本",
        "从关键章节提炼可迁移的判断标准",
        "读完之后要形成能复用的行动语言",
    )
    return themes.mapIndexed { index, theme ->
        BookViewpointDraft(
            title = "$title：$theme。",
            content = buildWeReadContent(title, author, intro, chapterText, reviewText, theme, index),
            example = buildWeReadExample(title, chapterText, reviewText, theme, index),
        )
    }
}

fun weReadUserMessage(error: WeReadSkillException): String = when (error.type) {
    WeReadSkillErrorType.MissingApiKey -> "请先在设置中配置微信读书 API Key"
    WeReadSkillErrorType.NoResults -> "微信读书未找到相关书籍"
    WeReadSkillErrorType.RemoteFailure -> error.message.ifBlank { "微信读书服务暂时不可用" }
}

private fun parseWeReadRoot(jsonText: String): JsonObject {
    val root = runCatching { weReadJson.parseToJsonElement(jsonText).jsonObject }.getOrElse { error ->
        throw WeReadSkillException(WeReadSkillErrorType.RemoteFailure, "微信读书返回数据格式异常", error)
    }
    val errcode = root["errcode"]?.jsonPrimitive?.intOrNull ?: 0
    if (errcode != 0) {
        throw WeReadSkillException(WeReadSkillErrorType.RemoteFailure, root.stringValue("errmsg").ifBlank { "微信读书服务返回错误" })
    }
    return root.objectValue("data") ?: root
}

private fun parseWeReadSearchItem(item: JsonObject): BookSearchResult? {
    val info = item.objectValue("bookInfo") ?: item.objectValue("book") ?: item
    val bookId = info.stringValue("bookId").trim()
    val title = info.stringValue("title").trim()
    if (bookId.isBlank() || title.isBlank()) return null
    return BookSearchResult(
        title = title,
        author = info.stringValue("author"),
        category = info.stringValue("category"),
        introduction = info.stringValue("intro"),
        isbn = info.stringValue("isbn"),
        coverUrl = info.stringValue("cover"),
        sourceSummary = buildWeReadSourceSummary(
            rating = item.intValue("newRating").takeIf { it > 0 } ?: info.intValue("newRating"),
            readingCount = item.intValue("readingCount").takeIf { it > 0 } ?: info.intValue("readingCount"),
            ratingCount = item.intValue("newRatingCount").takeIf { it > 0 } ?: info.intValue("newRatingCount"),
        ),
        sourceUrl = "weread://reading?bId=$bookId",
    )
}

private fun buildWeReadSourceSummary(rating: Int, readingCount: Int, ratingCount: Int): String {
    val ratingText = if (rating > 0) "${rating / 10}.${rating % 10} 分" else "暂无评分"
    return "微信读书 $ratingText，$readingCount 人在读，$ratingCount 人评分"
}

private fun buildWeReadContent(
    title: String,
    author: String,
    intro: String,
    chapterText: String,
    reviewText: String,
    theme: String,
    index: Int,
): String = "$theme，是阅读《${title}》时最值得沉淀的第${index + 1}个判断。$author 在书中呈现的核心背景是：$intro 结合 $chapterText 等章节，可以看到问题并不是单点技巧，而是环境、选择和后果连续作用。读者提到“${reviewText.take(36)}”，这说明观点必须回到具体压力中验证，才能真正转化为稳定的理解。"

private fun buildWeReadExample(
    title: String,
    chapterText: String,
    reviewText: String,
    theme: String,
    index: Int,
): String = "例如一位读者读完《${title}》后，没有只记录金句，而是把第${index + 1}个观点“$theme”写进自己的工作复盘。他先对照 $chapterText 梳理事件顺序，再把书评里提到的“${reviewText.take(36)}”当作提醒，重新描述团队遇到的真实约束、误判来源和下一步动作。这样做之后，讨论不再停留在感受层面，而能形成可执行的判断标准。"

private fun Any.toJsonPrimitive(): JsonPrimitive = when (this) {
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    else -> JsonPrimitive(toString())
}

private fun String.extractWeReadBookId(): String? = Regex("[?&]bId=([^&]+)")
    .find(this)
    ?.groupValues
    ?.getOrNull(1)
    ?.takeIf { it.isNotBlank() }

private fun JsonObject.stringValue(key: String): String = this[key]?.jsonPrimitive?.contentOrNull ?: ""

private fun JsonObject.intValue(key: String): Int = this[key]?.jsonPrimitive?.intOrNull ?: 0

private fun JsonObject.objectValue(key: String): JsonObject? = runCatching { this[key]?.jsonObject }.getOrNull()

private fun JsonObject.arrayValue(key: String): JsonArray? = runCatching { this[key]?.jsonArray }.getOrNull()

private fun kotlinx.serialization.json.JsonElement.asJsonObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()
