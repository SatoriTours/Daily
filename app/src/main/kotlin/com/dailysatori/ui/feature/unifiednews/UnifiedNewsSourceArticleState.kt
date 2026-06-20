package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.service.remotenews.RemoteArticle

internal fun shouldResetUnifiedNewsSourceSelection(
    selection: UnifiedNewsSourceSelection,
    remoteSources: List<UnifiedNewsRemoteSourceOption>,
    externalFavoriteSources: List<UnifiedNewsExternalFavoriteSourceOption> = emptyList(),
): Boolean = when (selection) {
    is UnifiedNewsSourceSelection.RemoteSource -> remoteSources.none { it.id == selection.id }
    is UnifiedNewsSourceSelection.ExternalFavoriteSource -> externalFavoriteSources.none { it.id == selection.id }
    else -> false
}

internal fun resolvedUnifiedNewsSourceSelection(
    selection: UnifiedNewsSourceSelection,
    remoteSources: List<UnifiedNewsRemoteSourceOption>,
    externalFavoriteSources: List<UnifiedNewsExternalFavoriteSourceOption> = emptyList(),
): UnifiedNewsSourceSelection = if (shouldResetUnifiedNewsSourceSelection(selection, remoteSources, externalFavoriteSources)) {
    UnifiedNewsSourceSelection.Summary
} else {
    selection
}

internal fun hasUnifiedNewsSourceArticlesCache(
    state: UnifiedNewsState,
    sourceId: Long,
    summaryDate: String,
): Boolean = state.sourceArticlesByCacheKey.containsKey(sourceArticleCacheKey(sourceId, summaryDate))

internal fun cachedUnifiedNewsSourceArticles(
    state: UnifiedNewsState,
    sourceId: Long,
    summaryDate: String,
): List<RemoteArticle> = state.sourceArticlesByCacheKey[sourceArticleCacheKey(sourceId, summaryDate)].orEmpty()

internal fun UnifiedNewsState.withUnifiedNewsSourceArticlesLoading(sourceId: Long): UnifiedNewsState = copy(
    sourceArticlesLoadingSourceId = sourceId,
    sourceArticlesError = null,
)

internal fun UnifiedNewsState.withUnifiedNewsSourceArticlesFailure(message: String): UnifiedNewsState = copy(
    sourceArticlesLoadingSourceId = null,
    sourceArticlesError = message,
)

internal fun UnifiedNewsState.withUnifiedNewsSourceArticlesLoaded(
    sourceId: Long,
    summaryDate: String,
    articles: List<RemoteArticle>,
): UnifiedNewsState = copy(
    sourceArticlesByCacheKey = sourceArticlesByCacheKey + (sourceArticleCacheKey(sourceId, summaryDate) to articles),
    sourceArticlesLoadingSourceId = null,
    sourceArticlesError = null,
)

internal fun UnifiedNewsState.withUnifiedNewsSourceArticleRequestInvalidated(): UnifiedNewsState = copy(
    sourceArticlesLoadingSourceId = null,
    sourceArticlesError = null,
)
