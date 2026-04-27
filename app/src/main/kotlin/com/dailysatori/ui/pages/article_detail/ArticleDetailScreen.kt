package com.dailysatori.ui.pages.article_detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import com.dailysatori.ui.components.LoadingIndicator
import com.dailysatori.ui.components.SAppBar
import com.dailysatori.ui.components.SmartImage
import com.dailysatori.ui.theme.Spacing
import com.dailysatori.viewmodel.ArticleDetailViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            SAppBar(
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
            )
        },
    ) { padding ->
        if (state.isLoading && state.article == null) {
            LoadingIndicator()
        } else if (state.article == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text("文章未找到", modifier = Modifier.padding(Spacing.m))
            }
        } else {
            val article = state.article!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
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
