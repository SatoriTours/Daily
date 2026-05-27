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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.unifiednews.dailyUnifiedNewsWindowFor
import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.ArticleListScreen
import com.dailysatori.ui.feature.remotenews.RemoteArticleSummaryCard
import com.dailysatori.ui.feature.remotenews.RemoteArticleDetailScreen
import com.dailysatori.ui.feature.remotenews.RemoteDigestDetailScreen
import com.dailysatori.ui.feature.settings.SettingsScreen
import com.dailysatori.ui.feature.settings.SettingsViewModel
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.IconSize
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
private fun UnifiedNewsSummaryPage(state: UnifiedNewsState, viewModel: UnifiedNewsViewModel, onMyClick: () -> Unit) {
    AppScaffold(
        title = "新闻汇总",
        showBack = false,
        myNavigationLabel = "我的",
        onMyNavigationClick = onMyClick,
        actions = { UnifiedNewsMenu(viewModel) },
    ) { modifier ->
        Column(modifier = modifier.fillMaxSize()) {
            UnifiedNewsSourceSwitcher(state = state, viewModel = viewModel)
            if (state.isRegenerating) UnifiedNewsGeneratingSkeleton(summaryDate = state.regeneratingSummaryDate)
            val refreshMessage = state.manualRefreshMessage ?: state.error
            if (!state.isRegenerating && !refreshMessage.isNullOrBlank()) {
                UnifiedNewsRefreshMessage(refreshMessage)
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (val selection = state.sourceSelection) {
                    UnifiedNewsSourceSelection.Summary -> UnifiedNewsSummaryContent(state, viewModel)
                    is UnifiedNewsSourceSelection.RemoteSource -> UnifiedNewsSourceArticleContent(state, selection, viewModel)
                }
            }
        }
    }
}

@Composable
private fun UnifiedNewsSummaryContent(state: UnifiedNewsState, viewModel: UnifiedNewsViewModel) {
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

@Composable
private fun UnifiedNewsSourceSwitcher(state: UnifiedNewsState, viewModel: UnifiedNewsViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        FilterChip(
            selected = state.sourceSelection is UnifiedNewsSourceSelection.Summary,
            onClick = viewModel::selectSummarySource,
            label = { Text("汇总") },
        )
        state.remoteSources.forEach { source ->
            FilterChip(
                selected = (state.sourceSelection as? UnifiedNewsSourceSelection.RemoteSource)?.id == source.id,
                onClick = { viewModel.selectRemoteSource(source) },
                label = { Text(source.name) },
            )
        }
    }
}

