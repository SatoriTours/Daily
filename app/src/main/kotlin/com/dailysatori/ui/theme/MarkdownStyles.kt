package com.dailysatori.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
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
    fun typography(): MarkdownTypography = DefaultMarkdownTypography(
        h1 = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 34.sp,
        ),
        h2 = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
            lineHeight = 30.sp,
        ),
        h3 = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 26.sp,
        ),
        h4 = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        h5 = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        ),
        h6 = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        text = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 28.sp,
        ),
        code = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        ),
        inlineCode = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
        ),
        quote = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 28.sp,
            fontStyle = FontStyle.Italic,
        ),
        paragraph = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 28.sp,
        ),
        ordered = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 28.sp,
        ),
        bullet = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 28.sp,
        ),
        list = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 28.sp,
        ),
        link = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 28.sp,
        ),
    )

    @Composable
    fun cardTypography(): MarkdownTypography = DefaultMarkdownTypography(
        h1 = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
        ),
        h2 = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        h3 = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        ),
        h4 = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        h5 = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        h6 = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        ),
        text = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        ),
        code = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 20.sp,
        ),
        inlineCode = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
        ),
        quote = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            fontStyle = FontStyle.Italic,
        ),
        paragraph = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        ),
        ordered = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        ),
        bullet = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        ),
        list = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        ),
        link = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        ),
    )

    @Composable
    fun cardPadding(): MarkdownPadding = markdownPadding(
        block = 4.dp,
        list = 6.dp,
        listItemBottom = 8.dp,
        indentList = 16.dp,
        codeBlock = PaddingValues(8.dp),
        blockQuote = PaddingValues(8.dp),
        blockQuoteText = PaddingValues(0.dp),
        blockQuoteBar = PaddingValues.Absolute(0.dp, 0.dp, 0.dp, 0.dp),
    )

    @Composable
    fun padding(): MarkdownPadding = markdownPadding(
        block = 10.dp,
        list = 12.dp,
        listItemBottom = 20.dp,
        indentList = 24.dp,
        codeBlock = PaddingValues(12.dp),
        blockQuote = PaddingValues(12.dp),
        blockQuoteText = PaddingValues(0.dp),
        blockQuoteBar = PaddingValues.Absolute(0.dp, 0.dp, 0.dp, 0.dp),
    )
}
