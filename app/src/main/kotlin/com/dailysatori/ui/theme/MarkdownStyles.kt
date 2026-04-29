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
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
        ),
        h2 = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 26.sp,
        ),
        h3 = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        h4 = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 22.sp,
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
            lineHeight = 18.sp,
        ),
        text = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 27.sp,
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
            fontSize = 15.sp,
            lineHeight = 25.sp,
            fontStyle = FontStyle.Italic,
        ),
        paragraph = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 27.sp,
        ),
        ordered = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 27.sp,
        ),
        bullet = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 27.sp,
        ),
        list = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 27.sp,
        ),
        link = TextStyle(
            fontFamily = LatoFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 27.sp,
        ),
    )

    @Composable
    fun padding(): MarkdownPadding = markdownPadding(
        block = 12.dp,
        list = 8.dp,
        listItemBottom = 4.dp,
        indentList = 12.dp,
        codeBlock = PaddingValues(12.dp),
        blockQuote = PaddingValues(12.dp),
        blockQuoteText = PaddingValues(0.dp),
        blockQuoteBar = PaddingValues.Absolute(0.dp, 0.dp, 0.dp, 0.dp),
    )
}
