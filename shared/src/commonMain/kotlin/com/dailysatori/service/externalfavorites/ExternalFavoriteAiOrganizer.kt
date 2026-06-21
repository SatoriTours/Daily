package com.dailysatori.service.externalfavorites

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.shared.db.External_favorite_item
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
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
    private val generateAnalysis: (suspend (ExternalFavoriteAiInput) -> ExternalFavoriteAiAnalysis)? = null,
) {
    suspend fun organizePending(limit: Long = 10, includeFailed: Boolean = false): Int {
        return organizeItems(if (includeFailed) itemRepo.retryableAi(limit) else itemRepo.pendingAi(limit))
    }

    suspend fun organizePendingForSource(sourceId: Long, limit: Long = 10, includeFailed: Boolean = false): Int {
        return organizeItems(if (includeFailed) itemRepo.retryableAiBySource(sourceId, limit) else itemRepo.pendingAiBySource(sourceId, limit))
    }

    private suspend fun organizeItems(items: List<External_favorite_item>): Int {
        var processed = 0
        items.forEach { item ->
            val article = item.article_id?.let(articleRepo::getById)
            if (article == null) {
                itemRepo.markAiState(item.id, ExternalItemAiStatus.failed.name, "missing_article", "Linked article was not found.")
                processed += 1
                return@forEach
            }

            val input = item.toAiInput()
            val analysis = runCatching { generateAnalysis?.invoke(input) ?: generateWithAi(input) }
                .getOrElse { error ->
                    itemRepo.markAiState(
                        item.id,
                        ExternalItemAiStatus.failed.name,
                        "ai_failed",
                        error.message.orEmpty().ifBlank { "External favorite AI organization failed." },
                    )
                    processed += 1
                    return@forEach
                }

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

    private fun External_favorite_item.toAiInput(): ExternalFavoriteAiInput =
        ExternalFavoriteAiInput(
            provider = provider,
            title = title.trim(),
            text = text.trim(),
            authorName = author_name.trim(),
            sourceCreatedAt = source_created_at,
            canonicalUrl = canonical_url?.trim().orEmpty(),
        )

    private fun ExternalFavoriteAiInput.toPromptContent(): String =
        """
        标题：${title.ifBlank { "X 收藏" }}
        作者：${authorName.ifBlank { "未知" }}
        时间：${sourceCreatedAt?.let { Instant.fromEpochMilliseconds(it).toString() } ?: "未知"}
        链接：$canonicalUrl

        原文：
        ${text.ifBlank { "（无正文）" }}
        """.trimIndent()

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

            ## AI 整理

            $organized
        """.trimIndent()
    }

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
            "你是内容整理助手。请直接基于用户收藏的 X/Twitter 原文整理内容，不要抓取网页。" +
                "只输出 JSON，字段为 title、summary、markdown。title 是简洁中文标题，summary 是 1-3 句摘要，markdown 是结构化正文。"
    }
}
