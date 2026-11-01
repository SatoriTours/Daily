package com.dailysatori.ui.feature.unifiednews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(headlinesTitleResource(summary.summary_date)),
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.news_focus_updated, summary.summary_date,
                TimeUtils.formatDateTime(summary.updated_at).substringAfter(' ')), Modifier.padding(top = Spacing.xs),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DailyHeadlinesReadingContent(briefing.lead, stories, query, onOpen, onCitationClick)
    }
}

@Composable
private fun DailyHeadlinesReadingContent(
    lead: String?,
    stories: List<DailyHeadlineStory>,
    query: String,
    onOpen: () -> Unit,
    onCitationClick: (Unified_news_source) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        lead?.let {
            Text(remember(it) { dailyHeadlineLeadText(it) },
                Modifier.padding(bottom = Spacing.m),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Column {
            if (stories.isNotEmpty()) {
                Row(Modifier.padding(bottom = Spacing.xs)
                    .then(if (lead == null) Modifier.padding(top = Spacing.m) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs)) {
                    Text(stringResource(R.string.news_focus_key_points), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            stories.forEachIndexed { index, story ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DailyHeadlineStoryRow(index, story, onCitationClick)
            }
            if (stories.isEmpty() && query.isBlank()) {
                Text(stringResource(R.string.news_hotnews_no_points), Modifier.padding(vertical = Spacing.m),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = Height.touchTarget),
            contentPadding = PaddingValues(vertical = Spacing.s)) {
            Text(stringResource(R.string.news_focus_read_briefing), Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Default.ChevronRight, null, Modifier.size(IconSize.s))
        }
    }
}

internal fun dailyHeadlineLeadText(lead: String): AnnotatedString =
    AnnotatedString(visibleUnifiedNewsTextWithoutCitation(lead))

@Composable
private fun DailyHeadlineStoryRow(index: Int, story: DailyHeadlineStory, onOpen: (Unified_news_source) -> Unit) {
    val source = story.source
    Row(Modifier.fillMaxWidth().heightIn(min = Height.touchTarget)
        .clickable(enabled = source != null) { source?.let(onOpen) }.padding(vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs)) {
        Text((index + 1).toString().padStart(2, '0'), Modifier.width(IconSize.s).padding(top = Spacing.xxs),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(story.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun TodayNewsOpportunityCard(
    item: NewsOpportunity,
    onOpen: () -> Unit,
    isCaptured: Boolean,
    onTopicAction: () -> Unit,
    showDivider: Boolean = true,
) {
    val i18n: I18nService = koinInject()
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.news_focus_view_opportunity), onClick = onOpen)
            .padding(top = Spacing.s, bottom = Spacing.s)) {
            val preview = item.preview
            if (preview != null) {
                Text(preview.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OpportunityPreviewLine(stringResource(R.string.news_focus_reason), preview.reason,
                    Modifier.padding(top = Spacing.s), emphasized = false)
                OpportunityPreviewLine(stringResource(R.string.news_focus_opportunity), preview.opportunity,
                    Modifier.padding(top = Spacing.xs), emphasized = true)
            } else {
                Text(stringResource(R.string.news_focus_preview_pending), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.news_focus_preview_pending_hint), Modifier.padding(top = Spacing.s),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(listOf(item.category, item.article.source).filter { it.isNotBlank() }.joinToString(" · "),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onTopicAction, contentPadding = PaddingValues(horizontal = Spacing.s),
                    colors = ButtonDefaults.textButtonColors(contentColor = if (isCaptured)
                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Text(i18n.t(opportunityActionLabelKey(isCaptured)), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun OpportunityPreviewLine(label: String, text: String, modifier: Modifier, emphasized: Boolean) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs),
        verticalAlignment = Alignment.Top) {
        Text(label, Modifier.padding(top = Spacing.xxs), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
