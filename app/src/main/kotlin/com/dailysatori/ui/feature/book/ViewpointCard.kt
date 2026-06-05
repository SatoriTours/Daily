package com.dailysatori.ui.feature.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.ui.theme.*
import com.mikepenz.markdown.m3.Markdown

@Composable
fun ViewpointCard(
    title: String,
    content: String,
    example: String,
    bookTitle: String,
    author: String,
    page: Int,
    total: Int,
    modifier: Modifier = Modifier,
    fillAvailableHeight: Boolean = false,
    reserveBottomSpace: Boolean = false,
    showProgress: Boolean = false,
    status: String = "ready",
    errorMessage: String = "",
    onRetry: () -> Unit = {},
    onReflect: () -> Unit = {},
) {
    val contentModifier = if (fillAvailableHeight) modifier.fillMaxWidth().fillMaxHeight() else modifier.fillMaxWidth()
    Box(
        modifier = contentModifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            ViewpointHeader(
                title = title,
                bookTitle = bookTitle,
                author = author,
                page = page,
                total = total,
                showProgress = showProgress,
            )

            when (status) {
                "failed" -> ViewpointRetryBody(errorMessage = errorMessage, onRetry = onRetry)
                "generating" -> ViewpointGeneratingBody()
                else -> {
                    ViewpointBody(content = content, example = example)
                    OutlinedButton(onClick = onReflect, modifier = Modifier.align(Alignment.End)) {
                        Text(booksReflectionActionText())
                    }
                }
            }

            if (reserveBottomSpace) {
                Spacer(modifier = Modifier.height(bookReadingBottomSpace()))
            }
        }
    }
}

@Composable
private fun ViewpointHeader(
    title: String,
    bookTitle: String,
    author: String,
    page: Int,
    total: Int,
    showProgress: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ViewpointMetaRow(
            bookTitle = bookTitle,
            author = author,
            page = page,
            total = total,
            showProgress = showProgress,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = viewpointDisplayTitle(title, bookTitle),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ViewpointMetaRow(
    bookTitle: String,
    author: String,
    page: Int,
    total: Int,
    showProgress: Boolean,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = viewpointBookLine(bookTitle, author),
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (viewpointShouldShowPageCounter(showProgress, total)) {
            Text(
                text = booksReadingProgressText(page, total),
                style = style,
                color = color,
                modifier = Modifier.padding(start = Spacing.m),
            )
        }
    }
}

@Composable
private fun ViewpointBody(content: String, example: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Markdown(
            content = content,
            typography = MarkdownStyles.bookTypography(),
            padding = MarkdownStyles.cardPadding(),
        )

        if (example.isNotBlank()) {
            Column(
                modifier = Modifier.padding(top = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Article,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSize.m),
                    )
                    Text(
                        text = "案例",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Markdown(
                    content = example,
                    typography = MarkdownStyles.bookTypography(),
                    padding = MarkdownStyles.cardPadding(),
                )
            }
        }
    }
}

@Composable
private fun ViewpointRetryBody(errorMessage: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = errorMessage.ifBlank { "这个观点生成失败，可以只重新生成这一条。" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text("重新生成这个观点")
        }
    }
}

@Composable
private fun ViewpointGeneratingBody() {
    Text(
        text = "正在重新生成这个观点...",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

fun viewpointCardFillsAvailableHeight(fillAvailableHeight: Boolean): Boolean = fillAvailableHeight

fun viewpointCardContentStartsAtTop(): Boolean = true

fun viewpointShouldShowPageCounter(showProgress: Boolean, total: Int): Boolean = showProgress || total > 1

fun bookReadingBottomSpace() = Height.navBar + Spacing.xxl

fun viewpointDisplayTitle(title: String, bookTitle: String): String {
    val cleanTitle = title.trim()
    val cleanBook = normalizedBookTitle(bookTitle)
    if (cleanBook.isBlank()) return cleanTitle

    val prefixPattern = Regex("^(?:《${Regex.escape(cleanBook)}》|${Regex.escape(cleanBook)})\\s*[：:]\\s*")
    return cleanTitle.replaceFirst(prefixPattern, "").trim()
}

fun viewpointBookLine(bookTitle: String, author: String): String {
    val cleanTitle = normalizedBookTitle(bookTitle)
    if (cleanTitle.isBlank()) return ""
    val cleanAuthor = author.trim()
    return if (cleanAuthor.isBlank()) "《$cleanTitle》" else "《$cleanTitle》 · $cleanAuthor"
}

private fun normalizedBookTitle(bookTitle: String): String {
    val cleanTitle = bookTitle.trim()
    return cleanTitle
        .removePrefix("《")
        .removeSuffix("》")
        .trim()
}
