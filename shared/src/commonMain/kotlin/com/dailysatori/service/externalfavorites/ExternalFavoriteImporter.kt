package com.dailysatori.service.externalfavorites

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.shared.db.External_favorite_item
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ExternalFavoriteImporter(
    private val itemRepo: ExternalFavoriteItemRepository,
    private val articleRepo: ArticleRepository,
) {
    fun importPending(limit: Long = 50): Int =
        importItems(itemRepo.pendingImport(limit))

    fun importPendingForSource(sourceId: Long, limit: Long = 50): Int =
        importItems(itemRepo.pendingImportBySource(sourceId, limit))

    fun repairImportedArticleCovers(limit: Long = 50): Int {
        var repaired = 0
        itemRepo.importedWithMissingArticleCover(limit).forEach { item ->
            val articleId = item.article_id ?: return@forEach
            val coverImageUrl = item.coverImageUrlFromNormalizedJson() ?: return@forEach
            if (articleRepo.fillCoverImageUrlIfMissing(articleId, coverImageUrl)) {
                repaired += 1
            }
        }
        return repaired
    }

    fun repairImportedPlaceholderArticles(limit: Long = 50): Int {
        var repaired = 0
        itemRepo.importedWithPlaceholderArticle(limit).forEach { item ->
            val articleId = item.article_id ?: return@forEach
            articleRepo.updateStatus(articleId, "completed")
            itemRepo.markAiState(item.id, ExternalItemAiStatus.pending.name)
            repaired += 1
        }
        return repaired
    }

    private fun importItems(items: List<External_favorite_item>): Int {
        var importedCount = 0
        items.forEach { item ->
            val url = item.canonical_url?.trim().orEmpty()
            if (url.isBlank()) {
                itemRepo.markImportFailed(item.id, "missing_url", "External favorite item has no canonical URL.")
                return@forEach
            }

            val existingArticle = articleRepo.getByUrl(url)
            val existedBeforeImport = existingArticle != null
            val shouldOrganizeArticle = existingArticle == null || articleRepo.isExternalFavoritePlaceholder(existingArticle)
            val article = articleRepo.saveExternalFavoriteArticle(
                title = item.importTitle(),
                url = url,
                summary = item.text.trim(),
                markdown = item.toDeterministicMarkdown(url),
                pubDate = item.source_created_at ?: item.favorited_at,
                coverImageUrl = item.coverImageUrlFromNormalizedJson(),
            )
            itemRepo.markImported(
                itemId = item.id,
                articleId = article.id,
                duplicateLinked = existedBeforeImport,
                aiStatus = if (shouldOrganizeArticle) ExternalItemAiStatus.pending else ExternalItemAiStatus.not_needed,
            )
            importedCount += 1
        }
        return importedCount
    }

    private fun External_favorite_item.importTitle(): String =
        title.trim().ifBlank { "X 收藏" }

    private fun External_favorite_item.toDeterministicMarkdown(url: String): String {
        val author = author_name.trim().ifBlank { "未知" }
        val created = source_created_at?.let { Instant.fromEpochMilliseconds(it).toString() } ?: "未知"
        val body = text.trim().ifBlank { "（无正文）" }
        val tweetUrl = normalizedJsonString("canonical_tweet_url")
        val sourceLinks = if (!tweetUrl.isNullOrBlank() && tweetUrl != url) {
            listOf("- X 链接：$tweetUrl", "- 文章链接：$url")
        } else {
            listOf("- 链接：$url")
        }.joinToString("\n            ")
        return """
            # X 收藏

            ## 原文

            - 作者：$author
            - 时间：$created
            $sourceLinks

            $body

            ## AI 整理

            待整理
        """.trimIndent()
    }

    private fun External_favorite_item.coverImageUrlFromNormalizedJson(): String? =
        runCatching {
            val root = json.parseToJsonElement(normalized_json).jsonObject
            (root["url_images"] as? JsonArray)
                ?.firstNotNullOfOrNull { it.jsonPrimitive.contentOrNull?.takeIf { value -> value.isNotBlank() } }
                ?: (root["media"] as? JsonArray)
                    ?.mapNotNull { it as? JsonObject }
                    ?.firstNotNullOfOrNull { media ->
                        media.stringValue("url") ?: media.stringValue("preview_image_url")
                    }
                    ?.takeIf { it.isNotBlank() }
        }.getOrNull()

    private fun External_favorite_item.normalizedJsonString(key: String): String? =
        runCatching {
            json.parseToJsonElement(normalized_json).jsonObject.stringValue(key)
        }.getOrNull()

    private fun JsonObject.stringValue(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
