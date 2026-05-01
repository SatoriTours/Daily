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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.card.ArticleCard
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.input.SearchBar
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun ArticleListScreen(
    onArticleClick: (Long) -> Unit = {},
) {
    val viewModel: ArticlesViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
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

        if (state.isSearchVisible) {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = { viewModel.search(it) },
                onSearch = { viewModel.search(it) },
                onClose = { viewModel.toggleSearch() },
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                        )
                    }
                }
            }
        }
    }
}
