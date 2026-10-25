package com.dailysatori.ui.feature.article

import com.dailysatori.ui.component.appbar.PageAction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.ElevatedAssistChip
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dailysatori.shouldScrollToTopAfterArticleAdded
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.card.ArticleCard
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.input.SearchBar
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    onArticleClick: (Long) -> Unit = {},
    onBack: (() -> Unit)? = null,
    showFavoritesOnly: Boolean = false,
    lockFavoritesFilter: Boolean = false,
    showTopBar: Boolean = true,
    refreshRequestKey: Int = 0,
    externalFavoriteSourceId: Long? = null,
    embeddedSearchQuery: String? = null,
    scrollToTopRequestKey: Int = 0,
    viewModelKey: String? = null,
) {
    val viewModel: ArticlesViewModel = koinViewModel(key = viewModelKey)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var addUrlInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val effectiveShowFavoritesOnly = if (lockFavoritesFilter) true else showFavoritesOnly

    ArticleListEffects(
        state = state,
        listState = listState,
        lifecycleOwner = lifecycleOwner,
        effectiveShowFavoritesOnly = effectiveShowFavoritesOnly,
        externalFavoriteSourceId = externalFavoriteSourceId,
        embeddedSearchQuery = embeddedSearchQuery,
        scrollToTopRequestKey = scrollToTopRequestKey,
        refreshRequestKey = refreshRequestKey,
        viewModel = viewModel,
    )
    ArticleListContent(
        state = state,
        listState = listState,
        showTopBar = showTopBar,
        onBack = onBack,
        lockFavoritesFilter = lockFavoritesFilter,
        onAddClick = { showAddDialog = true },
        onSearchClick = viewModel::toggleSearch,
        onSearch = viewModel::search,
        onRefresh = viewModel::refreshArticles,
        onFavoriteFilterClick = viewModel::toggleFavoritesOnly,
        onArticleClick = onArticleClick,
        onFavoriteClick = viewModel::toggleFavorite,
        onShareClick = { url -> openArticleUrl(context, url) },
        onNewArticlesClick = {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
                viewModel.clearNewArticlesIndicator()
            }
        },
    )
    if (showAddDialog) {
        ArticleAddDialog(
            url = addUrlInput,
            isAdding = state.isAddingArticle,
            onUrlChange = { addUrlInput = it },
            onDismiss = {
                showAddDialog = false
                addUrlInput = ""
            },
            onConfirm = { url ->
                viewModel.addArticle(url)
                showAddDialog = false
                addUrlInput = ""
            },
        )
    }
}

@Composable
private fun ArticleListEffects(
    state: ArticlesState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    lifecycleOwner: LifecycleOwner,
    effectiveShowFavoritesOnly: Boolean,
    externalFavoriteSourceId: Long?,
    embeddedSearchQuery: String?,
    scrollToTopRequestKey: Int,
    refreshRequestKey: Int,
    viewModel: ArticlesViewModel,
) {
    fun firstVisibleArticleId(): Long? = state.articles.getOrNull(listState.firstVisibleItemIndex)?.id
    fun isAtTop(): Boolean = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    val articleIds = remember(state.articles) { state.articles.map { it.id } }
    val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    LaunchedEffect(lastVisibleIndex, state.hasMore) {
        if (state.hasMore && lastVisibleIndex >= state.articles.size - 5) viewModel.loadMoreArticles()
    }
    LaunchedEffect(effectiveShowFavoritesOnly) { viewModel.setFavoritesOnly(effectiveShowFavoritesOnly) }
    LaunchedEffect(externalFavoriteSourceId, embeddedSearchQuery) {
        viewModel.setExternalFavoriteSource(externalFavoriteSourceId)
        viewModel.search(embeddedSearchQuery.orEmpty())
    }
    LaunchedEffect(refreshRequestKey) { if (refreshRequestKey > 0) viewModel.refreshArticles() }
    LaunchedEffect(scrollToTopRequestKey, state.articles.isNotEmpty()) {
        if (scrollToTopRequestKey > 0 && state.articles.isNotEmpty()) {
            listState.animateScrollToItem(0)
            viewModel.clearNewArticlesIndicator()
        }
    }
    LaunchedEffect(state.scrollToTopRequest, articleIds) {
        if (shouldScrollToTopAfterArticleAdded(state.scrollToTopRequest) && state.addedArticleId in articleIds) {
            listState.animateScrollToItem(0)
            viewModel.consumeAddedArticleScroll()
        }
    }
    LaunchedEffect(isAtTop()) { if (isAtTop()) viewModel.clearNewArticlesIndicator() }
    LaunchedEffect(articleIds, listState.firstVisibleItemIndex) {
        viewModel.checkNewArticlesAbove(firstVisibleArticleId(), isAtTop())
    }
    DisposableEffect(lifecycleOwner, state.articles, listState.firstVisibleItemIndex) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.rememberVisibleTopArticle(firstVisibleArticleId(), isAtTop())
                Lifecycle.Event.ON_RESUME -> viewModel.checkNewArticlesAbove(firstVisibleArticleId(), isAtTop())
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleListContent(
    state: ArticlesState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    showTopBar: Boolean,
    onBack: (() -> Unit)?,
    lockFavoritesFilter: Boolean,
    onAddClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onFavoriteFilterClick: () -> Unit,
    onArticleClick: (Long) -> Unit,
    onFavoriteClick: (Long) -> Unit,
    onShareClick: (String?) -> Unit,
    onNewArticlesClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (showTopBar) {
            ArticleListTopBar(onBack, lockFavoritesFilter, state.showFavoritesOnly, onAddClick, onSearchClick, onFavoriteFilterClick,
                search = if (state.isSearchVisible) com.dailysatori.ui.component.appbar.PageSearch(
                    state.searchQuery, "搜索", onSearch, onSearchClick,
                ) else null)
        }
        if (!showTopBar && state.isSearchVisible) {
            SearchBar(state.searchQuery, onQueryChange = onSearch, onSearch = onSearch, onClose = onSearchClick)
        }
        state.loadError?.let { message ->
            TextButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(message, color = MaterialTheme.colorScheme.error)
            }
        }
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
            ArticleListBody(state, listState, onArticleClick, onFavoriteClick, onShareClick, onNewArticlesClick)
        }
    }
}

