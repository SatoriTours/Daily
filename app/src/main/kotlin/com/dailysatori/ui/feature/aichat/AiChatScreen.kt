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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.service.memory.MemoryExtractService
import com.dailysatori.shared.db.Memory_entry
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.compose.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

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
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(padding),
            )
        }

        if (state.messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
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
                    item {
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
                }
            }
        }
    }

    if (showMemorySheet) {
        MemorySearchSheet(onDismiss = { showMemorySheet = false })
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s).imePadding(),
        ) {
            Surface(
                shape = RoundedCornerShape(Radius.circular),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = Spacing.m, end = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "问我任何问题...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        maxLines = 6,
                        enabled = enabled,
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    FilledIconButton(
                        onClick = onSend,
                        enabled = inputText.isNotBlank() && enabled,
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = contentColorFor(MaterialTheme.colorScheme.primary),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Text(
                "基于你的知识库和记忆回答",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = Spacing.xs, start = Spacing.s),
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessageUi) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = Radius.m, topEnd = Radius.m,
                    bottomStart = if (isUser) Radius.m else Radius.xs,
                    bottomEnd = if (isUser) Radius.xs else Radius.m,
                ),
                color = when {
                    isUser -> MaterialTheme.colorScheme.primary
                    message.isError -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
                modifier = Modifier.fillMaxWidth(0.85f),
            ) {
                if (isUser) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(Spacing.m),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Markdown(
                        content = message.content,
                        colors = com.mikepenz.markdown.m3.markdownColor(),
                        typography = com.dailysatori.ui.theme.MarkdownStyles.cardTypography(),
                        padding = com.dailysatori.ui.theme.MarkdownStyles.cardPadding(),
                        modifier = Modifier.padding(
                            start = Spacing.m, end = Spacing.m,
                            top = Spacing.m, bottom = Spacing.s,
                        ),
                    )
                }
            }
        }

        if (!isUser && message.searchResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Surface(
                shape = RoundedCornerShape(Radius.s),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = Spacing.s),
            ) {
                Column(modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs)) {
                    message.searchResults.take(3).forEach { result ->
                        Text(
                            text = "\uD83D\uDCC4 ${result.type}: ${result.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemorySearchSheet(onDismiss: () -> Unit) {
    val memoryRepo = koinInject<MemoryRepository>()
    val extractService = koinInject<MemoryExtractService>()
    val articleRepo = koinInject<ArticleRepository>()
    val diaryRepo = koinInject<DiaryRepository>()
    val bookRepo = koinInject<BookRepository>()
    val viewpointRepo = koinInject<BookViewpointRepository>()
    var searchQuery by remember { mutableStateOf("") }
    var memories by remember { mutableStateOf<List<Memory_entry>>(emptyList()) }
    var isRebuilding by remember { mutableStateOf(false) }
    var rebuildProgress by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(searchQuery) {
        withContext(Dispatchers.IO) {
            memories = if (searchQuery.isBlank()) {
                memoryRepo.getAllSync()
            } else {
                memoryRepo.search(searchQuery, 50)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.m)) {
            Text("记忆搜索", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(Spacing.s))

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索记忆...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(Radius.m),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )

            if (isRebuilding && rebuildProgress.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.s))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(rebuildProgress, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(Spacing.s))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${memories.size} 条记忆",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        isRebuilding = true
                        scope.launch(Dispatchers.IO) {
                            extractService.rebuildAll(
                                articleRepo, diaryRepo, bookRepo, viewpointRepo,
                                onProgress = { rebuildProgress = it },
                            )
                            memories = memoryRepo.getAllSync()
                            isRebuilding = false
                        }
                    },
                    enabled = !isRebuilding,
                ) {
                    Text("重建全部记忆")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(400.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                contentPadding = PaddingValues(vertical = Spacing.s),
            ) {
                items(memories, key = { it.id }) { memory ->
                    MemoryEntryCard(memory)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.m))
        }
    }
}

@Composable
private fun MemoryEntryCard(memory: Memory_entry) {
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemoryTypeChip(memory.type)
                Spacer(modifier = Modifier.width(Spacing.s))
                Text(
                    memory.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                memory.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MemoryTypeChip(type: String) {
    val (label, color) = when (type) {
        "core" -> "核心" to MaterialTheme.colorScheme.primary
        "content" -> "内容" to MaterialTheme.colorScheme.secondary
        "chat" -> "对话" to MaterialTheme.colorScheme.tertiary
        else -> type to MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(Radius.xs),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
