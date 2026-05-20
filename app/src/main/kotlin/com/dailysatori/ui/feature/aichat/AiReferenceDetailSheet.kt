package com.dailysatori.ui.feature.aichat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailysatori.core.util.TimeUtils
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.Book
import com.dailysatori.shared.db.Diary
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.media.SmartImage
import com.dailysatori.ui.feature.book.ViewpointCard
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown

fun diaryReferenceImagePaths(images: String?): List<String> = images
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotBlank() && it != "null" }
    .orEmpty()

fun diaryReferenceTags(tags: String?): List<String> = tags
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotBlank() && it != "null" }
    .orEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiReferenceDetailSheet(
    state: AiReferenceDetailState,
    onDismiss: () -> Unit,
    onArticleClick: (Long) -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = Spacing.m)
                .padding(bottom = Spacing.xxl),
        ) {
            Text(
                text = "引用详情",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = Spacing.m),
            )
            ReferenceDetailContent(state, onArticleClick)
        }
    }
}

@Composable
private fun ReferenceDetailContent(
    state: AiReferenceDetailState,
    onArticleClick: (Long) -> Unit,
) {
    when {
        state.isLoading -> LoadingIndicator(modifier = Modifier.height(160.dp))
        state.article != null -> ArticleReferenceSummary(state.article, onArticleClick)
        state.diary != null -> DiaryReferenceSummary(state.diary)
        state.viewpoint != null -> ViewpointCard(
            title = state.viewpoint.title,
            content = state.viewpoint.content,
            example = state.viewpoint.example,
            bookTitle = state.book?.let { "《${it.title}》 · ${it.author}" }.orEmpty(),
        )
        state.book != null -> BookReferenceSummary(state.book)
        else -> EmptyState(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = state.error ?: "内容不存在或已删除",
            modifier = Modifier.fillMaxWidth().height(180.dp),
        )
    }
}

@Composable
private fun DiaryReferenceSummary(diary: Diary) {
    val imagePaths = diaryReferenceImagePaths(diary.images)
    val tags = diaryReferenceTags(diary.tags)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = TimeUtils.formatShortDateTime(diary.created_at),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (diary.content.isNotBlank()) {
            Spacer(modifier = Modifier.height(Spacing.s))
            Markdown(
                content = diary.content.trim(),
                typography = MarkdownStyles.compactTypography(),
                padding = MarkdownStyles.compactPadding(),
            )
        }
        if (imagePaths.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.m))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                items(imagePaths, key = { it }) { imagePath ->
                    SmartImage(
                        imagePath = imagePath,
                        size = 96.dp,
                        contentDescription = "日记图片",
                    )
                }
            }
        }
        if (tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Spacing.m))
            Text(
                text = tags.joinToString(" ") { "#$it" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArticleReferenceSummary(
    article: Article,
    onArticleClick: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = article.ai_title ?: article.title ?: "无标题文章",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        val summary = article.ai_content?.takeIf { it.isNotBlank() } ?: article.comment.orEmpty()
        if (summary.isNotBlank()) {
            Spacer(modifier = Modifier.height(Spacing.m))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.m))
        TextButton(onClick = { onArticleClick(article.id) }) {
            Text("打开完整文章")
        }
    }
}

@Composable
private fun BookReferenceSummary(book: Book) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (book.author.isNotBlank()) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (book.introduction.isNotBlank()) {
            Spacer(modifier = Modifier.height(Spacing.m))
            Text(
                text = book.introduction,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
