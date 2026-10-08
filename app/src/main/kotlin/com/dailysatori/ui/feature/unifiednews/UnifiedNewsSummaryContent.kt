package com.dailysatori.ui.feature.unifiednews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
import com.dailysatori.shared.db.Unified_news_summary
import com.dailysatori.ui.component.appbar.MainPageHeader
import com.dailysatori.ui.feature.myspace.*
import com.dailysatori.service.ideatopic.opportunityIdeaCaptureInput
import com.dailysatori.ui.feature.ideatopic.IdeaTopicCaptureSheet
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun NewsFocusHeader(
    tabs: List<com.dailysatori.ui.component.appbar.HomeCompactTab>,
    selectedTabIndex: Int,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().background(MaterialTheme.colorScheme.background)) {
        MainPageHeader(
            title = stringResource(R.string.news_focus_title),
            modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.s),
        ) {
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, stringResource(R.string.news_focus_search)) }
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, stringResource(R.string.news_focus_refresh)) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedTabIndex
                Column(Modifier.width(IntrinsicSize.Max).selectable(selected, role = Role.Tab, onClick = tab.onClick),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tab.label, Modifier.padding(vertical = Spacing.m), style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth().height(BorderWidth.m).background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun UnifiedNewsSummaryContent(
    state: UnifiedNewsState,
    viewModel: UnifiedNewsViewModel,
    onBriefing: (Long) -> Unit,
    onOpportunities: () -> Unit,
    onOpportunity: (String) -> Unit,
    onTopic: (String) -> Unit = {},
) {
    val opportunities: MySpaceViewModel = koinViewModel()
    val opportunityState by opportunities.state.collectAsStateWithLifecycle()
    val topicLinks by opportunities.opportunityTopicLinks.collectAsStateWithLifecycle()
    var capturingOpportunity by remember { mutableStateOf<com.dailysatori.service.opportunity.NewsOpportunity?>(null) }
    val operationFailed by opportunities.operationFailed.collectAsStateWithLifecycle()
    val task by opportunities.task.collectAsStateWithLifecycle()
    val analysisError = recommendationError(opportunityState, task, stringResource(R.string.my_space_error))
    val summaries = filteredUnifiedNewsSummaries(state.summaries, state.sourcesBySummaryId, state.searchQuery)
    val summary = latestHeadlinesSummary(state.summaries, state.lastSuccessfulSummary)
    val headlineSummaries = if (state.searchQuery.isBlank()) listOfNotNull(summary) else summaries.filter { it.content.isNotBlank() }
    val recommendations = recommendedArticles(opportunityState.items)
    val listState = rememberLazyListState()
    LaunchedEffect(opportunities) { opportunities.observeRecommendations() }
    LaunchedEffect(summary?.updated_at) { if (summary != null) opportunities.refreshRecommendations() }
    LaunchedEffect(state.summaryRefreshCompletedToken) {
        if (state.summaryRefreshCompletedToken > 0) listState.scrollToItem(0)
    }
    LaunchedEffect(state.scrollToTopRequestKey) {
        if (state.scrollToTopRequestKey > 0) listState.animateScrollToItem(0)
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.s, bottom = Height.navBar + Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        items(headlineSummaries, key = { "headlines-${it.id}" }) { entry -> DailyHeadlinesPreview(entry) { onBriefing(entry.id) } }
        if (headlineSummaries.isEmpty()) item(key = "headlines-empty") {
            Column(Modifier.padding(vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.news_focus_empty), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.news_focus_empty_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "opportunity-heading") {
            HorizontalDivider(Modifier.padding(vertical = Spacing.m), color = MaterialTheme.colorScheme.outlineVariant)
            OpportunitySectionHeader(onOpportunities)
        }
        if (opportunityState.isUpdating || task?.status in listOf("queued", "running", "retrying")) item(key = "opportunity-progress") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(task?.progress_message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.my_space_updating_recommendations),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { opportunities.cancelAnalysis() }) { Text(stringResource(R.string.my_space_cancel)) }
            }
        }
        if (operationFailed || analysisError != null) item(key = "error") {
            Text(analysisError ?: stringResource(R.string.my_space_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = onOpportunities) { Text(stringResource(R.string.my_space_expand)) }
                TextButton(onClick = { opportunities.clearError() }) { Text(stringResource(R.string.my_space_close)) }
            }
        }
        if (recommendations.isEmpty()) item(key = "empty-opportunities") {
            MyEmptyBlock(stringResource(R.string.my_space_opportunity_empty), stringResource(
                if (!opportunityState.hasAnalysisContext) R.string.my_space_analyze_missing else R.string.my_space_no_relation),
                stringResource(R.string.news_focus_more), onOpportunities)
        }
        itemsIndexed(recommendations, key = { _, item -> item.id }) { index, item ->
            val topicId = topicLinks[item.id]
            val isCaptured = topicId != null
            NewsOpportunityCard(
                item = item,
                rank = index + 1,
                onOpen = { onOpportunity(item.id) },
                isCaptured = isCaptured,
                onTopicAction = {
                    if (topicId != null) {
                        onTopic(topicId)
                    } else {
                        capturingOpportunity = item
                    }
                },
            )
        }
    }
    capturingOpportunity?.let { opp ->
        val input = opportunityIdeaCaptureInput(opp)
        IdeaTopicCaptureSheet(
            source = input.source,
            initialContent = input.content,
            onCaptured = { capturedTopicId ->
                capturingOpportunity = null
                onTopic(capturedTopicId)
            },
            onDismiss = { capturingOpportunity = null },
        )
    }
}

