package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.service.mcp.canOpenSearchResult
import com.dailysatori.service.mcp.McpSearchResult
import com.dailysatori.service.mcp.searchResultTypeLabel
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.MarkdownStyles
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

fun chatMessageTreatment(role: String, isError: Boolean): ChatMessageTreatment = when {
    isError -> ChatMessageTreatment.ErrorNote
    role == "user" -> ChatMessageTreatment.MutedUserNote
    else -> ChatMessageTreatment.StructuredAssistantNote
}

fun assistantMessageUsesEditorialRail(): Boolean = true

fun userMessageUsesMutedContainer(): Boolean = true

fun structuredAssistantContent(content: String): StructuredAssistantContent {
    val trimmed = content.trim()
    val firstLine = trimmed.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (!firstLine.startsWith("#")) return StructuredAssistantContent("AI 回复", trimmed)
    val title = firstLine.trimStart('#').trim().ifBlank { "AI 回复" }
    val body = trimmed.lines().drop(1).joinToString("\n").trim()
    return StructuredAssistantContent(title, body.ifBlank { trimmed })
}

@Composable
fun MessageBubble(
    message: ChatMessageUi,
    onReferenceClick: (McpSearchResult) -> Unit = {},
    onDelete: (ChatMessageUi) -> Unit = {},
    onReAsk: (ChatMessageUi) -> Unit = {},
) {
    val isUser = message.role == "user"
    val assistantContent = message.content.trim()
    val treatment = chatMessageTreatment(message.role, message.isError)
    val clipboard = LocalClipboardManager.current
    var showActions by remember(message.id) { mutableStateOf(false) }
    if (!isUser && assistantContent.isBlank() && message.searchResults.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(if (isUser) 0.82f else 0.96f)
                    .pointerInput(message.id) { detectTapGestures(onLongPress = { showActions = true }) },
            ) {
                Column {
                    when (treatment) {
                        ChatMessageTreatment.MutedUserNote -> MutedUserMessage(message.content)
                        ChatMessageTreatment.StructuredAssistantNote -> StructuredAssistantMessage(
                            content = assistantContent,
                            searchResults = message.searchResults,
                            onReferenceClick = onReferenceClick,
                        )
                        ChatMessageTreatment.ErrorNote -> ErrorAssistantMessage(assistantContent)
                    }
                    DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                        DropdownMenuItem(
                            text = { Text("复制") },
                            onClick = {
                                clipboard.setText(AnnotatedString(message.content))
                                showActions = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                showActions = false
                                onDelete(message)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("重问") },
                            onClick = {
                                showActions = false
                                onReAsk(message)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MutedUserMessage(content: String) {
    Surface(
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = content,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
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
    searchResults: List<McpSearchResult>,
    onReferenceClick: (McpSearchResult) -> Unit,
) {
    val structured = structuredAssistantContent(content)
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Surface(
            modifier = Modifier.width(BorderWidth.l).fillMaxHeight(),
            shape = RoundedCornerShape(Radius.circular),
            color = MaterialTheme.colorScheme.outlineVariant,
        ) {}
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            AssistantKicker()
            Text(
                text = structured.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            if (structured.body.isNotBlank()) {
                Markdown(
                    content = structured.body,
                    typography = MarkdownStyles.summaryTypography(),
                    padding = MarkdownStyles.summaryPadding(),
                )
            }
            if (searchResults.isNotEmpty()) {
                SearchResultsSection(searchResults, onReferenceClick)
            }
        }
    }
}

@Composable
private fun AssistantKicker() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Surface(
            modifier = Modifier.width(BorderWidth.l).height(BorderWidth.l),
            shape = RoundedCornerShape(Radius.circular),
            color = MaterialTheme.colorScheme.primary,
        ) {}
        Text(
            text = "AI 回复",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
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
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .padding(start = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.circular),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.clickable { sectionExpanded = !sectionExpanded },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
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
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
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
                    maxLines = 2,
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
