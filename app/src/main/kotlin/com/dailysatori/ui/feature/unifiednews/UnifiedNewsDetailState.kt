package com.dailysatori.ui.feature.unifiednews

internal fun UnifiedNewsState.withUnifiedNewsSourceDetailCleared(): UnifiedNewsState = copy(
    selectedHotNews = null,
    selectedHotNewsArticle = null,
    hotNewsDetailLoading = false,
    hotNewsDetailFailed = false,
    selectedRemoteDigest = null,
    selectedRemoteArticle = null,
    selectedRemoteArticleLocalId = null,
    selectedRemoteArticleIsFavorite = false,
)

internal fun UnifiedNewsState.withUnifiedNewsDetailRequestStarted(
    target: UnifiedNewsNavigationTarget,
): UnifiedNewsState = withUnifiedNewsSourceDetailCleared().copy(
    navigationTarget = target,
    error = null,
)

internal fun UnifiedNewsState.withUnifiedNewsSourceDetailClosed(): UnifiedNewsState =
    withUnifiedNewsSourceDetailCleared().copy(
        navigationTarget = null,
        isLoading = false,
    )
