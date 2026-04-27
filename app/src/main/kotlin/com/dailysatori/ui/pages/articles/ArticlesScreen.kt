package com.dailysatori.ui.pages.articles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.dailysatori.shared.db.Article
import com.dailysatori.ui.components.*
import com.dailysatori.ui.theme.*
import com.dailysatori.ui.feature.article.ArticlesViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlesScreen(
    onArticleClick: (Long) -> Unit = {},
) {
    val viewModel: ArticlesViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            SAppBar(
                title = "文章",
                showBack = false,
                actions = {
                    IconButton(onClick = { viewModel.toggleSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("标签筛选") },
                                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                                onClick = { showMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text(if (state.showFavoritesOnly) "显示全部" else "只看收藏") },
                                leadingIcon = {
                                    Icon(
                                        if (state.showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (state.showFavoritesOnly) MaterialTheme.colorScheme.error else LocalContentColor.current,
                                    )
                                },
                                onClick = {
                                    viewModel.toggleFavoritesOnly()
                                    showMenu = false
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isSearchVisible) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.search(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    placeholder = { Text("搜索文章...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
            }

            if (state.isLoading && state.articles.isEmpty()) {
                LoadingIndicator()
            } else if (state.articles.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.FilterList,
                    title = "暂无文章",
                    subtitle = "导入数据或保存链接来添加文章",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    items(state.articles, key = { it.id }) { article ->
                        ArticleCardItem(article = article, onClick = { onArticleClick(article.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun ArticleCardItem(article: Article, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.m),
    ) {
        Row(modifier = Modifier.padding(Spacing.m)) {
            SmartImage(
                imagePath = article.cover_image ?: article.cover_image_url,
                modifier = Modifier.padding(end = Spacing.m),
                size = 80.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = article.ai_title ?: article.title ?: "无标题",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val content = article.ai_content ?: article.content
                if (!content.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val pubDate = article.pub_date
                if (pubDate != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatTime(pubDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(epochMs: Long): String {
    val now = java.time.Instant.now().toEpochMilli()
    val diff = now - epochMs
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        else -> {
            val instant = java.time.Instant.ofEpochMilli(epochMs)
            val localDate = java.time.LocalDate.ofInstant(instant, java.time.ZoneId.systemDefault())
            "${localDate.year}-${localDate.monthValue.toString().padStart(2, '0')}-${localDate.dayOfMonth.toString().padStart(2, '0')}"
        }
    }
}