@Composable
private fun DailyHeadlinesPreview(summary: Unified_news_summary, onOpen: () -> Unit) {
    val briefing = remember(summary.content) { unifiedNewsBriefingContent(summary.content) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(top = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(stringResource(R.string.news_focus_updated, summary.summary_date,
            com.dailysatori.core.util.TimeUtils.formatDateTime(summary.generated_at ?: summary.updated_at).substringAfter(' ')),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(headlinesTitleResource(summary.summary_date)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(briefing.lead ?: briefing.points.take(3).joinToString(" ") { it.text },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        TextButton(onClick = onOpen, contentPadding = PaddingValues(vertical = Spacing.xs)) {
            Text(stringResource(R.string.news_focus_read_more))
        }
    }
}

internal fun headlinesTitleResource(date: String): Int =
    if (date == java.time.LocalDate.now().toString()) R.string.news_focus_headlines else R.string.news_focus_past_headlines

internal fun latestHeadlinesSummary(
    summaries: List<Unified_news_summary>,
    retained: Unified_news_summary? = null,
): Unified_news_summary? = summaries.firstOrNull { it.content.isNotBlank() }
    ?: retained?.takeIf { it.content.isNotBlank() }

@Composable
private fun OpportunitySectionHeader(onMore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.my_space_useful), Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onMore, contentPadding = PaddingValues(Spacing.xs)) {
                Text(stringResource(R.string.news_focus_more))
                Icon(Icons.Default.ChevronRight, null, Modifier.size(IconSize.s))
            }
        }
        Text(stringResource(R.string.news_focus_continuous), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
    }
}

internal fun filteredUnifiedNewsSummaries(
    summaries: List<Unified_news_summary>,
    sourcesBySummaryId: Map<Long, List<com.dailysatori.shared.db.Unified_news_source>>,
    query: String,
): List<Unified_news_summary> {
    val keyword = query.trim()
    if (keyword.isBlank()) return summaries
    return summaries.filter { summary ->
        summary.content.contains(keyword, ignoreCase = true) || summary.summary_date.contains(keyword, ignoreCase = true) ||
            sourcesBySummaryId[summary.id].orEmpty().any { source ->
                listOf(source.title, source.summary, source.ref_key, source.source_filename)
                    .any { it?.contains(keyword, ignoreCase = true) == true }
            }
    }
}
