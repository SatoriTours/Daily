package com.dailysatori.ui.feature.unifiednews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
internal fun TodayUnifiedNewsCard(
    summary: Unified_news_summary,
    sources: List<Unified_news_source>,
    onCitationClick: (Unified_news_source) -> Unit,
) {
    val briefing = remember(summary.content) { unifiedNewsBriefingContent(summary.content) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.xl),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            UnifiedNewsMagazineCover(summary = summary, sources = sources, briefing = briefing)
            if (briefing.points.isNotEmpty()) {
                UnifiedNewsMagazineStoryList(
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
private fun UnifiedNewsMagazineCover(
    summary: Unified_news_summary,
    sources: List<Unified_news_source>,
    briefing: UnifiedNewsBriefingContent,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            UnifiedNewsBriefingBadge(unifiedNewsSummaryTitle(summary.summary_date))
            if (sources.isNotEmpty()) UnifiedNewsBriefingBadge("${sources.size} 个来源")
        }
        Text(
            text = briefing.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        briefing.lead?.let { lead ->
            Text(
                text = lead,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun UnifiedNewsMagazineStoryList(
    points: List<UnifiedNewsBriefingPoint>,
    sources: List<Unified_news_source>,
    onCitationClick: (Unified_news_source) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("关键要点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        points.forEachIndexed { index, point ->
            val source = point.citation?.let { citation -> sources.firstOrNull { it.ref_key == citation } }
            UnifiedNewsMagazineStoryRow(
                index = index,
                point = point,
                source = source,
                showDivider = index > 0,
                onCitationClick = onCitationClick,
            )
        }
    }
}

@Composable
private fun UnifiedNewsMagazineStoryRow(
    index: Int,
    point: UnifiedNewsBriefingPoint,
    source: Unified_news_source?,
    showDivider: Boolean,
    onCitationClick: (Unified_news_source) -> Unit,
) {
    val rowModifier = if (source != null) Modifier.clickable { onCitationClick(source) } else Modifier

    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
        Row(
            modifier = rowModifier.fillMaxWidth().padding(vertical = Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
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
private fun UnifiedNewsBriefingFallback(
    summary: Unified_news_summary,
    sources: List<Unified_news_source>,
    onCitationClick: (Unified_news_source) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text("完整简报", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        CitationText(
            content = summary.content.ifBlank { "暂无正文" },
            modifier = Modifier.fillMaxWidth(),
        ) { citation ->
            sources.firstOrNull { it.ref_key == citation }?.let(onCitationClick)
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
