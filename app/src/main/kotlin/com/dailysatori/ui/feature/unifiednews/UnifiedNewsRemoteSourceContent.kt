package com.dailysatori.ui.feature.unifiednews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.news.NewsStateMessage
import com.dailysatori.ui.component.news.NewsStatusBanner
import com.dailysatori.ui.component.news.newsCompactListContentPadding
import com.dailysatori.ui.feature.remotenews.RemoteArticleSummaryCard
import com.dailysatori.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnifiedNewsSourceArticleContent(
    state: UnifiedNewsState,
    selection: UnifiedNewsSourceSelection.RemoteSource,
    viewModel: UnifiedNewsViewModel,
) {
    val sourceArticles = state.sourceArticlesBySourceId[selection.id].orEmpty()
    val articles = filteredUnifiedNewsRemoteArticles(sourceArticles, state.searchQuery)
    val isLoading = state.sourceArticlesLoadingSourceId == selection.id
    val isLoadingMore = state.sourceArticlesLoadingMoreSourceId == selection.id
    when {
        isLoading && sourceArticles.isEmpty() -> LoadingIndicator()
        state.sourceArticlesError != null && sourceArticles.isEmpty() -> UnifiedNewsSourceArticleMessage(
            title = state.sourceArticlesError,
            actionLabel = "同步",
            onAction = { viewModel.syncRemoteSource(selection.id) },
            isError = true,
        )
        sourceArticles.isEmpty() -> UnifiedNewsSourceArticleMessage(
            title = "这个来源暂时没有已同步文章",
            subtitle = "今天没有新文章时，稍后下拉刷新即可",
            actionLabel = "同步",
            onAction = { viewModel.syncRemoteSource(selection.id) },
        )
        articles.isEmpty() && state.searchQuery.isNotBlank() -> UnifiedNewsSourceArticleMessage(
            title = "这个来源没有匹配新闻",
            subtitle = "换个关键词或清除搜索后查看全部",
            actionLabel = "清除",
            onAction = { viewModel.search("") },
        )
        else -> UnifiedNewsSourceArticleList(
            articles = articles,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            sourceArticlesError = state.sourceArticlesError,
            canLoadMore = state.searchQuery.isBlank(),
            scrollToTopRequestKey = state.scrollToTopRequestKey,
            viewModel = viewModel,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun UnifiedNewsSourceArticleList(
    articles: List<RemoteArticle>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    sourceArticlesError: String?,
    canLoadMore: Boolean,
    scrollToTopRequestKey: Int,
    viewModel: UnifiedNewsViewModel,
) {
    val listState = rememberLazyListState()
    if (canLoadMore) LoadMoreWhenAtEnd(listState, articles.size, viewModel::loadMoreSelectedRemoteSource)
    LaunchedEffect(scrollToTopRequestKey) {
        if (scrollToTopRequestKey > 0 && articles.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    PullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        isRefreshing = isLoading,
        onRefresh = viewModel::refreshSelectedRemoteSource,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = newsCompactListContentPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (sourceArticlesError != null) item(key = "source-article-error") {
                NewsStatusBanner(message = "刷新失败，正在显示已同步文章：$sourceArticlesError")
            }
            items(articles, key = { it.id }) { article ->
                RemoteArticleSummaryCard(article) { viewModel.openSourceArticle(article) }
            }
            if (isLoadingMore) item(key = "source-article-loading-more") {
                Box(modifier = Modifier.fillMaxWidth().padding(Spacing.s), contentAlignment = Alignment.Center) {
                    Text("加载更多历史新闻...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal fun filteredUnifiedNewsRemoteArticles(articles: List<RemoteArticle>, query: String): List<RemoteArticle> {
    val keyword = query.trim().lowercase()
    if (keyword.isBlank()) return articles
    return articles.filter { article -> article.matchesUnifiedNewsRemoteArticleSearch(keyword) }
}

private fun RemoteArticle.matchesUnifiedNewsRemoteArticleSearch(keyword: String): Boolean =
    listOfNotNull(
        title,
        summary,
        content,
        url,
        feedName,
        domain,
        status,
    ).any { it.contains(keyword, ignoreCase = true) } ||
        viewpoints.any { it.contains(keyword, ignoreCase = true) }

@Composable
private fun LoadMoreWhenAtEnd(listState: LazyListState, itemCount: Int, onLoadMore: () -> Unit) {
    val shouldLoadMore by remember(listState, itemCount) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            itemCount > 0 && lastVisible >= itemCount - 1
        }
    }
    LaunchedEffect(shouldLoadMore, itemCount) {
        if (shouldLoadMore) onLoadMore()
    }
}

@Composable
private fun UnifiedNewsSourceArticleMessage(
    title: String,
    subtitle: String? = null,
    actionLabel: String,
    onAction: () -> Unit,
    isError: Boolean = false,
) {
    NewsStateMessage(title = title, subtitle = subtitle, actionLabel = actionLabel, onAction = onAction, isError = isError)
}
