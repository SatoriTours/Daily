package com.dailysatori.ui.feature.aichat

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.service.mcp.McpSearchResult
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.compose.Markdown

@Composable
fun MessageBubble(message: ChatMessageUi) {
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
                    Markdown(
                        content = message.content,
                        colors = com.mikepenz.markdown.m3.markdownColor(),
                        typography = MarkdownStyles.cardTypography(),
                        padding = MarkdownStyles.cardPadding(),
                        modifier = Modifier.padding(
                            start = Spacing.m, end = Spacing.m,
                            top = Spacing.m, bottom = Spacing.s,
                        ),
                    )
                }
            }
        }

        if (!isUser && message.searchResults.isNotEmpty()) {
            SearchResultsSection(message.searchResults)
        }
    }
}

@Composable
private fun SearchResultsSection(results: List<McpSearchResult>) {
    Spacer(modifier = Modifier.height(Spacing.xxs))
    Surface(
        shape = RoundedCornerShape(Radius.s),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = Spacing.s),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs)) {
            results.take(3).forEach { result ->
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
