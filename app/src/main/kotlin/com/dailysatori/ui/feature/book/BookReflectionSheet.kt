package com.dailysatori.ui.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.component.dialog.ConfirmDialog
import com.dailysatori.ui.feature.aichat.ChatInputField
import com.dailysatori.ui.feature.aichat.ChatMessageUi
import com.dailysatori.ui.feature.aichat.MessageBubble
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing

@Composable
fun BookReflectionSheet(
    state: BookReflectionState,
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPromptClick: (String) -> Unit,
    onGenerateSummary: () -> Unit,
    onNewQuestion: () -> Unit,
    onShowCurrent: () -> Unit,
    onShowHistory: () -> Unit,
    onShowSettled: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onRetryLatest: () -> Unit,
    onViewSessionProcess: (Long) -> Unit = onSelectSession,
) {
    var sessionPendingDelete by remember { mutableStateOf<BookReflectionSessionUi?>(null) }
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f)) {
        BookReflectionHeader(
            state = state,
            onNewQuestion = onNewQuestion,
            onShowHistory = onShowHistory,
            onShowSettled = onShowSettled,
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.s),
        )
        BookReflectionScrollableContent(
            state = state,
            onPromptClick = onPromptClick,
            onGenerateSummary = onGenerateSummary,
            onNewQuestion = onNewQuestion,
            onShowCurrent = onShowCurrent,
            onShowHistory = onShowHistory,
            onShowSettled = onShowSettled,
            onSelectSession = onSelectSession,
            onDeleteSessionRequest = { sessionPendingDelete = it },
            onRetryLatest = onRetryLatest,
            onViewSessionProcess = onViewSessionProcess,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        ChatInputField(
            inputText = inputText,
            onInputChange = onInputChange,
            onSend = onSend,
            onStop = onStop,
            isProcessing = state.isProcessing,
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.xxl),
        )
    }
    sessionPendingDelete?.let { session ->
        ConfirmDialog(
            title = "删除对话",
            message = "确定要删除「${session.title}」吗？这会同时删除这段对话里的所有消息。",
            confirmText = "删除",
            onConfirm = {
                sessionPendingDelete = null
                onDeleteSession(session.id)
            },
            onDismiss = {
                sessionPendingDelete = null
            },
        )
    }
}

