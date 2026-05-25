package com.dailysatori.ui.feature.remotenews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.service.parser.normalizeArticleMarkdownImages
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.ui.component.content.MarkdownTabPager
import com.dailysatori.ui.component.content.MarkdownTabRow
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.articleCoverHeightAfterScroll
import com.dailysatori.ui.feature.article.articleCoverMaxHeightDp
import com.dailysatori.ui.feature.article.openArticleUrl
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch

private val remoteArticleDetailTabTitles = listOf("AI 摘要", "原文")

@Composable
fun RemoteArticleDetailScreen(
    article: RemoteArticle,
    onBack: () -> Unit,
    isFavorite: Boolean = false,
    onFavoriteClick: () -> Unit = {},
    showFavoriteAction: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var coverHeightDp by remember { mutableIntStateOf(articleCoverMaxHeightDp) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    val coverImage = article.coverUrl
    val hasCover = !coverImage.isNullOrBlank()

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
            if (showFavoriteAction) {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "取消收藏" else "收藏",
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { openArticleUrl(context, article.url) }) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = "在浏览器打开")
            }
        },
    ) { modifier ->
        Column(modifier = modifier.fillMaxSize()) {
            MarkdownTabPager(pagerState = pagerState, modifier = Modifier.weight(1f)) { page ->
                val listState = rememberLazyListState()
                val nestedScrollConnection = remember(listState, hasCover, density) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (!hasCover) return Offset.Zero
                            val deltaDp = with(density) { available.y.toDp().value.toInt() }
                            val contentAtTop = listState.firstVisibleItemIndex == 0 &&
                                listState.firstVisibleItemScrollOffset == 0
                            val nextHeight = articleCoverHeightAfterScroll(
                                currentHeightDp = coverHeightDp,
                                scrollDeltaDp = deltaDp,
                                contentAtTop = contentAtTop,
                            )
                            if (nextHeight == coverHeightDp) return Offset.Zero

                            val consumedDp = nextHeight - coverHeightDp
                            coverHeightDp = nextHeight
                            return Offset(x = 0f, y = with(density) { consumedDp.dp.toPx() })
                        }
                    }
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    if (hasCover && coverHeightDp > 0) {
                        RemoteArticleCoverImage(
                            imageUrl = coverImage.orEmpty(),
                            modifier = Modifier.fillMaxWidth().height(coverHeightDp.dp),
                        )
                    }
                    MarkdownTabRow(
                        tabTitles = remoteArticleDetailTabTitles,
                        selectedTabIndex = selectedTabIndex,
                        onTabSelected = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    )
                    RemoteArticleHeader(article)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection),
                    ) {
                        item(key = "remote-content-$page") {
                            Box(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
                                RemoteArticleMarkdownContent(
                                    remoteArticleDetailPageContent(
                                        page = page,
                                        summary = article.summary,
                                        viewpoints = article.viewpoints,
                                        original = article.content,
                                        originalImageUrls = listOfNotNull(article.coverUrl),
                                    ),
                                )
                            }
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
    originalImageUrls: List<String> = emptyList(),
): String = when (page) {
    0 -> remoteArticleSummaryPageContent(summary, viewpoints)
    else -> remoteArticleOriginalPageContent(original, originalImageUrls)
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

private fun remoteArticleOriginalPageContent(original: String?, imageUrls: List<String>): String {
    val content = original?.trim()?.takeIf { it.isNotBlank() } ?: "暂无原文内容"
    if (content == "暂无原文内容" || imageUrls.isEmpty() || !content.hasRemoteImagePlaceholder()) return content
    return normalizeArticleMarkdownImages(content, imageUrls)
}

@Composable
private fun RemoteArticleMarkdownContent(content: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(BorderWidth.xs, MaterialTheme.colorScheme.outline),
    ) {
        SelectionContainer {
            Box(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m)) {
                Markdown(
                    content = content,
                    typography = MarkdownStyles.remoteArticleTypography(),
                    padding = MarkdownStyles.remoteArticlePadding(),
                )
            }
        }
    }
}

@Composable
private fun RemoteArticleCoverImage(imageUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(context, imageUrl) { ImageRequest.Builder(context).data(imageUrl).build() }
    AsyncImage(
        model = request,
        placeholder = painterResource(android.R.drawable.ic_menu_gallery),
        error = painterResource(android.R.drawable.ic_menu_report_image),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun RemoteArticleHeader(article: RemoteArticle) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = article.title.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        articleRemoteMetaText(article)?.let { meta ->
            Surface(shape = RoundedCornerShape(Radius.circular), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun articleRemoteMetaText(article: RemoteArticle): String? = listOfNotNull(
    article.feedName,
    article.domain,
    article.createdAt?.take(10),
    article.importanceScore?.let { "重要性 ${String.format("%.1f", it)}" },
).filter { it.isNotBlank() }.take(4).joinToString(" · ").takeIf { it.isNotBlank() }

private fun String.hasRemoteImagePlaceholder(): Boolean =
    Regex("""[!！](?:\[[^]]*]|图片|配图|插图|图像|image|photo|figure)""", RegexOption.IGNORE_CASE).containsMatchIn(this)
