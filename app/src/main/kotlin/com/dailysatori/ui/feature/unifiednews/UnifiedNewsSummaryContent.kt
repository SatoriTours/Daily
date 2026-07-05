package com.dailysatori.ui.feature.unifiednews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.news.NewsStateMessage
import com.dailysatori.ui.component.news.newsCompactListContentPadding
import com.dailysatori.ui.theme.Spacing

@Composable
internal fun UnifiedNewsSummaryContent(state: UnifiedNewsState, viewModel: UnifiedNewsViewModel) {
    val summaries = if (state.isRegenerating) {
        state.summaries.filter { summary -> summary.summary_date != state.regeneratingSummaryDate }
    } else {
        state.summaries
    }
    val visibleSummaries = filteredUnifiedNewsSummaries(summaries, state.sourcesBySummaryId, state.searchQuery)
    val listState = rememberLazyListState()
    if (state.summaryRefreshCompletedToken > 0) {
        LaunchedEffect(state.summaryRefreshCompletedToken) {
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(state.scrollToTopRequestKey) {
        if (state.scrollToTopRequestKey > 0 && visibleSummaries.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    when {
        state.isLoading -> LoadingIndicator()
        visibleSummaries.isEmpty() -> NewsStateMessage(
            icon = Icons.AutoMirrored.Filled.Article,
            title = if (state.searchQuery.isBlank()) "暂无新闻汇总" else "汇总中没有匹配内容",
            subtitle = if (state.searchQuery.isBlank()) "点击上方刷新按钮生成新闻汇总" else "换个关键词或清除搜索后查看全部",
        )
        else -> LazyColumn(
            state = listState,
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            contentPadding = newsCompactListContentPadding(),
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

internal fun filteredUnifiedNewsSummaries(
    summaries: List<com.dailysatori.shared.db.Unified_news_summary>,
    sourcesBySummaryId: Map<Long, List<com.dailysatori.shared.db.Unified_news_source>>,
    query: String,
): List<com.dailysatori.shared.db.Unified_news_summary> {
    val keyword = query.trim()
    if (keyword.isBlank()) return summaries
    return summaries.filter { summary ->
        summary.content.contains(keyword, ignoreCase = true) ||
            summary.summary_date.contains(keyword, ignoreCase = true) ||
            sourcesBySummaryId[summary.id].orEmpty().any { source ->
                listOf(source.title, source.summary, source.ref_key, source.source_filename)
                    .any { it?.contains(keyword, ignoreCase = true) == true }
            }
    }
}
