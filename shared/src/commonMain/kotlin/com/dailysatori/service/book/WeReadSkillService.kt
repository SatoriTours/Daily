package com.dailysatori.service.book

import co.touchlab.kermit.Logger
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.McpServerRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.SkillConfigRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.mcp.RemoteMcpClient
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.service.security.SecretCipherPrefix
import com.dailysatori.service.skill.BuiltInSkillTemplates
import com.dailysatori.shared.db.Ai_config
import com.dailysatori.shared.db.Book
import com.dailysatori.shared.db.Book_viewpoint
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import kotlinx.serialization.Serializable

private const val WE_READ_GATEWAY = "https://i.weread.qq.com/api/agent/gateway"
private const val WE_READ_SKILL_VERSION = "1.0.3"
private const val BOOK_VIEWPOINT_ENRICH_CONCURRENCY = 10

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

@Serializable
data class WeReadBookInfo(
    val bookId: String,
    val title: String,
    val author: String = "",
    val intro: String = "",
    val category: String = "",
    val rating: Int = 0,
    val ratingCount: Int = 0,
)

@Serializable
data class WeReadChapter(
    val chapterUid: Int,
    val chapterIdx: Int,
    val title: String,
    val level: Int = 0,
    val wordCount: Int = 0,
)

@Serializable
data class WeReadReview(
    val content: String,
    val star: Int = 0,
)

@Serializable
data class BookViewpointOutline(
    val title: String,
    val brief: String,
    val focus: String,
    val searchQuery: String,
    val caseIntent: String,
)

@Serializable
data class BookViewpointRetryContext(
    val bookTitle: String,
    val bookAuthor: String,
    val bookCategory: String,
    val bookIntroduction: String,
    val info: WeReadBookInfo,
    val chapters: List<WeReadChapter>,
    val reviews: List<WeReadReview>,
    val outline: BookViewpointOutline,
)

interface BookAiFallbackGenerator {
    suspend fun generate(
        book: BookSearchResult,
        info: WeReadBookInfo,
        chapters: List<WeReadChapter>,
        reviews: List<WeReadReview>,
    ): List<BookViewpointDraft>

    suspend fun regenerate(book: Book, viewpoint: Book_viewpoint): BookViewpointDraft {
        throw WeReadSkillException(WeReadSkillErrorType.AiFallbackFailure, "AI 观点生成失败，请稍后重试")
    }
}

