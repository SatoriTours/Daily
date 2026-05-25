package com.dailysatori.ui.feature.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing
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
    showProgress: Boolean = true,
) {
    val contentModifier = if (fillAvailableHeight) modifier.fillMaxWidth().fillMaxHeight() else modifier.fillMaxWidth()
    Column(
        modifier = contentModifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        ViewpointHeader(
            title = title,
            bookTitle = bookTitle,
            author = author,
            page = page,
            total = total,
            showProgress = showProgress,
        )

        ViewpointBody(content = content, example = example)
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
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = viewpointDisplayTitle(title, bookTitle),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (showProgress) {
            Spacer(modifier = Modifier.width(Spacing.s))
            Text(
                text = booksReadingProgressText(page, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    viewpointBookLine(bookTitle, author).takeIf { it.isNotBlank() }?.let { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
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
            Text(
                "案例",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Markdown(
                content = example,
                typography = MarkdownStyles.bookTypography(),
                padding = MarkdownStyles.cardPadding(),
            )
        }
    }
}

fun viewpointCardFillsAvailableHeight(fillAvailableHeight: Boolean): Boolean = fillAvailableHeight

fun viewpointCardContentStartsAtTop(): Boolean = true

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
