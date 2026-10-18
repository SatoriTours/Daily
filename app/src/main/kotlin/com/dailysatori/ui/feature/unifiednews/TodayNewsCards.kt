package com.dailysatori.ui.feature.unifiednews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.R
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import com.dailysatori.ui.feature.myspace.opportunityActionLabelKey
import com.dailysatori.ui.theme.*
import org.koin.compose.koinInject

@Composable
internal fun DailyHeadlinesPreview(
    summary: Unified_news_summary,
    sources: List<Unified_news_source>,
    articleSummariesBySource: Map<Long, String>,
    query: String,
    onOpen: () -> Unit,
    onCitationClick: (Unified_news_source) -> Unit,
) {
    val briefing = remember(summary.content) { unifiedNewsBriefingContent(summary.content) }
    val stories = remember(summary, sources, articleSummariesBySource, query) {
        dailyHeadlineStories(summary, sources, articleSummariesBySource, query)
    }
    Card(shape = RoundedCornerShape(Radius.l), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(horizontal = Spacing.m).padding(top = Spacing.m)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.news_focus_summary_title), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.news_focus_updated, summary.summary_date,
                    TimeUtils.formatDateTime(summary.updated_at).substringAfter(' ')),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(headlinesTitleResource(summary.summary_date)), Modifier.padding(top = Spacing.s),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            briefing.lead?.let { lead ->
                Text(visibleUnifiedNewsTextWithoutCitation(lead), Modifier.padding(top = Spacing.s, bottom = Spacing.s),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            stories.forEachIndexed { index, story ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DailyHeadlineStoryRow(index, story, onCitationClick)
            }
            if (stories.isEmpty() && query.isBlank()) {
                Text(stringResource(R.string.news_hotnews_no_points), Modifier.padding(vertical = Spacing.m),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.s)) {
                Text(stringResource(R.string.news_focus_read_briefing), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
                Icon(Icons.Default.ChevronRight, null, Modifier.size(IconSize.s))
            }
        }
    }
}

@Composable
private fun DailyHeadlineStoryRow(index: Int, story: DailyHeadlineStory, onOpen: (Unified_news_source) -> Unit) {
    val source = story.source
    Row(Modifier.fillMaxWidth().clickable(enabled = source != null) { source?.let(onOpen) }
        .padding(vertical = Spacing.s + Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text((index + 1).toString().padStart(2, '0'), Modifier.padding(top = Spacing.xs),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(story.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            story.description?.let { description ->
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun TodayNewsOpportunityCard(
    item: NewsOpportunity,
    onOpen: () -> Unit,
    isCaptured: Boolean,
    onTopicAction: () -> Unit,
) {
    val i18n: I18nService = koinInject()
    Card(onClick = onOpen, shape = RoundedCornerShape(Radius.m), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s + Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (item.category.isNotBlank()) {
                Surface(shape = RoundedCornerShape(Radius.xs),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) {
                    Text(item.category, Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(item.title, Modifier.padding(top = Spacing.xs),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.relevance.ifBlank { item.action }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            HorizontalDivider(Modifier.padding(top = Spacing.s), color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(item.article.source, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.news_focus_view_opportunity), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Default.ChevronRight, null, Modifier.size(IconSize.xs), tint = MaterialTheme.colorScheme.primary)
                IconButton(onClick = onTopicAction, modifier = Modifier.size(Height.buttonSmall)) {
                    Icon(Icons.Outlined.Lightbulb, i18n.t(opportunityActionLabelKey(isCaptured)), Modifier.size(IconSize.s),
                        tint = if (isCaptured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
