package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.core.diagnostics.performanceContent
import com.dailysatori.service.diagnostics.PerformanceResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.dailysatori.R
import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import com.dailysatori.ui.theme.*

private const val StorySummaryInkAlpha = 0.82f
private const val StoryChevronAlpha = 0.82f

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
        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Height.navBar + Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
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
            Column(
                Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
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
    val overviewLayout = remember(digest.overview) { headlineOverviewLayout(digest.overview) }
    val hasRemaining = query.isBlank() && digest.remaining.isNotBlank()
    val hasContentBelowTop = digest.stories.isNotEmpty() || hasRemaining
    val showFallbackHeader = overviewLayout == null && entry.content.isNotBlank()

    Column(Modifier.fillMaxWidth()) {
        if (overviewLayout != null) {
            HeadlineOverviewBlock(layout = overviewLayout, onBriefing = onBriefing)
            if (hasContentBelowTop) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                    thickness = BorderWidth.xs,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        } else if (showFallbackHeader) {
            HeadlineBriefingFallbackHeader(dateLabel = entry.summary_date, onBriefing = onBriefing)
            if (hasContentBelowTop) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                    thickness = BorderWidth.xs,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        if (digest.stories.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                digest.stories.forEachIndexed { index, story ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = Spacing.xs + Spacing.l + Spacing.s, end = Spacing.xs),
                            thickness = BorderWidth.xs,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    HeadlineStoryBlock(story = story, onSourceClick = onSourceClick, index = index + 1)
                }
            }
        }
        if (hasRemaining) {
            if (digest.stories.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                    thickness = BorderWidth.xs,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            CitationText(
                content = digest.remaining,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.m),
                onCitationClick = onCitationClick,
            )
        }
    }
}

@Composable
private fun HeadlineOverviewBlock(
    layout: HeadlineOverviewLayout,
    onBriefing: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val firstParagraphAsTop = layout.lead == null && layout.paragraphs.isNotEmpty()
    val bodyParagraphs = if (firstParagraphAsTop) layout.paragraphs.drop(1) else layout.paragraphs

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.inset))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = MaterialTheme.colorScheme.primary),
                role = Role.Button,
                onClickLabel = stringResource(R.string.news_focus_read_briefing),
                onClick = onBriefing,
            )
            .padding(horizontal = Spacing.xs, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs + Spacing.xxs),
            verticalAlignment = Alignment.Top,
        ) {
            if (layout.lead != null) {
                Text(
                    text = layout.lead,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else if (firstParagraphAsTop) {
                Text(
                    text = layout.paragraphs.first(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.padding(top = Spacing.xs).size(IconSize.xs),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        bodyParagraphs.forEach { paragraph ->
            Text(
                text = paragraph,
                modifier = Modifier.fillMaxWidth().padding(end = Spacing.xs + Spacing.xxs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HeadlineBriefingFallbackHeader(
    dateLabel: String,
    onBriefing: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Height.touchTarget)
            .clip(RoundedCornerShape(Radius.inset))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = MaterialTheme.colorScheme.primary),
                role = Role.Button,
                onClickLabel = stringResource(R.string.news_focus_read_briefing),
                onClick = onBriefing,
            )
            .padding(horizontal = Spacing.xs, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(
            text = dateLabel,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(IconSize.xs),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun HeadlineStoryBlock(
    story: DailyHeadlineStory,
    onSourceClick: (Unified_news_source) -> Unit,
    index: Int? = null,
) {
    val hasSources = story.sources.isNotEmpty()
    val hasMultipleSources = story.sources.size > 1
    var sourceMenuExpanded by remember(story.sources) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val clickLabel = if (hasMultipleSources) {
        stringResource(R.string.news_focus_choose_source)
    } else {
        stringResource(R.string.news_focus_view_source)
    }

    val rowModifier = Modifier
        .fillMaxWidth()
        .let { base ->
            if (!hasSources) base
            else base
                .clip(RoundedCornerShape(Radius.inset))
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = MaterialTheme.colorScheme.primary),
                    role = Role.Button,
                    onClickLabel = clickLabel,
                    onClick = {
                        if (hasMultipleSources) sourceMenuExpanded = true
                        else onSourceClick(story.sources.single())
                    },
                )
        }
        .padding(horizontal = Spacing.xs, vertical = Spacing.s + Spacing.xs)

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = rowModifier,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.Top,
        ) {
            if (index != null) {
                Text(
                    text = index.toString().padStart(2, '0'),
                    modifier = Modifier.width(Spacing.l).padding(top = Spacing.xs),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs + Spacing.xxs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs + Spacing.xxs),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = story.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium.editorial(),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (hasSources) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.padding(top = Spacing.xs).size(IconSize.xs),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = StoryChevronAlpha),
                        )
                    }
                }
                story.paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph,
                        modifier = Modifier.fillMaxWidth().padding(end = Spacing.xs + Spacing.xxs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = StorySummaryInkAlpha),
                    )
                }
            }
        }
        if (hasMultipleSources) {
            DropdownMenu(
                expanded = sourceMenuExpanded,
                onDismissRequest = { sourceMenuExpanded = false },
            ) {
                story.sources.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(source.title.ifBlank { headlineSourceMeta(source).ifBlank { source.ref_key } }) },
                        onClick = {
                            sourceMenuExpanded = false
                            onSourceClick(source)
                        },
                    )
                }
            }
        }
    }
}
