package com.dailysatori.ui.feature.crayfishnews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.service.crayfishnews.CrayfishNewsDetail
import com.dailysatori.ui.component.card.CustomCard
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.news.NewsStateMessage
import com.dailysatori.ui.component.news.newsListContentPadding
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown
import org.koin.androidx.compose.koinViewModel

@Composable
fun CrayfishNewsScreen(onBackToRemoteNews: () -> Unit) {
    val viewModel: CrayfishNewsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadInitial() }
    CrayfishNewsListScreen(state, viewModel, onBackToRemoteNews)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrayfishNewsListScreen(
    state: CrayfishNewsState,
    viewModel: CrayfishNewsViewModel,
    onBackToRemoteNews: () -> Unit,
) {
    AppScaffold(title = state.mode.title, showBack = false, actions = { CrayfishNewsMenu(state.mode, viewModel, onBackToRemoteNews) }) { modifier ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = modifier.fillMaxSize(),
        ) {
            CrayfishNewsListContent(state, viewModel)
        }
    }
}

@Composable
private fun CrayfishNewsMenu(
    mode: CrayfishNewsMode,
    viewModel: CrayfishNewsViewModel,
    onBackToRemoteNews: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (mode != CrayfishNewsMode.LATEST) DropdownMenuItem(
                text = { Text("综合新闻") },
                leadingIcon = { Icon(Icons.Default.Article, contentDescription = null) },
                onClick = { viewModel.switchMode(CrayfishNewsMode.LATEST); expanded = false },
            )
            if (mode != CrayfishNewsMode.DJI) DropdownMenuItem(
                text = { Text("大疆新闻") },
                leadingIcon = { Icon(Icons.Default.Flight, contentDescription = null) },
                onClick = { viewModel.switchMode(CrayfishNewsMode.DJI); expanded = false },
            )
            DropdownMenuItem(
                text = { Text("返回远程新闻") },
                leadingIcon = { Icon(Icons.Default.Article, contentDescription = null) },
                onClick = { onBackToRemoteNews(); expanded = false },
            )
            DropdownMenuItem(
                text = { Text("刷新") },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                onClick = { viewModel.refresh(); expanded = false },
            )
        }
    }
}

@Composable
private fun CrayfishNewsListContent(state: CrayfishNewsState, viewModel: CrayfishNewsViewModel) {
    val listState = rememberLazyListState()
    val articles = if (state.mode == CrayfishNewsMode.DJI) state.djiArticles else state.generalArticles
    val totalCount = if (state.mode == CrayfishNewsMode.DJI) state.djiFiles.size else state.generalFiles.size
    LoadMoreWhenAtEnd(listState, articles.size, viewModel::loadMore)

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && articles.isEmpty() -> LoadingIndicator()
            state.error != null && articles.isEmpty() -> NewsStateMessage(
                title = state.error,
                actionLabel = "重试",
                onAction = viewModel::refresh,
                isError = true,
            )
            articles.isEmpty() -> NewsStateMessage(icon = Icons.Default.Article, title = "暂无内容", subtitle = "小龙虾新闻暂时没有可显示的数据")
            else -> CrayfishArticleFeed(articles, totalCount, state.isLoadingMore, listState)
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
private fun CrayfishArticleFeed(
    articles: List<CrayfishNewsDetail>,
    totalCount: Int,
    isLoadingMore: Boolean,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = newsListContentPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        itemsIndexed(articles, key = { _, item -> item.filename }) { index, article ->
            CrayfishArticleCard(article, index + 1, totalCount)
        }
        if (isLoadingMore) item(key = "crayfish-news-loading-more") {
            Box(modifier = Modifier.fillMaxWidth().padding(Spacing.s), contentAlignment = Alignment.Center) {
                Text("加载更多历史新闻...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CrayfishArticleCard(article: CrayfishNewsDetail, index: Int, totalCount: Int) {
    val displayContent = remember(article.content) { article.content.withoutIntroBlock() }
    CustomCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            ArticleMeta(article, index, totalCount)
            HorizontalDivider()
            SelectionContainer {
                Markdown(
                    content = displayContent,
                    typography = MarkdownStyles.summaryTypography(),
                    padding = MarkdownStyles.summaryPadding(),
                )
            }
        }
    }
}

@Composable
private fun ArticleMeta(article: CrayfishNewsDetail, index: Int, totalCount: Int) {
    val title = article.generated?.takeIf { it.isNotBlank() } ?: article.filename.removeSuffix(".md")
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(modifier = Modifier.padding(horizontal = Spacing.s, vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("第 $index 篇 / 共 $totalCount 篇", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun String.withoutIntroBlock(): String {
    val lines = lines()
    val firstSection = lines.indexOfFirst { it.startsWith("## ") }
    return if (firstSection < 0) trim() else lines.drop(firstSection).joinToString("\n").trim()
}
