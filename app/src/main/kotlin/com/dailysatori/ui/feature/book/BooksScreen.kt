package com.dailysatori.ui.feature.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    selectedBookId: Long? = null,
    selectedViewpointId: Long? = null,
    bookAnalysisMessage: String? = null,
    onSelectedBookConsumed: () -> Unit = {},
    onBookAnalysisMessageConsumed: () -> Unit = {},
    onMyClick: () -> Unit = {},
) {
    val viewModel: BooksViewModel = koinViewModel()
    val addBookViewModel: BookSearchViewModel = koinViewModel()
    val contentSearchViewModel: BookContentSearchViewModel = koinViewModel()
    val reflectionViewModel: BookReflectionViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val addState by addBookViewModel.state.collectAsState()
    val contentSearchState by contentSearchViewModel.state.collectAsState()
    val reflectionState by reflectionViewModel.state.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<Book?>(null) }
    var showBookSheet by remember { mutableStateOf(false) }
    var showReflectionSheet by remember { mutableStateOf(false) }
    var reflectionInput by remember { mutableStateOf("") }
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

    LaunchedEffect(bookAnalysisMessage) {
        if (bookAnalysisMessage != null) {
            inlineBookAnalysisMessage = bookAnalysisMessage
            onBookAnalysisMessageConsumed()
        }
    }

    LaunchedEffect(inlineBookAnalysisMessage) {
        if (inlineBookAnalysisMessage != null) {
            delay(booksAnalysisNoticeDurationMs())
            inlineBookAnalysisMessage = null
        }
    }

    LaunchedEffect(state.refreshMessage) {
        if (state.refreshMessage != null) {
            delay(booksAnalysisNoticeDurationMs())
            viewModel.clearRefreshMessage()
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

    fun openReflectionForCurrentPage() {
        val vp = state.viewpoints.getOrNull(state.currentPage) ?: return
        reflectionViewModel.stopGeneration()
        reflectionInput = ""
        reflectionViewModel.openViewpoint(
            viewpointId = vp.id,
            bookTitle = currentBook?.title.orEmpty(),
            author = currentBook?.author.orEmpty(),
            viewpointTitle = vp.title,
            viewpointContent = vp.content,
            viewpointExample = vp.example,
        )
        showReflectionSheet = true
    }

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
                    if (state.currentBookId != null) {
                        DropdownMenuItem(
                            text = { Text(booksRefreshCurrentBookMenuText()) },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) },
                            enabled = state.refreshingBookId == null,
                            onClick = { viewModel.refreshCurrentBook(); showMenu = false },
                        )
                    }
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
        floatingActionButton = {
            if (state.viewpoints.isNotEmpty()) {
                MiniReflectButton(onClick = ::openReflectionForCurrentPage)
            }
        },
    ) { modifier ->
        Column(modifier = modifier.fillMaxSize()) {
            val visibleAnalysisMessage = booksAnalysisBannerMessage(bookAnalysisMessage, inlineBookAnalysisMessage)
            if (visibleAnalysisMessage != null) {
                BooksInlineNotice(text = visibleAnalysisMessage)
            }
            if (state.refreshingBookId != null) {
                val refreshingTitle = state.books.find { it.id == state.refreshingBookId }?.title ?: currentBook?.title.orEmpty()
                BooksInlineNotice(text = booksRefreshInProgressText(refreshingTitle))
            }
            state.refreshMessage?.let { message ->
                BooksInlineNotice(text = message)
            }
            state.error?.let { error ->
                BooksInlineNotice(text = error)
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
                            reserveBottomSpace = true,
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

    if (showReflectionSheet) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden },
        )
        val closeReflectionSheet = {
            reflectionViewModel.stopGeneration()
            reflectionInput = ""
            showReflectionSheet = false
        }
        ModalBottomSheet(
            onDismissRequest = closeReflectionSheet,
            sheetState = sheetState,
            dragHandle = {
                ReflectionSheetDragHandle(onDismiss = closeReflectionSheet)
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
            tonalElevation = 0.dp,
        ) {
            BookReflectionSheet(
                state = reflectionState,
                inputText = reflectionInput,
                onInputChange = { reflectionInput = it },
                onSend = {
                    if (reflectionInput.isNotBlank()) {
                        reflectionViewModel.sendMessage(reflectionInput)
                        reflectionInput = ""
                    }
                },
                onStop = reflectionViewModel::stopGeneration,
                onPromptClick = { prompt ->
                    reflectionInput = prompt
                    reflectionViewModel.sendMessage(prompt)
                    reflectionInput = ""
                },
                onGenerateSummary = reflectionViewModel::generateSummary,
                onNewQuestion = reflectionViewModel::createNewSegment,
                onShowCurrent = reflectionViewModel::showCurrent,
                onShowHistory = reflectionViewModel::showHistory,
                onShowSettled = reflectionViewModel::showSettled,
                onSelectSession = reflectionViewModel::selectSession,
                onDeleteSession = reflectionViewModel::deleteSession,
                onRetryLatest = reflectionViewModel::retryLatest,
            )
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
                            onRefresh = { viewModel.refreshBook(book.id) },
                            isRefreshing = state.refreshingBookId == book.id,
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
private fun MiniReflectButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(bottom = 88.dp)
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 6.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = booksReflectionActionText(), modifier = Modifier.size(21.dp))
            }
        }
    }
}

