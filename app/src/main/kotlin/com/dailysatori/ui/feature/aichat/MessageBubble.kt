package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

@Composable
fun MessageBubble(
    message: ChatMessageUi,
    onReferenceClick: (McpSearchResult) -> Unit = {},
    onDelete: (ChatMessageUi) -> Unit = {},
    onReAsk: (ChatMessageUi) -> Unit = {},
) {
    val isUser = message.role == "user"
    val assistantContent = message.content.trim()
    val clipboard = LocalClipboardManager.current
    var showActions by remember(message.id) { mutableStateOf(false) }
    if (!isUser && assistantContent.isBlank() && message.searchResults.isEmpty()) return

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
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .pointerInput(message.id) {
                        detectTapGestures(onLongPress = { showActions = true })
                    },
            ) {
                Column {
                    if (isUser) {
                        Text(
                            text = message.content,
                            modifier = Modifier.padding(Spacing.m),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        if (assistantContent.isNotBlank()) {
                            Column(modifier = Modifier.padding(Spacing.m)) {
                                Markdown(
                                    content = assistantContent,
                                    typography = MarkdownStyles.cardTypography(),
                                    padding = MarkdownStyles.cardPadding(),
                                )
                            }
                        }
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

        if (!isUser && message.searchResults.isNotEmpty()) {
            SearchResultsSection(message.searchResults, onReferenceClick)
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
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .padding(start = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.l),
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canOpen) Modifier.clickable { onReferenceClick(result) } else Modifier),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
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
