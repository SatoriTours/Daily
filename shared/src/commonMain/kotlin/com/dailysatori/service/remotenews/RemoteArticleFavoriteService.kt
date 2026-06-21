package com.dailysatori.service.remotenews

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.needsLocalAiReprocessingForChineseOutput
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.shared.db.Article

data class RemoteArticleFavoriteResult(
    val localArticle: Article?,
    val isFavorite: Boolean,
)

class RemoteArticleFavoriteService(
    private val articleRepo: ArticleRepository,
    private val webpageParserService: WebpageParserService,
) {
    suspend fun toggleFavorite(article: RemoteArticle, localId: Long?): RemoteArticleFavoriteResult {
        if (localId != null) {
            articleRepo.toggleFavorite(localId)
            val updated = articleRepo.getById(localId)
            return RemoteArticleFavoriteResult(updated, updated?.is_favorite == 1L)
        }

        val saved = articleRepo.saveRemoteArticleAsFavorite(article)
        if (saved?.id != null && article.needsLocalAiReprocessingForChineseOutput()) {
            webpageParserService.reprocessArticle(saved.id)
        }
        return RemoteArticleFavoriteResult(saved, saved?.is_favorite == 1L)
    }
}
