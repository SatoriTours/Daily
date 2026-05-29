package com.dailysatori.ui.feature.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.ui.theme.*
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
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
    showProgress: Boolean = false,
    status: String = "ready",
    errorMessage: String = "",
    onRetry: () -> Unit = {},
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
                else -> ViewpointBody(content = content, example = example)
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
        ViewpointMetaRow(bookTitle = bookTitle, author = author, page = page, total = total, showProgress = showProgress)
        Text(
            text = viewpointDisplayTitle(title, bookTitle),
            style = MaterialTheme.typography.titleMedium,
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = viewpointBookLine(bookTitle, author),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (viewpointShouldShowPageCounter(showProgress, total)) {
            Text(
                text = booksReadingProgressText(page, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            typography = viewpointReadingTypography(),
            padding = MarkdownStyles.cardPadding(),
        )

        if (example.isNotBlank()) {
            ViewpointExampleSection(example = example)
        }
    }
}

@Composable
private fun ViewpointExampleSection(example: String) {
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
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Markdown(
            content = example,
            typography = viewpointReadingTypography(),
            padding = MarkdownStyles.cardPadding(),
        )
    }
}

@Composable
private fun viewpointReadingTypography(): MarkdownTypography {
    val body = MaterialTheme.typography.bodyMedium.copy(
        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
    )
    return DefaultMarkdownTypography(
        h1 = MaterialTheme.typography.titleLarge,
        h2 = MaterialTheme.typography.titleMedium,
        h3 = MaterialTheme.typography.titleSmall,
        h4 = MaterialTheme.typography.titleSmall,
        h5 = MaterialTheme.typography.titleSmall,
        h6 = MaterialTheme.typography.labelLarge,
        text = body,
        code = MaterialTheme.typography.bodySmall,
        inlineCode = MaterialTheme.typography.labelMedium,
        quote = body,
        paragraph = body,
        ordered = body,
        bullet = body,
        list = body,
        link = body.copy(fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary),
    )
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
