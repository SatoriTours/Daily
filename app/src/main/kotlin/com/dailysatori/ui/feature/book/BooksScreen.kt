package com.dailysatori.ui.feature.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.dailysatori.shared.db.Book
import com.dailysatori.ui.component.dialog.ConfirmDialog
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.component.scaffold.AppScaffold
import com.dailysatori.ui.feature.book.component.BookAddSearchSheet
import com.dailysatori.ui.feature.book.component.BookContentSearchSheet
import com.dailysatori.ui.feature.book.component.BookPickerSwipeRow
import com.dailysatori.ui.theme.Radius
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
    onMyClick: () -> Unit = {},
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

    val currentBook = state.books.find { it.id == state.currentBookId }

    AppScaffold(
        title = booksReaderTitle(currentBook?.title, currentBook?.author),
        showBack = false,
        myNavigationLabel = "我的",
        onMyNavigationClick = onMyClick,
        actions = {
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = booksMoreActionsContentDescription())
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(booksAddActionContentDescription()) },
                        leadingIcon = { Icon(Icons.Default.Add, null) },
                        onClick = { inlineMode = BooksInlineMode.AddBook; showMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(booksContentSearchActionContentDescription()) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        onClick = { inlineMode = BooksInlineMode.ContentSearch; showMenu = false },
                    )
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
    ) { modifier ->
        Column(modifier = modifier.fillMaxSize()) {
            val visibleAnalysisMessage = booksAnalysisBannerMessage(bookAnalysisMessage, inlineBookAnalysisMessage)
            if (visibleAnalysisMessage != null) {
                BooksInlineNotice(text = visibleAnalysisMessage)
            }
            searchReturnLocation?.let { location ->
                BooksInlineNotice(
                    text = booksRestoreReadingText(),
                    onClick = {
                        targetViewpointId = null
                        viewModel.selectBook(location.bookId)
                        viewModel.setPage(location.page)
                        searchReturnLocation = null
                    },
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

                        ViewpointCard(
                            title = vp.title,
                            content = vp.content,
                            example = vp.example,
                            bookTitle = currentBook?.title.orEmpty(),
                            author = currentBook?.author.orEmpty(),
                            page = page,
                            total = state.viewpoints.size,
                            fillAvailableHeight = true,
                            status = vp.status,
                            errorMessage = vp.error_message,
                            onRetry = { viewModel.regenerateViewpoint(vp.id) },
                        )
                    }
                }
            }
        }
    }

    if (inlineMode != BooksInlineMode.Reading) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { inlineMode = BooksInlineMode.Reading },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
            tonalElevation = 0.dp,
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
            tonalElevation = 0.dp,
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

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    contentPadding = PaddingValues(bottom = bookPickerBottomPaddingDp().dp),
                ) {
                    items(state.books, key = { it.id }) { book ->
                        BookPickerSwipeRow(
                            book = book,
                            isSelected = book.id == state.currentBookId,
                            onSelect = {
                                viewModel.selectBook(book.id)
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
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

@Composable
private fun BooksInlineNotice(text: String, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m, vertical = Spacing.xs)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(Radius.l),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
        )
    }
}

fun booksEmptyStateSubtitle(hasCurrentBook: Boolean): String =
    if (hasCurrentBook) "这本书还没有观点，点击搜索重新添加并分析" else "搜索并添加一本书开始阅读"

fun booksAddActionContentDescription(): String = "添加新书"

fun booksContentSearchActionContentDescription(): String = "搜索读书内容"
fun booksMoreActionsContentDescription(): String = "更多读书操作"
fun booksFilterMenuText(): String = "筛选书籍"
fun booksTopLevelActionCount(): Int = 1
fun booksReaderTitle(title: String?, author: String?): String = title?.takeIf { it.isNotBlank() } ?: "读书"
fun booksReadingProgressText(page: Int, total: Int): String = "${(page + 1).coerceAtMost(total.coerceAtLeast(1))} / ${total.coerceAtLeast(1)}"
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
fun bookPickerUsesLazyList(): Boolean = true
fun bookPickerBottomPaddingDp(): Int = 48
fun bookResultSourceActionDescription(): String = "打开微信读书介绍"
fun bookResultSourceActionText(): String = "微信读书"
fun bookResultAddActionDescription(): String = "添加并分析"
fun bookSearchRetryActionText(): String = "重新搜索"
fun bookResultActionsUseBottomRow(): Boolean = true
fun bookResultIntroductionPreviewLength(): Int = 180
fun bookResultPrimaryActionText(isAnalyzing: Boolean): String = if (isAnalyzing) "分析中" else "添加并分析"

enum class BooksInlineMode {
    Reading,
    AddBook,
    ContentSearch;

    fun openAdd(): BooksInlineMode = AddBook
    fun toggleAdd(): BooksInlineMode = if (this == AddBook) Reading else AddBook
    fun openContentSearch(): BooksInlineMode = ContentSearch
    fun toggleContentSearch(): BooksInlineMode = if (this == ContentSearch) Reading else ContentSearch
}
