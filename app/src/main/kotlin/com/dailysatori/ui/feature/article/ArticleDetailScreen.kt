package com.dailysatori.ui.feature.article

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.dailysatori.service.externalfavorites.xPostIdFromStatusUrl
import com.dailysatori.ui.component.card.articleDisplayDomain
import com.dailysatori.ui.component.card.articleDisplayTitle
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.shared.db.Article
import com.dailysatori.ui.component.dialog.ConfirmDialog
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.news.ArticleReaderBody
import com.dailysatori.ui.component.news.ArticleReaderHeader
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

@Composable
fun ArticleDetailScreen(
    articleId: Long,
    onBack: () -> Unit = {},
) {
    val viewModel: ArticleDetailViewModel = koinViewModel { parametersOf(articleId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    var showMenu by remember { mutableStateOf(false) }
    var showOriginalSheet by remember { mutableStateOf(false) }
    var showRefreshConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var coverHeightDp by remember { mutableIntStateOf(articleCoverMaxHeightDp) }

    val title = extractDomain(state.article?.url)

    AppScaffold(
        title = title,
        onBack = onBack,
        actions = {
            ArticleDetailActions(
                state = state,
                expanded = showMenu,
                onExpandedChange = { showMenu = it },
                onOriginalClick = { showOriginalSheet = true },
                onRefreshClick = { showRefreshConfirm = true },
                onXApiRefreshClick = viewModel::refreshArticleWithXApi,
                onFavoriteClick = viewModel::toggleFavorite,
                onCopyClick = { url -> copyArticleUrl(context, url) },
                onOpenClick = { openArticleUrl(context, state.article?.url) },
                onDeleteClick = { showDeleteConfirm = true },
            )
        },
    ) { modifier ->
        ArticleDetailContent(
            state = state,
            coverHeightDp = coverHeightDp,
            onCoverHeightChange = { coverHeightDp = it },
            density = density,
            modifier = modifier,
        )
    }

    ArticleDetailDialogs(
        showRefreshConfirm = showRefreshConfirm,
        showDeleteConfirm = showDeleteConfirm,
        onRefreshDismiss = { showRefreshConfirm = false },
        onDeleteDismiss = { showDeleteConfirm = false },
        onRefreshConfirm = viewModel::refreshArticle,
        onDeleteConfirm = {
            viewModel.deleteArticle()
            onBack()
        },
    )

    if (showOriginalSheet) {
        state.article?.let { article ->
            ArticleOriginalBottomSheet(article = article, onDismiss = { showOriginalSheet = false })
        }
    }
}

@Composable
private fun ArticleDetailDialogs(
    showRefreshConfirm: Boolean,
    showDeleteConfirm: Boolean,
    onRefreshDismiss: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onRefreshConfirm: () -> Unit,
    onDeleteConfirm: () -> Unit,
) {
    if (showRefreshConfirm) {
        ArticleRefreshConfirmDialog(
            onConfirm = {
                onRefreshDismiss()
                onRefreshConfirm()
            },
            onDismiss = onRefreshDismiss,
        )
    }

    if (showDeleteConfirm) {
        ArticleDeleteConfirmDialog(
            onConfirm = {
                onDeleteDismiss()
                onDeleteConfirm()
            },
            onDismiss = onDeleteDismiss,
        )
    }
}

@Composable
private fun ArticleDetailActions(
    state: ArticleDetailState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOriginalClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onXApiRefreshClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onCopyClick: (String) -> Unit,
    onOpenClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            ArticleOriginalMenuItem(onExpandedChange, onOriginalClick)
            ArticleRefreshMenuItem(state, onExpandedChange, onRefreshClick)
            ArticleXApiRefreshMenuItem(state, onExpandedChange, onXApiRefreshClick)
            ArticleFavoriteMenuItem(state, onExpandedChange, onFavoriteClick)
            ArticleCopyLinkMenuItem(state.article?.url, onExpandedChange, onCopyClick)
            ArticleOpenMenuItem(onExpandedChange, onOpenClick)
            ArticleDeleteMenuItem(onExpandedChange, onDeleteClick)
        }
    }
}

