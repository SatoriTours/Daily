package com.dailysatori.ui.feature.aichat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailysatori.service.mcp.McpSearchResult
import com.dailysatori.service.mcp.searchResultOpenTarget
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel

@Composable
fun AiChatScreen(onArticleClick: (Long) -> Unit = {}, onMyClick: () -> Unit = {}) {
    val viewModel: AiChatViewModel = koinViewModel()
    val referenceDetailViewModel: AiReferenceDetailViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val referenceDetailState by referenceDetailViewModel.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showReferenceSheet by remember { mutableStateOf(false) }
    val loadOlderMessages = viewModel::loadOlderMessages
    val displayMessages = remember(state.messages) { aiChatDisplayMessages(state.messages) }
    val showThinking = aiChatShowsThinkingBubble(
        isProcessing = state.isProcessing,
        hasStreamingAssistant = displayMessages.any { it.isStreaming },
    )
    val showStoppedStatus = !state.isProcessing && state.currentStep == aiChatStoppedStatusText()
    var hasCompletedInitialScroll by remember { mutableStateOf(false) }

    LaunchedEffect(listState, state.canLoadOlderMessages, state.isLoadingOlderMessages, state.messages.size, displayMessages.firstOrNull()?.id) {
        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val firstVisibleItem = visibleItems.minByOrNull { it.index }
            aiChatShouldLoadOlder(
                firstVisibleItemIndex = firstVisibleItem?.index ?: 0,
                firstVisibleItemKey = firstVisibleItem?.key,
                oldestMessageId = displayMessages.firstOrNull()?.id,
                totalItemsCount = listState.layoutInfo.totalItemsCount,
                isScrollInProgress = listState.isScrollInProgress,
                canLoadOlder = state.canLoadOlderMessages,
                isLoadingOlder = state.isLoadingOlderMessages,
                messageCount = state.messages.size,
            )
        }.distinctUntilChanged().collect { shouldLoad ->
            if (shouldLoad) loadOlderMessages()
        }
    }

    LaunchedEffect(displayMessages.size) {
        if (!aiChatShouldForceInitialBottomScroll(hasCompletedInitialScroll, displayMessages.size)) return@LaunchedEffect
        val targetIndex = aiChatBottomScrollTargetIndex(
            messageCount = displayMessages.size,
            showThinking = showThinking,
            showStoppedStatus = showStoppedStatus,
        )
        if (targetIndex < 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > targetIndex }
        listState.scrollToItem(targetIndex)
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex } }.first { it }
        val bottomScrollOffset = aiChatBottomScrollOffsetForTarget(listState.layoutInfo, targetIndex)
        listState.scrollToItem(targetIndex, scrollOffset = bottomScrollOffset)
        hasCompletedInitialScroll = true
    }

    LaunchedEffect(displayMessages.size, displayMessages.lastOrNull()?.content, state.isProcessing) {
        val targetIndex = aiChatBottomScrollTargetIndex(
            messageCount = displayMessages.size,
            showThinking = showThinking,
            showStoppedStatus = showStoppedStatus,
        )
        if (targetIndex < 0) return@LaunchedEffect
        val appendedStatusRows = if (showThinking || showStoppedStatus) 1 else 0
        val nearBottom = aiChatIsNearBottom(
            lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
            targetIndex = targetIndex,
            appendedStatusRows = appendedStatusRows,
        )
        if (!listState.isScrollInProgress && nearBottom) {
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) {
                listState.scrollToItem(targetIndex)
            }
            val bottomScrollOffset = aiChatBottomScrollOffsetForTarget(listState.layoutInfo, targetIndex)
            listState.animateScrollToItem(targetIndex, scrollOffset = bottomScrollOffset)
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
            AiChatWelcomeBrief(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                contentPadding = PaddingValues(top = Spacing.m, bottom = Spacing.l),
            ) {
                items(
                    items = displayMessages,
                    key = { it.id },
                    contentType = { aiChatMessageContentType(it) },
                ) { message ->
                    MessageBubble(
                        message = message,
                        onReferenceClick = ::openReference,
                        onDelete = viewModel::deleteMessage,
                        onReAsk = viewModel::reAsk,
                    )
                }
                if (showThinking) {
                    item(key = "thinking", contentType = "status") {
                        ThinkingIndicator()
                    }
                }
                if (showStoppedStatus) {
                    item(key = "stopped", contentType = "status") {
                        AssistantStatusCard(text = state.currentStep)
                    }
                }
            }
        }
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

