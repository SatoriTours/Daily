package com.dailysatori.ui.feature.remotenews

internal fun RemoteNewsState.withRemoteNewsPageLoadStarted(
    refresh: Boolean,
    append: Boolean,
): RemoteNewsState = copy(
    isLoading = !refresh && !append,
    isRefreshing = refresh,
    isLoadingMore = append,
    error = null,
    loadMoreError = null,
)

internal fun RemoteNewsState.withRemoteNewsPageLoadFinished(refresh: Boolean): RemoteNewsState = copy(
    isLoading = false,
    isRefreshing = false,
    isLoadingMore = false,
    refreshCompletedToken = if (refresh) refreshCompletedToken + 1 else refreshCompletedToken,
)

internal fun RemoteNewsState.withRemoteNewsPageLoadFailure(
    message: String,
    append: Boolean,
): RemoteNewsState = copy(
    error = if (append) error else message,
    loadMoreError = if (append) message else null,
    isLoading = false,
    isRefreshing = false,
    isLoadingMore = false,
)
