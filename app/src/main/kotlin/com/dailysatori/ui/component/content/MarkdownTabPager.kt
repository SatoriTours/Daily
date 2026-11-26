package com.dailysatori.ui.component.content

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dailysatori.ui.theme.BorderWidth
import com.dailysatori.ui.theme.MarkdownStyles
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.m3.Markdown

@Composable
fun MarkdownTabPager(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    beyondViewportPageCount: Int = 1,
    pageContent: @Composable (Int) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        beyondViewportPageCount = beyondViewportPageCount,
    ) { page ->
        pageContent(page)
    }
}

@Composable
fun MarkdownTabRow(
    tabTitles: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = { HorizontalDivider(thickness = BorderWidth.xs, color = MaterialTheme.colorScheme.outline) },
    ) {
        tabTitles.forEachIndexed { index, title ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = { Text(title) },
            )
        }
    }
}

@Composable
fun MarkdownContent(
    content: String,
    typography: MarkdownTypography = MarkdownStyles.readingTypography(),
    padding: MarkdownPadding = MarkdownStyles.readingPadding(),
) {
    SelectionContainer {
        Markdown(
            content = content,
            typography = typography,
            padding = padding,
        )
    }
}
