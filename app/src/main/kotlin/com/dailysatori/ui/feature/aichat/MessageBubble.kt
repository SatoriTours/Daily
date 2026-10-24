package com.dailysatori.ui.feature.aichat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.dailysatori.R
import com.dailysatori.service.assistant.AssistantActionView
import com.dailysatori.service.assistant.AssistantReference
import com.dailysatori.service.mcp.McpSearchResult
import com.dailysatori.service.mcp.canOpenSearchResult
import com.dailysatori.service.mcp.searchResultTypeLabel
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.IconSize
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown

fun visibleReferenceCount(totalCount: Int, expanded: Boolean): Int =
    if (expanded) totalCount else totalCount.coerceAtMost(3)

fun visibleReferenceCount(totalCount: Int, sectionExpanded: Boolean, listExpanded: Boolean): Int =
    if (!sectionExpanded) 0 else visibleReferenceCount(totalCount, listExpanded)

fun referenceSectionActionText(expanded: Boolean): String = if (expanded) "收起引用" else "点击展开"

fun referenceExpansionText(totalCount: Int, expanded: Boolean): String? {
    if (totalCount <= 3) return null
    return if (expanded) "收起引用" else "展开剩余 ${totalCount - 3} 条"
}

enum class ChatMessageTreatment { MutedUserNote, StructuredAssistantNote, ErrorNote }

data class StructuredAssistantContent(val title: String, val body: String)

private val MarkdownAtxHeadingPrefix = Regex("""^#{1,6}(\s+|$)""")
private val ChatUserBubbleMaxWidth = 302.dp

fun chatMessageTreatment(role: String, isError: Boolean): ChatMessageTreatment = when {
    isError -> ChatMessageTreatment.ErrorNote
    role == "user" -> ChatMessageTreatment.MutedUserNote
    else -> ChatMessageTreatment.StructuredAssistantNote
}

fun assistantMessageUsesEditorialRail(): Boolean = false

fun userMessageUsesMutedContainer(): Boolean = true

fun structuredAssistantContent(content: String): StructuredAssistantContent {
    val trimmed = content.trim()
    val firstLine = trimmed.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (!MarkdownAtxHeadingPrefix.containsMatchIn(firstLine)) return StructuredAssistantContent("AI 回复", trimmed)
    val title = firstLine.trimStart('#').trim().ifBlank { "AI 回复" }
    val body = trimmed.lines().drop(1).joinToString("\n").trim()
    return StructuredAssistantContent(title, body)
}

fun assistantShouldRenderStructuredTitle(structured: StructuredAssistantContent): Boolean =
    structured.title != "AI 回复" && structured.body.isNotBlank()

