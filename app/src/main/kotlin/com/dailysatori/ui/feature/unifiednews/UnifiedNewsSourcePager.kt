package com.dailysatori.ui.feature.unifiednews

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull

internal fun unifiedNewsSourcePages(state: UnifiedNewsState): List<UnifiedNewsSourceSelection> = buildList {
    add(UnifiedNewsSourceSelection.Summary)
    state.remoteSources.forEach { add(UnifiedNewsSourceSelection.RemoteSource(it.id, it.name)) }
    state.externalFavoriteSources.forEach { add(UnifiedNewsSourceSelection.ExternalFavoriteSource(it.id, it.name)) }
    add(UnifiedNewsSourceSelection.LocalArticles)
}

internal fun unifiedNewsSourcePageKey(selection: UnifiedNewsSourceSelection): String = when (selection) {
    UnifiedNewsSourceSelection.Summary -> "news-summary"
    is UnifiedNewsSourceSelection.RemoteSource -> "news-remote-${selection.id}"
    is UnifiedNewsSourceSelection.ExternalFavoriteSource -> "news-favorite-${selection.id}"
    UnifiedNewsSourceSelection.LocalArticles -> "news-local"
}

internal fun unifiedNewsSourcePageIndex(
    pages: List<UnifiedNewsSourceSelection>,
    selection: UnifiedNewsSourceSelection,
): Int = pages.indexOfFirst { unifiedNewsSourcePageKey(it) == unifiedNewsSourcePageKey(selection) }.coerceAtLeast(0)

// Pager may retain this callback while its observed page count has already changed.
internal fun unifiedNewsSourcePagerKey(pages: State<List<UnifiedNewsSourceSelection>>): (Int) -> String = { index ->
    pages.value.getOrNull(index)?.let(::unifiedNewsSourcePageKey) ?: "news-unavailable-$index"
}

@Composable
internal fun rememberUnifiedNewsSourcePager(
    pages: State<List<UnifiedNewsSourceSelection>>,
    selection: UnifiedNewsSourceSelection,
    onSelected: (UnifiedNewsSourceSelection) -> Unit,
): PagerState {
    val pagerState = rememberPagerState(initialPage = unifiedNewsSourcePageIndex(pages.value, selection)) { pages.value.size }
    val currentSelection by rememberUpdatedState(selection)
    val select by rememberUpdatedState(onSelected)
    LaunchedEffect(pages.value.map(::unifiedNewsSourcePageKey)) {
        // Resolve by source identity when sources are added, removed, or reordered.
        pagerState.scrollToPage(unifiedNewsSourcePageIndex(pages.value, currentSelection))
        snapshotFlow { pagerState.settledPage.takeUnless { pagerState.isScrollInProgress } }
            .filterNotNull()
            .collect { index ->
                val settled = pages.value.getOrNull(index) ?: return@collect
                if (unifiedNewsSourcePageKey(settled) != unifiedNewsSourcePageKey(currentSelection)) select(settled)
            }
    }
    return pagerState
}

internal fun UnifiedNewsViewModel.selectNewsPage(selection: UnifiedNewsSourceSelection) {
    when (selection) {
        UnifiedNewsSourceSelection.Summary -> selectSummarySource()
        is UnifiedNewsSourceSelection.RemoteSource -> selectRemoteSource(UnifiedNewsRemoteSourceOption(selection.id, selection.name))
        is UnifiedNewsSourceSelection.ExternalFavoriteSource -> selectExternalFavoriteSource(UnifiedNewsExternalFavoriteSourceOption(selection.id, selection.name))
        UnifiedNewsSourceSelection.LocalArticles -> selectLocalArticlesSource()
    }
}

internal fun unifiedNewsSourcePageState(
    state: UnifiedNewsState,
    selection: UnifiedNewsSourceSelection,
    scrollRequest: Int,
    refreshRequest: Int,
): UnifiedNewsState {
    val selected = unifiedNewsSourcePageKey(selection) == unifiedNewsSourcePageKey(state.sourceSelection)
    return state.copy(
        searchQuery = state.searchQuery.takeIf { selected }.orEmpty(),
        sourceArticlesError = state.sourceArticlesError.takeIf { selected },
        scrollToTopRequestKey = scrollRequest,
        localArticleRefreshRequestKey = refreshRequest,
    )
}
