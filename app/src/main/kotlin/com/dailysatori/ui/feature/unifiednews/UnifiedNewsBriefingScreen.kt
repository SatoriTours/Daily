package com.dailysatori.ui.feature.unifiednews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.opportunity.newsArticleKey
import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.ui.component.card.articleDisplayDomain
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.component.news.ArticleReaderBody
import com.dailysatori.ui.feature.myspace.MySpaceViewModel
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@Composable
fun UnifiedNewsBriefingScreen(summaryId: Long, onBack: () -> Unit, onArticle: (Long) -> Unit, onOpportunity: (String) -> Unit) {
    val viewModel: UnifiedNewsViewModel = koinViewModel()
    val opportunities: MySpaceViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val opportunityState by opportunities.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable(summaryId) { mutableLongStateOf(summaryId) }
    var dates by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.loadInitial() }
    LaunchedEffect(state.navigationTarget) {
        val target = state.navigationTarget as? UnifiedNewsNavigationTarget.LocalArticle ?: return@LaunchedEffect
        onArticle(target.id)
        viewModel.closeSourceDetail()
    }
    if (UnifiedNewsDetailRoute(state, viewModel)) return
    val summary = state.summaries.firstOrNull { it.id == selectedId }
    val sources = state.sourcesBySummaryId[selectedId].orEmpty()
    val listState = rememberLazyListState()
    LaunchedEffect(selectedId) { listState.scrollToItem(0) }
    AppScaffold(title = stringResource(headlinesTitleResource(summary?.summary_date.orEmpty())), onBack = onBack, actions = {
        Box {
            IconButton(onClick = { dates = true }) { Icon(Icons.Outlined.CalendarMonth, stringResource(R.string.news_focus_history)) }
            DropdownMenu(expanded = dates, onDismissRequest = { dates = false }) {
                state.summaries.filter { it.content.isNotBlank() }.forEach { entry ->
                    DropdownMenuItem(text = { Text(entry.summary_date) }, onClick = { selectedId = entry.id; dates = false })
                }
            }
        }
    }) { modifier ->
        LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            if (summary == null) item {
                if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                else Text(stringResource(R.string.news_focus_empty), style = MaterialTheme.typography.bodyLarge)
            }
            if (summary != null) item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(stringResource(R.string.news_focus_news_count, summary.summary_date, sources.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val briefing = unifiedNewsBriefingContent(summary.content)
                    Text(stringResource(headlinesTitleResource(summary.summary_date)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    briefing.lead?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text(stringResource(R.string.news_focus_news_list), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Spacing.s))
                }
            }
            itemsIndexed(sources, key = { _, source -> source.id }) { index, source ->
                val related = relatedNewsOpportunities(source, opportunityState.items)
                NewsBriefingStory(source, index + 1, briefingSourceName(source, state), related,
                    onRead = { viewModel.openCitation(source) }, onOpportunity = onOpportunity)
            }
            if (summary != null && sources.isEmpty()) item {
                ArticleReaderBody(summary.content, typography = MarkdownStyles.cardTypography(), padding = MarkdownStyles.cardPadding())
            }
        }
    }
}

@Composable
private fun NewsBriefingStory(
    source: Unified_news_source,
    rank: Int,
    sourceName: String,
    related: List<NewsOpportunity>,
    onRead: () -> Unit,
    onOpportunity: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().clickable(onClick = onRead).padding(top = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(rank.toString().padStart(2, '0'), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(source.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        ArticleReaderBody(source.summary.ifBlank { stringResource(R.string.news_focus_no_summary) },
            typography = MarkdownStyles.cardTypography(), padding = MarkdownStyles.cardPadding())
        Text(stringResource(R.string.news_focus_source, sourceName), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onRead, contentPadding = PaddingValues(vertical = Spacing.xs)) { Text(stringResource(R.string.news_focus_read_article)) }
            if (related.isNotEmpty()) RelatedOpportunityLink(related, onOpportunity)
        }
    }
}

@Composable
private fun RelatedOpportunityLink(items: List<NewsOpportunity>, onOpen: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { if (items.size == 1) onOpen(items.single().id) else expanded = true },
            contentPadding = PaddingValues(vertical = Spacing.xs)) { Text(stringResource(R.string.news_focus_related, items.size)) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item -> DropdownMenuItem(text = { Text(item.title) }, onClick = { expanded = false; onOpen(item.id) }) }
        }
    }
}

internal fun relatedNewsOpportunities(source: Unified_news_source, items: List<NewsOpportunity>): List<NewsOpportunity> =
    items.filter { item ->
        !item.ignored && (
            (!source.source_url.isNullOrBlank() && !item.article.url.isNullOrBlank() &&
                newsArticleKey(source.source_url, "") == newsArticleKey(item.article.url, "")) ||
                (source.source_type == "local_favorite" && source.source_id != null && source.source_id == item.article.localArticleId)
            )
    }

private fun briefingSourceName(source: Unified_news_source, state: UnifiedNewsState): String {
    val sourceId = source.source_filename?.takeIf { it.startsWith("remote_news_source:") }?.substringAfter(':')?.toLongOrNull()
    return state.remoteSources.firstOrNull { it.id == sourceId }?.name
        ?: source.source_url?.takeIf { it.isNotBlank() }?.let(::articleDisplayDomain)
        ?: unifiedNewsSourceTypeLabel(source.source_type)
}
