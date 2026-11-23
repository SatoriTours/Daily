package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.core.diagnostics.performanceContent
import com.dailysatori.service.diagnostics.PerformanceResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.dailysatori.R
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import com.dailysatori.ui.theme.*

private const val HeadlineOverviewPreviewLength = 150

@Composable
internal fun UnifiedNewsHeadlinesContent(
    state: UnifiedNewsState,
    viewModel: UnifiedNewsViewModel,
    onBriefing: (Long) -> Unit,
) {
    val summaries = filteredUnifiedNewsSummaries(state.summaries, state.sourcesBySummaryId, state.searchQuery)
    val summary = latestHeadlinesSummary(state.summaries, state.lastSuccessfulSummary)
    val headlineSummaries = if (state.searchQuery.isBlank()) listOfNotNull(summary) else summaries.filter { it.content.isNotBlank() }
    val articleSummariesBySource = remember(state.articleIdsByNewsSource, state.articleSummariesById) {
        state.articleIdsByNewsSource.mapNotNull { (sourceId, articleId) ->
            state.articleSummariesById[articleId]?.let { sourceId to it }
        }.toMap()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.scrollToTopRequestKey) {
        if (state.scrollToTopRequestKey > 0) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().performanceContent(when {
            headlineSummaries.isNotEmpty() -> PerformanceResult.OK
            state.initialSummariesLoaded -> PerformanceResult.EMPTY
            else -> null
        }),
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.l, bottom = Height.navBar + Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(headlineSummaries, key = { "headlines-" + it.id }) { entry ->
            val sources = state.sourcesBySummaryId[entry.id].orEmpty()
            LaunchedEffect(entry.id, sources.size) { viewModel.prepareBriefingArticles(sources) }
            val digest = remember(entry.content, sources, articleSummariesBySource, state.searchQuery) {
                dailyHeadlineDigest(entry, sources, articleSummariesBySource, state.searchQuery)
            }
            HeadlineDigestBody(
                entry = entry,
                digest = digest,
                query = state.searchQuery,
                onBriefing = { onBriefing(entry.id) },
                onSourceClick = viewModel::openCitation,
                onCitationClick = { citation -> sources.firstOrNull { it.ref_key == citation }?.let(viewModel::openCitation) },
            )
        }
        if (headlineSummaries.isEmpty()) item(key = "headlines-empty") {
            Column(Modifier.padding(vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.news_focus_empty), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.news_focus_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun HeadlineDigestBody(
    entry: Unified_news_summary,
    digest: DailyHeadlineDigest,
    query: String,
    onBriefing: () -> Unit,
    onSourceClick: (Unified_news_source) -> Unit,
    onCitationClick: (String) -> Unit,
) {
    val overview = remember(digest.overview) { digest.overview.joinToString(" ") }
    val previewEnd = remember(overview) {
        overview.offsetByCodePoints(0, minOf(overview.codePointCount(0, overview.length), HeadlineOverviewPreviewLength))
    }
    val overviewClipped = previewEnd < overview.length
    val hasRemaining = query.isBlank() && digest.remaining.isNotBlank()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (overview.isEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { HeadlineDigestHeader(entry) }
        if (overview.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.news_focus_overview),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    HeadlineDigestHeader(entry)
                }
                Text(
                    if (overviewClipped) overview.substring(0, previewEnd).trimEnd() + "…" else overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (overviewClipped) HeadlineBriefingAction(onBriefing)
            }
            if (digest.stories.isNotEmpty() || hasRemaining) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (digest.stories.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                digest.stories.forEachIndexed { index, story ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = Spacing.m), color = MaterialTheme.colorScheme.outlineVariant)
                    HeadlineStoryBlock(story, onSourceClick)
                }
            }
        }
        if (hasRemaining) {
            if (digest.stories.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = Spacing.m), color = MaterialTheme.colorScheme.outlineVariant)
            }
            CitationText(
                content = digest.remaining,
                modifier = Modifier.fillMaxWidth(),
                onCitationClick = onCitationClick,
            )
        }
        if (!overviewClipped) HeadlineBriefingAction(onBriefing)
    }
}

@Composable
private fun HeadlineBriefingAction(onBriefing: () -> Unit) {
    TextButton(
        onClick = onBriefing,
        modifier = Modifier.fillMaxWidth().heightIn(min = Height.touchTarget),
        contentPadding = PaddingValues(vertical = Spacing.s),
    ) {
        Text(
            stringResource(R.string.news_focus_read_briefing),
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun HeadlineDigestHeader(entry: Unified_news_summary) {
    Text(
        stringResource(
            R.string.news_focus_updated,
            entry.summary_date,
            TimeUtils.formatDateTime(entry.updated_at).substringAfter(' '),
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun HeadlineStoryBlock(story: DailyHeadlineStory, onSourceClick: (Unified_news_source) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(story.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        story.paragraphs.forEach { paragraph ->
            Text(
                paragraph,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        val sourceMeta = headlineStorySourceMeta(story)
        if (sourceMeta.isNotBlank() || story.sources.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    sourceMeta,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HeadlineSourceAction(story.sources, onSourceClick)
            }
        }
    }
}

@Composable
private fun HeadlineSourceAction(
    sources: List<Unified_news_source>,
    onSourceClick: (Unified_news_source) -> Unit,
) {
    if (sources.isEmpty()) return
    if (sources.size == 1) {
        SourceLink(stringResource(R.string.news_focus_view_source)) { onSourceClick(sources.single()) }
        return
    }
    var expanded by remember { mutableStateOf(false) }
    Box {
        SourceLink(stringResource(R.string.news_focus_view_source) + " (" + sources.size + ")") { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sources.forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.title) },
                    onClick = {
                        expanded = false
                        onSourceClick(source)
                    },
                )
            }
        }
    }
}


@Composable
private fun SourceLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(vertical = Spacing.xs),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
