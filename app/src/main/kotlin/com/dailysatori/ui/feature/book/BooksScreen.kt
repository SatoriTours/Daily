package com.dailysatori.ui.feature.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailysatori.shared.db.Book
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.dialog.ConfirmDialog
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun BooksScreen(
    onSearchClick: () -> Unit = {},
) {
    val viewModel: BooksViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Book?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "读书",
            showBack = false,
            actions = {
                var showBookMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showBookMenu = true }) {
                        Icon(Icons.Default.MenuBook, contentDescription = "筛选书籍")
                    }
                    DropdownMenu(expanded = showBookMenu, onDismissRequest = { showBookMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("所有书籍") },
                            onClick = { viewModel.selectBook(null); showBookMenu = false },
                        )
                        state.books.forEach { book ->
                            DropdownMenuItem(
                                text = { Text(book.title) },
                                onClick = { viewModel.selectBook(book.id); showBookMenu = false },
                            )
                        }
                    }
                }
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = "搜索书籍")
                }
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("随机") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            onClick = { viewModel.shuffle(); showMenu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("刷新") },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            onClick = { viewModel.refresh(); showMenu = false },
                        )
                        if (state.currentBookId != null) {
                            DropdownMenuItem(
                                text = { Text("删除") },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showDeleteDialog = state.books.find { it.id == state.currentBookId }
                                    showMenu = false
                                },
                            )
                        }
                    }
                }
            },
        )

        if (state.isLoading && state.viewpoints.isEmpty()) {
            LoadingIndicator()
        } else if (state.viewpoints.isEmpty()) {
            EmptyState(
                icon = Icons.Default.MenuBook,
                title = "暂无读书观点",
                subtitle = "搜索并添加一本书开始阅读",
            )
        } else {
            if (state.books.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = state.currentPage.coerceIn(0, state.viewpoints.size - 1),
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = Spacing.m,
                ) {
                    state.viewpoints.forEachIndexed { _, _ ->
                        Tab(selected = false, onClick = {}) {
                            Box(modifier = Modifier.height(1.dp))
                        }
                    }
                }
            }

            if (state.viewpoints.isNotEmpty()) {
                val idx = state.currentPage.coerceIn(0, state.viewpoints.size - 1)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    val vp = state.viewpoints[idx]
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.m),
                        ) {
                            Column(modifier = Modifier.padding(Spacing.m)) {
                                val book = state.books.find { it.id == state.currentBookId }
                                if (book != null) {
                                    Text("《${book.title}》 · ${book.author}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.height(Spacing.s))
                                Text(vp.title, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(Spacing.s))
                                Text(vp.content, style = MaterialTheme.typography.bodyMedium, maxLines = 20, overflow = TextOverflow.Ellipsis)
                                if (vp.example.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(Spacing.s))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(Radius.s),
                                    ) {
                                        Text(vp.example, modifier = Modifier.padding(Spacing.s), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { book ->
        ConfirmDialog(
            title = "删除书籍",
            message = "确定要删除《${book.title}》及其所有观点吗？",
            onConfirm = {
                viewModel.deleteBook(book.id)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null },
        )
    }
}
