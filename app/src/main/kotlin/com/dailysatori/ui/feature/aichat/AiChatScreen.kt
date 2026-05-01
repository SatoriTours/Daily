package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiChatScreen() {
    val viewModel: AiChatViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showMemorySheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "AI 助手",
                showBack = false,
                actions = {
                    IconButton(onClick = { showMemorySheet = true }) {
                        Icon(Icons.Default.Search, contentDescription = "记忆搜索")
                    }
                    IconButton(
                        onClick = { viewModel.clearMessages() },
                        enabled = state.messages.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "新对话")
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                enabled = !state.isProcessing,
            )
        },
    ) { padding ->
        if (state.isProcessing && state.currentStep.isNotBlank()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(padding))
        }

        if (state.messages.isEmpty()) {
            AiChatEmptyState(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(vertical = Spacing.m),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
                if (state.isProcessing) {
                    item(key = "thinking") {
                        ThinkingIndicator()
                    }
                }
            }
        }
    }

    if (showMemorySheet) {
        MemorySearchSheet(onDismiss = { showMemorySheet = false })
    }
}

@Composable
private fun AiChatEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(Spacing.m))
            Text("AI 助手", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(Spacing.s))
            Text(
                "基于你的知识库和记忆回答",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = Radius.m, topEnd = Radius.m,
                bottomStart = Radius.xs, bottomEnd = Radius.m,
            ),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            Text(
                text = "思考中...",
                modifier = Modifier.padding(Spacing.m),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
