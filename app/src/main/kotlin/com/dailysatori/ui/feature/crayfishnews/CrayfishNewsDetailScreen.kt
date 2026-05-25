package com.dailysatori.ui.feature.crayfishnews

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import com.dailysatori.service.crayfishnews.CrayfishNewsDetail
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown

@Composable
fun CrayfishNewsDetailScreen(
    news: CrayfishNewsDetail,
    title: String = "小龙虾新闻",
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val content = news.content.withoutIntroBlock()

    AppScaffold(title = title, onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m),
        ) {
            if (content.isNotBlank()) {
                item {
                    SelectionContainer {
                        Markdown(
                            content = content,
                            typography = MarkdownStyles.readingTypography(),
                            padding = MarkdownStyles.readingPadding(),
                        )
                    }
                }
            }
        }
    }
}

private fun String.withoutIntroBlock(): String {
    val firstSectionIndex = lineSequence()
        .runningFold(0) { offset, line -> offset + line.length + 1 }
        .zip(lineSequence())
        .firstOrNull { (_, line) -> line.startsWith("## ") }
        ?.first
    return if (firstSectionIndex == null) trim() else drop(firstSectionIndex).trim()
}