@Composable
fun MessageBubble(
    message: ChatMessageUi,
    onReferenceClick: (McpSearchResult) -> Unit = {},
    onDelete: (ChatMessageUi) -> Unit = {},
    onReAsk: (ChatMessageUi) -> Unit = {},
    onExcludeFromMemory: (ChatMessageUi) -> Unit = {},
    onCancelTurn: (Long) -> Unit = {},
    onRetryTurn: (Long) -> Unit = {},
    onArticleClick: (Long) -> Unit = {},
    onReminderClick: (String) -> Unit = {},
    onTaskCenterClick: () -> Unit = {},
) {
    val isUser = message.role == "user"
    val assistantContent = remember(message.content) { message.content.trim() }
    val treatment = chatMessageTreatment(message.role, message.isError)
    val clipboard = LocalClipboardManager.current
    var showActions by remember(message.id) { mutableStateOf(false) }

    val hasAssistantBody = assistantContent.isNotBlank() || message.searchResults.isNotEmpty() || message.references.isNotEmpty() || message.actions.isNotEmpty()
    if (!isUser && !hasAssistantBody && message.taskDisplayState == TaskDisplayState.None) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            Box(
                modifier = Modifier
                    .chatBubbleWidth(isUser)
                    .pointerInput(message.id, message.isStreaming) { detectTapGestures(onLongPress = { if (!message.isStreaming) showActions = true }) },
            ) {
                Column {
                    when (treatment) {
                        ChatMessageTreatment.MutedUserNote -> MutedUserMessage(
                            content = message.content,
                            isExcluded = message.isExcludedFromMemory,
                        )
                        ChatMessageTreatment.StructuredAssistantNote -> StructuredAssistantMessage(
                            content = assistantContent,
                            isStreaming = message.isStreaming,
                            searchResults = message.searchResults,
                            references = message.references,
                            onReferenceClick = onReferenceClick,
                            onArticleClick = onArticleClick,
                            onReminderClick = onReminderClick,
                        )
                        ChatMessageTreatment.ErrorNote -> ErrorAssistantMessage(
                            content = assistantContent.ifBlank { message.errorMessage },
                        )
                    }

                    if (message.actions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            message.actions.forEach { action ->
                                AssistantActionCard(
                                    action = action,
                                    onArticleClick = onArticleClick,
                                    onReminderClick = onReminderClick,
                                    onTaskCenterClick = onTaskCenterClick,
                                )
                            }
                        }
                    }

                    if (message.taskDisplayState != TaskDisplayState.None) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        TaskStatusCard(
                            taskState = message.taskDisplayState,
                            turnId = message.turnId,
                            taskId = message.taskId,
                            onCancel = onCancelTurn,
                            onRetry = onRetryTurn,
                            onTaskCenterClick = onTaskCenterClick,
                        )
                    }

                    if (showActions) {
                        ChatMessageActionPopup(
                            isUser = isUser,
                            isExcluded = message.isExcludedFromMemory,
                            onDismiss = { showActions = false },
                            onCopy = {
                                clipboard.setText(AnnotatedString(message.content))
                                showActions = false
                            },
                            onDelete = {
                                showActions = false
                                onDelete(message)
                            },
                            onReAsk = {
                                showActions = false
                                onReAsk(message)
                            },
                            onExclude = {
                                showActions = false
                                onExcludeFromMemory(message)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.chatBubbleWidth(isUser: Boolean): Modifier =
    if (isUser) widthIn(max = ChatUserBubbleMaxWidth) else fillMaxWidth()

@Composable
private fun ChatMessageActionPopup(
    isUser: Boolean,
    isExcluded: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onReAsk: () -> Unit,
    onExclude: () -> Unit,
) {
    Popup(
        alignment = Alignment.TopEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier.padding(Spacing.xs),
            shape = RoundedCornerShape(Radius.circular),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ChatMessageActionButton("复制", onCopy)
                ChatMessageActionButton("删除", onDelete)
                if (isUser) {
                    if (!isExcluded) {
                        ChatMessageActionButton(stringResource(R.string.assistant_msg_exclude), onExclude)
                    }
                } else {
                    ChatMessageActionButton("重问", onReAsk)
                }
            }
        }
    }
}

@Composable
private fun ChatMessageActionButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MutedUserMessage(content: String, isExcluded: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(
            topStart = Radius.m,
            topEnd = Radius.m,
            bottomStart = Radius.m,
            bottomEnd = Radius.xs,
        ),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
            Text(
                text = content,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isExcluded) {
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = stringResource(R.string.assistant_msg_excluded_badge),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ErrorAssistantMessage(content: String) {
    Surface(
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = content,
            modifier = Modifier.padding(Spacing.m),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun StructuredAssistantMessage(
    content: String,
    isStreaming: Boolean,
    searchResults: List<McpSearchResult>,
    references: List<AssistantReference> = emptyList(),
    onReferenceClick: (McpSearchResult) -> Unit,
    onArticleClick: (Long) -> Unit,
    onReminderClick: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(
            topStart = Radius.m,
            topEnd = Radius.m,
            bottomStart = Radius.xs,
            bottomEnd = Radius.m,
        ),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (isStreaming) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else if (content.isNotBlank()) {
                val structured = remember(content) { structuredAssistantContent(content) }
                if (assistantShouldRenderStructuredTitle(structured)) {
                    Text(
                        text = structured.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Markdown(
                        content = structured.body,
                        typography = MarkdownStyles.summaryTypography(),
                        padding = MarkdownStyles.summaryPadding(),
                    )
                } else {
                    Markdown(
                        content = content,
                        typography = MarkdownStyles.summaryTypography(),
                        padding = MarkdownStyles.summaryPadding(),
                    )
                }
            }
            if (!isStreaming && searchResults.isNotEmpty()) {
                SearchResultsSection(searchResults, onReferenceClick)
            }
            if (!isStreaming && references.isNotEmpty()) {
                AssistantReferencesSection(references, onArticleClick, onReminderClick)
            }
        }
    }
}

@Composable
private fun AssistantReferencesSection(
    references: List<AssistantReference>,
    onArticleClick: (Long) -> Unit,
    onReminderClick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.m),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "参考来源 · ${references.size} 条",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (expanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded) {
            references.forEach { ref ->
                val articleId = if (ref.source == "article") ref.id.toLongOrNull() else null
                val isReminder = ref.source == "reminder"
                val isClickable = articleId != null || isReminder
                val clickModifier = when {
                    articleId != null -> Modifier.clickable { onArticleClick(articleId) }
                    isReminder -> Modifier.clickable { onReminderClick(ref.id) }
                    else -> Modifier
                }
                Surface(
                    shape = RoundedCornerShape(Radius.s),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().then(clickModifier),
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Text(
                            text = when (ref.source) {
                                "article" -> "文章"
                                "reminder" -> "提醒"
                                "diary" -> "日记"
                                "we_read" -> "微信读书"
                                else -> ref.source
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = ref.title.ifBlank { ref.id },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isClickable) {
                            Text(
                                text = "查看",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsSection(
    results: List<McpSearchResult>,
    onReferenceClick: (McpSearchResult) -> Unit,
) {
    var sectionExpanded by remember(results) { mutableStateOf(false) }
    var listExpanded by remember(results) { mutableStateOf(false) }
    val visibleCount = visibleReferenceCount(results.size, sectionExpanded, listExpanded)
    Spacer(modifier = Modifier.height(Spacing.xs))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.m),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().clickable { sectionExpanded = !sectionExpanded },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "引用来源",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = " · ${results.size} 条 · ${referenceSectionActionText(sectionExpanded)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        results.take(visibleCount).forEach { result ->
            SearchResultCard(result, onReferenceClick)
        }
        if (sectionExpanded) referenceExpansionText(results.size, listExpanded)?.let { actionText ->
            TextButton(onClick = { listExpanded = !listExpanded }) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: McpSearchResult,
    onReferenceClick: (McpSearchResult) -> Unit,
) {
    val canOpen = canOpenSearchResult(result.type)
    Surface(
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canOpen) Modifier.clickable { onReferenceClick(result) } else Modifier),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = searchResultTypeLabel(result.type),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                result.createdAt?.takeIf { it.isNotBlank() }?.let { createdAt ->
                    Text(
                        text = " · ${createdAt.take(10)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (result.isFavorite == true) {
                    Text(
                        text = " · 已收藏",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            result.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = referenceSummaryMaxLines(),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (canOpen) {
                Text(
                    text = "点击查看详情",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun TaskStatusCard(
    taskState: TaskDisplayState,
    turnId: Long?,
    taskId: Long?,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onTaskCenterClick: () -> Unit,
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    if (showCancelDialog && turnId != null) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(R.string.assistant_task_cancel_dialog_title)) },
            text = { Text(stringResource(R.string.assistant_task_cancel_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCancel(turnId)
                        showCancelDialog = false
                    },
                ) {
                    Text(stringResource(R.string.assistant_task_cancel_btn), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(R.string.reminder_cancel))
                }
            },
        )
    }

    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = when (taskState) {
            TaskDisplayState.Failed -> MaterialTheme.colorScheme.errorContainer
            TaskDisplayState.Cancelled -> MaterialTheme.colorScheme.surfaceVariant
            TaskDisplayState.RequiresResume -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                when (taskState) {
                    TaskDisplayState.Waiting -> {
                        Icon(Icons.Filled.HourglassEmpty, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.s))
                        Text(stringResource(R.string.assistant_task_waiting), style = MaterialTheme.typography.bodySmall)
                    }
                    TaskDisplayState.Processing -> {
                        CircularProgressIndicator(strokeWidth = BorderWidth.l, modifier = Modifier.size(IconSize.xs))
                        Text(stringResource(R.string.assistant_task_processing), style = MaterialTheme.typography.bodySmall)
                    }
                    TaskDisplayState.Failed -> {
                        Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(IconSize.s))
                        Text(stringResource(R.string.assistant_task_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    TaskDisplayState.Cancelled -> {
                        Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.s))
                        Text(stringResource(R.string.assistant_task_cancelled), style = MaterialTheme.typography.bodySmall)
                    }
                    TaskDisplayState.RequiresResume -> {
                        Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(IconSize.s))
                        Text(stringResource(R.string.assistant_task_resuming), style = MaterialTheme.typography.bodySmall)
                    }
                    else -> Unit
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if ((taskState == TaskDisplayState.Waiting || taskState == TaskDisplayState.Processing) && turnId != null) {
                    OutlinedButton(
                        onClick = { showCancelDialog = true },
                        shape = RoundedCornerShape(Radius.circular),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.s, vertical = Spacing.xxs),
                    ) {
                        Text(stringResource(R.string.assistant_task_cancel_btn), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if ((taskState == TaskDisplayState.Failed || taskState == TaskDisplayState.RequiresResume) && turnId != null) {
                    OutlinedButton(
                        onClick = { onRetry(turnId) },
                        shape = RoundedCornerShape(Radius.circular),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.s, vertical = Spacing.xxs),
                    ) {
                        Text(stringResource(R.string.assistant_task_retry_btn), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (taskId != null) {
                    IconButton(
                        onClick = onTaskCenterClick,
                        modifier = Modifier.size(Spacing.xxl),
                    ) {
                        Icon(
                            Icons.Filled.Task,
                            contentDescription = stringResource(R.string.assistant_action_task_center),
                            modifier = Modifier.size(IconSize.xs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssistantActionCard(
    action: AssistantActionView,
    onArticleClick: (Long) -> Unit,
    onReminderClick: (String) -> Unit,
    onTaskCenterClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    val statusText = when (action.status) {
                        "planned" -> stringResource(R.string.assistant_action_status_planned)
                        "submitted" -> stringResource(R.string.assistant_action_status_submitted)
                        "succeeded" -> stringResource(R.string.assistant_action_status_succeeded)
                        "needs_input" -> stringResource(R.string.assistant_action_status_needs_input)
                        "cancelled" -> stringResource(R.string.assistant_action_status_cancelled)
                        "failed" -> stringResource(R.string.assistant_action_status_failed)
                        "schedule_pending" -> stringResource(R.string.assistant_action_status_schedule_pending)
                        else -> action.status
                    }
                    val statusColor = when (action.status) {
                        "succeeded" -> MaterialTheme.colorScheme.primary
                        "failed" -> MaterialTheme.colorScheme.error
                        "cancelled" -> MaterialTheme.colorScheme.onSurfaceVariant
                        "needs_input" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    val actionTypeLabel = when (action.kind.lowercase()) {
                        "article" -> stringResource(R.string.assistant_action_article_view)
                        "reminder" -> stringResource(R.string.assistant_action_reminder_view)
                        else -> action.kind
                    }
                    Text(
                        text = actionTypeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "· $statusText",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                }
                if (action.message.isNotBlank()) {
                    Text(
                        text = action.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                val targetId = action.targetId
                if (action.kind == "article" && targetId != null) {
                    val articleId = targetId.toLongOrNull()
                    if (articleId != null) {
                        TextButton(onClick = { onArticleClick(articleId) }) {
                            Text(stringResource(R.string.assistant_action_article_view), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                } else if (action.kind == "reminder" && targetId != null) {
                    TextButton(onClick = { onReminderClick(targetId) }) {
                        Text(stringResource(R.string.assistant_action_reminder_view), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (action.taskId != null) {
                    IconButton(
                        onClick = onTaskCenterClick,
                        modifier = Modifier.size(Spacing.xxl),
                    ) {
                        Icon(
                            Icons.Filled.Task,
                            contentDescription = stringResource(R.string.assistant_action_task_center),
                            modifier = Modifier.size(IconSize.xs),
                        )
                    }
                }
            }
        }
    }
}

fun referenceSummaryMaxLines(): Int = 4
