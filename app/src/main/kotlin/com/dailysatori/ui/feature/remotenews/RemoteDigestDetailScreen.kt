package com.dailysatori.ui.feature.remotenews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailysatori.service.remotenews.RemoteDigest
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown

@Composable
fun RemoteDigestDetailScreen(
    digest: RemoteDigest,
    onBack: () -> Unit,
    onArticleClick: (Long) -> Unit,
) {
    BackHandler(onBack = onBack)

    AppScaffold(title = digest.title ?: "总结", onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m),
        ) {
            item { DigestBody(digest) }
            if (digest.articles.isNotEmpty()) {
                item { HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.s)) }
                item { RemoteDigestReferencedArticlesHeader(digest.articles.size) }
                items(digest.articles, key = { it.id }) { article ->
                    RemoteArticleSummaryCard(article = article, onClick = { onArticleClick(article.id) })
                }
            }
        }
    }
}

@Composable
fun DigestBody(digest: RemoteDigest, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.xs))
            Text(remoteDigestTimestampText(digest), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }

        digest.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            SelectionContainer {
                Markdown(
                    content = summary,
                    typography = MarkdownStyles.readingTypography(),
                    padding = MarkdownStyles.readingPadding(),
                )
            }
        }

        digest.sections.forEach { section ->
            DigestSection(sectionTitle = section.topic ?: section.title.orEmpty(), summary = section.summary, highlights = section.highlights)
        }
    }
}

@Composable
private fun RemoteDigestReferencedArticlesHeader(articleCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(Spacing.s))
        Text("引用文章", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        Text("$articleCount 篇", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DigestSection(sectionTitle: String, summary: String?, highlights: List<String>) {
    if (sectionTitle.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(Spacing.s))
            Text(sectionTitle, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
    summary?.takeIf { it.isNotBlank() }?.let { secSummary ->
        SelectionContainer {
            Markdown(
                content = secSummary,
                typography = MarkdownStyles.readingTypography(),
                padding = MarkdownStyles.readingPadding(),
            )
        }
    }
    highlights.forEach { highlight ->
        Row(modifier = Modifier.padding(start = Spacing.m)) {
            Text("•", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Spacing.s))
            Text(highlight, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

internal fun remoteDigestTimestampText(digest: RemoteDigest): String {
    val date = digest.date.orEmpty()
    val time = digest.generatedAt.timeText() ?: digest.startedAt.timeText()
    return listOfNotNull(date, time).filter { it.isNotBlank() }.joinToString(" ")
}

private fun String?.timeText(): String? {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return null
    val timeStart = value.indexOf('T').takeIf { it >= 0 } ?: value.indexOf(' ').takeIf { it >= 0 } ?: return null
    return value.drop(timeStart + 1).take(5).takeIf { it.length == 5 && it[2] == ':' }
}
