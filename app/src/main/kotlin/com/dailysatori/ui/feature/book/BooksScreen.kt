package com.dailysatori.ui.feature.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dailysatori.shared.db.Book
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.dialog.ConfirmDialog
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
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

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.isLoading && state.viewpoints.isEmpty()) {
                LoadingIndicator()
            } else if (state.viewpoints.isEmpty()) {
                EmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    icon = Icons.Default.MenuBook,
                    title = "暂无读书观点",
                    subtitle = "搜索并添加一本书开始阅读",
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    val idx = state.currentPage.coerceIn(0, state.viewpoints.size - 1)
                    val vp = state.viewpoints[idx]
                    val book = state.books.find { it.id == state.currentBookId }
                    val bookTitle = if (book != null) "《${book.title}》 · ${book.author}" else ""

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(Spacing.m),
                        verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        item {
                            ViewpointCard(
                                title = vp.title,
                                content = vp.content,
                                example = vp.example,
                                bookTitle = bookTitle,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { viewModel.setPage((idx - 1).coerceAtLeast(0)) },
                            enabled = idx > 0,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一条")
                            Text("上一条")
                        }
                        Text(
                            text = "第 ${idx + 1} / ${state.viewpoints.size} 条",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        TextButton(
                            onClick = { viewModel.setPage((idx + 1).coerceAtMost(state.viewpoints.size - 1)) },
                            enabled = idx < state.viewpoints.size - 1,
                        ) {
                            Text("下一条")
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一条")
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
