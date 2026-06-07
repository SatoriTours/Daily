package com.dailysatori.service.externalfavorites

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.ExternalFavoriteItemRepository

class ExternalFavoriteAiOrganizer(
    private val itemRepo: ExternalFavoriteItemRepository,
    private val articleRepo: ArticleRepository,
) {
    fun organizePending(limit: Long = 10): Int {
        var processed = 0
        itemRepo.pendingAi(limit).forEach { item ->
            val article = item.article_id?.let(articleRepo::getById)
            if (article == null) {
                itemRepo.markAiState(item.id, ExternalItemAiStatus.failed.name, "missing_article", "Linked article was not found.")
            } else {
                itemRepo.markAiState(item.id, ExternalItemAiStatus.not_needed.name)
            }
            processed += 1
        }
        return processed
    }
}
