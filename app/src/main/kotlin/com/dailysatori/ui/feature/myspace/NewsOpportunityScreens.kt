package com.dailysatori.ui.feature.myspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
import com.dailysatori.service.diary.DiaryThoughtState
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.ideatopic.opportunityIdeaCaptureInput
import com.dailysatori.ui.feature.ideatopic.IdeaTopicCaptureSheet
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.openArticleUrl
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewsOpportunityListScreen(onBack: () -> Unit, onThoughts: () -> Unit, onOpen: (String) -> Unit, onArticle: (Long) -> Unit, onTopic: (String) -> Unit = {}, viewModel: MySpaceViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val thoughts by viewModel.thoughtState.collectAsStateWithLifecycle()
    val task by viewModel.task.collectAsStateWithLifecycle()
    val failed by viewModel.operationFailed.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(OpportunityFilter.PENDING) }
    var editingFocus by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var choosingContext by rememberSaveable { mutableStateOf(false) }
    var capturingOpportunity by remember { mutableStateOf<NewsOpportunity?>(null) }
    val action = recommendationAction(state.hasAnalysisContext, state.isUpdating, task?.status)
    val busy = action == RecommendationAction.WAIT
    val analysisError = recommendationError(state, task, stringResource(R.string.my_space_error))
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backgroundMessage = stringResource(R.string.news_focus_background_running)
    val requestUpdate = {
        when (action) {
            RecommendationAction.UPDATE -> confirming = true
            RecommendationAction.SET_UP_CONTEXT -> choosingContext = true
            RecommendationAction.WAIT -> { scope.launch { snackbar.showSnackbar(backgroundMessage) }; Unit }
        }
    }
    val organizeThoughts = { viewModel.organizeThoughts(); onThoughts() }
    LaunchedEffect(viewModel) { viewModel.observeRecommendations() }
    LaunchedEffect(state.hasAnalysisContext) { if (state.hasAnalysisContext) choosingContext = false }
    BackHandler(onBack = onBack)
    AppScaffold(title = stringResource(R.string.my_space_recommendations_title), onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) }, actions = {
        if (state.hasAnalysisContext) IconButton(onClick = requestUpdate) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.my_space_analyze))
        }
    }) { modifier ->
        LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            item {
                Column(Modifier.padding(bottom = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.news_focus_continuous_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.news_focus_ranking_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TabRow(selectedTabIndex = filter.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                    OpportunityFilter.entries.forEach { value ->
                        Tab(selected = filter == value, onClick = { filter = value }, text = {
                            Text(opportunityFilterLabel(value), style = MaterialTheme.typography.labelLarge)
                        })
                    }
                }
            }
            item { RecommendationContextCard(state.hasAnalysisContext, state.focus, thoughts, busy, { editingFocus = true }, organizeThoughts) }
            if (busy) item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(task?.progress_message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.my_space_updating_recommendations),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { viewModel.cancelAnalysis() }) { Text(stringResource(R.string.my_space_cancel)) }
                }
            }
            val error = analysisError
            if (error != null || failed) item {
                Text(error ?: stringResource(R.string.my_space_error), color = MaterialTheme.colorScheme.error)
                Row {
                    TextButton(onClick = requestUpdate, enabled = !busy) { Text(stringResource(R.string.my_space_retry)) }
                    TextButton(onClick = { viewModel.clearError() }) { Text(stringResource(R.string.my_space_close)) }
                }
            }
            if (state.hasAnalysisContext || state.items.isNotEmpty()) {
                val entries = opportunityItems(state.items, filter)
                item { Text(
                    if (filter == OpportunityFilter.PENDING) stringResource(R.string.news_focus_recommended_count, entries.size)
                    else opportunityFilterLabel(filter),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.s),
                ) }
                if (entries.isEmpty()) item {
                    val hint = if (filter == OpportunityFilter.PENDING) stringResource(when {
                        !state.hasAnalysisContext -> R.string.my_space_analyze_missing
                        state.candidateCount == 0 -> R.string.my_space_opportunity_hint
                        state.items.isEmpty() -> R.string.my_space_no_relation
                        else -> R.string.my_space_no_pending_hint
                    }) else ""
                    MyEmptyBlock(stringResource(when (filter) {
                        OpportunityFilter.PENDING -> R.string.my_space_no_pending
                        OpportunityFilter.SAVED -> R.string.my_space_no_saved
                        OpportunityFilter.ACTED -> R.string.my_space_no_acted
                        OpportunityFilter.IGNORED -> R.string.my_space_no_ignored
                    }), hint, "", {})
                }
                itemsIndexed(entries, key = { _, it -> it.id }) { index, entry ->
                    NewsOpportunityCard(
                        entry,
                        index + 1,
                        { onOpen(entry.id) },
                        { viewModel.setSaved(entry.id, !entry.saved) },
                        onCaptureIdea = { capturingOpportunity = entry },
                    )
                }
            }
        }
    }
    capturingOpportunity?.let { opp ->
        val input = opportunityIdeaCaptureInput(opp)
        IdeaTopicCaptureSheet(
            source = input.source,
            initialContent = input.content,
            onCaptured = {
                capturingOpportunity = null
                onTopic(it)
            },
            onDismiss = { capturingOpportunity = null },
        )
    }
    if (choosingContext) AlertDialog(
        onDismissRequest = { choosingContext = false },
        title = { Text(stringResource(R.string.my_space_recommendation_context_title)) },
        text = { Text(stringResource(if (thoughts.isUpdating) R.string.my_space_waiting_for_thoughts else R.string.my_space_recommendation_context_hint)) },
        confirmButton = { TextButton(onClick = { choosingContext = false; organizeThoughts() }) { Text(stringResource(R.string.my_space_organize)) } },
        dismissButton = { TextButton(onClick = { choosingContext = false; editingFocus = true }) { Text(stringResource(R.string.my_space_focus)) } },
    )
    if (editingFocus) FocusDialog(state.focus, failed, { editingFocus = false }) { value -> viewModel.saveFocus(value) { editingFocus = false } }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text(stringResource(R.string.my_space_analyze)) },
        text = { Text(stringResource(R.string.my_space_analysis_confirm)) },
        confirmButton = { TextButton(onClick = { confirming = false; viewModel.analyze() }) { Text(stringResource(R.string.my_space_confirm)) } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.my_space_cancel)) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendationContextCard(
    hasContext: Boolean,
    focus: String,
    thoughts: DiaryThoughtState,
    busy: Boolean,
    onEditFocus: () -> Unit,
    onOrganizeThoughts: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.m), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = Spacing.m, vertical = if (hasContext) Spacing.xs else Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (hasContext) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.news_focus_direction, focus.ifBlank { stringResource(R.string.my_space_thought_basis) }),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = onEditFocus, enabled = !busy) { Text(stringResource(R.string.news_focus_adjust)) }
                }
            } else {
                Text(stringResource(R.string.my_space_recommendation_context_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.my_space_recommendation_context_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (thoughts.isUpdating) Text(stringResource(R.string.my_space_waiting_for_thoughts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                thoughts.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Button(onClick = onEditFocus, enabled = !busy) { Text(stringResource(R.string.my_space_set_focus)) }
                    if (!thoughts.isUpdating) OutlinedButton(onClick = onOrganizeThoughts) { Text(stringResource(R.string.my_space_organize)) }
                }
                if (!thoughts.useInChat) Text(stringResource(R.string.my_space_permission), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun opportunityFilterLabel(filter: OpportunityFilter) = stringResource(when (filter) {
    OpportunityFilter.PENDING -> R.string.news_focus_recommended
    OpportunityFilter.SAVED -> R.string.my_space_saved
    OpportunityFilter.ACTED -> R.string.my_space_acted
    OpportunityFilter.IGNORED -> R.string.my_space_ignored
})

@Composable
private fun FocusDialog(initial: String, failed: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.my_space_focus)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(stringResource(R.string.my_space_focus_hint))
            OutlinedTextField(text, { if (it.length <= 2_000) text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text(stringResource(R.string.my_space_focus_placeholder)) })
            Text(stringResource(R.string.my_space_use_thoughts), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.my_space_focus_change), style = MaterialTheme.typography.bodySmall)
            if (failed) Text(stringResource(R.string.my_space_error), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.my_space_save_changes)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.my_space_cancel)) } })
}