@Composable
private fun BookReflectionScrollableContent(
    state: BookReflectionState,
    onPromptClick: (String) -> Unit,
    onGenerateSummary: () -> Unit,
    onNewQuestion: () -> Unit,
    onShowCurrent: () -> Unit,
    onShowHistory: () -> Unit,
    onShowSettled: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onDeleteSessionRequest: (BookReflectionSessionUi) -> Unit,
    onRetryLatest: () -> Unit,
    onViewSessionProcess: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val latestMessage = state.messages.lastOrNull()

    LaunchedEffect(state.messages.size, state.isProcessing, latestMessage?.content?.length, latestMessage?.status, state.reflectionView) {
        if (state.reflectionView == BookReflectionView.Current && listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, bottom = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        state.activeSession?.takeIf { state.reflectionView == BookReflectionView.Current && it.summary.isNotBlank() }?.let { session ->
            item { BookReflectionSummaryCard(session) }
        }
        state.activeSession?.takeIf { state.reflectionView == BookReflectionView.Current && it.summaryStatus == "failed" }?.let { session ->
            item { BookReflectionStatusCard(session.summaryError.ifBlank { "沉淀失败，请稍后重试。" }) }
        }
        if (state.reflectionView == BookReflectionView.Current) {
            item {
                BookReflectionQuestionRows(
                    hasMessages = state.messages.isNotEmpty(),
                    isProcessing = state.isProcessing,
                    onPromptClick = onPromptClick,
                )
            }
        }
        state.error?.takeIf { it.isNotBlank() }?.let { error -> item { BookReflectionStatusCard(error) } }
        BookReflectionContent(
            state = state,
            onPromptClick = onPromptClick,
            onGenerateSummary = onGenerateSummary,
            onRetryLatest = onRetryLatest,
            onSelectSession = onSelectSession,
            onViewSessionProcess = onViewSessionProcess,
            onDeleteSessionRequest = onDeleteSessionRequest,
            onShowCurrent = onShowCurrent,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.BookReflectionContent(
    state: BookReflectionState,
    onPromptClick: (String) -> Unit,
    onGenerateSummary: () -> Unit,
    onRetryLatest: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onViewSessionProcess: (Long) -> Unit,
    onDeleteSessionRequest: (BookReflectionSessionUi) -> Unit,
    onShowCurrent: () -> Unit,
) {
    if (state.isLoading) {
        item { BookReflectionStatusCard("正在加载思考片段...") }
    } else when (state.reflectionView) {
        BookReflectionView.Current -> BookReflectionMessages(state, onPromptClick, onGenerateSummary, onRetryLatest)
        BookReflectionView.History -> BookReflectionHistory(
            sessions = bookReflectionHistorySessions(
                sessions = state.sessions,
                activeSession = state.activeSession,
                activeMessages = state.messages,
            ),
            isProcessing = state.isProcessing,
            onSelectSession = onSelectSession,
            onViewSessionProcess = onViewSessionProcess,
            onDeleteSessionRequest = onDeleteSessionRequest,
            onShowCurrent = onShowCurrent,
        )
        BookReflectionView.Settled -> BookReflectionSettled(
            sessions = bookReflectionSettledSessions(state.sessions),
            isProcessing = state.isProcessing,
            onViewSessionProcess = onViewSessionProcess,
            onDeleteSessionRequest = onDeleteSessionRequest,
            onShowCurrent = onShowCurrent,
        )
    }
}

@Composable
private fun BookReflectionHeader(
    state: BookReflectionState,
    onNewQuestion: () -> Unit,
    onShowHistory: () -> Unit,
    onShowSettled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("想一想", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            BookReflectionHeaderActions(
                actionState = bookReflectionHeaderActionState(state),
                onNewQuestion = onNewQuestion,
                onShowHistory = onShowHistory,
                onShowSettled = onShowSettled,
            )
        }
    }
}

@Composable
private fun BookReflectionHeaderActions(
    actionState: BookReflectionHeaderActionState,
    onNewQuestion: () -> Unit,
    onShowHistory: () -> Unit,
    onShowSettled: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), modifier = Modifier.padding(Spacing.xxs)) {
            BookReflectionHeaderIconAction(
                selected = false,
                enabled = actionState.newQuestionEnabled,
                onClick = onNewQuestion,
                contentDescription = "新增问题",
            )
            {
                Icon(Icons.Filled.Add, contentDescription = "新增问题")
            }
            BookReflectionHeaderIconAction(
                selected = actionState.historySelected,
                onClick = onShowHistory,
                enabled = actionState.historyEnabled,
                contentDescription = "历史",
            )
            {
                Icon(Icons.Filled.History, contentDescription = "历史")
            }
            BookReflectionHeaderIconAction(
                selected = actionState.settledSelected,
                onClick = onShowSettled,
                enabled = actionState.settledEnabled,
                contentDescription = "已沉淀",
            )
            {
                Icon(Icons.Filled.CheckCircle, contentDescription = "已沉淀")
            }
        }
    }
}

@Composable
private fun BookReflectionHeaderIconAction(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    icon: @Composable () -> Unit,
) {
    val selectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val iconTint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(Radius.circular),
        color = if (selected) selectedColor else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides iconTint) {
                icon()
            }
        }
    }
}

