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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.ArticleListScreen
import com.dailysatori.ui.feature.remotenews.RemoteArticleDetailScreen
import com.dailysatori.ui.feature.remotenews.RemoteDigestDetailScreen
import com.dailysatori.ui.feature.settings.SettingsScreen
import com.dailysatori.ui.feature.settings.SettingsViewModel
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun UnifiedNewsScreen(
    settingsViewModel: SettingsViewModel,
    onArticleClick: (Long) -> Unit = {},
    onMyClick: () -> Unit = {},
) {
    val viewModel: UnifiedNewsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadInitial() }
    LaunchedEffect(state.navigationTarget) {
        val target = state.navigationTarget as? UnifiedNewsNavigationTarget.LocalArticle ?: return@LaunchedEffect
        onArticleClick(target.id)
        viewModel.closeSourceDetail()
    }

    if (state.selectedRemoteArticle != null) {
        RemoteArticleDetailScreen(
            article = state.selectedRemoteArticle!!,
            onBack = viewModel::closeSourceDetail,
            isFavorite = state.selectedRemoteArticleIsFavorite,
            showFavoriteAction = true,
            onFavoriteClick = viewModel::toggleSelectedRemoteArticleFavorite,
        )
        return
    }
    if (state.selectedRemoteDigest != null) {
        RemoteDigestDetailScreen(
            digest = state.selectedRemoteDigest!!,
            onBack = viewModel::closeSourceDetail,
            onArticleClick = { id -> viewModel.openCitationSource("remote_article", id, null) },
        )
        return
    }
    if (state.navigationTarget != null && state.isLoading) {
        UnifiedNewsSourceDetailLoadingScreen(onBack = viewModel::closeSourceDetail)
        return
    }
    val detailError = state.error
    if (state.navigationTarget != null && detailError != null) {
        UnifiedNewsSourceDetailErrorScreen(message = detailError, onBack = viewModel::closeSourceDetail)
        return
    }

    BackHandler(enabled = state.page != UnifiedNewsPage.SUMMARY) {
        viewModel.switchPage(UnifiedNewsPage.SUMMARY)
    }

    when (state.page) {
        UnifiedNewsPage.SUMMARY -> UnifiedNewsSummaryPage(state, viewModel, onMyClick)
        UnifiedNewsPage.LOCAL_ARTICLES -> ArticleListScreen(onArticleClick = onArticleClick)
        UnifiedNewsPage.LOCAL_FAVORITES -> ArticleListScreen(
            onArticleClick = onArticleClick,
            showFavoritesOnly = true,
            lockFavoritesFilter = true,
        )
        UnifiedNewsPage.SETTINGS -> SettingsScreen(settingsViewModel)
    }
}

@Composable
private fun UnifiedNewsSummaryPage(state: UnifiedNewsState, viewModel: UnifiedNewsViewModel, onMyClick: () -> Unit) {
    AppScaffold(
        title = "新闻汇总",
        showBack = false,
        myNavigationLabel = "我的",
        onMyNavigationClick = onMyClick,
        actions = { UnifiedNewsMenu(viewModel) },
    ) { modifier ->
        Column(modifier = modifier.fillMaxSize()) {
            if (state.isRegenerating) UnifiedNewsGeneratingSkeleton(summaryDate = state.regeneratingSummaryDate)
            val refreshMessage = state.manualRefreshMessage ?: state.error
            if (!state.isRegenerating && !refreshMessage.isNullOrBlank()) {
                UnifiedNewsRefreshMessage(refreshMessage)
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val visibleSummaries = if (state.isRegenerating) {
                    state.summaries.filter { summary -> summary.summary_date != state.regeneratingSummaryDate }
                } else {
                    state.summaries
                }
                when {
                    state.isLoading -> LoadingIndicator()
                    visibleSummaries.isEmpty() -> EmptyState(
                        icon = Icons.AutoMirrored.Filled.Article,
                        title = "暂无新闻汇总",
                        subtitle = "点击右上角生成/更新当日新闻",
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m),
                        verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        items(visibleSummaries, key = { it.id }) { summary ->
                            TodayUnifiedNewsCard(
                                summary = summary,
                                sources = state.sourcesBySummaryId[summary.id].orEmpty(),
                                onCitationClick = viewModel::openCitation,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedNewsRefreshMessage(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.s),
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
        shape = RoundedCornerShape(Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            summaryDate?.let {
                Text(unifiedNewsSummaryTitle(summaryDate), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            SkeletonLine(width = 160.dp, alpha = alpha)
            SkeletonLine(width = 280.dp, alpha = alpha)
            SkeletonLine(width = 240.dp, alpha = alpha)
            SkeletonLine(width = 300.dp, alpha = alpha)
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
            MenuItem("本地文章", Icons.AutoMirrored.Filled.Article) { viewModel.switchPage(UnifiedNewsPage.LOCAL_ARTICLES); expanded = false }
            MenuItem("本地收藏", Icons.Default.Bookmark) { viewModel.switchPage(UnifiedNewsPage.LOCAL_FAVORITES); expanded = false }
            MenuItem("生成/更新当日新闻", Icons.Default.Refresh) { viewModel.regenerateCurrentWindow(); expanded = false }
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = onClick)
}

@Composable
private fun TodayUnifiedNewsCard(
    summary: Unified_news_summary,
    sources: List<Unified_news_source>,
    onCitationClick: (Unified_news_source) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    unifiedNewsSummaryTitle(summary.summary_date),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(
                        "${sources.size} 个来源",
                        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            CitationText(
                content = summary.content.ifBlank { "暂无正文" },
                modifier = Modifier.fillMaxWidth(),
            ) { citation ->
                sources.firstOrNull { it.ref_key == citation }?.let(onCitationClick)
            }
        }
    }
}
