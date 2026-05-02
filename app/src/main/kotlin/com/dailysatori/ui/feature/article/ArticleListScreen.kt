package com.dailysatori.ui.feature.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.card.ArticleCard
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.input.SearchBar
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    onArticleClick: (Long) -> Unit = {},
) {
    val viewModel: ArticlesViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var addUrlInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "文章",
            showBack = false,
            actions = {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "添加文章")
                }
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

        if (state.isSearchVisible) {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.search(it) },
                onSearch = { viewModel.search(it) },
                onClose = { viewModel.toggleSearch() },
            )
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refreshArticles() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.isLoading && state.articles.isEmpty()) {
                    LoadingIndicator()
                } else if (state.articles.isEmpty()) {
                    EmptyState(
                        modifier = Modifier.align(Alignment.Center),
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
                            ArticleCard(
                                article = article,
                                onClick = { onArticleClick(article.id) },
                                onFavoriteClick = { viewModel.toggleFavorite(article.id) },
                                onShareClick = {
                                    openArticleUrl(context, article.url)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                addUrlInput = ""
            },
            title = { Text("添加文章") },
            text = {
                OutlinedTextField(
                    value = addUrlInput,
                    onValueChange = { addUrlInput = it },
                    label = { Text("文章链接") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.s),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = addUrlInput.trim()
                        if (url.isNotBlank()) {
                            viewModel.addArticle(url)
                            showAddDialog = false
                            addUrlInput = ""
                        }
                    },
                    enabled = addUrlInput.isNotBlank() && !state.isAddingArticle,
                ) {
                    Text(if (state.isAddingArticle) "添加中..." else "确定")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    addUrlInput = ""
                }) {
                    Text("取消")
                }
            },
        )
    }
}