@Composable
private fun BookReflectionQuestionRows(
    hasMessages: Boolean,
    isProcessing: Boolean,
    onPromptClick: (String) -> Unit,
) {
    val prompts = if (hasMessages) {
        listOf(
            "继续追问" to "这个提醒可以怎么落到我今天的一个决定里？",
            "换个角度" to bookReflectionAlternativePrompt(),
        )
    } else {
        listOf(
            "补角度" to bookReflectionStartingPrompts()[0],
            "举例子" to bookReflectionStartingPrompts()[1],
            "反问我" to bookReflectionStartingPrompts()[2],
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        prompts.forEach { (label, prompt) ->
            BookReflectionQuestionRow(label = label, prompt = prompt, enabled = !isProcessing, onClick = { onPromptClick(prompt) })
        }
    }
}

@Composable
private fun BookReflectionQuestionRow(
    label: String,
    prompt: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.padding(Spacing.m)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BookReflectionStatusCard(text: String) {
    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            text = text,
            modifier = Modifier.padding(Spacing.m),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BookReflectionSummaryCard(session: BookReflectionSessionUi) {
    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("已沉淀", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(session.summary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyBookReflectionPrompts(onPromptClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        bookReflectionStartingPrompts().forEach { prompt ->
            AssistChip(onClick = { onPromptClick(prompt) }, label = { Text(prompt) })
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.BookReflectionMessages(
    state: BookReflectionState,
    onPromptClick: (String) -> Unit,
    onGenerateSummary: () -> Unit,
    onRetryLatest: () -> Unit,
) {
    if (state.messages.isEmpty()) {
        item { EmptyBookReflectionPrompts(onPromptClick) }
        return
    }
    items(state.messages, key = { it.id }) { message ->
        MessageBubble(message = message.toChatMessageUi())
    }
    if (bookReflectionCanRetryLatest(state.messages)) {
        item { OutlinedButton(onClick = onRetryLatest, modifier = Modifier.fillMaxWidth()) { Text("重新生成") } }
    }
    if (bookReflectionShouldShowSettleAction(state.messages, state.reflectionView)) {
        item {
            BookReflectionSummaryAction(
                summary = state.activeSession?.summary.orEmpty(),
                isSummarizing = state.isSummarizing,
                onGenerateSummary = onGenerateSummary,
            )
        }
    }
}

@Composable
private fun BookReflectionSummaryAction(
    summary: String,
    isSummarizing: Boolean,
    onGenerateSummary: () -> Unit,
) {
    Button(onClick = onGenerateSummary, enabled = !isSummarizing, modifier = Modifier.fillMaxWidth()) {
        Text(if (isSummarizing) "沉淀中" else if (summary.isBlank()) "沉淀" else "更新")
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.BookReflectionHistory(
    sessions: List<BookReflectionSessionUi>,
    isProcessing: Boolean,
    onSelectSession: (Long) -> Unit,
    onViewSessionProcess: (Long) -> Unit,
    onDeleteSessionRequest: (BookReflectionSessionUi) -> Unit,
    onShowCurrent: () -> Unit,
) {
    item {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("反思历史", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "回到当前",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(enabled = !isProcessing) { onShowCurrent() },
            )
        }
    }
    items(sessions, key = { it.id }) { session ->
        BookReflectionHistoryItem(
            session = session,
            isProcessing = isProcessing,
            onClick = {
                onViewSessionProcess(session.id)
            },
            onLongClick = { onDeleteSessionRequest(session) },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.BookReflectionSettled(
    sessions: List<BookReflectionSessionUi>,
    isProcessing: Boolean,
    onViewSessionProcess: (Long) -> Unit,
    onDeleteSessionRequest: (BookReflectionSessionUi) -> Unit,
    onShowCurrent: () -> Unit,
) {
    item {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("已沉淀", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "回到当前",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(enabled = !isProcessing) { onShowCurrent() },
            )
        }
    }
    if (sessions.isEmpty()) {
        item { BookReflectionStatusCard("还没有沉淀。") }
    } else {
        items(sessions, key = { it.id }) { session ->
            BookReflectionHistoryItem(
                session = session,
                isProcessing = isProcessing,
                onClick = { onViewSessionProcess(session.id) },
                onLongClick = { onDeleteSessionRequest(session) },
            )
        }
    }
}

@Composable
private fun BookReflectionHistoryItem(
    session: BookReflectionSessionUi,
    isProcessing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            enabled = !isProcessing,
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(session.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(session.summary.ifBlank { "还没有沉淀" }, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (session.summary.isBlank()) "未沉淀" else "已沉淀",
                style = MaterialTheme.typography.labelMedium,
                color = if (session.summary.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun BookReflectionMessageUi.toChatMessageUi(): ChatMessageUi = ChatMessageUi(
    id = id,
    role = role,
    content = if (status == "failed" && errorMessage.isNotBlank()) errorMessage else content,
    timestamp = createdAt,
    isError = status == "failed",
    isStreaming = isStreaming,
)
