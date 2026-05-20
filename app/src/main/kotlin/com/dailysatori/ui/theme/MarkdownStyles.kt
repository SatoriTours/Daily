package com.dailysatori.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownPadding

object MarkdownStyles {

    @Composable
    fun readingTypography(): MarkdownTypography = typographyScale(
        bodySize = 16,
        bodyLine = 27,
        h1 = 24,
        h2 = 21,
        h3 = 18,
        linkColor = MaterialTheme.colorScheme.primary,
    )

    @Composable
    fun summaryTypography(): MarkdownTypography = typographyScale(
        bodySize = 15,
        bodyLine = 24,
        h1 = 22,
        h2 = 19,
        h3 = 17,
        linkColor = MaterialTheme.colorScheme.primary,
    )

    @Composable
    fun compactTypography(): MarkdownTypography = typographyScale(
        bodySize = 14,
        bodyLine = 21,
        h1 = 18,
        h2 = 16,
        h3 = 15,
        linkColor = MaterialTheme.colorScheme.primary,
    )

    @Composable
    fun readingPadding(): MarkdownPadding = markdownPadding(
        block = 10.dp,
        list = 10.dp,
        listItemBottom = 8.dp,
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
    fun cardTypography(): MarkdownTypography = compactTypography()

    @Composable
    fun cardPadding(): MarkdownPadding = compactPadding()

    @Composable
    fun remoteArticleTypography(): MarkdownTypography = readingTypography()

    @Composable
    fun remoteArticlePadding(): MarkdownPadding = readingPadding()
}

private fun typographyScale(
    bodySize: Int,
    bodyLine: Int,
    h1: Int,
    h2: Int,
    h3: Int,
    linkColor: Color,
): MarkdownTypography = DefaultMarkdownTypography(
    h1 = contentStyle(FontWeight.Bold, h1, h1 + 10),
    h2 = contentStyle(FontWeight.Bold, h2, h2 + 8),
    h3 = contentStyle(FontWeight.SemiBold, h3, h3 + 7),
    h4 = contentStyle(FontWeight.SemiBold, bodySize, bodyLine),
    h5 = contentStyle(FontWeight.Medium, bodySize - 1, bodyLine - 4),
    h6 = uiStyle(FontWeight.Medium, 13, 18),
    text = contentStyle(FontWeight.Normal, bodySize, bodyLine),
    code = uiStyle(FontWeight.Normal, 13, 20),
    inlineCode = uiStyle(FontWeight.Medium, 13, 20),
    quote = contentStyle(FontWeight.Normal, bodySize, bodyLine, FontStyle.Italic),
    paragraph = contentStyle(FontWeight.Normal, bodySize, bodyLine),
    ordered = contentStyle(FontWeight.Normal, bodySize, bodyLine),
    bullet = contentStyle(FontWeight.Normal, bodySize, bodyLine),
    list = contentStyle(FontWeight.Normal, bodySize, bodyLine),
    link = contentStyle(FontWeight.Medium, bodySize, bodyLine, color = linkColor),
)

private fun contentStyle(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    fontStyle: FontStyle = FontStyle.Normal,
    color: Color = Color.Unspecified,
): TextStyle = TextStyle(
    fontFamily = UiFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontStyle = fontStyle,
    color = color,
)

private fun uiStyle(weight: FontWeight, size: Int, lineHeight: Int): TextStyle = TextStyle(
    fontFamily = UiFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)