@Composable
private fun UnifiedNewsSourceArticleContent(
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
            actionLabel = "刷新",
            onAction = viewModel::refreshSelectedRemoteSource,
        )
        articles.isEmpty() -> UnifiedNewsSourceArticleMessage(
            title = "这个来源今天还没有新闻",
            actionLabel = "刷新",
            onAction = viewModel::refreshSelectedRemoteSource,
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
        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text("${selection.name} · 今日文章", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("共 ${articles.size} 篇", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = viewModel::refreshSelectedRemoteSource) { Text("刷新") }
            }
        }
        if (sourceArticlesError != null) item {
            Surface(shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(
                    text = "刷新失败，正在显示上次结果：$sourceArticlesError",
                    modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(articles, key = { it.id }) { article ->
            RemoteArticleSummaryCard(article) { viewModel.openSourceArticle(selection.id, article.id) }
        }
        if (isLoading) item {
            Box(modifier = Modifier.fillMaxWidth().padding(Spacing.s), contentAlignment = Alignment.Center) {
                Text("刷新中...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UnifiedNewsSourceArticleMessage(title: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SkeletonStatTile(alpha = alpha, modifier = Modifier.weight(1f))
                SkeletonStatTile(alpha = alpha, modifier = Modifier.weight(1f))
                SkeletonStatTile(alpha = alpha, modifier = Modifier.weight(1f))
            }
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
private fun SkeletonStatTile(alpha: Float, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SkeletonLine(width = 40.dp, alpha = alpha)
            SkeletonLine(width = 64.dp, alpha = alpha)
        }
    }
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
    val briefing = remember(summary.content) { unifiedNewsBriefingContent(summary.content) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.xl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            UnifiedNewsBriefingHero(summary = summary, sources = sources, briefing = briefing)
            if (briefing.points.isNotEmpty()) {
                UnifiedNewsBriefingPointList(
                    points = briefing.points,
                    sources = sources,
                    onCitationClick = onCitationClick,
                )
            } else {
                UnifiedNewsBriefingFallback(summary = summary, sources = sources, onCitationClick = onCitationClick)
            }
            UnifiedNewsBriefingSourceRow(sources)
        }
    }
}

@Composable
private fun UnifiedNewsBriefingHero(
    summary: Unified_news_summary,
    sources: List<Unified_news_source>,
    briefing: UnifiedNewsBriefingContent,
) {
    val citationCount = briefing.points.mapNotNull { it.citation }.distinct().size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                UnifiedNewsBriefingBadge(unifiedNewsSummaryTitle(summary.summary_date))
                UnifiedNewsBriefingBadge("${sources.size} 个来源")
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    text = briefing.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                briefing.lead?.let { lead ->
                    Text(
                        text = lead,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            UnifiedNewsBriefingStats(
                sourceCount = sources.size,
                pointCount = briefing.points.size,
                citationCount = citationCount,
            )
        }
    }
}

@Composable
private fun UnifiedNewsBriefingBadge(text: String) {
    Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun UnifiedNewsBriefingStats(sourceCount: Int, pointCount: Int, citationCount: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        UnifiedNewsBriefingStatTile("来源", sourceCount.toString(), Modifier.weight(1f))
        UnifiedNewsBriefingStatTile("重点", pointCount.toString(), Modifier.weight(1f))
        UnifiedNewsBriefingStatTile("引用", citationCount.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun UnifiedNewsBriefingStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UnifiedNewsBriefingPointList(
    points: List<UnifiedNewsBriefingPoint>,
    sources: List<Unified_news_source>,
    onCitationClick: (Unified_news_source) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("关键要点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        points.forEachIndexed { index, point ->
            val source = point.citation?.let { citation -> sources.firstOrNull { it.ref_key == citation } }
            UnifiedNewsBriefingPointRow(index = index, point = point, source = source, onCitationClick = onCitationClick)
        }
    }
}

@Composable
private fun UnifiedNewsBriefingPointRow(
    index: Int,
    point: UnifiedNewsBriefingPoint,
    source: Unified_news_source?,
    onCitationClick: (Unified_news_source) -> Unit,
) {
    val rowModifier = if (source != null) Modifier.clickable { onCitationClick(source) } else Modifier

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = rowModifier.fillMaxWidth().padding(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.Top,
        ) {
            UnifiedNewsBriefingNumberBadge(index + 1)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = point.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = unifiedNewsBriefingSourceHint(point.citation, source),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UnifiedNewsBriefingNumberBadge(number: Int) {
    Surface(
        modifier = Modifier.size(IconSize.xl),
        shape = RoundedCornerShape(Radius.circular),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun UnifiedNewsBriefingFallback(
    summary: Unified_news_summary,
    sources: List<Unified_news_source>,
    onCitationClick: (Unified_news_source) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(BorderWidth.s, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text("完整简报", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            CitationText(
                content = summary.content.ifBlank { "暂无正文" },
                modifier = Modifier.fillMaxWidth(),
            ) { citation ->
                sources.firstOrNull { it.ref_key == citation }?.let(onCitationClick)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnifiedNewsBriefingSourceRow(sources: List<Unified_news_source>) {
    if (sources.isEmpty()) return

    val visibleSources = sources.take(4)
    val hiddenCount = sources.size - visibleSources.size

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("来源覆盖", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            visibleSources.forEach { source -> UnifiedNewsBriefingSourceChip(source.title) }
            if (hiddenCount > 0) UnifiedNewsBriefingSourceChip("+$hiddenCount")
        }
    }
}

@Composable
private fun UnifiedNewsBriefingSourceChip(label: String) {
    Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun unifiedNewsBriefingSourceHint(citation: String?, source: Unified_news_source?): String = when {
    source != null && citation != null -> "引用 $citation · ${source.title}"
    citation != null -> "引用 $citation"
    else -> "未关联来源"
}