class DefaultBookAiFallbackGenerator(
    private val aiConfigService: AiConfigService,
    private val aiService: AiService,
    private val mcpServerRepository: McpServerRepository,
    private val remoteMcpClient: RemoteMcpClient,
) : BookAiFallbackGenerator {
    private val log = Logger.withTag("BookAiFallback")

    override suspend fun generate(
        book: BookSearchResult,
        info: WeReadBookInfo,
        chapters: List<WeReadChapter>,
        reviews: List<WeReadReview>,
    ): List<BookViewpointDraft> {
        val config = requireAiFallbackConfig(aiConfigService.getDefaultConfig())
        val outlines = generateOutlines(book, info, chapters, reviews, config)
        return enrichOutlines(book, info, chapters, reviews, config, outlines)
    }

    override suspend fun regenerate(book: Book, viewpoint: Book_viewpoint): BookViewpointDraft {
        val config = requireAiFallbackConfig(aiConfigService.getDefaultConfig())
        val context = parseBookViewpointRetryContext(viewpoint.outline_json)
            ?: fallbackRetryContext(book, viewpoint)
        return enrichOne(
            book = context.toSearchResult(),
            info = context.info,
            chapters = context.chapters,
            reviews = context.reviews,
            config = config,
            outline = context.outline,
            servers = mcpServerRepository.getEnabled().filter { it.server_url.startsWith("http") },
            retryContextJson = weReadJson.encodeToString(BookViewpointRetryContext.serializer(), context),
        )
    }

    private suspend fun generateOutlines(
        book: BookSearchResult,
        info: WeReadBookInfo,
        chapters: List<WeReadChapter>,
        reviews: List<WeReadReview>,
        config: Ai_config,
    ): List<BookViewpointOutline> {
        val response = completeWithAi(
            prompt = info.title.ifBlank { book.title },
            config = config,
            systemPrompt = buildBookViewpointOutlinePrompt(book, info, chapters, reviews),
            failure = "AI 观点生成失败，请稍后重试",
        )
        val parsedOutlines = parseBookViewpointOutlineJson(response)
        val supplementedOutlines = supplementBookViewpointOutlines(parsedOutlines, book, info, chapters, reviews, config)
        val completedOutlines = completeBookViewpointOutlines(supplementedOutlines, book, info, chapters, reviews)
        if (parsedOutlines.size < 10) {
            log.w {
                "AI outline response parsed ${parsedOutlines.size}/10; supplemented to ${supplementedOutlines.size}; completed to ${completedOutlines.size} for ${info.title.ifBlank { book.title }}"
            }
        }
        if (completedOutlines.size < 10) {
            throw WeReadSkillException(WeReadSkillErrorType.AiFallbackFailure, "AI 观点生成失败，请稍后重试")
        }
        return completedOutlines
    }

    private suspend fun supplementBookViewpointOutlines(
        outlines: List<BookViewpointOutline>,
        book: BookSearchResult,
        info: WeReadBookInfo,
        chapters: List<WeReadChapter>,
        reviews: List<WeReadReview>,
        config: Ai_config,
    ): List<BookViewpointOutline> {
        var completed = outlines.distinctBy { it.title.normalizedOutlineKey() }.take(10)
        while (completed.size < 10) {
            val missingCount = 10 - completed.size
            val response = try {
                completeWithAi(
                    prompt = info.title.ifBlank { book.title },
                    config = config,
                    systemPrompt = buildBookViewpointOutlineSupplementPrompt(book, info, chapters, reviews, completed, missingCount),
                    failure = "AI 观点生成失败，请稍后重试",
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                log.w(error) { "AI outline supplement failed for ${info.title.ifBlank { book.title }}" }
                return completed
            }
            val existing = completed.map { it.title.normalizedOutlineKey() }.toSet()
            val additions = parseBookViewpointOutlineJson(response)
                .filterNot { it.title.normalizedOutlineKey() in existing }
            if (additions.isEmpty()) return completed
            completed = (completed + additions).distinctBy { it.title.normalizedOutlineKey() }.take(10)
        }
        return completed
    }

    private suspend fun enrichOutlines(
        book: BookSearchResult,
        info: WeReadBookInfo,
        chapters: List<WeReadChapter>,
        reviews: List<WeReadReview>,
        config: Ai_config,
        outlines: List<BookViewpointOutline>,
    ): List<BookViewpointDraft> = coroutineScope {
        val servers = mcpServerRepository.getEnabled().filter { it.server_url.startsWith("http") }
        outlines.take(10).chunked(BOOK_VIEWPOINT_ENRICH_CONCURRENCY).flatMap { chunk ->
            chunk.map { outline ->
                val contextJson = bookViewpointRetryContextJson(book, info, chapters, reviews, outline)
                async { enrichOne(book, info, chapters, reviews, config, outline, servers, contextJson) }
            }.awaitAll()
        }
    }

    private suspend fun enrichOne(
        book: BookSearchResult,
        info: WeReadBookInfo,
        chapters: List<WeReadChapter>,
        reviews: List<WeReadReview>,
        config: Ai_config,
        outline: BookViewpointOutline,
        servers: List<com.dailysatori.shared.db.Mcp_server>,
        retryContextJson: String,
    ): BookViewpointDraft {
        val sourceNotes = runCatching { remoteMcpClient.collectWebSearchNotes(servers, outline.searchQuery) }.getOrDefault("")
        return try {
            val response = completeWithAi(
                prompt = outline.title,
                config = config,
                systemPrompt = buildBookViewpointEnrichmentPrompt(book, info, chapters, reviews, outline, sourceNotes),
                failure = "AI 观点生成失败，请稍后重试",
            )
            parseBookViewpointEnrichmentJson(response, outline, sourceNotes, retryContextJson)
                ?: failedBookViewpointDraft(outline, "AI 观点生成失败，请稍后重试", sourceNotes).copy(outlineJson = retryContextJson)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failedBookViewpointDraft(outline, "AI 观点生成失败，请稍后重试", sourceNotes).copy(outlineJson = retryContextJson)
        }
    }

    private suspend fun completeWithAi(
        prompt: String,
        config: Ai_config,
        systemPrompt: String,
        failure: String,
    ): String = try {
        aiService.complete(
            prompt = prompt,
            apiAddress = config.api_address,
            apiToken = config.api_token,
            modelName = config.model_name,
            provider = config.provider,
            systemPrompt = systemPrompt,
            temperature = 0.5,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw WeReadSkillException(WeReadSkillErrorType.AiFallbackFailure, failure, error)
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

fun bookViewpointEnrichConcurrency(): Int = BOOK_VIEWPOINT_ENRICH_CONCURRENCY

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
    val readyDrafts = aiDrafts.filter { it.status == "ready" }
    if (readyDrafts.size < 10) {
        throw WeReadSkillException(WeReadSkillErrorType.AiFallbackFailure, "AI 观点生成失败，请稍后重试")
    }
    return BookViewpointGenerationResult(readyDrafts.take(10), BookViewpointSource.AiFallback)
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
        title 尽量使用书中原有短语、章节概念、命题或公案关键词，像观点标题，不要把解释、否定、强调、说明写进 title。
        如果原始短语像半句或不完整，要保留核心词并补成完整观点标题，但不要补成讲解句。
        content 是观点正文，控制在 100 到 200 个中文字符，不超过 200 个中文字符；直接说明观点本身，优先用书籍资料中的原始表述，并交代它在书中的背景、位置或语境，再自然说明风险、条件和边界，不要使用“此观点”“这个观点”“这说明”“这意味着”等讲解口吻。
        content 不要使用“此观点”“这个观点”“这说明”“这意味着”等开头或转述句。
        example 至少 120 个中文字符，直接讲故事，不要写“在某某书中”“书中情境”这类套话；故事要写清人物或组织、冲突、行动、转折和结果。
        example 必须使用白话、现代中文，像给普通读者解释一个具体场景；不要写成古文、半文言、格言体或晦涩摘要。古书也要把情境翻译成今天能直接理解的说法。
        如果资料有限，可以做合理概括，但不要编造书中原文、页码、章节细节或具体引文。
    """.trimIndent()
}

fun buildBookViewpointOutlinePrompt(
    book: BookSearchResult,
    info: WeReadBookInfo,
    chapters: List<WeReadChapter>,
    reviews: List<WeReadReview>,
): String {
    val title = info.title.ifBlank { book.title }
    val author = info.author.ifBlank { book.author }
    return """
        请基于微信读书资料生成 10 个观点骨架，不要写长解释和案例。
        每个观点骨架用于后续联网 MCP 检索和 AI 补全。

        书名：$title
        作者：$author
        分类：${book.category.ifBlank { info.category }}
        简介：${info.intro.ifBlank { book.introduction }}
        可用目录：${chapters.take(12).joinToString("、") { it.title }}
        可用书评：${reviews.take(5).joinToString("\n") { it.content }}

        只返回 JSON 数组，数组包含 10 个对象。
        每个对象字段：title、brief、focus、searchQuery、caseIntent。
        title 尽量使用书中原有短语、章节概念、命题或公案关键词，不要把解释写进 title。
        如果原始短语像半句或不完整，要保留核心词并补成完整观点标题，但不要补成讲解句。
        brief 优先保留书籍资料中的原始表述，用 40-80 字直接呈现观点本身，不要写成“此观点……”式讲解。
        focus 是 2-8 个字的观点主题。
        searchQuery 必须适合 MCP 联网搜索，包含书名 + 作者 + 观点主题 + 关键词。
        caseIntent 说明这个观点适合真实案例、思想来源、寓言故事、童话故事或组织类比。
        不要编造章节正文、页码或原文引文。
    """.trimIndent()
}

fun buildBookViewpointOutlineSupplementPrompt(
    book: BookSearchResult,
    info: WeReadBookInfo,
    chapters: List<WeReadChapter>,
    reviews: List<WeReadReview>,
    existingOutlines: List<BookViewpointOutline>,
    missingCount: Int,
): String {
    val title = info.title.ifBlank { book.title }
    val author = info.author.ifBlank { book.author }
    return """
        请基于微信读书资料补齐缺少的 $missingCount 个观点骨架，不要写长解释和案例。
        这些观点骨架用于后续联网 MCP 检索和 AI 补全。

        书名：$title
        作者：$author
        分类：${book.category.ifBlank { info.category }}
        简介：${info.intro.ifBlank { book.introduction }}
        可用目录：${chapters.filter(::isUsefulBookChapterForViewpoint).take(12).joinToString("、") { cleanBookChapterTitle(it.title) }}
        可用书评：${reviews.take(5).joinToString("\n") { it.content }}

        已有观点：
        ${existingOutlines.joinToString("\n") { "- ${it.title}：${it.brief}" }}

        只返回 JSON 数组，数组只包含缺少的 $missingCount 个对象。
        每个对象字段：title、brief、focus、searchQuery、caseIntent。
        不要重复已有观点，也不要用封面、版权信息、目录、作者简介这类非正文项目凑数。
        title 尽量使用书中原有短语、章节概念、命题或公案关键词，不要把解释写进 title。
        如果原始短语像半句或不完整，要保留核心词并补成完整观点标题，但不要补成讲解句。
        brief 优先保留书籍资料中的原始表述，用 40-80 字直接呈现观点本身，不要写成“此观点……”式讲解。
        searchQuery 必须适合 MCP 联网搜索，包含书名 + 作者 + 观点主题 + 关键词。
        不要编造章节正文、页码、原文引文或微信读书内部 ID。
    """.trimIndent()
}

fun buildBookViewpointEnrichmentPrompt(
    book: BookSearchResult,
    info: WeReadBookInfo,
    chapters: List<WeReadChapter>,
    reviews: List<WeReadReview>,
    outline: BookViewpointOutline,
    sourceNotes: String,
): String {
    val title = info.title.ifBlank { book.title }
    val author = info.author.ifBlank { book.author }
    return """
        请把一个书籍观点骨架补全为最终观点卡片，只返回 JSON 对象：{"title":"...","content":"...","example":"..."}。

        书名：$title
        作者：$author
        简介：${info.intro.ifBlank { book.introduction }}
        可用目录：${chapters.take(12).joinToString("、") { it.title }}
        可用书评：${reviews.take(5).joinToString("\n") { it.content }}

        观点骨架：${weReadJson.encodeToString(BookViewpointOutline.serializer(), outline)}

        外部 MCP 资料：
        ${sourceNotes.ifBlank { "无可用外部资料" }}

        先判断 MCP 资料是否与书名、作者、观点主题匹配；无关资料必须忽略。
        title 优先沿用观点骨架中的书中短语；如果 MCP 或微信读书资料提供了更接近原文的短语，可替换为该短语。不要在 title 里追加解释句。
        如果标题像半句或不完整，要保留核心词并补成完整观点标题，但不要补成讲解句。
        content 直接说明观点本身，控制在 100 到 200 个中文字符，不超过 200 个中文字符；优先引用或贴近可用资料中的原始说法，并交代观点在书中的背景、位置或语境，再说明它的条件、边界和误用风险，不要写“此观点”“这个观点”“这说明”“这意味着”等讲解口吻。
        example 至少 120 个中文字符。真实案例必须来自微信读书资料或 MCP 资料；如果没有可靠真实案例，可以写寓言、童话、组织场景或类比故事。
        example 必须使用白话、现代中文，像给普通读者解释一个具体场景；不要写成古文、半文言、格言体或晦涩摘要。古书也要把情境翻译成今天能直接理解的说法。
        不能把类比或想象场景写成真实发生，也不要写“某知名企业曾经”这类无法验证的套话。
    """.trimIndent()
}

fun parseBookViewpointOutlineJson(response: String): List<BookViewpointOutline> {
    val array = runCatching { weReadJson.parseToJsonElement(extractJsonArray(response)).jsonArray }.getOrNull()
        ?: return emptyList()
    return array.mapNotNull { item ->
        val obj = item.asJsonObjectOrNull() ?: return@mapNotNull null
        val outline = BookViewpointOutline(
            title = obj.stringValue("title").trim(),
            brief = obj.stringValue("brief").trim(),
            focus = obj.stringValue("focus").trim(),
            searchQuery = obj.stringValue("searchQuery").trim(),
            caseIntent = obj.stringValue("caseIntent").trim(),
        )
        if (outline.title.length < 4 || outline.brief.length < 10 || outline.searchQuery.isBlank()) null else outline
    }.take(10)
}

fun completeBookViewpointOutlines(
    outlines: List<BookViewpointOutline>,
    book: BookSearchResult,
    info: WeReadBookInfo,
    chapters: List<WeReadChapter>,
    reviews: List<WeReadReview>,
): List<BookViewpointOutline> {
    val title = info.title.ifBlank { book.title }.ifBlank { "这本书" }
    val author = info.author.ifBlank { book.author }
    val intro = info.intro.ifBlank { book.introduction }
    val completed = outlines.distinctBy { it.title.normalizedOutlineKey() }.toMutableList()
    val existing = completed.map { it.title.normalizedOutlineKey() }.toMutableSet()

    fun addOutline(seedTitle: String, brief: String, focus: String, caseIntent: String) {
        if (completed.size >= 10) return
        val cleanTitle = seedTitle.trim().trim('。', '，', ',', '.', '：', ':')
        if (cleanTitle.isBlank()) return
        val key = cleanTitle.normalizedOutlineKey()
        if (!existing.add(key)) return
        completed += BookViewpointOutline(
            title = cleanTitle,
            brief = brief.trim().ifBlank { "$cleanTitle 是《$title》中可继续展开的核心线索，需要结合资料判断它的条件、边界和风险。" },
            focus = focus.trim().ifBlank { cleanTitle.take(8) },
            searchQuery = listOf(title, author, focus.ifBlank { cleanTitle }, cleanTitle)
                .filter { it.isNotBlank() }
                .joinToString(" "),
            caseIntent = caseIntent,
        )
    }

    chapters
        .sortedBy { it.chapterIdx }
        .filter(::isUsefulBookChapterForViewpoint)
        .forEach { chapter ->
            val chapterTitle = cleanBookChapterTitle(chapter.title)
            addOutline(
                seedTitle = chapterTitle,
                brief = "目录中的“$chapterTitle”提示这本书围绕这一线索展开判断，可从问答、处境和回应方式中提炼观点。",
                focus = chapterTitle.take(8),
                caseIntent = "书中情境、寓言或现代类比故事",
            )
        }

    if (intro.isNotBlank()) {
        addOutline(
            seedTitle = "${title}的核心问题",
            brief = intro.take(80),
            focus = "核心问题",
            caseIntent = "思想来源或现代类比故事",
        )
    }

    reviews
        .filter { it.content.isNotBlank() }
        .forEachIndexed { index, review ->
            addOutline(
                seedTitle = "读者反复注意的线索${index + 1}",
                brief = review.content.take(80),
                focus = "读者线索",
                caseIntent = "书评线索或现代类比故事",
            )
        }

    fallbackOutlineThemes().forEach { (seedTitle, focus) ->
        addOutline(
            seedTitle = seedTitle,
            brief = "$seedTitle 是《$title》中可用于阅读思考的补位线索，需要回到书籍资料中说明它成立的条件、边界和误用风险。",
            focus = focus,
            caseIntent = "寓言、组织场景或生活类比故事",
        )
    }

    return completed.take(10)
}

private val nonContentBookChapterTitles = setOf(
    "封面",
    "版权信息",
    "目录",
    "扉页",
    "前言",
    "序",
    "序言",
    "出版说明",
    "作者简介",
    "后记",
    "附录",
    "致谢",
    "参考文献",
)

private fun isUsefulBookChapterForViewpoint(chapter: WeReadChapter): Boolean {
    val title = cleanBookChapterTitle(chapter.title)
    if (title.isBlank()) return false
    if (title in nonContentBookChapterTitles) return false
    if (title.length <= 2) return false
    if (chapter.wordCount in 1..79) return false
    return true
}

private fun cleanBookChapterTitle(title: String): String = title
    .trim()
    .replace(Regex("""^\s*(第?[一二三四五六七八九十百千万\d]+[章节回讲篇])?[、.．:\s]+"""), "")
    .trim()
    .trim('。', '，', ',', '.', '：', ':')

private fun String.normalizedOutlineKey(): String = trim().trim('。', '，', ',', '.', '：', ':')

private fun fallbackOutlineThemes(): List<Pair<String, String>> = listOf(
    "从关键问答看见问题本身" to "关键问答",
    "先识别执着再谈行动" to "识别执着",
    "把抽象判断放回具体处境" to "具体处境",
    "不要把方法误认为答案" to "方法边界",
    "真正的转变发生在回应方式里" to "回应方式",
    "语言只能指向经验不能替代经验" to "语言边界",
    "用当下处境检验理解" to "当下检验",
    "看见分别心如何制造障碍" to "分别心",
    "从反常回应里打断惯性" to "打断惯性",
    "把理解落实到日常选择" to "日常选择",
)

fun failedBookViewpointDraft(outline: BookViewpointOutline, errorMessage: String, sourceNotes: String): BookViewpointDraft =
    BookViewpointDraft(
        title = outline.title,
        content = "",
        example = "",
        status = "failed",
        errorMessage = errorMessage,
        outlineJson = weReadJson.encodeToString(BookViewpointOutline.serializer(), outline),
        sourceNotes = sourceNotes,
    )

fun parseBookViewpointEnrichmentJson(
    response: String,
    outline: BookViewpointOutline,
    sourceNotes: String,
    outlineJson: String = weReadJson.encodeToString(BookViewpointOutline.serializer(), outline),
): BookViewpointDraft? {
    val obj = runCatching { weReadJson.parseToJsonElement(extractJsonObject(response)).jsonObject }.getOrNull()
        ?: return null
    val title = obj.stringValue("title").trim().ifBlank { outline.title }
    val content = obj.stringValue("content").trim()
    val example = obj.stringValue("example").trim()
    if (title.length < 4 || content.length < 40 || example.length < 120) return null
    return BookViewpointDraft(
        title = title,
        content = content,
        example = example,
        outlineJson = outlineJson,
        sourceNotes = sourceNotes,
    )
}

fun bookViewpointRetryContextJson(
    book: BookSearchResult,
    info: WeReadBookInfo,
    chapters: List<WeReadChapter>,
    reviews: List<WeReadReview>,
    outline: BookViewpointOutline,
): String = weReadJson.encodeToString(
    BookViewpointRetryContext.serializer(),
    BookViewpointRetryContext(
        bookTitle = info.title.ifBlank { book.title },
        bookAuthor = info.author.ifBlank { book.author },
        bookCategory = book.category.ifBlank { info.category },
        bookIntroduction = info.intro.ifBlank { book.introduction },
        info = info,
        chapters = chapters,
        reviews = reviews,
        outline = outline,
    ),
)

fun parseBookViewpointRetryContext(value: String): BookViewpointRetryContext? =
    runCatching { weReadJson.decodeFromString(BookViewpointRetryContext.serializer(), value) }.getOrNull()

private fun fallbackRetryContext(book: Book, viewpoint: Book_viewpoint): BookViewpointRetryContext {
    val outline = BookViewpointOutline(
        title = viewpoint.title,
        brief = viewpoint.content.ifBlank { viewpoint.title },
        focus = viewpoint.title.take(8),
        searchQuery = listOf(book.title, book.author, viewpoint.title).filter { it.isNotBlank() }.joinToString(" "),
        caseIntent = "真实或类比案例",
    )
    return BookViewpointRetryContext(
        bookTitle = book.title,
        bookAuthor = book.author,
        bookCategory = book.category,
        bookIntroduction = book.introduction,
        info = WeReadBookInfo(bookId = "", title = book.title, author = book.author, intro = book.introduction, category = book.category),
        chapters = emptyList(),
        reviews = emptyList(),
        outline = outline,
    )
}

private fun BookViewpointRetryContext.toSearchResult(): BookSearchResult = BookSearchResult(
    title = bookTitle,
    author = bookAuthor,
    category = bookCategory,
    introduction = bookIntroduction,
)

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

private fun extractJsonObject(response: String): String {
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
    val start = unfenced.indexOf('{')
    val end = unfenced.lastIndexOf('}')
    if (start < 0 || end < start) return "{}"
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
