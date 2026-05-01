package com.dailysatori.ui.feature.article

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

@Composable
fun ArticleDetailScreen(
    articleId: Long,
    onBack: () -> Unit = {},
) {
    val viewModel: ArticleDetailViewModel = koinViewModel { parametersOf(articleId) }
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    val title = extractDomain(state.article?.url)

    AppScaffold(
        title = title,
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.toggleFavorite() }) {
                Icon(
                    if (state.article?.is_favorite == 1L) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "收藏",
                )
            }
            IconButton(onClick = { /* share */ }) {
                Icon(Icons.Default.Share, contentDescription = "分享")
            }
        },
    ) { modifier ->
        if (state.isLoading && state.article == null) {
            Box(modifier = modifier.fillMaxSize()) {
                Text("加载中...", modifier = Modifier.padding(Spacing.m))
            }
        } else if (state.article == null) {
            Box(modifier = modifier.fillMaxSize()) {
                Text("文章未找到", modifier = Modifier.padding(Spacing.m))
            }
        } else {
            val article = state.article!!
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
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
                        onClick = { viewModel.selectTab(0) },
                        text = { Text("AI 摘要") },
                    )
                    Tab(
                        selected = state.selectedTabIndex == 1,
                        onClick = { viewModel.selectTab(1) },
                        text = { Text("原文") },
                    )
                }

                Box(modifier = Modifier.padding(Spacing.m)) {
                    when (state.selectedTabIndex) {
                        0 -> {
                            val summary = article.ai_content
                                ?: "暂无摘要内容"
                            SelectionContainer {
                                Markdown(
                                    content = summary,
                                    typography = MarkdownStyles.typography(),
                                    padding = MarkdownStyles.padding(),
                                )
                            }
                        }
                        else -> {
                            val original = article.ai_markdown_content
                                ?: "暂无原文内容"
                            SelectionContainer {
                                Markdown(
                                    content = original,
                                    typography = MarkdownStyles.typography(),
                                    padding = MarkdownStyles.padding(),
                                )
                            }
                        }
                    }
                }
            }
        }
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
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(resolvedPath)
            .crossfade(true)
            .build(),
        placeholder = painterResource(android.R.drawable.ic_menu_gallery),
        error = painterResource(android.R.drawable.ic_menu_report_image),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