private const val reflectionSheetDragDismissThresholdPx = 80f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReflectionSheetDragHandle(onDismiss: () -> Unit) {
    var dragDistancePx by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.s, bottom = Spacing.xs)
            .pointerInput(onDismiss) {
                detectVerticalDragGestures(
                    onDragStart = { dragDistancePx = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0f) {
                            dragDistancePx += dragAmount
                        }
                    },
                    onDragEnd = {
                        if (dragDistancePx >= reflectionSheetDragDismissThresholdPx) {
                            onDismiss()
                        }
                        dragDistancePx = 0f
                    },
                    onDragCancel = {
                        dragDistancePx = 0f
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        BottomSheetDefaults.DragHandle()
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
fun booksRefreshCurrentBookMenuText(): String = "刷新此书"
fun booksTopLevelActionCount(): Int = 1
fun booksReaderTitle(title: String?, author: String?): String = title?.takeIf { it.isNotBlank() } ?: "读书"
fun booksReadingProgressText(page: Int, total: Int): String = "${(page + 1).coerceAtMost(total.coerceAtLeast(1))} / ${total.coerceAtLeast(1)}"
fun booksAnalysisBannerMessage(routeMessage: String?, inlineMessage: String?): String? = routeMessage ?: inlineMessage
fun booksAnalysisNoticeDurationMs(): Long = 4_000L
fun booksRefreshInProgressText(title: String): String = "正在更新《${title.ifBlank { "这本书" }}》的读书观点"
fun booksRefreshQueuedText(title: String): String = "《${title.ifBlank { "这本书" }}》观点更新已加入任务"
fun booksRefreshSuccessText(title: String): String = "《${title.ifBlank { "这本书" }}》读书观点已更新"
fun booksAddSheetTitle(): String = "添加书籍"
fun booksContentSearchSheetTitle(): String = "搜索读书内容"
fun booksAddSearchLoadingText(): String = "正在搜索全网书籍资料，通常需要 5-10 秒"
fun booksContentSearchLoadingText(): String = "正在搜索本地书籍和观点"
fun booksReflectionActionText(): String = "想一想"
fun booksRestoreReadingText(): String = "返回搜索前阅读"
fun booksSwipeDeleteActionText(): String = "删除"
fun booksSwipeRefreshActionText(): String = "更新"
fun booksSwipeRefreshingActionText(): String = "更新中"
fun booksPickerUsesSwipeDelete(): Boolean = true
fun booksPickerUsesSwipeRefresh(): Boolean = true
fun booksSwipeDeleteRequiresConfirmation(): Boolean = false
fun booksSwipeDeleteStateKeyedByBookId(): Boolean = true
fun booksSwipeDeleteActionWidthDp(): Int = 72
fun booksSwipeRefreshActionWidthDp(): Int = 72
fun booksSwipeDeleteUsesFullRowBackground(): Boolean = false
fun booksSwipeDeleteMaxRevealDp(): Int = booksSwipeDeleteActionWidthDp()
fun booksSwipeRefreshMaxRevealDp(): Int = booksSwipeRefreshActionWidthDp()
fun booksPickerRowMinHeightDp(): Int = 72
fun booksSwipeDeleteActionMatchesRowHeight(): Boolean = booksSwipeDeleteActionWidthDp() == booksPickerRowMinHeightDp()
fun booksSwipeRefreshActionMatchesRowHeight(): Boolean = booksSwipeRefreshActionWidthDp() == booksPickerRowMinHeightDp()
fun booksSwipeDeleteActionIsSquare(): Boolean = true
fun booksSwipeRefreshActionIsSquare(): Boolean = true
fun booksPickerRowUsesFixedHeight(): Boolean = true
fun booksPickerRowTextUsesSingleLine(): Boolean = true
fun booksSwipeDeleteUsesJoinedEdgeShapes(): Boolean = true
fun booksSwipeRefreshUsesJoinedEdgeShapes(): Boolean = true
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
