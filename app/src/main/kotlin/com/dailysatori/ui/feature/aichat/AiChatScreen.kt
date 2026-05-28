package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.dailysatori.service.mcp.McpSearchResult
import com.dailysatori.service.mcp.searchResultOpenTarget
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiChatScreen(onArticleClick: (Long) -> Unit = {}, onMyClick: () -> Unit = {}) {
    val viewModel: AiChatViewModel = koinViewModel()
    val referenceDetailViewModel: AiReferenceDetailViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val referenceDetailState by referenceDetailViewModel.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showMemorySheet by remember { mutableStateOf(false) }
    var showReferenceSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    fun openReference(result: McpSearchResult) {
        if (searchResultOpenTarget(result.type) != null) {
            showReferenceSheet = true
            referenceDetailViewModel.load(result)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = "AI 助手",
                showBack = false,
                myNavigationLabel = "我的",
                onMyNavigationClick = onMyClick,
                actions = {
                    IconButton(onClick = { showMemorySheet = true }) {
                        Icon(Icons.Default.Search, contentDescription = "记忆搜索")
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
                onStop = viewModel::stopGeneration,
                isProcessing = state.isProcessing,
            )
        },
    ) { padding ->
        if (state.messages.isEmpty()) {
            EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                icon = Icons.Default.AutoAwesome,
                title = "AI 助手",
                subtitle = "可以搜索记忆、追问文章，也可以整理今天的想法",
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                contentPadding = PaddingValues(top = Spacing.m, bottom = Spacing.l),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onReferenceClick = ::openReference,
                        onDelete = viewModel::deleteMessage,
                        onReAsk = viewModel::reAsk,
                    )
                }
                if (state.isProcessing) {
                    item(key = "thinking") {
                        ThinkingIndicator()
                    }
                }
                if (!state.isProcessing && state.currentStep == aiChatStoppedStatusText()) {
                    item(key = "stopped") {
                        AssistantStatusCard(text = state.currentStep)
                    }
                }
            }
        }
    }

    if (showMemorySheet) {
        MemorySearchSheet(onDismiss = { showMemorySheet = false })
    }

    if (showReferenceSheet) {
        AiReferenceDetailSheet(
            state = referenceDetailState,
            onDismiss = {
                showReferenceSheet = false
                referenceDetailViewModel.clear()
            },
            onArticleClick = { articleId ->
                showReferenceSheet = false
                referenceDetailViewModel.clear()
                onArticleClick(articleId)
            },
        )
    }
}

fun aiChatShowsTopProgressIndicator(isProcessing: Boolean, currentStep: String): Boolean = false

fun aiChatShowsThinkingBubble(isProcessing: Boolean): Boolean = isProcessing

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

@Composable
private fun AssistantStatusCard(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.m),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(Spacing.m),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
