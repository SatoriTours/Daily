package com.dailysatori.service.externalfavorites

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.shared.db.External_favorite_item
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ExternalFavoriteAiInput(
    val provider: String,
    val title: String,
    val text: String,
    val authorName: String,
    val sourceCreatedAt: Long?,
    val canonicalUrl: String,
    val supplementUrl: String? = null,
    val supplementTitle: String? = null,
    val supplementText: String? = null,
    val supplementSourceType: String? = null,
)

data class ExternalFavoriteAiAnalysis(
    val title: String,
    val summary: String,
    val markdown: String,
)

class ExternalFavoriteAiOrganizer(
    private val itemRepo: ExternalFavoriteItemRepository,
    private val articleRepo: ArticleRepository,
    private val aiConfigService: AiConfigService? = null,
    private val aiService: AiService? = null,
    private val supplementResolver: ExternalFavoriteSupplementResolver? = null,
    private val generateAnalysis: (suspend (ExternalFavoriteAiInput) -> ExternalFavoriteAiAnalysis)? = null,
) {
    suspend fun organizePending(limit: Long = 10, includeFailed: Boolean = false): Int {
        return organizeItems(if (includeFailed) itemRepo.retryableAi(limit) else itemRepo.pendingAi(limit))
    }

    suspend fun organizePendingForSource(sourceId: Long, limit: Long = 10, includeFailed: Boolean = false): Int {
        return organizeItems(if (includeFailed) itemRepo.retryableAiBySource(sourceId, limit) else itemRepo.pendingAiBySource(sourceId, limit))
    }

    suspend fun organizePendingForSource(
        sourceId: Long,
        limit: Long = 10,
        includeFailed: Boolean = false,
        httpLogger: FavoriteSyncHttpLogger = NoopFavoriteSyncHttpLogger,
        taskId: Long? = null,
    ): Int {
        val items = if (includeFailed) itemRepo.retryableAiBySource(sourceId, limit) else itemRepo.pendingAiBySource(sourceId, limit)
        return organizeItems(items, httpLogger, taskId)
    }

    private suspend fun organizeItems(
        items: List<External_favorite_item>,
        httpLogger: FavoriteSyncHttpLogger = NoopFavoriteSyncHttpLogger,
        taskId: Long? = null,
    ): Int {
        var processed = 0
        items.forEach { item ->
            val article = item.article_id?.let(articleRepo::getById)
            if (article == null) {
                itemRepo.markAiState(item.id, ExternalItemAiStatus.failed.name, "missing_article", "Linked article was not found.")
                processed += 1
                return@forEach
            }

            val input = item.toAiInput()
                .withSupplementIfNeeded(item, httpLogger, taskId)
            logAiRequest(httpLogger, taskId, item, input)
            val analysis = runCatching { generateAnalysis?.invoke(input) ?: generateWithAi(input) }
                .getOrElse { error ->
                    logAiFailure(httpLogger, taskId, item, error)
                    itemRepo.markAiState(
                        item.id,
                        ExternalItemAiStatus.failed.name,
                        "ai_failed",
                        error.message.orEmpty().ifBlank { "External favorite AI organization failed." },
                    )
                    processed += 1
                    return@forEach
                }
            logAiResponse(httpLogger, taskId, item, analysis)

            val aiTitle = analysis.title.trim().ifBlank { article.title ?: input.title.ifBlank { "X 收藏" } }
            val summary = analysis.summary.trim().ifBlank { input.text }
            val markdown = input.toArticleMarkdown(aiTitle, analysis.markdown)

            articleRepo.updateAiTitle(article.id, aiTitle)
            articleRepo.updateAiContent(article.id, summary, aiTitle, article.cover_image_url)
            articleRepo.updateAiMarkdownContent(article.id, markdown)
            articleRepo.updateStatus(article.id, "completed")
            itemRepo.markAiState(item.id, ExternalItemAiStatus.completed.name)
            processed += 1
        }
        return processed
    }

    private suspend fun generateWithAi(input: ExternalFavoriteAiInput): ExternalFavoriteAiAnalysis {
        val config = aiConfigService?.getDefaultConfig()
            ?: throw IllegalStateException("AI config not set")
        val ai = aiService ?: throw IllegalStateException("AI service not set")
        if (config.api_address.isBlank() || config.api_token.isBlank() || config.model_name.isBlank()) {
            throw IllegalStateException("AI config not set")
        }

        val response = ai.summarize(
            content = input.toPromptContent(),
            systemPrompt = EXTERNAL_FAVORITE_SYSTEM_PROMPT,
            apiAddress = config.api_address.trim().trimEnd('/'),
            apiToken = config.api_token.trim(),
            modelName = config.model_name.trim(),
            provider = config.provider.trim(),
        )
        return parseAiAnalysis(response)
    }

    private fun logAiRequest(
        httpLogger: FavoriteSyncHttpLogger,
        taskId: Long?,
        item: External_favorite_item,
        input: ExternalFavoriteAiInput,
    ) {
        httpLogger.logRequest(
            taskId = taskId,
            label = "external_favorite_ai",
            method = "POST",
            url = "ai://external-favorite/organize",
            parameters = mapOf(
                "externalId" to item.external_id,
                "articleId" to item.article_id?.toString().orEmpty(),
                "title" to input.title,
                "body" to input.toPromptContent(),
            ),
        )
    }

    private fun logAiResponse(
        httpLogger: FavoriteSyncHttpLogger,
        taskId: Long?,
        item: External_favorite_item,
        analysis: ExternalFavoriteAiAnalysis,
    ) {
        httpLogger.logResponse(
            taskId = taskId,
            label = "external_favorite_ai",
            statusCode = 200,
            headers = mapOf("externalId" to item.external_id),
            body = """{"title":${jsonString(analysis.title)},"summary":${jsonString(analysis.summary)},"markdown":${jsonString(analysis.markdown)}}""",
        )
    }

    private fun logAiFailure(
        httpLogger: FavoriteSyncHttpLogger,
        taskId: Long?,
        item: External_favorite_item,
        error: Throwable,
    ) {
        httpLogger.logResponse(
            taskId = taskId,
            label = "external_favorite_ai",
            statusCode = 599,
            headers = mapOf("externalId" to item.external_id),
            body = error.message.orEmpty().ifBlank { "External favorite AI organization failed." },
        )
    }

    private fun jsonString(value: String): String =
        JsonPrimitive(value).toString()

    private suspend fun ExternalFavoriteAiInput.withSupplementIfNeeded(
        item: External_favorite_item,
        httpLogger: FavoriteSyncHttpLogger,
        taskId: Long?,
    ): ExternalFavoriteAiInput {
        val resolver = supplementResolver ?: return this
        if (hasEnoughExistingFavoriteText(text)) return this
        val supplement = runCatching { resolver.resolve(item, this, httpLogger, taskId) }.getOrNull()
            ?: return this
        val supplementText = supplement.text.trim()
        if (supplementText.isBlank()) return this
        return copy(
            supplementUrl = supplement.url.trim().takeIf { it.isNotBlank() },
            supplementTitle = supplement.title?.trim()?.takeIf { it.isNotBlank() },
            supplementText = supplementText,
            supplementSourceType = supplement.sourceType.trim().takeIf { it.isNotBlank() },
        )
    }

    private fun External_favorite_item.toAiInput(): ExternalFavoriteAiInput {
        val metadata = normalizedJsonObject()
        val textParts = listOf(
            text.trim(),
            metadata?.stringValue("url_title")?.trim(),
            metadata?.stringValue("url_description")?.trim(),
        )
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .distinct()
        return ExternalFavoriteAiInput(
            provider = provider,
            title = title.trim(),
            text = textParts.joinToString("\n\n"),
            authorName = author_name.trim(),
            sourceCreatedAt = source_created_at,
            canonicalUrl = canonical_url?.trim().orEmpty(),
        )
    }

    private fun External_favorite_item.normalizedJsonObject(): JsonObject? =
        runCatching { json.parseToJsonElement(normalized_json).jsonObject }.getOrNull()

    private fun JsonObject.stringValue(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun hasEnoughExistingFavoriteText(value: String): Boolean =
        effectiveFavoriteText(value).length >= MIN_EXISTING_TEXT_CHARS

    private fun effectiveFavoriteText(value: String): String =
        value.lines()
            .map { line ->
                line.trim()
                    .removePrefix("链接：")
                    .removePrefix("链接:")
                    .removePrefix("Link:")
                    .removePrefix("link:")
                    .trim()
            }
            .filterNot { line -> line.matches(Regex("""^(?:https?://|t\.co/)\S+$""", RegexOption.IGNORE_CASE)) }
            .joinToString("")

    private fun ExternalFavoriteAiInput.toPromptContent(): String =
        """
        整理要求：
        - 直接输出内容本身，不要用第三方视角介绍这篇内容。
        - 禁止使用“本文介绍了”“本文整理了”“这篇文章讨论了”等套话。
        - 不要写“谁分享了关于……”这类来源说明，除非作者身份本身就是内容重点。
        - 优先写结论、方法、步骤、清单、注意事项，让读者直接获得信息。

        标题：${title.ifBlank { "X 收藏" }}
        作者：${authorName.ifBlank { "未知" }}
        时间：${sourceCreatedAt?.let { Instant.fromEpochMilliseconds(it).toString() } ?: "未知"}
        链接：$canonicalUrl

        原文：
        ${text.ifBlank { "（无正文）" }}

        ${supplementPromptSection()}
        """.trimIndent()

    private fun ExternalFavoriteAiInput.supplementPromptSection(): String =
        supplementText?.trim()?.takeIf { it.isNotBlank() }?.let { body ->
            """
            补充来源：
            - 类型：${supplementSourceType.orEmpty().ifBlank { "web" }}
            - 标题：${supplementTitle.orEmpty().ifBlank { "未知" }}
            - 链接：${supplementUrl.orEmpty().ifBlank { "未知" }}

            补充正文：
            $body
            """.trimIndent()
        }.orEmpty()

    private fun ExternalFavoriteAiInput.toArticleMarkdown(aiTitle: String, aiMarkdown: String): String {
        val author = authorName.ifBlank { "未知" }
        val created = sourceCreatedAt?.let { Instant.fromEpochMilliseconds(it).toString() } ?: "未知"
        val body = text.ifBlank { "（无正文）" }
        val organized = aiMarkdown.trim().ifBlank { "暂无整理结果。" }
        return """
            # ${aiTitle.ifBlank { title.ifBlank { "X 收藏" } }}

            ## 原文

            - 作者：$author
            - 时间：$created
            - 链接：$canonicalUrl

            $body

            ${supplementMarkdownSection()}

            ## AI 整理

            $organized
        """.trimIndent()
    }

    private fun ExternalFavoriteAiInput.supplementMarkdownSection(): String =
        supplementText?.trim()?.takeIf { it.isNotBlank() }?.let { body ->
            """
            ## 补充来源

            - 类型：${supplementSourceType.orEmpty().ifBlank { "web" }}
            - 标题：${supplementTitle.orEmpty().ifBlank { "未知" }}
            - 链接：${supplementUrl.orEmpty().ifBlank { "未知" }}

            $body
            """.trimIndent()
        }.orEmpty()

    private fun parseAiAnalysis(response: String): ExternalFavoriteAiAnalysis {
        val trimmed = response.trim()
        val parsed = runCatching {
            val root = json.parseToJsonElement(trimmed).jsonObject
            ExternalFavoriteAiAnalysis(
                title = root["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                summary = root["summary"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                markdown = root["markdown"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.getOrNull()
        if (parsed != null && (parsed.title.isNotBlank() || parsed.summary.isNotBlank() || parsed.markdown.isNotBlank())) {
            return parsed
        }
        return ExternalFavoriteAiAnalysis(title = "", summary = trimmed.take(240), markdown = trimmed)
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        const val EXTERNAL_FAVORITE_SYSTEM_PROMPT =
            "你是内容整理助手。请直接基于用户收藏的 X/Twitter 原文和可用补充来源整理内容。" +
                "直接输出内容本身，不要用第三方视角，不要把内容写成对文章或帖子的介绍。" +
                "禁止使用“本文介绍了”“本文整理了”“这篇文章讨论了”等套话，不要写“谁分享了关于……”这类来源说明。" +
                "title 使用信息型标题，summary 直接概括关键结论，markdown 写成可直接阅读的结构化正文。" +
                "只输出 JSON，字段为 title、summary、markdown。"

        const val MIN_EXISTING_TEXT_CHARS = 20
    }
}
