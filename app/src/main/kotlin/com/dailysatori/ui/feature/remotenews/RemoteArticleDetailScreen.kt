package com.dailysatori.ui.feature.remotenews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.ui.component.content.MarkdownContent
import com.dailysatori.ui.component.content.MarkdownTabPager
import com.dailysatori.ui.component.content.MarkdownTabRow
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.openArticleUrl
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.launch

private val remoteArticleDetailTabTitles = listOf("AI 摘要", "原文")

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
                tabTitles = remoteArticleDetailTabTitles,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
            )
            MarkdownTabPager(pagerState = pagerState, modifier = Modifier.weight(1f)) { page ->
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
