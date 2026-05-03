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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dailysatori.service.book.BookSearchResult
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun BookSearchScreen(
    onBack: () -> Unit = {},
) {
    val viewModel: BookSearchViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    AppScaffold(
        title = "搜索书籍",
        onBack = onBack,
    ) { modifier ->
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
                    placeholder = { Text("输入书名...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.s),
                )
                Spacer(modifier = Modifier.width(Spacing.s))
                IconButton(
                    onClick = { viewModel.search() },
                    enabled = state.query.isNotBlank(),
                ) {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                }
            }

            when {
                state.isLoading -> {
                    LoadingIndicator()
                }
                state.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            state.error ?: "搜索失败",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                state.results.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.results) { result ->
                            SearchResultItem(
                                result = result,
                                onAdd = { viewModel.addBook(result) },
                                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
                            )
                        }
                        item { Spacer(modifier = Modifier.height(Spacing.xxl)) }
                    }
                }
                state.addedBookTitle != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(modifier = Modifier.height(Spacing.s))
                            Text(
                                "已添加《${state.addedBookTitle}》",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "输入书名搜索",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: BookSearchResult,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.m))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(Spacing.m),
        verticalAlignment = Alignment.Top,
    ) {
        if (result.coverUrl.isNotBlank()) {
            AsyncImage(
                model = result.coverUrl,
                contentDescription = result.title,
                modifier = Modifier
                    .size(width = 60.dp, height = 90.dp)
                    .clip(RoundedCornerShape(Radius.xs)),
            )
            Spacer(modifier = Modifier.width(Spacing.m))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (result.author.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    result.author,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (result.introduction.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    result.introduction,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(Spacing.s))
        FilledTonalButton(
            onClick = onAdd,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = Spacing.m,
                vertical = Spacing.xs,
            ),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(Spacing.xxs))
            Text("添加")
        }
    }
}
