package com.dailysatori.ui.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    onNewSegment: () -> Unit,
    onToggleHistory: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onRetryLatest: () -> Unit,
    onViewSessionProcess: (Long) -> Unit = onSelectSession,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        item { BookReflectionHeader(state) }
        state.activeSession?.takeIf { it.summary.isNotBlank() }?.let { session ->
            item { BookReflectionSummaryCard(session) }
        }
        item {
            BookReflectionActions(
                summary = state.activeSession?.summary.orEmpty(),
                isSummarizing = state.isSummarizing,
                showHistory = state.showHistory,
                onGenerateSummary = onGenerateSummary,
                onNewSegment = onNewSegment,
                onToggleHistory = onToggleHistory,
            )
        }
        state.error?.takeIf { it.isNotBlank() }?.let { error -> item { BookReflectionStatusCard(error) } }
        BookReflectionContent(
            state = state,
            onPromptClick = onPromptClick,
            onRetryLatest = onRetryLatest,
            onSelectSession = onSelectSession,
            onViewSessionProcess = onViewSessionProcess,
            onToggleHistory = onToggleHistory,
        )
        item {
            ChatInputField(
                inputText = inputText,
                onInputChange = onInputChange,
                onSend = onSend,
                onStop = onStop,
                isProcessing = state.isProcessing,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.BookReflectionContent(
    state: BookReflectionState,
    onPromptClick: (String) -> Unit,
    onRetryLatest: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onViewSessionProcess: (Long) -> Unit,
    onToggleHistory: () -> Unit,
) {
    if (state.isLoading) {
        item { BookReflectionStatusCard("正在加载思考片段...") }
    } else if (state.showHistory) {
        BookReflectionHistory(state.sessions, onSelectSession, onViewSessionProcess, onToggleHistory)
    } else {
        BookReflectionMessages(state, onPromptClick, onRetryLatest)
    }
}

@Composable
private fun BookReflectionHeader(state: BookReflectionState) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("深入想想", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(state.viewpointTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("《${state.bookTitle}》", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = if (expanded) "收起观点" else "展开观点",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { expanded = !expanded },
        )
        if (expanded) {
            Text(state.viewpointContent, style = MaterialTheme.typography.bodySmall)
            if (state.viewpointExample.isNotBlank()) Text(state.viewpointExample, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BookReflectionActions(
    summary: String,
    isSummarizing: Boolean,
    showHistory: Boolean,
    onGenerateSummary: () -> Unit,
    onNewSegment: () -> Unit,
    onToggleHistory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Button(onClick = onGenerateSummary, enabled = !isSummarizing, modifier = Modifier.fillMaxWidth()) {
            Text(if (isSummarizing) "沉淀中" else if (summary.isBlank()) "沉淀这一段" else "更新沉淀")
        }
        OutlinedButton(onClick = onNewSegment, modifier = Modifier.fillMaxWidth()) { Text("换个角度聊") }
        OutlinedButton(onClick = onToggleHistory, modifier = Modifier.fillMaxWidth()) { Text(if (showHistory) "当前" else "历史") }
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
}

private fun androidx.compose.foundation.lazy.LazyListScope.BookReflectionHistory(
    sessions: List<BookReflectionSessionUi>,
    onSelectSession: (Long) -> Unit,
    onViewSessionProcess: (Long) -> Unit,
    onToggleHistory: () -> Unit,
) {
    items(sessions, key = { it.id }) { session ->
        Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(session.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(session.summary.ifBlank { "还没有沉淀" }, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                OutlinedButton(
                    onClick = {
                        onSelectSession(session.id)
                        onToggleHistory()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("继续聊") }
                OutlinedButton(
                    onClick = {
                        onViewSessionProcess(session.id)
                        onToggleHistory()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("查看过程") }
            }
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
