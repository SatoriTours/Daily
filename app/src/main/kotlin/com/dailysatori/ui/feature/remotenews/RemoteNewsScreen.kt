package com.dailysatori.ui.feature.remotenews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.service.remotenews.RemoteFeed
import com.dailysatori.ui.component.card.CustomCard
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.news.NewsStateMessage
import com.dailysatori.ui.component.news.newsListContentPadding
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.crayfishnews.CrayfishNewsScreen
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun RemoteNewsScreen(onArticleClick: (Long) -> Unit = {}) {
    val viewModel: RemoteNewsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.localArticleNavigationTarget) {
        val articleId = state.localArticleNavigationTarget ?: return@LaunchedEffect
        onArticleClick(articleId)
        viewModel.clearLocalArticleNavigationTarget()
    }

    when {
        state.mode == RemoteNewsMode.CRAYFISH -> CrayfishNewsScreen(onBackToRemoteNews = { viewModel.switchMode(RemoteNewsMode.DIGESTS) })
        state.selectedArticle != null -> RemoteArticleDetailScreen(
            article = state.selectedArticle!!,
            onBack = viewModel::closeArticle,
            isFavorite = state.selectedArticleIsFavorite,
            showFavoriteAction = true,
            onFavoriteClick = viewModel::toggleSelectedArticleFavorite,
        )
        state.selectedDigest != null -> RemoteDigestDetailScreen(state.selectedDigest!!, viewModel::closeDigest, viewModel::openArticle)
        state.detailError != null -> RemoteNewsDetailError(state.detailError.orEmpty(), viewModel::closeDetailError)
        else -> RemoteNewsListScreen(state, viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteNewsListScreen(state: RemoteNewsState, viewModel: RemoteNewsViewModel) {
    LaunchedEffect(Unit) { viewModel.loadInitial() }

    AppScaffold(title = state.mode.title, showBack = false, actions = { RemoteNewsMenu(state.mode, viewModel) }) { modifier ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = modifier.fillMaxSize(),
        ) {
            RemoteNewsListContent(state, viewModel)
        }
    }
}

@Composable
private fun RemoteNewsMenu(mode: RemoteNewsMode, viewModel: RemoteNewsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (mode != RemoteNewsMode.DIGESTS) MenuItem("查看总结", Icons.Default.Article) { viewModel.switchMode(RemoteNewsMode.DIGESTS); expanded = false }
            if (mode != RemoteNewsMode.ARTICLES) MenuItem("查看文章", Icons.Default.Article) { viewModel.switchMode(RemoteNewsMode.ARTICLES); expanded = false }
            if (mode != RemoteNewsMode.FEEDS) MenuItem("查看信息源", Icons.Default.RssFeed) { viewModel.switchMode(RemoteNewsMode.FEEDS); expanded = false }
            if (mode != RemoteNewsMode.CRAYFISH) MenuItem("小龙虾新闻", Icons.Default.Article) { viewModel.switchMode(RemoteNewsMode.CRAYFISH); expanded = false }
            MenuItem("刷新", Icons.Default.Refresh) { viewModel.refresh(); expanded = false }
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)
}

@Composable
private fun RemoteNewsListContent(state: RemoteNewsState, viewModel: RemoteNewsViewModel) {
    val listState = rememberLazyListState()
    val itemsCount = when (state.mode) {
        RemoteNewsMode.DIGESTS -> state.digests.size
        RemoteNewsMode.ARTICLES -> state.articles.size
        RemoteNewsMode.FEEDS -> state.feeds.size
        RemoteNewsMode.CRAYFISH -> 0
    }
    LoadMoreWhenAtEnd(listState, itemsCount, viewModel::loadMore)
    if (state.refreshCompletedToken > 0) {
        LaunchedEffect(state.refreshCompletedToken) {
            listState.scrollToItem(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && itemsCount == 0 -> LoadingIndicator()
            state.error != null && itemsCount == 0 -> NewsStateMessage(
                title = state.error,
                actionLabel = "重试",
                onAction = viewModel::refresh,
                isError = true,
            )
            itemsCount == 0 -> NewsStateMessage(
                icon = Icons.Default.Article,
                title = "暂无内容",
                subtitle = "远程新闻暂时没有可显示的数据",
            )
            else -> RemoteNewsLazyList(state, listState, viewModel)
        }
    }
}

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
private fun RemoteNewsDetailError(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onBack) { Text("返回") }
    }
}

@Composable
private fun RemoteNewsLazyList(state: RemoteNewsState, listState: LazyListState, viewModel: RemoteNewsViewModel) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = newsListContentPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        when (state.mode) {
            RemoteNewsMode.DIGESTS -> items(state.digests, key = { it.id }) { digest ->
                CustomCard(onClick = { viewModel.openDigest(digest.id) }) {
                    DigestBody(digest = digest, modifier = Modifier.padding(Spacing.m))
                }
            }
            RemoteNewsMode.ARTICLES -> items(state.articles, key = { it.id }) { RemoteArticleSummaryCard(it) { viewModel.openArticle(it) } }
            RemoteNewsMode.FEEDS -> items(state.feeds, key = { it.id }) { FeedCard(it) }
            RemoteNewsMode.CRAYFISH -> Unit
        }
        if (state.isLoadingMore) item(key = "remote-news-loading-more") {
            Box(modifier = Modifier.fillMaxWidth().padding(Spacing.s), contentAlignment = Alignment.Center) {
                Text("加载中...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.loadMoreError != null) item(key = "remote-news-load-more-error") {
            Text(state.loadMoreError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Spacing.s))
        }
    }
}

@Composable
private fun FeedCard(feed: RemoteFeed) {
    CustomCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(96.dp)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Spacing.m, top = Spacing.m, bottom = Spacing.m, end = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(feed.name.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(feed.category ?: feed.feedType.orEmpty(), modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("健康 ${feed.healthScore?.let { String.format("%.0f", it) } ?: "0"}", modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (feed.isEnabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            if (feed.isEnabled) "启用" else "停用",
                            modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                if (!feed.lastFetchedAt.isNullOrBlank() || !feed.nextFetchAt.isNullOrBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(top = Spacing.xxs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        feed.lastFetchedAt?.let { Text("上次抓取: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        feed.nextFetchAt?.let { Text("下次: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
