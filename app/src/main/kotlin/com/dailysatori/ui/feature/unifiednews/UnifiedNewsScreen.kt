package com.dailysatori.ui.feature.unifiednews

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.appbar.HomeCompactHeader
import com.dailysatori.ui.component.appbar.HomeCompactTab
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.ArticleListScreen
import com.dailysatori.ui.feature.remotenews.RemoteArticleDetailScreen
import com.dailysatori.ui.feature.remotenews.RemoteDigestDetailScreen
import com.dailysatori.ui.feature.settings.SettingsScreen
import com.dailysatori.ui.feature.settings.SettingsViewModel
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun UnifiedNewsScreen(
    settingsViewModel: SettingsViewModel,
    onArticleClick: (Long) -> Unit = {},
    onMyClick: () -> Unit = {},
    avatarBadgeCount: Int = 0,
) {
    val viewModel: UnifiedNewsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadInitial() }
    LaunchedEffect(state.navigationTarget) {
        val target = state.navigationTarget as? UnifiedNewsNavigationTarget.LocalArticle ?: return@LaunchedEffect
        onArticleClick(target.id)
        viewModel.closeSourceDetail()
    }

    if (UnifiedNewsDetailRoute(state = state, viewModel = viewModel)) return

    UnifiedNewsMainPageRoute(
        state = state,
        viewModel = viewModel,
        settingsViewModel = settingsViewModel,
        onArticleClick = onArticleClick,
        onMyClick = onMyClick,
        avatarBadgeCount = avatarBadgeCount,
    )
}

@Composable
private fun UnifiedNewsDetailRoute(state: UnifiedNewsState, viewModel: UnifiedNewsViewModel): Boolean {
    val remoteArticle = state.selectedRemoteArticle
    if (remoteArticle != null) {
        BackHandler(onBack = viewModel::closeSourceDetail)
        RemoteArticleDetailScreen(
            article = remoteArticle,
            onBack = viewModel::closeSourceDetail,
            isFavorite = state.selectedRemoteArticleIsFavorite,
            showFavoriteAction = true,
            onFavoriteClick = viewModel::toggleSelectedRemoteArticleFavorite,
        )
        return true
    }

    val remoteDigest = state.selectedRemoteDigest
    if (remoteDigest != null) {
        BackHandler(onBack = viewModel::closeSourceDetail)
        RemoteDigestDetailScreen(
            digest = remoteDigest,
            onBack = viewModel::closeSourceDetail,
            onArticleClick = viewModel::openSourceArticle,
        )
        return true
    }

    if (state.navigationTarget != null && state.isLoading) {
        UnifiedNewsSourceDetailLoadingScreen(onBack = viewModel::closeSourceDetail)
        return true
    }

    val detailError = state.error
    if (state.navigationTarget != null && detailError != null) {
        UnifiedNewsSourceDetailErrorScreen(message = detailError, onBack = viewModel::closeSourceDetail)
        return true
    }

    return false
}

@Composable
private fun UnifiedNewsMainPageRoute(
    state: UnifiedNewsState,
    viewModel: UnifiedNewsViewModel,
    settingsViewModel: SettingsViewModel,
    onArticleClick: (Long) -> Unit,
    onMyClick: () -> Unit,
    avatarBadgeCount: Int,
) {
    BackHandler(enabled = state.page != UnifiedNewsPage.SUMMARY) {
        viewModel.switchPage(UnifiedNewsPage.SUMMARY)
    }
    BackHandler(enabled = state.page == UnifiedNewsPage.SUMMARY && state.isSearchVisible) {
        viewModel.closeSearch()
    }

    when (state.page) {
        UnifiedNewsPage.SUMMARY -> UnifiedNewsSummaryPage(state, viewModel, onArticleClick, onMyClick, avatarBadgeCount)
        UnifiedNewsPage.LOCAL_ARTICLES -> ArticleListScreen(
            onArticleClick = onArticleClick,
            onBack = { viewModel.switchPage(UnifiedNewsPage.SUMMARY) },
        )
        UnifiedNewsPage.LOCAL_FAVORITES -> ArticleListScreen(
            onArticleClick = onArticleClick,
            onBack = { viewModel.switchPage(UnifiedNewsPage.SUMMARY) },
            showFavoritesOnly = true,
            lockFavoritesFilter = true,
        )
        UnifiedNewsPage.SETTINGS -> SettingsScreen(settingsViewModel, onBack = { viewModel.switchPage(UnifiedNewsPage.SUMMARY) })
    }
}

