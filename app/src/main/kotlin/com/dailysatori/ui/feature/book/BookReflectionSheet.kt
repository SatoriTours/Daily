package com.dailysatori.ui.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
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
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        BookReflectionHeader(state)
        state.activeSession?.takeIf { it.summary.isNotBlank() }?.let { BookReflectionSummaryCard(it) }
        BookReflectionActions(
            summary = state.activeSession?.summary.orEmpty(),
            isSummarizing = state.isSummarizing,
            showHistory = state.showHistory,
            onGenerateSummary = onGenerateSummary,
            onNewSegment = onNewSegment,
            onToggleHistory = onToggleHistory,
        )
        if (state.showHistory) {
            BookReflectionHistory(state.sessions, onSelectSession)
        } else {
            BookReflectionMessages(state, onPromptClick, onRetryLatest)
        }
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
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Button(onClick = onGenerateSummary, enabled = !isSummarizing) {
            Text(if (isSummarizing) "沉淀中" else if (summary.isBlank()) "沉淀这一段" else "更新沉淀")
        }
        OutlinedButton(onClick = onNewSegment) { Text("换个角度聊") }
        OutlinedButton(onClick = onToggleHistory) { Text(if (showHistory) "当前" else "历史") }
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
private fun BookReflectionMessages(
    state: BookReflectionState,
    onPromptClick: (String) -> Unit,
    onRetryLatest: () -> Unit,
) {
    if (state.messages.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            bookReflectionStartingPrompts().forEach { prompt ->
                AssistChip(onClick = { onPromptClick(prompt) }, label = { Text(prompt) })
            }
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        items(state.messages, key = { it.id }) { message ->
            MessageBubble(message = message.toChatMessageUi())
        }
        if (bookReflectionCanRetryLatest(state.messages)) {
            item { OutlinedButton(onClick = onRetryLatest) { Text("重新生成") } }
        }
    }
}

@Composable
private fun BookReflectionHistory(
    sessions: List<BookReflectionSessionUi>,
    onSelectSession: (Long) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        items(sessions, key = { it.id }) { session ->
            Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(modifier = Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(session.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(session.summary.ifBlank { "还没有沉淀" }, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        OutlinedButton(onClick = { onSelectSession(session.id) }) { Text("继续聊") }
                        OutlinedButton(onClick = { onSelectSession(session.id) }) { Text("查看过程") }
                    }
                }
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
