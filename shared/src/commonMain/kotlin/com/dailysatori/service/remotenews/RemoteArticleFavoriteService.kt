package com.dailysatori.service.remotenews

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.needsLocalAiReprocessingForChineseOutput
import com.dailysatori.shared.db.Article

data class RemoteArticleFavoriteResult(
    val localArticle: Article?,
    val isFavorite: Boolean,
    val needsReprocessing: Boolean = false,
)

class RemoteArticleFavoriteService(
    private val articleRepo: ArticleRepository,
) {
    suspend fun toggleFavorite(article: RemoteArticle, localId: Long?): RemoteArticleFavoriteResult {
        if (localId != null) {
            articleRepo.toggleFavorite(localId)
            val updated = articleRepo.getById(localId)
            return RemoteArticleFavoriteResult(updated, updated?.is_favorite == 1L)
        }

        val saved = articleRepo.saveRemoteArticleAsFavorite(article)
        return RemoteArticleFavoriteResult(
            localArticle = saved,
            isFavorite = saved?.is_favorite == 1L,
            needsReprocessing = saved?.id != null && article.needsLocalAiReprocessingForChineseOutput(),
        )
    }
}
