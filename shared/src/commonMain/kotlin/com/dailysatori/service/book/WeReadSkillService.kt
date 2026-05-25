package com.dailysatori.service.book

import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.SkillConfigRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.service.security.SecretCipherPrefix
import com.dailysatori.service.skill.BuiltInSkillTemplates
import com.dailysatori.shared.db.Ai_config
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

enum class WeReadSkillErrorType { MissingApiKey, MissingAiFallbackConfig, AiFallbackFailure, NoResults, RemoteFailure }

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

interface BookAiFallbackGenerator {
    suspend fun generate(
        book: BookSearchResult,
        info: WeReadBookInfo,
        chapters: List<WeReadChapter>,
        reviews: List<WeReadReview>,
    ): List<BookViewpointDraft>
}

class DefaultBookAiFallbackGenerator(
    private val aiConfigService: AiConfigService,
    private val aiService: AiService,
) : BookAiFallbackGenerator {
    override suspend fun generate(
        book: BookSearchResult,
        info: WeReadBookInfo,
        chapters: List<WeReadChapter>,
        reviews: List<WeReadReview>,
    ): List<BookViewpointDraft> {
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
            throw WeReadSkillException(WeReadSkillErrorType.AiFallbackFailure, "AI 观点生成失败，请稍后重试", error)
        }
        return parseAiFallbackViewpointJson(response)
    }
}

class WeReadSkillService(
    private val client: HttpClient,
    private val settingRepository: SettingRepository,
    private val secretCipher: SecretCipher,
    private val aiFallbackGenerator: BookAiFallbackGenerator,
    private val skillConfigRepository: SkillConfigRepository,
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
        val info = parseWeReadBookInfo(callGateway("/book/info", mapOf("bookId" to bookId))).withSearchFallback(book, bookId)
        val chapters = parseWeReadChapters(callGateway("/book/chapterinfo", mapOf("bookId" to bookId)))
        val reviews = parseWeReadReviews(
            callGateway("/review/list", mapOf("bookId" to bookId, "reviewListType" to 1, "count" to 10)),
        )
        return selectWeReadOrAiViewpoints(book, info, chapters, reviews, aiFallbackGenerator)
    }

    private fun WeReadBookInfo.withSearchFallback(book: BookSearchResult, bookId: String): WeReadBookInfo = copy(
        bookId = this.bookId.ifBlank { bookId },
        title = title.ifBlank { book.title },
        author = author.ifBlank { book.author },
        intro = intro.ifBlank { book.introduction },
        category = category.ifBlank { book.category },
    )

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
        val skill = skillConfigRepository.getBuiltInByTemplateId(BuiltInSkillTemplates.weRead)
        val legacyStored = settingRepository.get(SettingKeys.weReadApiKey).orEmpty()
        return resolveWeReadTokenFromSkillOrLegacy(
            skillToken = skill?.api_token,
            skillEnabled = skill?.enabled == 1L,
            legacyStored = legacyStored,
            isEncrypted = secretCipher::isEncrypted,
            decrypt = secretCipher::decrypt,
            onLegacyPlaintext = { key -> settingRepository.upsert(SettingKeys.weReadApiKey, secretCipher.encrypt(key)) },
        )
    }
}

fun requireWeReadApiKey(value: String?): String = value?.trim()?.takeIf { it.isNotBlank() }
    ?: throw WeReadSkillException(WeReadSkillErrorType.MissingApiKey, "请先在 Skills 中配置微信读书 Token")

fun hasSufficientWeReadMaterial(
    info: WeReadBookInfo,
    chapters: List<WeReadChapter>,
    reviews: List<WeReadReview>,
    drafts: List<BookViewpointDraft>,
): Boolean {
    val materialLength = info.intro.length + chapters.sumOf { it.title.length } + reviews.sumOf { it.content.length }
    val hasSupportMaterial = chapters.any { it.title.isNotBlank() } || reviews.any { it.content.isNotBlank() }
    return info.title.isNotBlank() &&
        hasSupportMaterial &&
        materialLength >= 40 &&
        drafts.size >= 10 &&
        drafts.all { it.content.length >= 40 && it.example.length >= 120 }
}

fun requireAiFallbackConfig(config: Ai_config?): Ai_config {
    val value = config ?: throw WeReadSkillException(
        WeReadSkillErrorType.MissingAiFallbackConfig,
        "微信读书资料不足，请先配置默认 AI 模型后重试",
    )
    if (value.api_token.isBlank()) {
        throw WeReadSkillException(
            WeReadSkillErrorType.MissingAiFallbackConfig,
            "微信读书资料不足，请先配置默认 AI 模型后重试",
        )
    }
    return value
}

suspend fun selectWeReadOrAiViewpoints(
    book: BookSearchResult,
    info: WeReadBookInfo,
    chapters: List<WeReadChapter>,
    reviews: List<WeReadReview>,
    aiFallbackGenerator: BookAiFallbackGenerator,
): BookViewpointGenerationResult {
    val aiDrafts = aiFallbackGenerator.generate(book, info, chapters, reviews)
    if (aiDrafts.size < 10) {
        throw WeReadSkillException(WeReadSkillErrorType.AiFallbackFailure, "AI 观点生成失败，请稍后重试")
    }
    return BookViewpointGenerationResult(aiDrafts, BookViewpointSource.AiFallback)
}

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

