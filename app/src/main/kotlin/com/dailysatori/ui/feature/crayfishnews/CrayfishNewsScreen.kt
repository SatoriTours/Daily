package com.dailysatori.ui.feature.crayfishnews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.History
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
import com.dailysatori.service.crayfishnews.CrayfishNewsDetail
import com.dailysatori.service.crayfishnews.CrayfishNewsListItem
import com.dailysatori.ui.component.card.CustomCard
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown
import org.koin.androidx.compose.koinViewModel

@Composable
fun CrayfishNewsScreen() {
    val viewModel: CrayfishNewsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadInitial() }

    when {
        state.selectedNews != null -> CrayfishNewsDetailScreen(state.selectedNews!!, viewModel::closeNews)
        else -> CrayfishNewsListScreen(state, viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrayfishNewsListScreen(state: CrayfishNewsState, viewModel: CrayfishNewsViewModel) {
    AppScaffold(title = state.mode.title, showBack = false, actions = { CrayfishNewsMenu(state.mode, viewModel) }) { modifier ->
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
private fun CrayfishNewsMenu(mode: CrayfishNewsMode, viewModel: CrayfishNewsViewModel) {
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
            if (mode != CrayfishNewsMode.ARCHIVE) DropdownMenuItem(
                text = { Text("历史新闻") },
                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                onClick = { viewModel.switchMode(CrayfishNewsMode.ARCHIVE); expanded = false },
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
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingIndicator()
            state.error != null -> CrayfishNewsError(state.error, viewModel::refresh)
            else -> when (state.mode) {
                CrayfishNewsMode.LATEST -> LatestNewsContent(state.latestNews, viewModel::openLatestDetail)
                CrayfishNewsMode.DJI -> LatestNewsContent(state.djiNews, viewModel::openDjiDetail)
                CrayfishNewsMode.ARCHIVE -> ArchiveContent(state, viewModel)
            }
        }
    }
}

@Composable
private fun LatestNewsContent(news: CrayfishNewsDetail?, onClick: () -> Unit) {
    if (news == null) {
        EmptyState(icon = Icons.Default.Article, title = "暂无内容", subtitle = "小龙虾新闻暂时没有可显示的数据")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item {
            CustomCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    val title = news.filename.removeSuffix(".md")
                        .replace("news-summary-", "")
                        .replace("dji-news-", "")
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    news.generated?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                    news.sections.forEach { (sectionTitle, _) ->
                        if (sectionTitle.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                                Spacer(Modifier.width(Spacing.xs))
                                Text(sectionTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
        if (news.content.isNotBlank()) {
            item {
                SelectionContainer {
                    Markdown(
                        content = news.content,
                        typography = MarkdownStyles.cardTypography(),
                        padding = MarkdownStyles.cardPadding(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveContent(state: CrayfishNewsState, viewModel: CrayfishNewsViewModel) {
    val allItems = state.archiveGeneral.map { it to "general" } + state.archiveDji.map { it to "dji" }
    if (allItems.isEmpty()) {
        EmptyState(icon = Icons.Default.History, title = "暂无内容", subtitle = "小龙虾新闻暂时没有历史数据")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        items(allItems, key = { it.first.filename }) { (item, category) ->
            ArchiveItemCard(item, category, onClick = {
                viewModel.openArchiveItem(item.filename, category)
            })
        }
    }
}

@Composable
private fun ArchiveItemCard(item: CrayfishNewsListItem, category: String, onClick: () -> Unit) {
    CustomCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val displayTitle = item.filename.removeSuffix(".md")
                    .replace("news-summary-", "")
                    .replace("dji-news-", "")
                Text(displayTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (category == "general") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        if (category == "general") "综合" else "DJI",
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            item.generated?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.preview.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CrayfishNewsError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("重试") }
    }
}