@Composable
private fun ArticleListBody(
    state: ArticlesState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onArticleClick: (Long) -> Unit,
    onFavoriteClick: (Long) -> Unit,
    onShareClick: (String?) -> Unit,
    onNewArticlesClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.articles.isEmpty() -> LoadingIndicator()
            state.articles.isEmpty() -> EmptyState(
                modifier = Modifier.align(Alignment.Center),
                icon = Icons.Default.FilterList,
                title = "暂无文章",
                subtitle = "导入数据或保存链接来添加文章",
            )
            else -> ArticleListItems(state, listState, onArticleClick, onFavoriteClick, onShareClick, onNewArticlesClick)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ArticleListItems(
    state: ArticlesState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onArticleClick: (Long) -> Unit,
    onFavoriteClick: (Long) -> Unit,
    onShareClick: (String?) -> Unit,
    onNewArticlesClick: () -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(articleListItemSpacingDp.dp),
    ) {
        items(state.articles, key = { it.id }, contentType = { "article" }) { article ->
            ArticleCard(
                article = article,
                onClick = { onArticleClick(article.id) },
                onFavoriteClick = { onFavoriteClick(article.id) },
                onShareClick = { onShareClick(article.url) },
            )
        }
    }
    if (state.newArticlesAboveCount > 0) {
        ElevatedAssistChip(
            onClick = onNewArticlesClick,
            label = { Text("上方有 ${state.newArticlesAboveCount} 篇新文章") },
            modifier = Modifier.align(Alignment.TopCenter).offset(y = Spacing.s),
        )
    }
}

@Composable
private fun ArticleAddDialog(
    url: String,
    isAdding: Boolean,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.xl),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        title = { Text("添加文章") },
        text = { ArticleAddUrlField(url, onUrlChange) },
        confirmButton = { Button(onClick = { onConfirm(url.trim()) }, enabled = url.isNotBlank() && !isAdding) { Text(if (isAdding) "添加中..." else "确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ArticleAddUrlField(url: String, onUrlChange: (String) -> Unit) {
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text("文章链接") },
        placeholder = { Text("https://...") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.s),
    )
}

@Composable
private fun ArticleListTopBar(
    onBack: (() -> Unit)?,
    lockFavoritesFilter: Boolean,
    showFavoritesOnly: Boolean,
    onAddClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFavoriteFilterClick: () -> Unit,
    search: com.dailysatori.ui.component.appbar.PageSearch? = null,
) {
    AppTopBar(
        title = "文章",
        search = search,
        showBack = onBack != null,
        onBack = onBack,
        pageActions = buildList {
            add(PageAction("添加文章", Icons.Default.Add, onClick = onAddClick))
            add(PageAction("搜索", Icons.Default.Search, onClick = onSearchClick))
            add(PageAction("标签筛选", Icons.Default.FilterList, enabled = false) { /* Existing placeholder. */ })
            if (!lockFavoritesFilter) add(PageAction(if (showFavoritesOnly) "显示全部" else "只看收藏",
                if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder, onClick = onFavoriteFilterClick))
        },
    )
}