@Composable
private fun UnifiedNewsSummaryPage(
    state: UnifiedNewsState,
    viewModel: UnifiedNewsViewModel,
    onArticleClick: (Long) -> Unit,
    onMyClick: () -> Unit,
    avatarBadgeCount: Int,
) {
    Scaffold(
        topBar = {
            UnifiedNewsTopBar(
                state = state,
                viewModel = viewModel,
                onMyClick = onMyClick,
                avatarBadgeCount = avatarBadgeCount,
            )
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        Column(modifier = modifier.fillMaxSize()) {
            if (state.isRegenerating) UnifiedNewsGeneratingSkeleton(summaryDate = state.regeneratingSummaryDate)
            val refreshMessage = state.manualRefreshMessage ?: state.error
            if (!state.isRegenerating && !refreshMessage.isNullOrBlank()) {
                UnifiedNewsRefreshMessage(refreshMessage)
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (val selection = state.sourceSelection) {
                    UnifiedNewsSourceSelection.Summary -> UnifiedNewsSummaryContent(state, viewModel)
                    is UnifiedNewsSourceSelection.RemoteSource -> UnifiedNewsSourceArticleContent(state, selection, viewModel)
                    is UnifiedNewsSourceSelection.ExternalFavoriteSource -> ArticleListScreen(
                        onArticleClick = onArticleClick,
                        showTopBar = false,
                        refreshRequestKey = state.localArticleRefreshRequestKey,
                        externalFavoriteSourceId = selection.id,
                        embeddedSearchQuery = state.searchQuery,
                        scrollToTopRequestKey = state.scrollToTopRequestKey,
                    )
                    UnifiedNewsSourceSelection.LocalArticles -> ArticleListScreen(
                        onArticleClick = onArticleClick,
                        showTopBar = false,
                        refreshRequestKey = state.localArticleRefreshRequestKey,
                        embeddedSearchQuery = state.searchQuery,
                        scrollToTopRequestKey = state.scrollToTopRequestKey,
                    )
                }
            }
        }
    }
}

@Composable
private fun UnifiedNewsTopBar(
    state: UnifiedNewsState,
    viewModel: UnifiedNewsViewModel,
    onMyClick: () -> Unit,
    avatarBadgeCount: Int,
) {
    if (state.isSearchVisible) {
        UnifiedNewsSearchTopBar(
            query = state.searchQuery,
            onQueryChange = viewModel::search,
            onClose = viewModel::closeSearch,
        )
    } else {
        HomeCompactHeader(
            avatarBadgeCount = avatarBadgeCount,
            tabs = unifiedNewsHeaderTabs(state, viewModel),
            selectedTab = unifiedNewsSelectedTab(state),
            onAvatar = onMyClick,
            onSearch = viewModel::toggleSearch,
            onRefresh = viewModel::refreshSelectedSource,
        )
    }
}

private fun unifiedNewsHeaderTabs(state: UnifiedNewsState, viewModel: UnifiedNewsViewModel): List<HomeCompactTab> = buildList {
    fun selectOrScrollTop(selected: Boolean, select: () -> Unit) {
        if (selected) viewModel.requestScrollToTop() else select()
    }
    add(HomeCompactTab("汇总") { selectOrScrollTop(state.sourceSelection == UnifiedNewsSourceSelection.Summary, viewModel::selectSummarySource) })
    state.remoteSources.forEach { source ->
        add(HomeCompactTab(source.name) { selectOrScrollTop((state.sourceSelection as? UnifiedNewsSourceSelection.RemoteSource)?.id == source.id) { viewModel.selectRemoteSource(source) } })
    }
    state.externalFavoriteSources.forEach { source ->
        add(HomeCompactTab(source.name) { selectOrScrollTop((state.sourceSelection as? UnifiedNewsSourceSelection.ExternalFavoriteSource)?.id == source.id) { viewModel.selectExternalFavoriteSource(source) } })
    }
    add(HomeCompactTab("本地新闻") { selectOrScrollTop(state.sourceSelection == UnifiedNewsSourceSelection.LocalArticles, viewModel::selectLocalArticlesSource) })
}

private fun unifiedNewsSelectedTab(state: UnifiedNewsState): String = when (val selection = state.sourceSelection) {
    UnifiedNewsSourceSelection.Summary -> "汇总"
    is UnifiedNewsSourceSelection.RemoteSource -> selection.name
    is UnifiedNewsSourceSelection.ExternalFavoriteSource -> selection.name
    UnifiedNewsSourceSelection.LocalArticles -> "本地新闻"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnifiedNewsSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    TopAppBar(
        expandedHeight = Height.appBar,
        windowInsets = TopAppBarDefaults.windowInsets,
        title = {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(Height.searchBar),
                        shape = RoundedCornerShape(Radius.circular),
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(start = Spacing.m, end = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "搜索",
                                modifier = Modifier.size(IconSize.m),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = "搜索",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "清除",
                                        modifier = Modifier.size(IconSize.s),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        actions = {
            TextButton(onClick = onClose) {
                Text("取消")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun UnifiedNewsRefreshMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.s),
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UnifiedNewsGeneratingSkeleton(summaryDate: String?) {
    val transition = rememberInfiniteTransition(label = "unified-news-generating")
    val alpha by transition.animateFloat(
        initialValue = 0.38f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "unified-news-generating-alpha",
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.s),
        shape = RoundedCornerShape(Radius.xl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            summaryDate?.let {
                Text(unifiedNewsSummaryTitle(summaryDate), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.l),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    SkeletonLine(width = 132.dp, alpha = alpha)
                    SkeletonLine(width = 260.dp, alpha = alpha)
                    SkeletonLine(width = 220.dp, alpha = alpha)
                }
            }
            SkeletonLine(width = 300.dp, alpha = alpha)
            SkeletonLine(width = 240.dp, alpha = alpha)
            SkeletonLine(width = 280.dp, alpha = alpha)
        }
    }
}

@Composable
private fun UnifiedNewsSourceDetailLoadingScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    AppScaffold(title = "来源详情", onBack = onBack) { modifier ->
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
    }
}

@Composable
private fun UnifiedNewsSourceDetailErrorScreen(message: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    AppScaffold(title = "来源详情", onBack = onBack) { modifier ->
        Box(modifier = modifier.fillMaxSize().padding(Spacing.m), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.Article,
                title = "来源详情加载失败",
                subtitle = message,
            )
        }
    }
}

@Composable
private fun SkeletonLine(width: androidx.compose.ui.unit.Dp, alpha: Float) {
    Box(
        modifier = Modifier
            .width(width)
            .height(Spacing.m)
            .alpha(alpha)
            .clip(RoundedCornerShape(Radius.circular))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

@Composable
private fun UnifiedNewsMenu(viewModel: UnifiedNewsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MenuItem("本地收藏", Icons.Default.Bookmark) { viewModel.switchPage(UnifiedNewsPage.LOCAL_FAVORITES); expanded = false }
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)
}
