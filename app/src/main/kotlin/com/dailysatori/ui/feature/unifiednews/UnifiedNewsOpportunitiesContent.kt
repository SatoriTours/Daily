package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.core.diagnostics.performanceContent
import com.dailysatori.service.diagnostics.PerformanceResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
import com.dailysatori.service.ideatopic.opportunityIdeaCaptureInput
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.ui.feature.article.openArticleUrl
import com.dailysatori.ui.feature.ideatopic.IdeaTopicCaptureSheet
import com.dailysatori.ui.feature.myspace.MySpaceViewModel
import com.dailysatori.ui.feature.myspace.OpportunityFilter
import com.dailysatori.ui.feature.myspace.opportunityActionLabelKey
import com.dailysatori.ui.feature.myspace.opportunityItems
import com.dailysatori.ui.feature.myspace.recommendationError
import com.dailysatori.ui.theme.Height
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
internal fun UnifiedNewsOpportunitiesContent(
    state: UnifiedNewsState,
    onOpportunities: () -> Unit,
    onOpportunity: (String) -> Unit,
    onTopic: (String) -> Unit,
    onArticleClick: (Long) -> Unit = {},
    onRefreshConsumed: () -> Unit = {},
) {
    val opportunities: MySpaceViewModel = koinViewModel()
    val opportunityState by opportunities.state.collectAsStateWithLifecycle()
    val topicLinks by opportunities.opportunityTopicLinks.collectAsStateWithLifecycle()
    val task by opportunities.task.collectAsStateWithLifecycle()
    val operationFailed by opportunities.operationFailed.collectAsStateWithLifecycle()
    var capturingOpportunity by remember { mutableStateOf<NewsOpportunity?>(null) }
    val analysisError = recommendationError(opportunityState, task, stringResource(R.string.my_space_error))
    val isAnalyzing = opportunityState.isUpdating || task?.status in listOf("queued", "running", "retrying")
    val progress = if (isAnalyzing) {
        task?.progress_message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.my_space_updating_recommendations)
    } else null
    val items = remember(opportunityState.items, topicLinks, state.searchQuery) {
        val pending = opportunityItems(opportunityState.items, OpportunityFilter.PENDING, topicLinks)
        val keyword = state.searchQuery.trim()
        if (keyword.isBlank()) pending else pending.filter { item -> item.matchesOpportunityQuery(keyword) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(opportunities) { opportunities.observeRecommendations() }
    LaunchedEffect(state.opportunityRefreshRequestKey) {
        if (state.opportunityRefreshRequestKey <= 0) return@LaunchedEffect
        if (opportunityState.hasAnalysisContext && !isAnalyzing) opportunities.analyze()
        onRefreshConsumed()
    }
    LaunchedEffect(state.scrollToTopRequestKey) {
        if (state.scrollToTopRequestKey > 0) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().performanceContent(PerformanceResult.OK.takeIf { items.isNotEmpty() }),
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.m, bottom = Height.navBar + Spacing.xxl),
    ) {
        item(key = "opportunity-status") {
            OpportunityStatusRow(
                progress = progress,
                error = if (operationFailed) analysisError ?: stringResource(R.string.my_space_error) else analysisError,
                onRetry = opportunities::analyze,
                onCancel = opportunities::cancelAnalysis,
                onDismiss = opportunities::clearError,
                onOpenAll = onOpportunities,
            )
        }
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            OpportunityReadingItem(
                item = item,
                isCaptured = topicLinks[item.id] != null,
                onOpen = { onOpportunity(item.id) },
                onArticleClick = onArticleClick,
                onTopicAction = {
                    val topicId = topicLinks[item.id]
                    if (topicId != null) onTopic(topicId) else capturingOpportunity = item
                },
            )
        }
        if (items.isEmpty()) item(key = "opportunity-empty") {
            Column(Modifier.padding(vertical = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.my_space_no_pending), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        if (!opportunityState.hasAnalysisContext) R.string.my_space_analyze_missing
                        else R.string.my_space_no_pending_hint,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    capturingOpportunity?.let { opportunity ->
        val input = opportunityIdeaCaptureInput(opportunity)
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
private fun OpportunityStatusRow(
    progress: String?,
    error: String?,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onOpenAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                progress ?: stringResource(R.string.news_focus_opportunities_intro),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress != null) {
                IconButton(onClick = onCancel, modifier = Modifier.size(Height.touchTarget)) {
                    Icon(
                        Icons.Default.Close,
                        stringResource(R.string.my_space_cancel),
                        Modifier.size(IconSize.s),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onOpenAll, contentPadding = PaddingValues(horizontal = Spacing.s)) {
                Text(stringResource(R.string.my_space_recommendations_title), style = MaterialTheme.typography.labelMedium)
            }
        }
        if (error != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    error,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.my_space_retry)) }
                IconButton(onClick = onDismiss, modifier = Modifier.size(Height.touchTarget)) {
                    Icon(
                        Icons.Default.Close,
                        stringResource(R.string.my_space_close),
                        Modifier.size(IconSize.s),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun OpportunityReadingItem(
    item: NewsOpportunity,
    isCaptured: Boolean,
    onOpen: () -> Unit,
    onArticleClick: (Long) -> Unit = {},
    onTopicAction: () -> Unit,
) {
    val i18n: I18nService = koinInject()
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OpportunityReadingField(stringResource(R.string.news_focus_opportunity_point), opportunityText(item))
        OpportunityReadingField(stringResource(R.string.news_focus_why_watch), opportunityWhyText(item))
        OpportunityReadingField(stringResource(R.string.my_space_fact), opportunityEvidenceText(item))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.news_focus_news_basis) + " · " + item.article.title,
                Modifier.weight(1f).clickable {
                    val localId = item.article.localArticleId
                    when {
                        localId != null -> onArticleClick(localId)
                        !item.article.url.isNullOrBlank() -> openArticleUrl(context, item.article.url)
                        else -> onOpen()
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onTopicAction,
                modifier = Modifier.heightIn(min = Height.touchTarget),
                contentPadding = PaddingValues(horizontal = Spacing.s),
            ) {
                Text(i18n.t(opportunityActionLabelKey(isCaptured)), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun OpportunityReadingField(label: String, text: String) {
    if (text.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Product opportunity plus the target user/need; not the short preview as the only source. */
internal fun opportunityText(item: NewsOpportunity): String {
    val value = item.preview?.opportunity.orEmpty().trim()
    return mergeOpportunityText(listOf(opportunityTargetText(item.relevance), value).filterNot { it == item.title.trim() })
}

private fun opportunityTargetText(relevance: String): String {
    val firstLine = relevance.lineSequence().map(String::trim).firstOrNull { it.isNotBlank() }.orEmpty()
    val marker = InferenceMarkerRegex.find(firstLine) ?: return firstLine
    return firstLine.substring(0, marker.range.first).trim().trimEnd('；', ';', '。', '.')
}

/** Why it matters: the inference association and the generated judgment, never the fact field. */
internal fun opportunityWhyText(item: NewsOpportunity): String = mergeOpportunityText(
    listOf(relevanceInferenceText(item.relevance), item.preview?.reason.orEmpty()),
)

/** Objective news signal, shown as evidence below the judgment instead of inside it. */
internal fun opportunityEvidenceText(item: NewsOpportunity): String = item.fact.trim()

private val InferenceMarkerRegex = Regex("""(?:推断|推测)[：:]""")

private fun relevanceInferenceText(relevance: String): String {
    val lines = relevance.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
    if (lines.size > 1) return lines.drop(1).joinToString("\n")
    val marker = InferenceMarkerRegex.find(relevance) ?: return ""
    return relevance.substring(marker.range.last + 1).trim()
}

private fun mergeOpportunityText(parts: List<String>): String {
    val merged = mutableListOf<String>()
    parts.map(String::trim).filter(String::isNotBlank).forEach { part ->
        if (merged.any { it.contains(part) }) return@forEach
        merged.removeAll { part.contains(it) }
        merged += part
    }
    return merged.joinToString("\n\n")
}

internal fun NewsOpportunity.matchesOpportunityQuery(keyword: String): Boolean =
    listOf(title, category, fact, relevance, article.title, article.source)
        .any { it.contains(keyword, ignoreCase = true) }
