package com.dailysatori.ui.feature.ideatopic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.ideatopic.IdeaMessageRoles
import com.dailysatori.service.ideatopic.IdeaMessageStatus
import com.dailysatori.service.ideatopic.IdeaSessionSummaryStatus
import com.dailysatori.service.ideatopic.IdeaTopicMessage
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.*
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun IdeaTopicSessionScreen(
    topicId: String,
    sessionId: String,
    onBack: () -> Unit,
    viewModel: IdeaTopicSessionViewModel = koinViewModel(key = "idea-session:$sessionId", parameters = { parametersOf(sessionId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val i18n: I18nService = koinInject()
    AppScaffold(
        title = state.title.ifBlank { i18n.t("idea_topic.discussion_title") },
        onBack = onBack,
    ) { modifier ->
        IdeaTopicDiscussionContent("$topicId:$sessionId", viewModel, modifier)
    }
}

/** The topic landing page and legacy session routes share the same persistent discussion UI. */
@Composable
internal fun IdeaTopicDiscussionContent(
    draftKey: String,
    viewModel: IdeaTopicSessionViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val i18n: I18nService = koinInject()
    var inputText by rememberSaveable(draftKey) { mutableStateOf("") }
    var scrollToLatest by remember(draftKey) { mutableStateOf(true) }
    var hasNewReply by remember(draftKey) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lastMessage = state.messages.lastOrNull()
    val lastItemIndex = state.messages.lastIndex + if (state.messages.size >= 30) 1 else 0

    LaunchedEffect(lastMessage?.id, lastMessage?.content?.length) {
        if (lastMessage == null) return@LaunchedEffect
        val layout = listState.layoutInfo
        val lastVisible = layout.visibleItemsInfo.lastOrNull()
        val nearBottom = lastVisible != null && lastVisible.index == layout.totalItemsCount - 1 &&
            lastVisible.offset + lastVisible.size <= layout.viewportEndOffset
        if (scrollToLatest || nearBottom) {
            listState.scrollToItem(lastItemIndex.coerceAtLeast(0))
            scrollToLatest = false
            hasNewReply = false
        } else hasNewReply = true
    }

    Column(modifier.fillMaxSize().imePadding()) {
        if (state.error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
                shape = RoundedCornerShape(Radius.m),
            ) {
                Text(
                    i18n.t(ideaTopicErrorLabelKey(state.error!!)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(Spacing.s),
                )
            }
        }

        if (state.messages.isNotEmpty() || state.summary.isNotBlank()) {
            SessionSummaryCard(
                summary = state.summary,
                summaryStatus = state.summaryStatus,
                canSummarize = state.canSummarize,
                partial = state.summaryPartial,
                onSummarize = viewModel::summarize,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (state.messages.size >= 30) {
                item(key = "load-older") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = viewModel::loadOlder, enabled = !state.isLoadingOlder) {
                            if (state.isLoadingOlder) {
                                CircularProgressIndicator(Modifier.size(IconSize.s), strokeWidth = BorderWidth.s)
                                Spacer(Modifier.width(Spacing.xs))
                            }
                            Text(i18n.t("idea_topic.load_older"))
                        }
                    }
                }
            }
            if (state.messages.isEmpty()) {
                item(key = "empty-prompt") {
                    DiscussionEmptyState(onExample = { inputText = it })
                }
            } else {
                items(state.messages, key = { it.id }) { message ->
                    MessageItem(
                        message = message,
                        canRetry = !state.busy && message.id == state.messages.lastOrNull()?.id,
                        onRetry = { viewModel.retry(message.id) },
                    )
                }
            }
        }

        if (hasNewReply) {
            TextButton(
                onClick = {
                    scope.launch { listState.animateScrollToItem(lastItemIndex.coerceAtLeast(0)) }
                    hasNewReply = false
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text(i18n.t("idea_topic.view_new_reply")) }
        }

        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = Spacing.xs) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(i18n.t("idea_topic.send_hint")) },
                    maxLines = 4,
                    shape = RoundedCornerShape(Radius.m),
                )
                if (state.busy) {
                    IconButton(onClick = viewModel::cancel) {
                        Icon(Icons.Default.Stop, i18n.t("idea_topic.action_cancel_ai"), tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    IconButton(
                        enabled = inputText.isNotBlank() && state.topicId != null,
                        onClick = {
                            val draft = inputText
                            viewModel.send(draft) {
                                if (inputText == draft) inputText = ""
                                scrollToLatest = true
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            i18n.t("idea_topic.send_feedback"),
                            tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscussionEmptyState(onExample: (String) -> Unit) {
    val i18n: I18nService = koinInject()
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(IconSize.xxl), tint = MaterialTheme.colorScheme.primary)
        Text(i18n.t("idea_topic.discussion_empty_title"), style = MaterialTheme.typography.titleMedium)
        Text(
            i18n.t("idea_topic.discussion_empty_hint"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf("discussion_example_idea", "discussion_example_feedback", "discussion_example_blocked").forEach { key ->
            val example = i18n.t("idea_topic.$key")
            OutlinedButton(onClick = { onExample(example) }, modifier = Modifier.fillMaxWidth()) { Text(example) }
        }
    }
}

@Composable
private fun SessionSummaryCard(
    summary: String,
    summaryStatus: String,
    canSummarize: Boolean,
    partial: Boolean,
    onSummarize: () -> Unit,
) {
    val i18n: I18nService = koinInject()
    var showSummary by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.s))
                Text(i18n.t("idea_topic.session_summary"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                if (summary.isNotBlank()) {
                    TextButton(onClick = { showSummary = true }) { Text(i18n.t("idea_topic.view_summary")) }
                }
                if (summaryStatus == IdeaSessionSummaryStatus.Pending) {
                    CircularProgressIndicator(Modifier.size(IconSize.s), strokeWidth = BorderWidth.s)
                } else {
                    TextButton(onClick = onSummarize, enabled = canSummarize) {
                        Text(i18n.t(if (summary.isBlank()) "idea_topic.session_summarize" else "idea_topic.update_summary"))
                    }
                }
            }
            if (summaryStatus == IdeaSessionSummaryStatus.NeedsUpdate) {
                Text(i18n.t("idea_topic.session_summary_needs_update"), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (showSummary && summary.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showSummary = false },
            title = { Text(i18n.t("idea_topic.session_summary")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(i18n.t("idea_topic.summary_notice"), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (partial) Text(i18n.t("idea_topic.session_summary_partial"), style = MaterialTheme.typography.labelSmall)
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSummary = false }) { Text(i18n.t("idea_topic.close")) }
            },
        )
    }
}

@Composable
private fun MessageItem(message: IdeaTopicMessage, canRetry: Boolean, onRetry: () -> Unit) {
    val i18n: I18nService = koinInject()
    val isUser = message.role == IdeaMessageRoles.User
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Surface(
            shape = RoundedCornerShape(
                topStart = Radius.m, topEnd = Radius.m,
                bottomStart = if (isUser) Radius.m else Radius.xs,
                bottomEnd = if (isUser) Radius.xs else Radius.m,
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.widthIn(max = Spacing.xxl * 7),
        ) {
            Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    i18n.t(if (isUser) "idea_topic.message_you" else "idea_topic.message_ai"),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                )
                if (message.content.isNotBlank()) {
                    Text(message.content, style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                }
                if (message.status == IdeaMessageStatus.Pending) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        CircularProgressIndicator(Modifier.size(IconSize.s), strokeWidth = BorderWidth.s)
                        Text(i18n.t("idea_topic.replying"), style = MaterialTheme.typography.labelSmall)
                    }
                } else if (message.status == IdeaMessageStatus.Failed || message.status == IdeaMessageStatus.Interrupted) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            i18n.t(if (message.status == IdeaMessageStatus.Interrupted) "idea_topic.reply_interrupted" else "idea_topic.feedback_saved_reply_failed"),
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        if (canRetry) IconButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, i18n.t("idea_topic.retry"), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
