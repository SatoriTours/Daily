package com.dailysatori.ui.feature.unifiednews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.R
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.service.hotnews.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.ui.feature.settings.hotnews.HotNewsSettingsScreen
import com.dailysatori.ui.theme.*
import org.koin.compose.koinInject

@Composable
internal fun HotNewsContent(state: UnifiedNewsState, viewModel: UnifiedNewsViewModel, isActive: Boolean) {
    val i18n: I18nService = koinInject()
    var expanded by remember { mutableStateOf(emptySet<HotNewsPlatform>()) }
    var showSourceSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    if (showSourceSettings) {
        BackHandler(enabled = isActive) { showSourceSettings = false }
        HotNewsSettingsScreen(onBack = { showSourceSettings = false })
        return
    }
    LaunchedEffect(isActive) { if (isActive) viewModel.refreshHotNews() }
    LaunchedEffect(state.scrollToTopRequestKey) { if (state.scrollToTopRequestKey > 0) listState.animateScrollToItem(0) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.m, bottom = Height.navBar + Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (state.hotNewsBoards.isEmpty()) item { Text(i18n.t("news_sources.all_disabled")) }
        items(state.hotNewsBoards, key = { it.platform.key }) { board ->
            HotNewsBoardCard(board, board.platform in expanded, state.searchQuery, viewModel::openHotNews,
                onExpand = { expanded = if (board.platform in expanded) expanded - board.platform else expanded + board.platform },
                onRetry = { viewModel.retryHotNews(board) }, onConfigure = { showSourceSettings = true })
        }
    }
}

@Composable
private fun HotNewsBoardCard(board: HotNewsBoard, expanded: Boolean, query: String,
    onOpen: (HotNewsItem) -> Unit, onExpand: () -> Unit, onRetry: () -> Unit, onConfigure: () -> Unit) {
    val i18n: I18nService = koinInject()
    val matching = board.items.filter { query.isBlank() || (it.displayTitle ?: it.originalTitle).contains(query, true) || it.displaySummary.contains(query, true) }
    val feedOnly = board.platform in setOf(HotNewsPlatform.X, HotNewsPlatform.XIAOHONGSHU)
    Card(shape = RoundedCornerShape(Radius.l), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.m)) {
            HotNewsBoardHeader(board)
            if (board.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            val error = board.error
            if (error != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when (error) {
                    HotNewsError.FETCH_FAILED -> stringResource(when {
                        feedOnly -> R.string.news_hotnews_feed_failed
                        board.items.isEmpty() -> R.string.news_hotnews_fetch_failed_empty
                        else -> R.string.news_hotnews_fetch_failed
                    })
                    HotNewsError.AI_NOT_CONFIGURED -> stringResource(R.string.news_hotnews_ai_missing)
                    HotNewsError.TRANSLATION_FAILED -> stringResource(R.string.news_hotnews_translation_failed)
                    HotNewsError.TRANSLATION_NETWORK_FAILED -> stringResource(R.string.news_hotnews_translation_network_failed)
                    HotNewsError.FEED_NOT_CONFIGURED -> i18n.t("news_sources.feed_required")
                }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val configure = error == HotNewsError.FEED_NOT_CONFIGURED || (error == HotNewsError.FETCH_FAILED && feedOnly)
                TextButton(onClick = if (configure) onConfigure else onRetry, enabled = !board.refreshing) {
                    Text(stringResource(if (configure) R.string.news_hotnews_configure_source else R.string.news_hotnews_retry))
                }
            }
            if (board.items.isEmpty() && error == null) Text(stringResource(if (board.refreshing) R.string.news_hotnews_loading else R.string.news_hotnews_empty),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (if (expanded || query.isNotBlank()) matching else matching.take(3)).forEach { item ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                HotNewsRankRow(item, board.refreshing, onOpen)
            }
            if (matching.size > 3 && query.isBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = onExpand, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.s)) {
                    Text(stringResource(when {
                        expanded -> R.string.news_hotnews_less
                        feedOnly || board.items.any { it.fromFeed } -> R.string.news_hotnews_feed_more
                        else -> R.string.news_hotnews_more
                    }, matching.size), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(IconSize.s), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HotNewsRankRow(item: HotNewsItem, refreshing: Boolean, onOpen: (HotNewsItem) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(item.rank.toString(), Modifier.width(IconSize.m), style = MaterialTheme.typography.labelLarge,
            color = if (item.rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(item.displayTitle ?: item.originalTitle, style = MaterialTheme.typography.titleSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.displayTitle == null) Text(stringResource(if (refreshing) R.string.news_hotnews_translation_pending
                else R.string.news_hotnews_item_translation_failed), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val metadata = hotNewsMetrics(item).ifBlank { item.displaySummary }
            if (metadata.isNotBlank()) {
                Text(metadata, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HotNewsBoardHeader(board: HotNewsBoard) {
    val i18n: I18nService = koinInject()
    val feed = board.platform in setOf(HotNewsPlatform.X, HotNewsPlatform.XIAOHONGSHU) || board.items.any { it.fromFeed }
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Surface(shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Icon(when (board.platform) {
                HotNewsPlatform.GITHUB -> Icons.Outlined.Code
                HotNewsPlatform.LINUX_DO, HotNewsPlatform.V2EX -> Icons.Outlined.Forum
                else -> Icons.AutoMirrored.Outlined.TrendingUp
            }, null, Modifier.padding(Spacing.s).size(IconSize.m), tint = MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(board.platform.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(if (feed) i18n.t("news_sources.subscription") else stringResource(when (board.platform) {
                HotNewsPlatform.GITHUB -> R.string.news_hotnews_daily
                HotNewsPlatform.HACKER_NEWS -> R.string.news_hotnews_top
                else -> R.string.news_hotnews_topics
            }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (board.fetchedAt > 0) {
            Text(stringResource(R.string.news_hotnews_updated, TimeUtils.formatDateTime(board.fetchedAt).substringAfter(' ')),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun hotNewsMetrics(item: HotNewsItem): String {
    val heat = item.heat?.let { when (item.platform) {
        HotNewsPlatform.GITHUB -> stringResource(R.string.news_hotnews_growth, it)
        HotNewsPlatform.HACKER_NEWS -> stringResource(R.string.news_hotnews_points, it)
        else -> it
    } }.orEmpty()
    val comments = item.comments?.let { stringResource(R.string.news_hotnews_comments, it) }.orEmpty()
    return listOf(heat, comments).filter(String::isNotBlank).joinToString(" · ")
}
