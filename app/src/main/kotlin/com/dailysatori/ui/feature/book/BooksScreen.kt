package com.dailysatori.ui.feature.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailysatori.shared.db.Book
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.dialog.ConfirmDialog
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.feature.book.component.BookAddSearchSheet
import com.dailysatori.ui.feature.book.component.BookContentSearchSheet
import com.dailysatori.ui.feature.book.component.BookPickerSwipeRow
import com.dailysatori.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    selectedBookId: Long? = null,
    selectedViewpointId: Long? = null,
    bookAnalysisMessage: String? = null,
    onSelectedBookConsumed: () -> Unit = {},
) {
    val viewModel: BooksViewModel = koinViewModel()
    val addBookViewModel: BookSearchViewModel = koinViewModel()
    val contentSearchViewModel: BookContentSearchViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val addState by addBookViewModel.state.collectAsState()
    val contentSearchState by contentSearchViewModel.state.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Book?>(null) }
    var showBookSheet by remember { mutableStateOf(false) }
    var targetViewpointId by remember { mutableStateOf<Long?>(null) }
    var inlineMode by remember { mutableStateOf(BooksInlineMode.Reading) }
    var inlineBookAnalysisMessage by remember { mutableStateOf<String?>(null) }
    var searchReturnLocation by remember { mutableStateOf<BookReadingLocation?>(null) }

    LaunchedEffect(addState.addedBookId) {
        addState.addedBookId?.let {
            viewModel.selectBook(it)
            inlineBookAnalysisMessage = addState.analysisMessage
            inlineMode = BooksInlineMode.Reading
            addBookViewModel.clearAdded()
        }
    }

    LaunchedEffect(inlineMode, addState.query) {
        val query = addState.query.trim()
        if (inlineMode == BooksInlineMode.AddBook && query.isNotBlank()) {
            delay(500)
            if (addState.query.trim() == query) addBookViewModel.search()
        }
    }

    LaunchedEffect(inlineMode, contentSearchState.query) {
        val query = contentSearchState.query.trim()
        if (inlineMode == BooksInlineMode.ContentSearch && query.isNotBlank()) {
            delay(250)
            if (contentSearchState.query.trim() == query) contentSearchViewModel.search()
        }
    }

    LaunchedEffect(selectedBookId) {
        selectedBookId?.let {
            targetViewpointId = selectedViewpointId
            viewModel.selectBook(it)
            onSelectedBookConsumed()
        }
    }

    LaunchedEffect(targetViewpointId, state.viewpoints) {
        val targetId = targetViewpointId ?: return@LaunchedEffect
        val page = state.viewpoints.indexOfFirst { it.id == targetId }
        if (page >= 0) {
            viewModel.setPage(page)
            targetViewpointId = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = "读书",
            showBack = false,
            actions = {
                IconButton(onClick = { inlineMode = inlineMode.toggleAdd() }) {
                    Icon(Icons.Default.Add, contentDescription = booksAddActionContentDescription())
                }
                IconButton(onClick = { inlineMode = inlineMode.toggleContentSearch() }) {
                    Icon(Icons.Default.Search, contentDescription = booksContentSearchActionContentDescription())
                }
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(booksFilterMenuText()) },
                            leadingIcon = { Icon(Icons.Default.FilterList, null) },
                            onClick = { showBookSheet = true; showMenu = false },
                        )
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

        val visibleAnalysisMessage = booksAnalysisBannerMessage(bookAnalysisMessage, inlineBookAnalysisMessage)
        if (visibleAnalysisMessage != null) {
            Text(
                text = visibleAnalysisMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            )
        }
        searchReturnLocation?.let { location ->
            Text(
                text = booksRestoreReadingText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        targetViewpointId = null
                        viewModel.selectBook(location.bookId)
                        viewModel.setPage(location.page)
                        searchReturnLocation = null
                    }
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.isLoading && state.viewpoints.isEmpty()) {
                LoadingIndicator()
            } else if (state.viewpoints.isEmpty()) {
                EmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = "暂无读书观点",
                    subtitle = booksEmptyStateSubtitle(state.currentBookId != null),
                )
            } else {
                val pagerState = rememberPagerState(
                    initialPage = state.currentPage,
                    pageCount = { state.viewpoints.size },
                )

                LaunchedEffect(state.currentBookId) {
                    pagerState.scrollToPage(0)
                }

                LaunchedEffect(state.currentPage) {
                    if (pagerState.currentPage != state.currentPage) pagerState.scrollToPage(state.currentPage)
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
                        fillAvailableHeight = true,
                    )
                }
            }
        }
    }

    if (inlineMode != BooksInlineMode.Reading) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { inlineMode = BooksInlineMode.Reading },
            sheetState = sheetState,
        ) {
            when (inlineMode) {
                BooksInlineMode.AddBook -> BookAddSearchSheet(
                    state = addState,
                    onQueryChange = { addBookViewModel.updateQuery(it) },
                    onRetry = { addBookViewModel.search() },
                    onAdd = { addBookViewModel.addAndAnalyzeBook(it) },
                )
                BooksInlineMode.ContentSearch -> BookContentSearchSheet(
                    state = contentSearchState,
                    onQueryChange = { contentSearchViewModel.updateQuery(it) },
                    onResultClick = { result ->
                        searchReturnLocation = searchReturnLocation ?: rememberReadingLocation(
                            currentBookId = state.currentBookId,
                            currentPage = state.currentPage,
                        )
                        targetViewpointId = result.viewpointId
                        viewModel.selectBook(result.bookId)
                        inlineMode = BooksInlineMode.Reading
                    },
                )
                BooksInlineMode.Reading -> Unit
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
                    key(book.id) {
                        val isSelected = book.id == state.currentBookId
                        BookPickerSwipeRow(
                            book = book,
                            isSelected = isSelected,
                            onSelect = {
                                viewModel.selectBook(book.id)
                                scope.launch {
                                    sheetState.hide()
                                }.invokeOnCompletion {
                                    if (!sheetState.isVisible) showBookSheet = false
                                }
                            },
                            onDelete = { viewModel.deleteBook(book.id) },
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
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

fun booksEmptyStateSubtitle(hasCurrentBook: Boolean): String =
    if (hasCurrentBook) "这本书还没有观点，点击搜索重新添加并分析" else "搜索并添加一本书开始阅读"

fun booksAddActionContentDescription(): String = "添加新书"

fun booksContentSearchActionContentDescription(): String = "搜索读书内容"
fun booksFilterMenuText(): String = "筛选书籍"
fun booksTopLevelActionCount(): Int = 3
fun booksAnalysisBannerMessage(routeMessage: String?, inlineMessage: String?): String? = routeMessage ?: inlineMessage
fun booksAddSheetTitle(): String = "添加书籍"
fun booksContentSearchSheetTitle(): String = "搜索读书内容"
fun booksAddSearchLoadingText(): String = "正在搜索全网书籍资料，通常需要 5-10 秒"
fun booksContentSearchLoadingText(): String = "正在搜索本地书籍和观点"
fun booksRestoreReadingText(): String = "返回搜索前阅读"
fun booksSwipeDeleteActionText(): String = "删除"
fun booksPickerUsesSwipeDelete(): Boolean = true
fun booksSwipeDeleteRequiresConfirmation(): Boolean = false
fun booksSwipeDeleteStateKeyedByBookId(): Boolean = true
fun booksSwipeDeleteActionWidthDp(): Int = 72
fun booksSwipeDeleteUsesFullRowBackground(): Boolean = false
fun booksSwipeDeleteMaxRevealDp(): Int = booksSwipeDeleteActionWidthDp()
fun booksPickerRowMinHeightDp(): Int = 72
fun booksSwipeDeleteActionMatchesRowHeight(): Boolean = booksSwipeDeleteActionWidthDp() == booksPickerRowMinHeightDp()
fun booksSwipeDeleteActionIsSquare(): Boolean = true
fun booksPickerRowUsesFixedHeight(): Boolean = true
fun booksPickerRowTextUsesSingleLine(): Boolean = true
fun booksSwipeDeleteUsesJoinedEdgeShapes(): Boolean = true
fun bookResultDoubanActionDescription(): String = "打开豆瓣介绍"
fun bookResultAddActionDescription(): String = "添加并分析"
fun bookSearchRetryActionText(): String = "重新搜索"
fun bookResultActionsUseBottomRow(): Boolean = true

enum class BooksInlineMode {
    Reading,
    AddBook,
    ContentSearch;

    fun openAdd(): BooksInlineMode = AddBook
    fun toggleAdd(): BooksInlineMode = if (this == AddBook) Reading else AddBook
    fun openContentSearch(): BooksInlineMode = ContentSearch
    fun toggleContentSearch(): BooksInlineMode = if (this == ContentSearch) Reading else ContentSearch
}
