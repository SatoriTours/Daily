package com.dailysatori.ui.feature.article

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.dailysatori.ui.component.content.MarkdownContent
import com.dailysatori.ui.component.content.MarkdownTabPager
import com.dailysatori.ui.component.content.MarkdownTabRow
import com.dailysatori.ui.component.dialog.ConfirmDialog
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import androidx.compose.runtime.rememberCoroutineScope

private val articleDetailTabTitles = listOf("AI 摘要", "原文")

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
    var showRefreshConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var coverHeightDp by remember { mutableIntStateOf(articleCoverMaxHeightDp) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    val title = extractDomain(state.article?.url)

    LaunchedEffect(pagerState.currentPage) {
        if (state.selectedTabIndex != pagerState.currentPage) {
            viewModel.selectTab(pagerState.currentPage)
        }
    }

    LaunchedEffect(state.selectedTabIndex) {
        if (pagerState.currentPage != state.selectedTabIndex) {
            pagerState.animateScrollToPage(state.selectedTabIndex)
        }
    }

    AppScaffold(
        title = title,
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("刷新文章") },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        enabled = canManuallyRefreshArticle(state.isRefreshing, state.article?.status),
                        onClick = {
                            showMenu = false
                            showRefreshConfirm = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (state.article?.is_favorite == 1L) "取消收藏" else "收藏") },
                        leadingIcon = {
                            Icon(
                                if (state.article?.is_favorite == 1L) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            viewModel.toggleFavorite()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("在浏览器打开") },
                        leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            openArticleUrl(context, state.article?.url)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除文章", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            showMenu = false
                            showDeleteConfirm = true
                        },
                    )
                }
            }
        },
    ) { modifier ->
        if (state.isLoading && state.article == null) {
            LoadingIndicator(modifier = modifier)
        } else if (state.article == null && !state.isRefreshing) {
            EmptyState(
                modifier = modifier,
                icon = Icons.Default.Favorite,
                title = "文章未找到",
                subtitle = "该文章可能已被删除",
            )
        } else {
            Column(modifier = modifier.fillMaxSize()) {
                if (state.refreshError != null) {
                    Text(
                        state.refreshError ?: "",
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val article = state.article ?: return@Column
                val coverImage = article.cover_image ?: article.cover_image_url
                val hasCover = !coverImage.isNullOrBlank()

                if (state.isRefreshing) {
                    if (hasCover) {
                        ArticleCoverImage(
                            imagePath = coverImage.orEmpty(),
                            modifier = Modifier.fillMaxWidth().height(articleCoverMaxHeightDp.dp),
                        )
                    }
                    MarkdownTabRow(
                        tabTitles = articleDetailTabTitles,
                        selectedTabIndex = state.selectedTabIndex,
                        onTabSelected = { index ->
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        },
                    )
                    ArticleProcessingStepper(
                        status = state.processingStage,
                        progress = state.processingProgress,
                        modifier = Modifier.padding(Spacing.m),
                    )
                } else {
                    MarkdownTabPager(
                        pagerState = pagerState,
                        modifier = Modifier.weight(1f),
                    ) { page ->
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
                                ArticleCoverImage(
                                    imagePath = coverImage.orEmpty(),
                                    modifier = Modifier.fillMaxWidth().height(coverHeightDp.dp),
                                )
                            }
                            MarkdownTabRow(
                                tabTitles = articleDetailTabTitles,
                                selectedTabIndex = state.selectedTabIndex,
                                onTabSelected = { index ->
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                            )
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(nestedScrollConnection),
                            ) {
                                item(key = "content-$page") {
                                    Box(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
                                        MarkdownContent(
                                            articleDetailPageContent(
                                                page = page,
                                                summary = article.ai_content,
                                                original = article.ai_markdown_content,
                                                originalImageUrls = listOfNotNull(article.cover_image_url),
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
    }

    if (showRefreshConfirm) {
        AlertDialog(
            onDismissRequest = { showRefreshConfirm = false },
            shape = RoundedCornerShape(Radius.xl),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            iconContentColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("刷新文章？") },
            text = { Text("刷新会重新获取网页内容并重新生成摘要，已有 AI 摘要和原文可能被覆盖。确定刷新吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRefreshConfirm = false
                        viewModel.refreshArticle()
                    },
                ) {
                    Text("刷新")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRefreshConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = articleDeleteDialogTitle(),
            message = articleDeleteDialogMessage(),
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteArticle()
                onBack()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

internal fun articleDeleteDialogTitle(): String = "删除文章"

internal fun articleDeleteDialogMessage(): String = "确定要删除这篇文章吗？"

private fun extractDomain(url: String?): String {
    if (url.isNullOrBlank()) return "文章详情"
    return url.removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("/")
        .removePrefix("www.")
        .ifBlank { "文章详情" }
}

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
