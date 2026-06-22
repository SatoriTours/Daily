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

    fun repairImportedXLongArticlePendingArticles(limit: Long = 50): Int {
        var repaired = 0
        itemRepo.importedXLongArticlePending(limit).forEach { item ->
            val articleId = item.article_id ?: return@forEach
            val article = articleRepo.getById(articleId) ?: return@forEach
            val url = item.canonical_url?.trim().orEmpty()
            if (!isXArticleUrl(url) || item.text.isBlank()) return@forEach
            articleRepo.update(
                id = article.id,
                title = item.importTitle(),
                aiTitle = article.ai_title,
                aiContent = item.importBody(url),
                aiMarkdownContent = item.toDeterministicMarkdown(url),
                url = article.url,
                isFavorite = article.is_favorite ?: 0L,
                comment = article.comment,
                status = "completed",
                coverImage = article.cover_image,
                coverImageUrl = item.coverImageUrlFromNormalizedJson() ?: article.cover_image_url,
                pubDate = article.pub_date ?: item.source_created_at ?: item.favorited_at,
            )
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
            val refreshLinkedExternalFavoriteContent = item.article_id != null && existingArticle?.id == item.article_id
            val importedStatus = externalFavoriteImportedArticleStatus(item, url)
            val shouldOrganizeArticle = importedStatus == "completed" &&
                (existingArticle == null || articleRepo.isExternalFavoritePlaceholder(existingArticle))
            val article = articleRepo.saveExternalFavoriteArticle(
                title = item.importTitle(),
                url = url,
                summary = item.importBody(url),
                markdown = item.importMarkdown(url, importedStatus),
                pubDate = item.source_created_at ?: item.favorited_at,
                coverImageUrl = item.coverImageUrlFromNormalizedJson(),
                status = importedStatus,
                refreshLinkedExternalFavoriteContent = refreshLinkedExternalFavoriteContent,
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

    private fun externalFavoriteImportedArticleStatus(item: External_favorite_item, url: String): String =
        if (item.provider == ExternalFavoriteProvider.X.id && !isXStatusLikeUrl(url) && !isXArticleUrl(url)) {
            "pending"
        } else {
            "completed"
        }

    private fun External_favorite_item.importMarkdown(url: String, status: String): String =
        if (status == "pending") "" else toDeterministicMarkdown(url)

    private fun External_favorite_item.toDeterministicMarkdown(url: String): String {
        val author = author_name.trim().ifBlank { "未知" }
        val created = source_created_at?.let { Instant.fromEpochMilliseconds(it).toString() } ?: "未知"
        val body = importBody(url).ifBlank { "（无正文）" }
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

    private fun External_favorite_item.importBody(url: String): String {
        val metadata = normalizedJsonObject()
        val directText = text.trim().takeUnless { isXArticleUrl(url) && isOnlyLinkText(it) }
        return listOf(
            metadata?.stringValue("note_text"),
            directText,
            metadata?.stringValue("url_title"),
            metadata?.stringValue("url_description"),
        )
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
            .joinToString("\n\n")
    }

    private fun External_favorite_item.coverImageUrlFromNormalizedJson(): String? =
        runCatching {
            val root = normalizedJsonObject() ?: return@runCatching null
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
        normalizedJsonObject()?.stringValue(key)

    private fun External_favorite_item.normalizedJsonObject(): JsonObject? =
        runCatching { json.parseToJsonElement(normalized_json).jsonObject }.getOrNull()

    private fun isOnlyLinkText(value: String): Boolean {
        val link = value.trim()
            .removePrefix("链接：")
            .removePrefix("链接:")
            .removePrefix("Link:")
            .removePrefix("link:")
            .trim()
        return Regex("""^https?://(?:t\.co/[A-Za-z0-9_%-]+|(?:mobile\.)?(?:twitter\.com|x\.com)/i/article/\d+)/?$""", RegexOption.IGNORE_CASE)
            .matches(link)
    }

    private fun JsonObject.stringValue(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
