package com.dailysatori.ui.feature.ideatopic

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailysatori.R
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
    viewModel: IdeaTopicSessionViewModel = koinViewModel(parameters = { parametersOf(sessionId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val i18n: I18nService = koinInject()
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Scroll to bottom when new messages arrive
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    AppScaffold(
        title = state.title.ifBlank { i18n.t("idea_topic.tab_conversations") },
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier.fillMaxSize()
        ) {
            // Error banner
            if (state.error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
                    shape = RoundedCornerShape(Radius.m),
                ) {
                    Text(
                        text = i18n.t(ideaTopicErrorLabelKey(state.error!!)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(Spacing.s),
                    )
                }
            }

            // Summary Section
            SessionSummaryCard(
                summary = state.summary,
                summaryStatus = state.summaryStatus,
                canSummarize = state.canSummarize,
                partial = state.summaryPartial,
                onSummarize = viewModel::summarize,
            )

            // Message List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                // Load older messages button
                if (state.messages.size >= 30) {
                    item(key = "load-older") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(
                                onClick = viewModel::loadOlder,
                                enabled = !state.isLoadingOlder,
                            ) {
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = i18n.t("idea_topic.session_messages_empty"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(state.messages, key = { it.id }) { message ->
                        MessageItem(
                            message = message,
                            onRetry = { viewModel.retry(message.id) },
                        )
                    }
                }
            }

            // Bottom Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = Spacing.xs,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.m, vertical = Spacing.xs),
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
                            Icon(Icons.Default.Stop, contentDescription = i18n.t("idea_topic.action_cancel_ai"), tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                val text = inputText.trim()
                                if (text.isNotBlank()) {
                                    viewModel.send(text)
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.s))
                    Text(
                        text = i18n.t("idea_topic.session_summary"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (summaryStatus == IdeaSessionSummaryStatus.NeedsUpdate) {
                    Surface(
                        shape = RoundedCornerShape(Radius.s),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = i18n.t("idea_topic.session_summary_needs_update"),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                        )
                    }
                }
            }

            if (partial) Text(i18n.t("idea_topic.session_summary_partial"), style = MaterialTheme.typography.labelSmall)
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (summaryStatus == IdeaSessionSummaryStatus.Pending) {
                    Text(
                        text = i18n.t("idea_topic.session_summarizing"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TextButton(onClick = onSummarize, enabled = canSummarize) {
                        Text(i18n.t("idea_topic.session_summarize"), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageItem(
    message: IdeaTopicMessage,
    onRetry: () -> Unit,
) {
    val i18n: I18nService = koinInject()
    val isUser = message.role == IdeaMessageRoles.User

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = Radius.m,
                topEnd = Radius.m,
                bottomStart = if (isUser) Radius.m else Radius.xs,
                bottomEnd = if (isUser) Radius.xs else Radius.m,
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.widthIn(max = Spacing.xxl * 7),
        ) {
            Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (message.content.isNotBlank()) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (message.status == IdeaMessageStatus.Pending) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(IconSize.s),
                            strokeWidth = BorderWidth.s,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = i18n.t("idea_topic.action_proposing"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (message.status == IdeaMessageStatus.Failed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = i18n.t("idea_topic.error.ai_failure") + (message.error?.let { " ($it)" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = i18n.t("idea_topic.retry"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(IconSize.s))
                        }
                    }
                } else if (message.status == IdeaMessageStatus.Interrupted) {
                    Text(
                        text = "（已中断）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
