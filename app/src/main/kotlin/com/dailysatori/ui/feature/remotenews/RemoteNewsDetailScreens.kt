package com.dailysatori.ui.feature.remotenews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.remotenews.RemoteDigest
import com.dailysatori.ui.component.card.CustomCard
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.articleCardContentVerticalPaddingDp
import com.dailysatori.ui.feature.article.articleCardHeightDp
import com.dailysatori.ui.feature.article.articleCardSummaryMaxLines
import com.dailysatori.ui.feature.article.openArticleUrl
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Radius
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.m),
        ) {
            item { DigestBody(digest) }
            if (digest.articles.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.s))
                }
                item {
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
                        Text("${digest.articles.size} 篇", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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
            Text(digest.date.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }

        digest.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            SelectionContainer {
                Markdown(
                    content = summary,
                    typography = MarkdownStyles.typography(),
                    padding = MarkdownStyles.padding(),
                )
            }
        }

        digest.sections.forEach { section ->
            val sectionTitle = section.topic ?: section.title.orEmpty()
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
            section.summary?.takeIf { it.isNotBlank() }?.let { secSummary ->
                SelectionContainer {
                    Markdown(
                        content = secSummary,
                        typography = MarkdownStyles.typography(),
                        padding = MarkdownStyles.padding(),
                    )
                }
            }
            section.highlights.forEach { h ->
                Row(modifier = Modifier.padding(start = Spacing.m)) {
                    Text("•", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Spacing.s))
                    Text(h, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun RemoteArticleDetailScreen(
    article: RemoteArticle,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    AppScaffold(
        title = article.domain ?: article.feedName ?: "文章",
        onBack = onBack,
        actions = {
            IconButton(onClick = { openArticleUrl(context, article.url) }) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = "在浏览器打开")
            }
        },
    ) { modifier ->
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.m),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(article.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        article.feedName?.let {
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(it, modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        article.createdAt?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            val articleSummary = article.summary?.takeIf { it.isNotBlank() }
            if (articleSummary != null || article.viewpoints.isNotEmpty()) {
                item { HorizontalDivider() }
                item { SectionHeader("AI 摘要") }
                item {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            articleSummary?.let {
                                Markdown(content = it, typography = MarkdownStyles.cardTypography(), padding = MarkdownStyles.cardPadding())
                            }
                            article.viewpoints.forEach { viewpoint ->
                                Row {
                                    Text("•", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                    Spacer(Modifier.width(Spacing.s))
                                    Text(viewpoint, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            val articleContent = article.content?.takeIf { it.isNotBlank() }
            if (articleContent != null) {
                item { HorizontalDivider() }
                item { SectionHeader("原文") }
                item {
                    SelectionContainer {
                        Markdown(
                            content = articleContent,
                            typography = MarkdownStyles.typography(),
                            padding = MarkdownStyles.padding(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(Spacing.s))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun RemoteArticleSummaryCard(article: RemoteArticle, onClick: () -> Unit) {
    CustomCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(articleCardHeightDp.dp)) {
            RemoteArticleCover(article.coverUrl, modifier = Modifier.width(120.dp).fillMaxHeight())
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.m, vertical = articleCardContentVerticalPaddingDp.dp),
            ) {
                Text(article.title.orEmpty(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                article.summary?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = articleCardSummaryMaxLines, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    article.feedName?.let { name ->
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Text(name, modifier = Modifier.padding(horizontal = Spacing.s, vertical = 1.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    article.domain?.let { domain ->
                        if (article.feedName != null) Spacer(Modifier.width(Spacing.xs))
                        Text(domain, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteArticleCover(coverUrl: String?, modifier: Modifier = Modifier) {
    if (coverUrl.isNullOrBlank()) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(topStart = Radius.m, bottomStart = Radius.m))
                .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHighest))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        }
        return
    }
    val context = LocalContext.current
    val request = remember(context, coverUrl) { ImageRequest.Builder(context).data(coverUrl).build() }
    AsyncImage(
        model = request,
        placeholder = painterResource(android.R.drawable.ic_menu_gallery),
        error = painterResource(android.R.drawable.ic_menu_report_image),
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(topStart = Radius.m, bottomStart = Radius.m)),
        contentScale = ContentScale.Crop,
    )
}
