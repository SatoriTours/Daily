package com.dailysatori.ui.feature.remotenews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.dailysatori.ui.component.news.ArticleReaderBody
import com.dailysatori.ui.component.news.ArticleReaderHeader
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.article.articleCoverHeightAfterScroll
import com.dailysatori.ui.feature.article.articleCoverMaxHeightDp
import com.dailysatori.ui.feature.article.openArticleUrl
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing

@Composable
fun RemoteArticleDetailScreen(
    article: RemoteArticle,
    onBack: () -> Unit,
    isFavorite: Boolean = false,
    onFavoriteClick: () -> Unit = {},
    showFavoriteAction: Boolean = false,
) {
    val density = LocalDensity.current
    var showMenu by remember { mutableStateOf(false) }
    var showOriginalSheet by remember { mutableStateOf(false) }
    var coverHeightDp by remember { mutableIntStateOf(articleCoverMaxHeightDp) }

    BackHandler(onBack = onBack)

    AppScaffold(
        title = article.domain ?: article.feedName ?: "文章",
        onBack = onBack,
        actions = {
            RemoteArticleDetailActions(
                article = article,
                isFavorite = isFavorite,
                showFavoriteAction = showFavoriteAction,
                expanded = showMenu,
                onExpandedChange = { showMenu = it },
                onOriginalClick = { showOriginalSheet = true },
                onFavoriteClick = onFavoriteClick,
            )
        },
    ) { modifier ->
        Column(modifier = modifier.fillMaxSize()) {
            RemoteArticleDetailPager(
                article = article,
                coverHeightDp = coverHeightDp,
                onCoverHeightChange = { coverHeightDp = it },
                density = density,
            )
        }
    }

    if (showOriginalSheet) {
        RemoteArticleOriginalBottomSheet(article = article, onDismiss = { showOriginalSheet = false })
    }
}

@Composable
private fun RemoteArticleDetailActions(
    article: RemoteArticle,
    isFavorite: Boolean,
    showFavoriteAction: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOriginalClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            RemoteArticleOriginalMenuItem(onExpandedChange, onOriginalClick)
            if (showFavoriteAction) {
                RemoteArticleFavoriteMenuItem(isFavorite, onExpandedChange, onFavoriteClick)
            }
            RemoteArticleOpenMenuItem(article, onExpandedChange)
        }
    }
}

@Composable
private fun RemoteArticleOriginalMenuItem(onExpandedChange: (Boolean) -> Unit, onOriginalClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text("查看原文") },
        leadingIcon = { Icon(Icons.Default.Article, contentDescription = null) },
        onClick = {
            onExpandedChange(false)
            onOriginalClick()
        },
    )
}

@Composable
private fun RemoteArticleFavoriteMenuItem(
    isFavorite: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onFavoriteClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(if (isFavorite) "取消收藏" else "收藏") },
        leadingIcon = {
            Icon(
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = {
            onExpandedChange(false)
            onFavoriteClick()
        },
    )
}

@Composable
private fun RemoteArticleOpenMenuItem(article: RemoteArticle, onExpandedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    DropdownMenuItem(
        text = { Text("在浏览器打开") },
        leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
        onClick = {
            onExpandedChange(false)
            openArticleUrl(context, article.url)
        },
    )
}

@Composable
private fun RemoteArticleDetailPager(
    article: RemoteArticle,
    coverHeightDp: Int,
    onCoverHeightChange: (Int) -> Unit,
    density: Density,
) {
    val coverImage = article.coverUrl
    val hasCover = !coverImage.isNullOrBlank()
    RemoteArticleDetailPage(article, coverImage, hasCover, coverHeightDp, onCoverHeightChange, density)
}

@Composable
private fun RemoteArticleDetailPage(
    article: RemoteArticle,
    coverImage: String?,
    hasCover: Boolean,
    coverHeightDp: Int,
    onCoverHeightChange: (Int) -> Unit,
    density: Density,
) {
    val listState = rememberLazyListState()
    val nestedScrollConnection = rememberRemoteArticleDetailNestedScrollConnection(
        hasCover, coverHeightDp, onCoverHeightChange, listState, density,
    )
    Column(modifier = Modifier.fillMaxSize()) {
        if (hasCover && coverHeightDp > 0) {
            RemoteArticleCoverImage(imageUrl = coverImage.orEmpty(), modifier = Modifier.fillMaxWidth().height(coverHeightDp.dp))
        }
        ArticleReaderHeader(remoteArticleDisplayTitle(article), remoteArticleMetaChips(article))
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
            item(key = "remote-summary-content") { RemoteArticleDetailBody(article) }
        }
    }
}

@Composable
private fun RemoteArticleDetailBody(article: RemoteArticle) {
    Box(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        ArticleReaderBody(
            content = remoteArticleDetailPageContent(
                page = 0,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteArticleOriginalBottomSheet(article: RemoteArticle, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "原文",
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
            style = MaterialTheme.typography.titleLarge,
        )
        Box(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
            ArticleReaderBody(
                content = remoteArticleOriginalPageContent(article.content, listOfNotNull(article.coverUrl)),
                typography = MarkdownStyles.remoteArticleTypography(),
                padding = MarkdownStyles.remoteArticlePadding(),
            )
        }
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
        viewpointContent.takeIf { it.isNotBlank() },
    ).joinToString(separator = "\n\n").ifBlank { "暂无摘要内容，请刷新当前来源后重试。" }
}

private fun remoteArticleOriginalPageContent(original: String?, imageUrls: List<String>): String {
    val content = original?.trim()?.takeIf { it.isNotBlank() } ?: "暂无原文内容，请刷新当前来源后重试。"
    if (content == "暂无原文内容，请刷新当前来源后重试。" || imageUrls.isEmpty() || !content.hasRemoteImagePlaceholder()) return content
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
    article.publishedAt?.take(10) ?: article.createdAt?.take(10),
    article.importanceScore?.let { "重要性 ${String.format("%.1f", it)}" },
).filter { it.isNotBlank() }.take(4)

private fun remoteArticleDisplayTitle(article: RemoteArticle): String = listOfNotNull(
    article.title?.trim()?.takeIf { it.isNotBlank() },
    article.summary?.trim()?.takeIf { it.isNotBlank() }?.take(48),
    article.feedName?.trim()?.takeIf { it.isNotBlank() },
    article.domain?.trim()?.takeIf { it.isNotBlank() },
).firstOrNull() ?: "未命名远程文章"

private fun String.hasRemoteImagePlaceholder(): Boolean =
    Regex("""[!！](?:\[[^]]*]|图片|配图|插图|图像|image|photo|figure)""", RegexOption.IGNORE_CASE).containsMatchIn(this)
