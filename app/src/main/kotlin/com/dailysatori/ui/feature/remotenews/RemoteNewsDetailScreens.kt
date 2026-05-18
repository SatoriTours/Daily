package com.dailysatori.ui.feature.remotenews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.dailysatori.ui.component.content.MarkdownContent
import com.dailysatori.ui.component.content.MarkdownTabPager
import com.dailysatori.ui.component.content.MarkdownTabRow
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.articleCardContentVerticalPaddingDp
import com.dailysatori.ui.feature.article.articleCardHeightDp
import com.dailysatori.ui.feature.article.articleCardSummaryMaxLines
import com.dailysatori.ui.feature.article.openArticleUrl
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch

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
            Text(remoteDigestTimestampText(digest), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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

@Composable
fun RemoteArticleDetailScreen(
    article: RemoteArticle,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        if (selectedTabIndex != pagerState.currentPage) selectedTabIndex = pagerState.currentPage
    }

    LaunchedEffect(selectedTabIndex) {
        if (pagerState.currentPage != selectedTabIndex) pagerState.animateScrollToPage(selectedTabIndex)
    }

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
        Column(modifier = modifier.fillMaxSize()) {
            RemoteArticleHeroCard(article)
            MarkdownTabRow(
                tabTitles = listOf("AI 摘要", "原文"),
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
            )
            MarkdownTabPager(
                pagerState = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item(key = "remote-content-$page") {
                        Box(modifier = Modifier.padding(Spacing.m)) {
                            MarkdownContent(
                                remoteArticleDetailPageContent(
                                    page = page,
                                    summary = article.summary,
                                    viewpoints = article.viewpoints,
                                    original = article.content,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun remoteArticleDetailPageContent(
    page: Int,
    summary: String?,
    viewpoints: List<String>,
    original: String?,
): String = when (page) {
    0 -> remoteArticleSummaryPageContent(summary, viewpoints)
    else -> original?.trim()?.takeIf { it.isNotBlank() } ?: "暂无原文内容"
}

private fun remoteArticleSummaryPageContent(summary: String?, viewpoints: List<String>): String {
    val summaryContent = summary?.trim()?.takeIf { it.isNotBlank() }
    val viewpointContent = viewpoints
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n") { "- $it" }

    return listOfNotNull(
        summaryContent,
        viewpointContent.takeIf { it.isNotBlank() }?.let { "## 关键观点\n\n$it" },
    ).joinToString(separator = "\n\n").ifBlank { "暂无摘要内容" }
}

@Composable
private fun RemoteArticleHeroCard(article: RemoteArticle) {
    Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text("阅读详情", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(article.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            RemoteArticleMetaChips(article)
        }
    }
}

@Composable
private fun RemoteArticleMetaChips(article: RemoteArticle) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        listOfNotNull(
            article.feedName,
            article.domain,
            article.createdAt?.take(10),
            article.importanceScore?.let { "重要性 ${String.format("%.1f", it)}" },
        ).filter { it.isNotBlank() }.take(4).forEach { label ->
            Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.surface) {
                Text(label, modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs), style = MaterialTheme.typography.labelSmall)
            }
        }
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
