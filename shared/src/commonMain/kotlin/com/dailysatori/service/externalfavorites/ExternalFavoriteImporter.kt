package com.dailysatori.service.externalfavorites

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.shared.db.External_favorite_item
import kotlinx.datetime.Instant

class ExternalFavoriteImporter(
    private val itemRepo: ExternalFavoriteItemRepository,
    private val articleRepo: ArticleRepository,
) {
    fun importPending(limit: Long = 50): Int =
        importItems(itemRepo.pendingImport(limit))

    fun importPendingForSource(sourceId: Long, limit: Long = 50): Int =
        importItems(itemRepo.pendingImportBySource(sourceId, limit))

    private fun importItems(items: List<External_favorite_item>): Int {
        var importedCount = 0
        items.forEach { item ->
            val url = item.canonical_url?.trim().orEmpty()
            if (url.isBlank()) {
                itemRepo.markImportFailed(item.id, "missing_url", "External favorite item has no canonical URL.")
                return@forEach
            }

            val existedBeforeImport = articleRepo.getByUrl(url) != null
            val article = articleRepo.saveExternalFavoriteArticle(
                title = item.importTitle(),
                url = url,
                summary = item.text.trim(),
                markdown = item.toDeterministicMarkdown(url),
                pubDate = item.source_created_at ?: item.favorited_at,
            )
            itemRepo.markImported(item.id, article.id, duplicateLinked = existedBeforeImport)
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
        return """
            # X 收藏

            ## 原文

            - 作者：$author
            - 时间：$created
            - 链接：$url

            $body

            ## AI 整理

            待整理
        """.trimIndent()
    }
}
