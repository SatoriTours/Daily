package com.dailysatori.ui.feature.article

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun ArticleDetailScreen(
    articleId: Long,
    onBack: () -> Unit = {},
) {
    val viewModel: ArticleDetailViewModel = koinViewModel { parametersOf(articleId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showRefreshConfirm by remember { mutableStateOf(false) }
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
                        enabled = !state.isRefreshing,
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
                if (!coverImage.isNullOrBlank()) {
                    ArticleCoverImage(
                        imagePath = coverImage,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                    )
                }

                TabRow(selectedTabIndex = state.selectedTabIndex, modifier = Modifier.fillMaxWidth()) {
                    Tab(
                        selected = state.selectedTabIndex == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text("AI 摘要") },
                    )
                    Tab(
                        selected = state.selectedTabIndex == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("原文") },
                    )
                }

                if (state.isRefreshing) {
                    ArticleProcessingStepper(
                        status = state.processingStage,
                        progress = state.processingProgress,
                        modifier = Modifier.padding(Spacing.m),
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f),
                        beyondViewportPageCount = 1,
                    ) { page ->
                        val pageScrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(pageScrollState)
                                .padding(Spacing.m),
                        ) {
                            ArticleMarkdownContent(
                                articleDetailPageContent(
                                    page = page,
                                    summary = article.ai_content,
                                    original = article.ai_markdown_content,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRefreshConfirm) {
        AlertDialog(
            onDismissRequest = { showRefreshConfirm = false },
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
}

@Composable
private fun ArticleMarkdownContent(content: String) {
    SelectionContainer {
        Markdown(
            content = content,
            typography = MarkdownStyles.typography(),
            padding = MarkdownStyles.padding(),
        )
    }
}

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
