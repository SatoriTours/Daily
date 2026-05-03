package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun MessageBubble(
    message: ChatMessageUi,
    onReferenceClick: (McpSearchResult) -> Unit = {},
) {
    val isUser = message.role == "user"
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
                    Column(modifier = Modifier.padding(Spacing.m)) {
                        Markdown(
                            content = message.content,
                            typography = MarkdownStyles.cardTypography(),
                            padding = MarkdownStyles.cardPadding(),
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
    Spacer(modifier = Modifier.height(Spacing.xs))
    Column(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .padding(start = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "引用来源",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = " · ${results.size} 条",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        results.take(3).forEach { result ->
            SearchResultCard(result, onReferenceClick)
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