@Composable
private fun ArticleOriginalMenuItem(onExpandedChange: (Boolean) -> Unit, onOriginalClick: () -> Unit) {
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
private fun ArticleRefreshMenuItem(
    state: ArticleDetailState,
    onExpandedChange: (Boolean) -> Unit,
    onRefreshClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text("刷新文章") },
        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
        enabled = canManuallyRefreshArticle(state.isRefreshing, state.article?.status),
        onClick = {
            onExpandedChange(false)
            onRefreshClick()
        },
    )
}

@Composable
private fun ArticleXApiRefreshMenuItem(
    state: ArticleDetailState,
    onExpandedChange: (Boolean) -> Unit,
    onXApiRefreshClick: () -> Unit,
) {
    val visible = canRefreshArticleWithXApi(state.article?.url)
    if (!visible) return
    DropdownMenuItem(
        text = { Text(articleXApiRefreshMenuLabel()) },
        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
        enabled = !state.isRefreshing,
        onClick = {
            onExpandedChange(false)
            onXApiRefreshClick()
        },
    )
}

@Composable
private fun ArticleFavoriteMenuItem(
    state: ArticleDetailState,
    onExpandedChange: (Boolean) -> Unit,
    onFavoriteClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(if (state.article?.is_favorite == 1L) "取消收藏" else "收藏") },
        leadingIcon = {
            Icon(if (state.article?.is_favorite == 1L) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
        },
        onClick = {
            onExpandedChange(false)
            onFavoriteClick()
        },
    )
}

@Composable
private fun ArticleCopyLinkMenuItem(
    url: String?,
    onExpandedChange: (Boolean) -> Unit,
    onCopyClick: (String) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(articleCopyLinkMenuLabel()) },
        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
        enabled = !url.isNullOrBlank(),
        onClick = {
            onExpandedChange(false)
            if (!url.isNullOrBlank()) onCopyClick(url)
        },
    )
}

@Composable
private fun ArticleOpenMenuItem(onExpandedChange: (Boolean) -> Unit, onOpenClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text("在浏览器打开") },
        leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
        onClick = {
            onExpandedChange(false)
            onOpenClick()
        },
    )
}

@Composable
private fun ArticleDeleteMenuItem(onExpandedChange: (Boolean) -> Unit, onDeleteClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text("删除文章", color = MaterialTheme.colorScheme.error) },
        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        onClick = {
            onExpandedChange(false)
            onDeleteClick()
        },
    )
}

@Composable
private fun ArticleDetailContent(
    state: ArticleDetailState,
    coverHeightDp: Int,
    onCoverHeightChange: (Int) -> Unit,
    density: Density,
    modifier: Modifier,
) {
    when {
        state.isLoading && state.article == null -> LoadingIndicator(modifier = modifier)
        state.article == null && !state.isRefreshing -> EmptyState(
            modifier = modifier,
            icon = Icons.Default.Favorite,
            title = "文章未找到",
            subtitle = "该文章可能已被删除",
        )
        else -> ArticleDetailLoadedContent(state, coverHeightDp, onCoverHeightChange, density, modifier)
    }
}

@Composable
private fun ArticleDetailLoadedContent(
    state: ArticleDetailState,
    coverHeightDp: Int,
    onCoverHeightChange: (Int) -> Unit,
    density: Density,
    modifier: Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        state.refreshError?.let { ArticleRefreshError(it) }
        val article = state.article ?: return@Column
        val coverImage = article.cover_image ?: article.cover_image_url
        if (state.isRefreshing) {
            ArticleRefreshingContent(article, state, coverImage)
        } else {
            ArticleDetailPage(article, coverImage, coverHeightDp, onCoverHeightChange, density)
        }
    }
}

