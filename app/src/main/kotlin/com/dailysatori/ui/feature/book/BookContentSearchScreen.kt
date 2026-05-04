package com.dailysatori.ui.feature.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun BookContentSearchScreen(
    onBack: () -> Unit = {},
    onResultClick: (bookId: Long, viewpointId: Long) -> Unit = { _, _ -> },
) {
    val viewModel: BookContentSearchViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    AppScaffold(title = "搜索读书内容", onBack = onBack) { modifier ->
        Column(modifier = modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { viewModel.updateQuery(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜索书名、作者或观点...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.s),
                )
                Spacer(modifier = Modifier.width(Spacing.s))
                IconButton(onClick = { viewModel.search() }, enabled = state.query.isNotBlank()) {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                }
            }

            when {
                state.visibleResults.isNotEmpty() -> BookContentSearchResults(state.visibleResults, onResultClick)
                state.hasSearched -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Default.Search,
                    title = "没有找到相关观点",
                    subtitle = "换个关键词试试",
                )
                else -> EmptyState(
                    modifier = Modifier.fillMaxSize(),
                    icon = Icons.Default.Search,
                    title = "搜索读书内容",
                    subtitle = "可搜索书名、作者、观点和案例",
                )
            }
        }
    }
}

@Composable
private fun BookContentSearchResults(
    results: List<BookContentSearchResultItem>,
    onResultClick: (bookId: Long, viewpointId: Long) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results) { result ->
            BookContentSearchResultRow(
                result = result,
                onClick = { onResultClick(result.bookId, result.viewpointId) },
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
            )
        }
        item { Spacer(modifier = Modifier.height(Spacing.xxl)) }
    }
}

@Composable
private fun BookContentSearchResultRow(
    result: BookContentSearchResultItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.m))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .padding(Spacing.m),
    ) {
        Text(result.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Text(
            bookContentSearchBookLine(result.bookTitle, result.author),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            result.content,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
