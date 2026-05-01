package com.dailysatori.ui.feature.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.dailysatori.shared.db.Book
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.dialog.ConfirmDialog
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    onSearchClick: () -> Unit = {},
) {
    val viewModel: BooksViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Book?>(null) }
    var showBookSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "读书",
            showBack = false,
            actions = {
                IconButton(onClick = { showBookSheet = true }) {
                    Icon(Icons.Default.MenuBook, contentDescription = "选择书籍")
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
                val pagerState = rememberPagerState(
                    initialPage = state.currentPage,
                    pageCount = { state.viewpoints.size },
                )

                LaunchedEffect(state.currentBookId) {
                    pagerState.scrollToPage(0)
                }

                LaunchedEffect(pagerState.currentPage) {
                    viewModel.setPage(pagerState.currentPage)
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val vp = state.viewpoints[page]
                    val book = state.books.find { it.id == state.currentBookId }
                    val bookTitle = if (book != null) "《${book.title}》 · ${book.author}" else ""

                    ViewpointCard(
                        title = vp.title,
                        content = vp.content,
                        example = vp.example,
                        bookTitle = bookTitle,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m),
                    )
                }
            }
        }
    }

    if (showBookSheet) {
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()

        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) showBookSheet = false
                }
            },
            sheetState = sheetState,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = Spacing.s)
                        .width(32.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.m)
                    .padding(bottom = Spacing.xxl),
            ) {
                Text(
                    "选择书籍",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = Spacing.m),
                )

                state.books.forEach { book ->
                    val isSelected = book.id == state.currentBookId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.s))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                            )
                            .clickable {
                                viewModel.selectBook(book.id)
                                scope.launch {
                                    sheetState.hide()
                                }.invokeOnCompletion {
                                    if (!sheetState.isVisible) showBookSheet = false
                                }
                            }
                            .padding(Spacing.m),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                book.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (book.author.isNotBlank()) {
                                Text(
                                    book.author,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (isSelected) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs))
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
