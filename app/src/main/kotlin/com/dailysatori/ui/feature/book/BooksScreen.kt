package com.dailysatori.ui.feature.book

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.dailysatori.shared.db.Book
import com.dailysatori.service.book.BookSearchResult
import com.dailysatori.ui.component.appbar.AppTopBar
import com.dailysatori.ui.component.dialog.ConfirmDialog
import com.dailysatori.ui.component.indicator.EmptyState
import com.dailysatori.ui.component.indicator.LoadingIndicator
import com.dailysatori.ui.theme.Radius
import com.dailysatori.ui.theme.Spacing
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
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

data class BookReadingLocation(val bookId: Long, val page: Int)

fun rememberReadingLocation(currentBookId: Long?, currentPage: Int): BookReadingLocation? =
    currentBookId?.let { BookReadingLocation(it, currentPage) }

@Composable
private fun BookPickerSwipeRow(
    book: Book,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val revealWidthPx = with(LocalDensity.current) { booksSwipeDeleteMaxRevealDp().dp.toPx() }
    var offsetX by remember(book.id) { mutableFloatStateOf(0f) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                modifier = Modifier
                    .size(booksSwipeDeleteActionWidthDp().dp)
                    .clip(bookPickerDeleteActionShape())
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable(onClick = onDelete),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    booksSwipeDeleteActionText(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(revealWidthPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = { offsetX = if (offsetX <= -revealWidthPx / 2f) -revealWidthPx else 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(-revealWidthPx, 0f)
                        },
                    )
                },
        ) {
            BookPickerRow(book = book, isSelected = isSelected, onSelect = onSelect)
        }
    }
}

@Composable
private fun BookPickerRow(
    book: Book,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(booksPickerRowMinHeightDp().dp)
            .clip(bookPickerRowShape())
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onSelect)
            .padding(Spacing.m),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.author.isNotBlank()) {
                Text(
                    book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
}

enum class BooksInlineMode {
    Reading,
    AddBook,
    ContentSearch;

    fun openAdd(): BooksInlineMode = AddBook
    fun toggleAdd(): BooksInlineMode = if (this == AddBook) Reading else AddBook
    fun openContentSearch(): BooksInlineMode = ContentSearch
    fun toggleContentSearch(): BooksInlineMode = if (this == ContentSearch) Reading else ContentSearch
}

@Composable
private fun BookAddSearchSheet(
    state: BookSearchState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onAdd: (BookSearchResult) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m)
            .padding(bottom = Spacing.xxl),
    ) {
        Text(booksAddSheetTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(Spacing.s))
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入书名添加...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(Radius.s),
        )
        if (state.isAnalyzing || state.analysisMessage != null) {
            AnalysisStatus(
                isAnalyzing = state.isAnalyzing,
                step = state.analysisStep,
                message = state.analysisMessage,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
        if (state.error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = onRetry,
                    enabled = state.query.isNotBlank() && !state.isLoading,
                    contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.xxs),
                ) {
                    Text(bookSearchRetryActionText(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (state.isLoading) SearchSheetStatus(booksAddSearchLoadingText())
        LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
            items(state.visibleResults.take(8)) { result ->
                BookSearchResultCard(
                    result = result,
                    isAnalyzing = state.isAnalyzing,
                    onAdd = { onAdd(result) },
                    onOpenDouban = { uriHandler.openUri(doubanBookSearchUrl(result)) },
                )
            }
        }
    }
}

@Composable
private fun BookSearchResultCard(
    result: BookSearchResult,
    isAnalyzing: Boolean,
    onAdd: () -> Unit,
    onOpenDouban: () -> Unit,
) {
    val context = LocalContext.current
    val imageRequest = remember(context, result.coverUrl) {
        ImageRequest.Builder(context)
            .data(result.coverUrl.ifBlank { null })
            .httpHeaders(
                NetworkHeaders.Builder()
                    .set("Referer", "https://book.douban.com/")
                    .set("User-Agent", "Mozilla/5.0 DailySatori Android")
                    .build(),
            )
            .build()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.s)
            .clip(RoundedCornerShape(Radius.m))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(Spacing.s),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = imageRequest,
            placeholder = painterResource(android.R.drawable.ic_menu_gallery),
            error = painterResource(android.R.drawable.ic_menu_gallery),
            contentDescription = result.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 72.dp, height = 104.dp).clip(RoundedCornerShape(Radius.s)),
        )
        Spacer(modifier = Modifier.width(Spacing.s))
        Column(modifier = Modifier.weight(1f)) {
            Text(result.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (result.author.isNotBlank()) Text(result.author, style = MaterialTheme.typography.labelSmall)
            if (result.introduction.isNotBlank()) {
                Text(
                    bookContentSearchPreview(result.introduction, 90),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FilledTonalButton(
                    onClick = onOpenDouban,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.xxs),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = bookResultDoubanActionDescription(),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xxs))
                    Text("豆瓣", style = MaterialTheme.typography.labelSmall)
                }
                FilledTonalButton(
                    onClick = onAdd,
                    enabled = !isAnalyzing,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.xxs),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = bookResultAddActionDescription(),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xxs))
                    Text("添加", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun bookPickerRowShape() = RoundedCornerShape(
    topStart = Radius.s,
    bottomStart = Radius.s,
    topEnd = 0.dp,
    bottomEnd = 0.dp,
)

private fun bookPickerDeleteActionShape() = RoundedCornerShape(
    topStart = 0.dp,
    bottomStart = 0.dp,
    topEnd = Radius.s,
    bottomEnd = Radius.s,
)

@Composable
private fun BookContentSearchSheet(
    state: BookContentSearchState,
    onQueryChange: (String) -> Unit,
    onResultClick: (BookContentSearchResultItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m)
            .padding(bottom = Spacing.xxl),
    ) {
        Text(booksContentSearchSheetTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(Spacing.s))
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索书名、作者或观点...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(Radius.s),
        )
        if (state.query.isNotBlank() && !state.hasSearched) SearchSheetStatus(booksContentSearchLoadingText())
        LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
            items(state.visibleResults.take(12)) { result ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onResultClick(result) }
                        .padding(top = Spacing.s)
                        .clip(RoundedCornerShape(Radius.m))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(Spacing.s),
                ) {
                    Text(result.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(bookContentSearchBookLine(result.bookTitle, result.author), style = MaterialTheme.typography.labelSmall)
                    Text(
                        bookContentSearchPreview(result.content),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                    if (result.example.isNotBlank()) {
                        Text(
                            "例子：${bookContentSearchPreview(result.example, 42)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xxs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSheetStatus(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.s)
            .clip(RoundedCornerShape(Radius.s))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .padding(Spacing.s),
    )
}
