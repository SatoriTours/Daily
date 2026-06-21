package com.dailysatori.ui.component.news

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.m3.Markdown

@Composable
fun ArticleReaderHeader(
    title: String,
    metaChips: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = title.ifBlank { "文章详情" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val meta = metaChips.map { it.trim() }.filter { it.isNotBlank() }.joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ArticleReaderBody(
    content: String,
    modifier: Modifier = Modifier,
    typography: MarkdownTypography = MarkdownStyles.readingTypography(),
    padding: MarkdownPadding = MarkdownStyles.readingPadding(),
) {
    SelectionContainer {
        Box(modifier = modifier.fillMaxWidth()) {
            Markdown(content = content, typography = typography, padding = padding)
        }
    }
}

@Composable
fun MagazineArticleHeader(
    title: String,
    metaChips: List<String>,
    intro: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        MagazineArticleMetaChips(metaChips)
        Text(
            text = title.ifBlank { "文章详情" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        intro?.trim()?.takeIf { it.isNotBlank() }?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun MagazineArticleTabSelector(
    titles: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        selectedLabelColor = MaterialTheme.colorScheme.primary,
        selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
    )
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        titles.forEachIndexed { index, title ->
            FilterChip(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                label = { Text(title) },
                colors = tabColors,
            )
        }
    }
}

@Composable
fun MagazineArticleBody(
    content: String,
    modifier: Modifier = Modifier,
    typography: MarkdownTypography = MarkdownStyles.readingTypography(),
    padding: MarkdownPadding = MarkdownStyles.readingPadding(),
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surface,
    ) {
        SelectionContainer {
            Box(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m)) {
                Markdown(content = content, typography = typography, padding = padding)
            }
        }
    }
}

@Composable
private fun MagazineArticleMetaChips(metaChips: List<String>) {
    val chips = metaChips.map { it.trim() }.filter { it.isNotBlank() }
    if (chips.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        chips.forEach { chip ->
            Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(
                    text = chip,
                    modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
