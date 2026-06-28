package com.dailysatori.ui.feature.unifiednews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.unifiednews.dailyUnifiedNewsWindowFor
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.news.NewsStateMessage
import com.dailysatori.ui.component.news.NewsStatusBanner
import com.dailysatori.ui.component.news.newsCompactListContentPadding
import com.dailysatori.ui.feature.remotenews.RemoteArticleSummaryCard
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
internal fun UnifiedNewsSourceArticleContent(
    state: UnifiedNewsState,
    selection: UnifiedNewsSourceSelection.RemoteSource,
    viewModel: UnifiedNewsViewModel,
) {
    val cacheKey = sourceArticleCacheKey(selection.id, dailyUnifiedNewsWindowFor().summaryDate)
    val articles = state.sourceArticlesByCacheKey[cacheKey].orEmpty()
    val isLoading = state.sourceArticlesLoadingSourceId == selection.id
    when {
        isLoading && articles.isEmpty() -> LoadingIndicator()
        state.sourceArticlesError != null && articles.isEmpty() -> UnifiedNewsSourceArticleMessage(
            title = state.sourceArticlesError,
            actionLabel = "同步",
            onAction = { viewModel.syncRemoteSource(selection.id) },
        )
        articles.isEmpty() -> UnifiedNewsSourceArticleMessage(
            title = "这个来源今天还没有新闻",
            actionLabel = "同步",
            onAction = { viewModel.syncRemoteSource(selection.id) },
        )
        else -> UnifiedNewsSourceArticleList(
            selection = selection,
            articles = articles,
            isLoading = isLoading,
            sourceArticlesError = state.sourceArticlesError,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun UnifiedNewsSourceArticleList(
    selection: UnifiedNewsSourceSelection.RemoteSource,
    articles: List<RemoteArticle>,
    isLoading: Boolean,
    sourceArticlesError: String?,
    viewModel: UnifiedNewsViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = newsCompactListContentPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item(key = "source-article-sync") {
            UnifiedNewsSourceArticleSyncBar(
                sourceName = selection.name,
                onSync = { viewModel.syncRemoteSource(selection.id) },
            )
        }
        if (sourceArticlesError != null) item(key = "source-article-error") {
            NewsStatusBanner(message = "刷新失败，正在显示上次结果：$sourceArticlesError")
        }
        items(articles, key = { it.id }) { article ->
            RemoteArticleSummaryCard(article) { viewModel.openSourceArticle(article) }
        }
        if (isLoading) item(key = "source-article-loading") {
            Box(modifier = Modifier.fillMaxWidth().padding(Spacing.s), contentAlignment = Alignment.Center) {
                Text("刷新中...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UnifiedNewsSourceArticleSyncBar(sourceName: String, onSync: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = sourceName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onSync) {
                Text("同步")
            }
        }
    }
}

@Composable
private fun UnifiedNewsSourceArticleMessage(title: String, actionLabel: String, onAction: () -> Unit) {
    NewsStateMessage(title = title, actionLabel = actionLabel, onAction = onAction)
}