@Composable
fun NewsOpportunityDetailScreen(id: String, onBack: () -> Unit, onChat: () -> Unit, onArticle: (Long) -> Unit, onTopic: (String) -> Unit = {}, viewModel: MySpaceViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failure by viewModel.operationFailed.collectAsStateWithLifecycle()
    val activeReminders by viewModel.activeReminderIds.collectAsStateWithLifecycle()
    val item = state.items.firstOrNull { it.id == id }
    var evidence by rememberSaveable(id) { mutableStateOf(false) }
    var reminder by rememberSaveable(id) { mutableStateOf(false) }
    var capturingOpportunity by remember { mutableStateOf<NewsOpportunity?>(null) }
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    AppScaffold(title = stringResource(R.string.my_space_useful), onBack = onBack, bottomBar = {
        if (item != null) Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            OutlinedButton(onClick = onChat, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.my_space_discuss)) }
            Button(onClick = { reminder = true }, modifier = Modifier.weight(1f)) { Text(stringResource(if (opportunityReminderId(id) in activeReminders || item.reminderId in activeReminders) R.string.my_space_view_reminder else R.string.my_space_convert)) }
        }
    }) { modifier ->
        if (item == null) Text(stringResource(R.string.my_space_unavailable), modifier.padding(Spacing.l)) else LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            item { Text(item.category, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium); Text(item.title, style = MaterialTheme.typography.headlineSmall) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TextButton(onClick = { viewModel.setSaved(id, !item.saved) }) { Text(stringResource(if (item.saved) R.string.my_space_unsave else R.string.my_space_save)) }
                TextButton(onClick = { viewModel.setIgnored(id, !item.ignored) }) { Text(stringResource(if (item.ignored) R.string.my_space_restore else R.string.my_space_irrelevant)) }
                TextButton(onClick = { capturingOpportunity = item }) { Text("收为点子") }
            } }
            if (failure) item { Text(stringResource(R.string.my_space_error), color = MaterialTheme.colorScheme.error) }
            item { OpportunityParagraph(stringResource(R.string.my_space_fact), item.fact) }
            item {
                TextButton(onClick = { evidence = !evidence }) { Text(stringResource(R.string.my_space_evidence)) }
                if (evidence) Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(item.article.title, style = MaterialTheme.typography.titleSmall)
                    Text(item.quote, style = MaterialTheme.typography.bodyMedium)
                    Text(listOfNotNull(item.article.source, item.article.publishedAt).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.article.localArticleId != null || item.article.url?.startsWith("https://") == true || item.article.url?.startsWith("http://") == true) {
                        TextButton(onClick = { item.article.localArticleId?.let(onArticle) ?: openArticleUrl(context, item.article.url) }) { Text(stringResource(R.string.my_space_original)) }
                    } else Text(stringResource(R.string.my_space_source_unavailable), style = MaterialTheme.typography.bodySmall)
                }
            }
            item { OpportunityParagraph(stringResource(R.string.my_space_relevance), item.relevance) }
            item { OpportunityParagraph(stringResource(R.string.my_space_action), item.action) }
            item { OpportunityParagraph(stringResource(R.string.my_space_caveat), item.caveat) }
        }
    }
    if (reminder && item != null) OpportunityReminderSheet(item, viewModel) { reminder = false }
    capturingOpportunity?.let { opp ->
        val input = opportunityIdeaCaptureInput(opp)
        IdeaTopicCaptureSheet(
            source = input.source,
            initialContent = input.content,
            onCaptured = {
                capturingOpportunity = null
                onTopic(it)
            },
            onDismiss = { capturingOpportunity = null },
        )
    }
}

@Composable
private fun OpportunityParagraph(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}
