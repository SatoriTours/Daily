package com.dailysatori.ui.feature.article

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailysatori.ui.component.media.SmartImage
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ArticleDetailScreen(
    articleId: Long,
    onBack: () -> Unit = {},
) {
    val viewModel: ArticleDetailViewModel = koinViewModel { parametersOf(articleId) }
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(articleId) {
        viewModel.loadArticle()
    }

    AppScaffold(
        title = state.article?.ai_title ?: state.article?.title ?: "文章详情",
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
                    SmartImage(
                        imagePath = coverImage,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        size = 260.dp,
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
                            val summary = article.ai_markdown_content
                                ?: article.ai_content
                                ?: article.content
                                ?: "暂无摘要内容"
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        else -> {
                            val original = article.html_content ?: article.content ?: "暂无原文内容"
                            Text(
                                text = original,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}
