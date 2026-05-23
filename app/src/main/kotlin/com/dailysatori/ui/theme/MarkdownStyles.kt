package com.dailysatori.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownPadding

object MarkdownStyles {

    @Composable
    fun readingTypography(): MarkdownTypography = typographyFrom(
        body = readingTextStyle(),
        h1 = MaterialTheme.typography.headlineLarge,
        h2 = MaterialTheme.typography.headlineMedium,
        h3 = MaterialTheme.typography.headlineSmall,
        linkColor = MaterialTheme.colorScheme.primary,
    )

    @Composable
    fun summaryTypography(): MarkdownTypography = cardTypography()

    @Composable
    fun compactTypography(): MarkdownTypography = cardTypography()

    @Composable
    fun readingPadding(): MarkdownPadding = markdownPadding(
        block = 10.dp,
        list = 8.dp,
        listItemBottom = 6.dp,
        indentList = 22.dp,
        codeBlock = PaddingValues(12.dp),
        blockQuote = PaddingValues(12.dp),
        blockQuoteText = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        blockQuoteBar = PaddingValues.Absolute(3.dp, 0.dp, 10.dp, 0.dp),
    )

    @Composable
    fun summaryPadding(): MarkdownPadding = markdownPadding(
        block = 8.dp,
        list = 8.dp,
        listItemBottom = 6.dp,
        indentList = 20.dp,
        codeBlock = PaddingValues(10.dp),
        blockQuote = PaddingValues(10.dp),
        blockQuoteText = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        blockQuoteBar = PaddingValues.Absolute(3.dp, 0.dp, 8.dp, 0.dp),
    )

    @Composable
    fun compactPadding(): MarkdownPadding = markdownPadding(
        block = 4.dp,
        list = 6.dp,
        listItemBottom = 6.dp,
        indentList = 16.dp,
        codeBlock = PaddingValues(8.dp),
        blockQuote = PaddingValues(8.dp),
        blockQuoteText = PaddingValues(0.dp),
        blockQuoteBar = PaddingValues.Absolute(0.dp, 0.dp, 0.dp, 0.dp),
    )

    @Composable
    fun typography(): MarkdownTypography = readingTypography()

    @Composable
    fun padding(): MarkdownPadding = readingPadding()

    @Composable
    fun cardTypography(): MarkdownTypography = typographyFrom(
        body = cardTextStyle(),
        h1 = MaterialTheme.typography.titleLarge,
        h2 = MaterialTheme.typography.titleMedium,
        h3 = MaterialTheme.typography.titleSmall,
        linkColor = MaterialTheme.colorScheme.primary,
    )

    @Composable
    fun bookTypography(): MarkdownTypography = typographyFrom(
        body = bookTextStyle(),
        h1 = MaterialTheme.typography.headlineSmall,
        h2 = MaterialTheme.typography.titleLarge,
        h3 = MaterialTheme.typography.titleMedium,
        linkColor = MaterialTheme.colorScheme.primary,
    )

    @Composable
    fun cardPadding(): MarkdownPadding = summaryPadding()

    @Composable
    fun remoteArticleTypography(): MarkdownTypography = readingTypography()

    @Composable
    fun remoteArticlePadding(): MarkdownPadding = readingPadding()
}

@Composable
private fun cardTextStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = UiFontFamily)

@Composable
private fun bookTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = UiFontFamily)

@Composable
private fun readingTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = UiFontFamily)

@Composable
private fun typographyFrom(
    body: TextStyle,
    h1: TextStyle,
    h2: TextStyle,
    h3: TextStyle,
    linkColor: Color,
): MarkdownTypography = DefaultMarkdownTypography(
    h1 = headingStyle(h1),
    h2 = headingStyle(h2),
    h3 = headingStyle(h3),
    h4 = headingStyle(h3),
    h5 = headingStyle(h3),
    h6 = headingStyle(MaterialTheme.typography.labelLarge),
    text = body,
    code = codeStyle(MaterialTheme.typography.bodySmall),
    inlineCode = codeStyle(MaterialTheme.typography.labelMedium),
    quote = body.copy(fontStyle = FontStyle.Italic),
    paragraph = body,
    ordered = body,
    bullet = body,
    list = body,
    link = body.copy(fontWeight = FontWeight.Medium, color = linkColor),
)

private fun headingStyle(style: TextStyle): TextStyle = style.copy(fontFamily = UiFontFamily)

private fun codeStyle(style: TextStyle): TextStyle = style.copy(fontFamily = UiFontFamily)