fun resolveWeReadTokenFromSkillOrLegacy(
    skillToken: String?,
    skillEnabled: Boolean,
    legacyStored: String,
    isEncrypted: (String) -> Boolean,
    decrypt: (String) -> String,
    onLegacyPlaintext: (String) -> Unit = {},
): String {
    if (skillToken != null) return if (skillEnabled) skillToken.trim() else ""
    return resolveStoredWeReadApiKey(
        stored = legacyStored,
        isEncrypted = isEncrypted,
        decrypt = decrypt,
        onPlaintext = onLegacyPlaintext,
    )
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
    val reviewText = if (reviews.any { it.content.isNotBlank() }) {
        "可用补充材料提示这本书关注抽象问题如何转化为现实压力与选择。"
    } else {
        "简介和目录提示这本书关注抽象问题如何转化为现实压力与选择。"
    }
    val themes = listOf(
        "先识别系统压力，再判断行动选择",
        "把不确定性拆成可讨论的具体约束",
        "核心概念需要放回历史条件和现实关系中理解",
        "宏大问题必须落到具体矛盾和具体判断",
        "有效行动要同时解释风险、条件和边界",
        "理论价值取决于它能否改变问题意识",
        "从论述结构里寻找反复出现的因果链",
        "用事实材料校正抽象判断中的理解偏差",
        "从关键章节提炼可迁移的判断标准",
        "把书中主张转化为能复用的行动语言",
    )
    return themes.mapIndexed { index, theme ->
        BookViewpointDraft(
            title = "$title：$theme。",
            content = buildWeReadContent(title, author, intro, chapterText, reviewText, theme, index),
            example = buildWeReadExample(title, chapterText, reviewText, theme, index),
        )
    }
}

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
        请基于微信读书接口返回的书籍资料生成 10 个书中核心观点卡片。
        这些观点由 AI 根据可用资料提炼，不能声称来自微信读书书评或原文。
        目标是提炼书里的关键主张、论点、方法和判断标准，不要写书评，不要写读后感，不要评价这本书好不好。

        书名：$title
        作者：$author
        分类：${book.category.ifBlank { info.category }}
        简介：$intro
        可用目录：$chapterText
        可用书评：$reviewText

        只返回 JSON 数组，不要 Markdown、解释或额外文本。
        数组必须包含 10 个对象，每个对象必须包含字段：title、content、example。
        title 必须是完整判断句，直接表达书中观点。
        content 是观点总结，控制在 80 到 160 个中文字符，必须讲清核心主张，并同时解释风险、条件和边界。
        example 至少 120 个中文字符，直接讲故事，不要写“在某某书中”“书中情境”这类套话；故事要写清人物或组织、冲突、行动、转折和结果。
        如果资料有限，可以做合理概括，但不要编造书中原文、页码、章节细节或具体引文。
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
        if (title.length < 4 || content.length < 40 || example.length < 120) return@mapNotNull null
        BookViewpointDraft(title = title, content = content, example = example)
    }.take(10)
}

fun weReadUserMessage(error: WeReadSkillException): String = when (error.type) {
    WeReadSkillErrorType.MissingApiKey -> "请先在 Skills 中配置微信读书 Token"
    WeReadSkillErrorType.MissingAiFallbackConfig -> "微信读书资料不足，请先配置默认 AI 模型后重试"
    WeReadSkillErrorType.AiFallbackFailure -> "AI 观点生成失败，请稍后重试"
    WeReadSkillErrorType.NoResults -> "微信读书未找到相关书籍"
    WeReadSkillErrorType.RemoteFailure -> error.message.ifBlank { "微信读书服务暂时不可用" }
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
): String = "$theme：先看${chapterText.take(10)}中的条件、矛盾和后果，再判断行动边界。"

private fun buildWeReadExample(
    title: String,
    chapterText: String,
    reviewText: String,
    theme: String,
    index: Int,
): String = "在《${title}》的书中情境里，围绕 $chapterText 展开的不是抽象口号，而是一段有压力的判断过程。主人公或组织先遇到局势混乱、信息不足和目标冲突，再用“$theme”重新分辨主要矛盾，随后调整行动顺序。转折发生在他们不再凭情绪下结论，而是把“${reviewText.take(24)}”放进具体条件中检验，最后才形成能够推动局面的判断。"

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

private fun JsonObject.stringValue(key: String): String = runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: ""

private fun JsonObject.intValue(key: String): Int = this[key]?.jsonPrimitive?.intOrNull ?: 0

private fun JsonObject.objectValue(key: String): JsonObject? = runCatching { this[key]?.jsonObject }.getOrNull()

private fun JsonObject.arrayValue(key: String): JsonArray? = runCatching { this[key]?.jsonArray }.getOrNull()

private fun kotlinx.serialization.json.JsonElement.asJsonObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()
