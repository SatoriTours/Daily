package com.dailysatori.service.remotenews

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.needsLocalAiReprocessingForChineseOutput
import com.dailysatori.data.repository.sourceContentHash
import com.dailysatori.shared.db.Article

data class RemoteArticleFavoriteResult(
    val localArticle: Article?,
    val isFavorite: Boolean,
    val needsReprocessing: Boolean = false,
)

class RemoteArticleFavoriteService(
    private val articleRepo: ArticleRepository,
    private val syncRepo: RemoteArticleSyncRepository? = null,
) {
    suspend fun toggleFavorite(article: RemoteArticle, localId: Long?): RemoteArticleFavoriteResult {
        if (localId != null) {
            articleRepo.toggleFavorite(localId)
            val updated = articleRepo.getById(localId)
            val isFavorite = updated?.is_favorite == 1L
            val sourceHash = article.sourceContentHash()
            val requiresProcessing = article.needsLocalAiReprocessingForChineseOutput()
            var mapping = syncRepo?.findByArticleId(localId)
            if (isFavorite) {
                syncRepo?.markFavorited(localId)
                if (mapping != null && mapping.source_content_hash.isBlank()) {
                    syncRepo?.recordSourceSnapshot(localId, sourceHash, requiresProcessing)
                    mapping = syncRepo?.findByArticleId(localId)
                }
            }
            val targetSourceHash = mapping?.source_content_hash?.takeIf(String::isNotBlank) ?: sourceHash
            return RemoteArticleFavoriteResult(
                localArticle = updated,
                isFavorite = isFavorite,
                needsReprocessing = isFavorite && (
                    mapping?.let { it.processed_content_hash != targetSourceHash } ?: requiresProcessing
                ),
            )
        }

        val saved = articleRepo.saveRemoteArticleAsFavorite(article)
        saved?.id?.let { syncRepo?.markFavorited(it) }
        return RemoteArticleFavoriteResult(
            localArticle = saved,
            isFavorite = saved?.is_favorite == 1L,
            needsReprocessing = saved?.id != null && article.needsLocalAiReprocessingForChineseOutput(),
        )
    }
}
