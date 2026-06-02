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
    val visibleSummaries = if (state.isRegenerating) {
        state.summaries.filter { summary -> summary.summary_date != state.regeneratingSummaryDate }
    } else {
        state.summaries
    }
    val listState = rememberLazyListState()
    if (state.summaryRefreshCompletedToken > 0) {
        LaunchedEffect(state.summaryRefreshCompletedToken) {
            listState.scrollToItem(0)
        }
    }
    when {
        state.isLoading -> LoadingIndicator()
        visibleSummaries.isEmpty() -> NewsStateMessage(
            icon = Icons.AutoMirrored.Filled.Article,
            title = "暂无新闻汇总",
            subtitle = "点击上方刷新按钮生成新闻汇总",
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