@Composable
private fun AiChatWelcomeBrief(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(horizontal = Spacing.l),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Surface(
                modifier = Modifier.width(BorderWidth.s).fillMaxHeight(),
                shape = RoundedCornerShape(Radius.circular),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
            ) {}
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    text = "Assistant Note",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "把今天的阅读和想法整理成一条线索",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "可以搜索记忆、追问文章，也可以把零散想法整理成可继续写下去的日记草稿。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                WelcomePromptRow(index = "01", title = "整理今天", body = "从新闻、日记和文章里提炼一条主线。")
                WelcomePromptRow(index = "02", title = "追问文章", body = "把阅读里的疑问变成连续批注。")
                WelcomePromptRow(index = "03", title = "搜索记忆", body = "回看过去写下的片段和线索。")
            }
        }
    }
}

@Composable
private fun WelcomePromptRow(index: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            text = index,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun aiChatShowsTopProgressIndicator(isProcessing: Boolean, currentStep: String): Boolean = false

fun aiChatShowsThinkingBubble(isProcessing: Boolean, hasStreamingAssistant: Boolean): Boolean =
    isProcessing && !hasStreamingAssistant

fun aiChatDisplayMessages(messages: List<ChatMessageUi>): List<ChatMessageUi> = messages

fun aiChatMessageContentType(message: ChatMessageUi): String = if (message.role == "user") "user" else "assistant"

fun aiChatBottomScrollTargetIndex(
    messageCount: Int,
    showThinking: Boolean,
    showStoppedStatus: Boolean,
): Int = if (messageCount <= 0) -1 else messageCount - 1 + if (showThinking || showStoppedStatus) 1 else 0

fun aiChatIsNearBottom(
    lastVisibleIndex: Int?,
    targetIndex: Int,
    appendedStatusRows: Int,
): Boolean = lastVisibleIndex == null || lastVisibleIndex >= targetIndex - 1 - appendedStatusRows

fun aiChatShouldForceInitialBottomScroll(hasCompletedInitialScroll: Boolean, messageCount: Int): Boolean =
    !hasCompletedInitialScroll && messageCount > 0

fun aiChatBottomScrollOffset(itemSize: Int, viewportSize: Int, afterContentPadding: Int): Int {
    val visibleViewport = (viewportSize - afterContentPadding).coerceAtLeast(0)
    return (itemSize - visibleViewport).coerceAtLeast(0)
}

private fun aiChatBottomScrollOffsetForTarget(
    layoutInfo: androidx.compose.foundation.lazy.LazyListLayoutInfo,
    targetIndex: Int,
): Int {
    val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex } ?: return 0
    return aiChatBottomScrollOffset(
        itemSize = targetItem.size,
        viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset,
        afterContentPadding = layoutInfo.afterContentPadding,
    )
}

fun aiChatShouldLoadOlder(
    firstVisibleItemIndex: Int,
    firstVisibleItemKey: Any?,
    oldestMessageId: String?,
    totalItemsCount: Int,
    isScrollInProgress: Boolean,
    canLoadOlder: Boolean,
    isLoadingOlder: Boolean,
    messageCount: Int,
): Boolean = messageCount > 0 && totalItemsCount > 0 && canLoadOlder && !isLoadingOlder && isScrollInProgress &&
    firstVisibleItemIndex <= 1 && firstVisibleItemKey == oldestMessageId

@Composable
private fun ThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "ai-thinking")
    val iconScale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(animation = tween(860), repeatMode = RepeatMode.Reverse),
        label = "ai-thinking-scale",
    )
    val iconAlpha by transition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(860), repeatMode = RepeatMode.Reverse),
        label = "ai-thinking-alpha",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.circular),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp).scale(iconScale).alpha(iconAlpha),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "AI 正在思考",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
