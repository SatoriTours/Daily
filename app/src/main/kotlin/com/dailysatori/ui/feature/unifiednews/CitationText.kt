package com.dailysatori.ui.feature.unifiednews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown

private val LinkedListItemRegex = Regex("""^\s*[-*+]\s+\[(.*)]\(daily-satori-citation://([^)]+)\)\s*$""")

private sealed class UnifiedNewsBlock {
    data class MarkdownBlock(val content: String) : UnifiedNewsBlock()
    data class BulletItem(val text: String, val citation: String?) : UnifiedNewsBlock()
}

@Composable
fun CitationText(
    content: String,
    modifier: Modifier = Modifier,
    titleOverrides: Map<String, String> = emptyMap(),
    onCitationClick: (String) -> Unit = {},
) {
    val displayContent = remember(content) { unifiedNewsMarkdownWithCitationLinks(displayUnifiedNewsMarkdown(content)) }
    val blocks = remember(displayContent) { unifiedNewsDisplayBlocks(displayContent) }
    val parentUriHandler = LocalUriHandler.current
    val uriHandler = object : UriHandler {
        override fun openUri(uri: String) {
            val citation = citationFromUnifiedNewsUrl(uri)
            if (citation != null) onCitationClick(citation) else parentUriHandler.openUri(uri)
        }
    }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        SelectionContainer {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                blocks.forEach { block ->
                    when (block) {
                        is UnifiedNewsBlock.MarkdownBlock -> Markdown(
                            modifier = Modifier.fillMaxWidth(),
                            content = block.content,
                            typography = MarkdownStyles.summaryTypography(),
                            padding = MarkdownStyles.summaryPadding(),
                        )
                        is UnifiedNewsBlock.BulletItem -> UnifiedNewsBulletItem(
                            text = plainUnifiedNewsListText(titleOverrides[block.citation] ?: block.text),
                            citation = block.citation,
                            onCitationClick = onCitationClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedNewsBulletItem(text: String, citation: String?, onCitationClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = citation != null,
                role = Role.Button,
                onClickLabel = "查看来源",
                onClick = { citation?.let(onCitationClick) },
            )
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = Spacing.s)
                .size(Spacing.xs)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        )
    }
}

private fun unifiedNewsDisplayBlocks(content: String): List<UnifiedNewsBlock> {
    val blocks = mutableListOf<UnifiedNewsBlock>()
    val markdownBuffer = mutableListOf<String>()

    fun flushMarkdownBuffer() {
        val block = markdownBuffer.joinToString("\n").trim()
        if (block.isNotBlank()) blocks += UnifiedNewsBlock.MarkdownBlock(block)
        markdownBuffer.clear()
    }

    content.lines().forEach { line ->
        val linked = LinkedListItemRegex.find(line)
        if (linked != null) {
            flushMarkdownBuffer()
            blocks += UnifiedNewsBlock.BulletItem(linked.groupValues[1], linked.groupValues[2])
        } else {
            markdownBuffer += line
        }
    }
    flushMarkdownBuffer()
    return blocks
}

private fun plainUnifiedNewsListText(text: String): String = text
    .replace(Regex("""\*\*(.*?)\*\*"""), "$1")
    .replace(Regex("""__(.*?)__"""), "$1")
    .replace(Regex("""`([^`]*)`"""), "$1")
    .trim()