@Composable
private fun ArticleRefreshError(message: String) {
    Text(
        message,
        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun ArticleRefreshingContent(
    article: Article,
    state: ArticleDetailState,
    coverImage: String?,
) {
    if (!coverImage.isNullOrBlank()) {
        ArticleCoverImage(imagePath = coverImage, modifier = Modifier.fillMaxWidth().height(articleCoverMaxHeightDp.dp))
    }
    ArticleMagazineHeader(article)
    ArticleProcessingStepper(state.processingStage, state.processingProgress, modifier = Modifier.padding(Spacing.m))
}

@Composable
private fun ArticleDetailPage(
    article: Article,
    coverImage: String?,
    coverHeightDp: Int,
    onCoverHeightChange: (Int) -> Unit,
    density: Density,
) {
    val hasCover = !coverImage.isNullOrBlank()
    val listState = rememberLazyListState()
    val nestedScrollConnection = rememberArticleDetailNestedScrollConnection(
        hasCover, coverHeightDp, onCoverHeightChange, listState, density,
    )
    Column(modifier = Modifier.fillMaxSize()) {
        if (hasCover && coverHeightDp > 0) {
            ArticleCoverImage(imagePath = coverImage.orEmpty(), modifier = Modifier.fillMaxWidth().height(coverHeightDp.dp))
        }
        ArticleMagazineHeader(article)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
            item(key = "summary-content") { ArticleDetailBody(article) }
        }
    }
}

@Composable
private fun ArticleDetailBody(article: Article) {
    Box(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        ArticleReaderBody(
            content = articleDetailPageContent(
                page = 0,
                summary = article.ai_content,
                original = article.ai_markdown_content,
                originalImageUrls = listOfNotNull(article.cover_image_url),
            ),
            typography = MarkdownStyles.readingTypography(),
            padding = MarkdownStyles.readingPadding(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleOriginalBottomSheet(article: Article, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "原文",
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
            style = MaterialTheme.typography.titleLarge,
        )
        Box(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)) {
            ArticleReaderBody(
                content = articleDetailPageContent(
                    page = 1,
                    summary = article.ai_content,
                    original = article.ai_markdown_content,
                    storedOriginal = article.original_markdown_content,
                    originalImageUrls = listOfNotNull(article.cover_image_url),
                ),
                typography = MarkdownStyles.readingTypography(),
                padding = MarkdownStyles.readingPadding(),
            )
        }
    }
}

@Composable
private fun rememberArticleDetailNestedScrollConnection(
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

@Composable
private fun ArticleMagazineHeader(article: Article) {
    ArticleReaderHeader(
        title = articleMagazineTitle(article),
        metaChips = articleMagazineMetaChips(article),
    )
}

@Composable
private fun ArticleRefreshConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Radius.xl),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("刷新文章？") },
        text = { Text("刷新会重新获取网页内容并重新生成摘要，已有 AI 摘要和原文可能被覆盖。确定刷新吗？") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("刷新") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ArticleDeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = articleDeleteDialogTitle(),
        message = articleDeleteDialogMessage(),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

internal fun articleDeleteDialogTitle(): String = "删除文章"

internal fun articleDeleteDialogMessage(): String = "确定要删除这篇文章吗？"

internal fun articleCopyLinkMenuLabel(): String = "复制网页链接"

internal fun articleCopyLinkClipLabel(): String = "网页链接"

internal fun articleCopyLinkSuccessMessage(): String = "已复制网页链接"

internal fun articleXApiRefreshMenuLabel(): String = "用 X API 刷新"

internal fun canRefreshArticleWithXApi(url: String?): Boolean =
    !url.isNullOrBlank() && xPostIdFromStatusUrl(url) != null

private fun copyArticleUrl(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(articleCopyLinkClipLabel(), url))
    Toast.makeText(context, articleCopyLinkSuccessMessage(), Toast.LENGTH_SHORT).show()
}

private fun extractDomain(url: String?): String = articleDisplayDomain(url)

private fun articleMagazineTitle(article: Article): String = articleDisplayTitle(article)

private fun articleMagazineMetaChips(article: Article): List<String> = listOfNotNull(
    extractDomain(article.url).takeIf { it != "文章详情" },
    article.pub_date?.let { TimeUtils.formatDate(it) } ?: TimeUtils.formatDate(article.created_at),
    article.status?.takeIf { it.isNotBlank() },
).take(3)

@Composable
private fun ArticleCoverImage(
    imagePath: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isLocal = !imagePath.startsWith("http://") && !imagePath.startsWith("https://")
    val resolvedPath = if (isLocal && !imagePath.startsWith("/")) {
        File(context.filesDir, "DailySatori/$imagePath").absolutePath
    } else {
        imagePath
    }
    val imageRequest = remember(context, resolvedPath) {
        ImageRequest.Builder(context)
            .data(resolvedPath)
            .build()
    }
    AsyncImage(
        model = imageRequest,
        placeholder = painterResource(android.R.drawable.ic_menu_gallery),
        error = painterResource(android.R.drawable.ic_menu_report_image),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
