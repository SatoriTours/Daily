package com.dailysatori.ui.feature.remotenews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dailysatori.service.parser.normalizeArticleMarkdownImages
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.ui.component.content.MarkdownTabPager
import com.dailysatori.ui.component.content.MarkdownTabRow
import com.dailysatori.ui.component.news.MagazineArticleBody
import com.dailysatori.ui.component.news.MagazineArticleHeader
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.articleCoverHeightAfterScroll
import com.dailysatori.ui.feature.article.articleCoverMaxHeightDp
import com.dailysatori.ui.feature.article.openArticleUrl
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing
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
    val density = LocalDensity.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var coverHeightDp by remember { mutableIntStateOf(articleCoverMaxHeightDp) }
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
        actions = { RemoteArticleDetailActions(article, isFavorite, showFavoriteAction, onFavoriteClick) },
    ) { modifier ->
        Column(modifier = modifier.fillMaxSize()) {
            RemoteArticleDetailPager(
                article = article,
                pagerState = pagerState,
                selectedTabIndex = selectedTabIndex,
                coverHeightDp = coverHeightDp,
                onCoverHeightChange = { coverHeightDp = it },
                density = density,
                onTabSelected = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
            )
        }
    }
}

@Composable
private fun RemoteArticleDetailActions(
    article: RemoteArticle,
    isFavorite: Boolean,
    showFavoriteAction: Boolean,
    onFavoriteClick: () -> Unit,
) {
    val context = LocalContext.current
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
}

@Composable
private fun ColumnScope.RemoteArticleDetailPager(
    article: RemoteArticle,
    pagerState: PagerState,
    selectedTabIndex: Int,
    coverHeightDp: Int,
    onCoverHeightChange: (Int) -> Unit,
    density: Density,
    onTabSelected: (Int) -> Unit,
) {
    val coverImage = article.coverUrl
    val hasCover = !coverImage.isNullOrBlank()
    MarkdownTabPager(pagerState = pagerState, modifier = Modifier.weight(1f)) { page ->
        RemoteArticleDetailPage(
            article, page, selectedTabIndex, coverImage, hasCover, coverHeightDp, onCoverHeightChange, density, onTabSelected,
        )
    }
}

@Composable
private fun RemoteArticleDetailPage(
    article: RemoteArticle,
    page: Int,
    selectedTabIndex: Int,
    coverImage: String?,
    hasCover: Boolean,
    coverHeightDp: Int,
    onCoverHeightChange: (Int) -> Unit,
    density: Density,
    onTabSelected: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val nestedScrollConnection = rememberRemoteArticleDetailNestedScrollConnection(
        hasCover, coverHeightDp, onCoverHeightChange, listState, density,
    )
    Column(modifier = Modifier.fillMaxSize()) {
        if (hasCover && coverHeightDp > 0) {
            RemoteArticleCoverImage(imageUrl = coverImage.orEmpty(), modifier = Modifier.fillMaxWidth().height(coverHeightDp.dp))
        }
        MagazineArticleHeader(article.title.orEmpty(), remoteArticleMetaChips(article), article.summary)
        MarkdownTabRow(remoteArticleDetailTabTitles, selectedTabIndex, onTabSelected)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
            item(key = "remote-content-$page") { RemoteArticleDetailBody(article, page) }
        }
    }
}

@Composable
private fun RemoteArticleDetailBody(article: RemoteArticle, page: Int) {
    Box(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        MagazineArticleBody(
            content = remoteArticleDetailPageContent(
                page = page,
                summary = article.summary,
                viewpoints = article.viewpoints,
                original = article.content,
                originalImageUrls = listOfNotNull(article.coverUrl),
            ),
            typography = MarkdownStyles.remoteArticleTypography(),
            padding = MarkdownStyles.remoteArticlePadding(),
        )
    }
}

@Composable
private fun rememberRemoteArticleDetailNestedScrollConnection(
    hasCover: Boolean,
    coverHeightDp: Int,
    onCoverHeightChange: (Int) -> Unit,
    listState: LazyListState,
    density: Density,
): NestedScrollConnection = remember(listState, hasCover, density, coverHeightDp) {
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!hasCover) return Offset.Zero
            val deltaDp = with(density) { available.y.toDp().value.toInt() }
            val contentAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            val nextHeight = articleCoverHeightAfterScroll(coverHeightDp, deltaDp, contentAtTop)
            if (nextHeight == coverHeightDp) return Offset.Zero
            onCoverHeightChange(nextHeight)
            return Offset(x = 0f, y = with(density) { (nextHeight - coverHeightDp).dp.toPx() })
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

private fun remoteArticleMetaChips(article: RemoteArticle): List<String> = listOfNotNull(
    article.feedName,
    article.domain,
    article.createdAt?.take(10),
    article.importanceScore?.let { "重要性 ${String.format("%.1f", it)}" },
).filter { it.isNotBlank() }.take(4)

private fun String.hasRemoteImagePlaceholder(): Boolean =
    Regex("""[!！](?:\[[^]]*]|图片|配图|插图|图像|image|photo|figure)""", RegexOption.IGNORE_CASE).containsMatchIn(this)
