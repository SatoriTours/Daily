package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.R
import com.dailysatori.service.localnews.LocalNewsBriefing
import com.dailysatori.service.localnews.LocalNewsIntent
import com.dailysatori.service.localnews.LocalNewsBriefingState
import com.dailysatori.service.localnews.LocalNewsSourceView
import com.dailysatori.ui.feature.unifiednews.unifiedNewsSourceTypeLabel
import com.dailysatori.ui.feature.unifiednews.unifiedNewsSummaryTimeLabel
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown

/**
 * A local news round. It is rendered outside the persisted message list, so it can never be confused with
 * a saved conversation turn; the badge and footnote say that explicitly.
 */
@Composable
fun LocalNewsTurnCard(turn: LocalNewsTurn, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = stringResource(R.string.assistant_local_news_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = turn.question,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val briefing = turn.briefing
        when {
            turn.isLoading -> Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(IconSize.xs), strokeWidth = 1.dp, color = MaterialTheme.colorScheme.primary)
                LocalNewsStatusText(stringResource(R.string.assistant_local_news_loading))
            }
            briefing == null -> LocalNewsStatusText(
                stringResource(R.string.assistant_local_news_read_failed),
                MaterialTheme.colorScheme.error,
            )
            else -> LocalNewsBriefingBody(briefing)
        }
    }
}

@Composable
private fun LocalNewsBriefingBody(briefing: LocalNewsBriefing) {
    when (briefing.state) {
        LocalNewsBriefingState.Ready -> LocalNewsReadyBody(briefing)
        LocalNewsBriefingState.NotPrepared -> LocalNewsStatusText(
            if (briefing.intent == LocalNewsIntent.Latest) {
                stringResource(R.string.assistant_local_news_latest_missing)
            } else if (briefing.latestDate.isNotBlank()) {
                stringResource(R.string.assistant_local_news_not_prepared_latest, briefing.latestDate)
            } else {
                stringResource(R.string.assistant_local_news_not_prepared)
            },
        )
        LocalNewsBriefingState.Generating -> LocalNewsStatusText(stringResource(R.string.assistant_local_news_generating))
        LocalNewsBriefingState.Failed -> LocalNewsStatusText(stringResource(R.string.assistant_local_news_failed))
        LocalNewsBriefingState.Empty -> LocalNewsStatusText(stringResource(R.string.assistant_local_news_empty))
    }
}

/**
 * Known windows print their time; an unknown window key would leak an internal identifier, so only the
 * real date is shown then.
 */
internal fun localNewsMetaLabel(briefing: LocalNewsBriefing): String =
    if (briefing.window in KNOWN_WINDOWS) unifiedNewsSummaryTimeLabel(briefing.summaryDate, briefing.window)
    else briefing.summaryDate

/** The generated title often repeats the first markdown heading; one of them is enough. */
internal fun localNewsShowsTitle(title: String, content: String): Boolean {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return false
    val heading = content.lineSequence().firstOrNull { it.isNotBlank() }
        ?.trim()?.trimStart('#')?.trim().orEmpty()
    return heading.isEmpty() || !(trimmed.contains(heading) || heading.contains(trimmed))
}

private val KNOWN_WINDOWS = setOf("daily", "0800", "1330", "1800", "2100", "final")

@Composable
private fun LocalNewsReadyBody(briefing: LocalNewsBriefing) {
    val meta = localNewsMetaLabel(briefing)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            text = if (briefing.isToday) meta else meta + " · " + stringResource(R.string.assistant_local_news_earlier),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (localNewsShowsTitle(briefing.title, briefing.content)) {
            Text(
                text = briefing.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Markdown(
            content = briefing.content,
            typography = MarkdownStyles.summaryTypography(),
            padding = MarkdownStyles.summaryPadding(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (briefing.sources.isNotEmpty()) LocalNewsSources(briefing.sources)
        Text(
            text = stringResource(R.string.assistant_local_news_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LocalNewsSources(sources: List<LocalNewsSourceView>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = stringResource(R.string.assistant_local_news_sources),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        sources.forEach { source ->
            val platform = source.platformName.ifBlank { unifiedNewsSourceTypeLabel(source.sourceType) }
            Text(
                text = platform + " · " + source.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LocalNewsStatusText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
}
