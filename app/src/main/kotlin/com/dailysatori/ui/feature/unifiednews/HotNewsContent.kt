package com.dailysatori.ui.feature.unifiednews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.dailysatori.R
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.service.hotnews.*
import com.dailysatori.service.opportunity.newsArticleKey
import com.dailysatori.ui.theme.*

@Composable
internal fun HotNewsContent(state: UnifiedNewsState, viewModel: UnifiedNewsViewModel, isActive: Boolean) {
    var expanded by remember { mutableStateOf(emptySet<HotNewsPlatform>()) }
    val listState = rememberLazyListState()
    LaunchedEffect(isActive) { if (isActive) viewModel.refreshHotNews() }
    LaunchedEffect(state.scrollToTopRequestKey) { if (state.scrollToTopRequestKey > 0) listState.animateScrollToItem(0) }
    val collected = state.sourcesBySummaryId.values.flatten().mapNotNull { it.source_url }.map { newsArticleKey(it, "") }.toSet()
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.m, bottom = Height.navBar + Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        items(state.hotNewsBoards, key = { it.platform.key }) { board ->
            HotNewsBoardCard(board, board.platform in expanded, state.searchQuery, collected, viewModel::openHotNews,
                onExpand = { expanded = if (board.platform in expanded) expanded - board.platform else expanded + board.platform },
                onRetry = { viewModel.refreshHotNews(force = true, platform = board.platform) })
        }
    }
}

@Composable
private fun HotNewsBoardCard(board: HotNewsBoard, expanded: Boolean, query: String, collected: Set<String>,
    onOpen: (HotNewsItem) -> Unit, onExpand: () -> Unit, onRetry: () -> Unit) {
    val matching = board.items.filter { query.isBlank() || it.displayTitle.orEmpty().contains(query, true) || it.displaySummary.contains(query, true) }
    Card(shape = RoundedCornerShape(Radius.l), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(board.platform.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(when (board.platform) {
                        HotNewsPlatform.GITHUB -> R.string.news_hotnews_daily
                        HotNewsPlatform.HACKER_NEWS -> R.string.news_hotnews_top
                        else -> R.string.news_hotnews_topics
                    }), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (board.fetchedAt > 0) Text(stringResource(R.string.news_hotnews_updated,
                    TimeUtils.formatDateTime(board.fetchedAt).substringAfter(' ')),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (board.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            val error = board.error
            if (error != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(when (error) {
                    HotNewsError.FETCH_FAILED -> R.string.news_hotnews_fetch_failed
                    HotNewsError.AI_NOT_CONFIGURED -> R.string.news_hotnews_ai_missing
                    HotNewsError.TRANSLATION_FAILED -> R.string.news_hotnews_translation_failed
                }), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.news_hotnews_retry)) }
            }
            if (board.items.isEmpty()) Text(stringResource(if (board.refreshing) R.string.news_hotnews_loading else R.string.news_hotnews_empty),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (if (expanded || query.isNotBlank()) matching else matching.take(3)).forEach { item ->
                HotNewsRankRow(item, newsArticleKey(item.url, "") in collected, onOpen)
            }
            if (matching.size > 3 && query.isBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = onExpand, contentPadding = PaddingValues(Spacing.xs)) {
                    Text(stringResource(if (expanded) R.string.news_hotnews_less else R.string.news_hotnews_more, matching.size))
                }
            }
        }
    }
}

@Composable
private fun HotNewsRankRow(item: HotNewsItem, collected: Boolean, onOpen: (HotNewsItem) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = item.displayTitle != null) { onOpen(item) }.padding(vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(item.rank.toString().padStart(2, '0'), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(item.displayTitle ?: stringResource(R.string.news_hotnews_translation_pending), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(hotNewsMetrics(item), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (collected) Text(stringResource(R.string.news_hotnews_collected), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
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
