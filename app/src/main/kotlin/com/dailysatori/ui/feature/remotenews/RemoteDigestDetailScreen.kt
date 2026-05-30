package com.dailysatori.ui.feature.remotenews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.remotenews.RemoteDigest
import com.dailysatori.ui.component.news.MagazineArticleBody
import com.dailysatori.ui.component.news.MagazineArticleHeader
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing

@Composable
fun RemoteDigestDetailScreen(
    digest: RemoteDigest,
    onBack: () -> Unit,
    onArticleClick: (RemoteArticle) -> Unit,
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
                    RemoteArticleSummaryCard(article = article, onClick = { onArticleClick(article) })
                }
            }
        }
    }
}

@Composable
fun DigestBody(digest: RemoteDigest, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        MagazineArticleHeader(
            title = digest.title ?: "总结",
            metaChips = listOfNotNull(remoteDigestTimestampText(digest).takeIf { it.isNotBlank() }),
            intro = digest.summary,
        )
        MagazineArticleBody(
            content = remoteDigestMagazineContent(digest),
            typography = MarkdownStyles.readingTypography(),
            padding = MarkdownStyles.readingPadding(),
        )
    }
}

@Composable
private fun RemoteDigestReferencedArticlesHeader(articleCount: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("引用文章", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        Text("$articleCount 篇", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun remoteDigestTimestampText(digest: RemoteDigest): String {
    val date = digest.date.orEmpty()
    val time = digest.generatedAt.timeText() ?: digest.startedAt.timeText()
    return listOfNotNull(date, time).filter { it.isNotBlank() }.joinToString(" ")
}

internal fun remoteDigestMagazineContent(digest: RemoteDigest): String = buildString {
    digest.summary?.trim()?.takeIf { it.isNotBlank() }?.let { appendLine(it).appendLine() }
    digest.sections.forEach { section ->
        val title = section.topic ?: section.title.orEmpty()
        if (title.isNotBlank()) appendLine("## $title").appendLine()
        section.summary?.trim()?.takeIf { it.isNotBlank() }?.let { appendLine(it).appendLine() }
        section.highlights.map { it.trim() }.filter { it.isNotBlank() }.forEach { appendLine("- $it") }
        appendLine()
    }
}.trim().ifBlank { "暂无总结内容" }

private fun String?.timeText(): String? {
    val value = this?.trim().orEmpty()
    if (value.isBlank()) return null
    val timeStart = value.indexOf('T').takeIf { it >= 0 } ?: value.indexOf(' ').takeIf { it >= 0 } ?: return null
    return value.drop(timeStart + 1).take(5).takeIf { it.length == 5 && it[2] == ':' }
}
