package com.dailysatori.ui.feature.myspace

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.openArticleUrl
import com.dailysatori.ui.theme.*
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewsOpportunityListScreen(onBack: () -> Unit, onOpen: (String) -> Unit, viewModel: MySpaceViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val task by viewModel.task.collectAsStateWithLifecycle()
    val failed by viewModel.operationFailed.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(OpportunityFilter.PENDING) }
    var editingFocus by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val busy = state.isUpdating || task?.status in listOf("queued", "running", "retrying")
    LaunchedEffect(Unit) { viewModel.refresh() }
    BackHandler(onBack = onBack)
    AppScaffold(title = stringResource(R.string.my_space_useful), onBack = onBack, actions = {
        TextButton(onClick = { confirming = true }, enabled = !busy && state.pendingCount > 0 && state.hasAnalysisContext) { Text(stringResource(R.string.my_space_analyze)) }
    }) { modifier ->
        LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            item { Text(stringResource(R.string.my_space_news_intro), style = MaterialTheme.typography.headlineSmall) }
            item { Text(stringResource(R.string.my_space_news_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                TextButton(onClick = { editingFocus = true }, enabled = !busy) { Text(stringResource(R.string.my_space_focus)) }
                if (state.focus.isNotBlank()) Text(state.focus, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.my_space_analysis_scope, state.readCount, state.pendingCount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!state.hasAnalysisContext) Text(stringResource(R.string.my_space_analyze_missing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (busy) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.progress.ifBlank { task?.progress_message.orEmpty() }.ifBlank { stringResource(R.string.my_space_queued) }, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.my_space_busy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val error = state.error ?: task?.last_error_message?.takeIf { task?.status == "failed" }
            if (error != null || failed) item {
                Text(error ?: stringResource(R.string.my_space_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { confirming = true }, enabled = !busy && state.hasAnalysisContext) { Text(stringResource(R.string.my_space_retry)) }
            }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OpportunityFilter.entries.forEach { value -> FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(opportunityFilterLabel(value)) }) }
            } }
            val entries = opportunityItems(state.items, filter)
            if (entries.isEmpty()) item {
                MyEmptyBlock(stringResource(R.string.my_space_none), stringResource(if (state.readCount == 0) R.string.my_space_opportunity_hint else R.string.my_space_no_relation), "", {})
            }
            items(entries, key = { it.id }) { entry -> OpportunitySummary(entry) { onOpen(entry.id) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        }
    }
    if (editingFocus) FocusDialog(state.focus, failed, { editingFocus = false }) { value -> viewModel.saveFocus(value) { editingFocus = false } }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text(stringResource(R.string.my_space_analyze)) },
        text = { Text(stringResource(R.string.my_space_analysis_confirm, state.pendingCount)) },
        confirmButton = { TextButton(onClick = { confirming = false; viewModel.analyze() }) { Text(stringResource(R.string.my_space_confirm)) } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.my_space_cancel)) } })
}

@Composable
private fun opportunityFilterLabel(filter: OpportunityFilter) = stringResource(when (filter) {
    OpportunityFilter.PENDING -> R.string.my_space_pending
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
fun NewsOpportunityDetailScreen(id: String, onBack: () -> Unit, onChat: () -> Unit, onArticle: (Long) -> Unit, viewModel: MySpaceViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failure by viewModel.operationFailed.collectAsStateWithLifecycle()
    val activeReminders by viewModel.activeReminderIds.collectAsStateWithLifecycle()
    val item = state.items.firstOrNull { it.id == id }
    var evidence by rememberSaveable(id) { mutableStateOf(false) }
    var reminder by rememberSaveable(id) { mutableStateOf(false) }
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
}

@Composable
private fun OpportunityParagraph(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}
