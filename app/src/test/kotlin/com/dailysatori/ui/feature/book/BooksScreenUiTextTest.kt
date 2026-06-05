package com.dailysatori.ui.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

class BooksScreenUiTextTest {
    @Test
    fun topBarActionsUseAddAndContentSearchLabels() {
        assertEquals("添加新书", booksAddActionContentDescription())
        assertEquals("搜索读书内容", booksContentSearchActionContentDescription())
        assertEquals("筛选书籍", booksFilterMenuText())
        assertEquals(1, booksTopLevelActionCount())
    }

    @Test
    fun immersiveReaderRemovesChromeAndKeepsSwipePaging() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt").readText()
        val readerFlow = source.extractBetween(
            start = "                } else {\n                    val pagerState = rememberPagerState(",
            end = "\n    if (inlineMode != BooksInlineMode.Reading)",
        )
        val pagerBlock = readerFlow.extractCallBlock("HorizontalPager(")

        assertEquals("读书", booksReaderTitle(null, null))
        assertEquals("原则", booksReaderTitle("原则", "Ray Dalio"))
        assertEquals("更多读书操作", booksMoreActionsContentDescription())
        assertTrue(source.contains("AppScaffold("))
        assertTrue(source.contains("Column(modifier = modifier.fillMaxSize())"))
        assertFalse(source.contains("AppTopBar("))
        assertFalse(source.contains("Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)"))
        assertTrue(readerFlow.contains("HorizontalPager("))
        assertTrue(pagerBlock.contains("ViewpointCard("))
        assertTrue(pagerBlock.contains("bookTitle = currentBook?.title.orEmpty()"))
        assertTrue(pagerBlock.contains("author = currentBook?.author.orEmpty()"))
        assertTrue(pagerBlock.contains("reserveBottomSpace = true"))
        assertFalse(pagerBlock.contains("showProgress = true"))
        assertFalse(readerFlow.contains("BookReadingProgressStrip("))
        assertFalse(readerFlow.contains("BookReadingNavigationBar("))
        assertFalse(readerFlow.contains("pagerState.animateScrollToPage"))
    }

    @Test
    fun viewpointReaderCentersTitleAndHidesProgressByDefault() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt").readText()

        assertTrue(source.contains("showProgress: Boolean = false"))
        assertTrue(source.contains("textAlign = TextAlign.Center"))
        assertTrue(source.contains("horizontalAlignment = Alignment.CenterHorizontally"))
        assertFalse(source.contains("Modifier.weight(1f)"))
    }

    @Test
    fun inlineModesSwitchBetweenAddSearchAndReading() {
        assertEquals(BooksInlineMode.AddBook, BooksInlineMode.Reading.toggleAdd())
        assertEquals(BooksInlineMode.Reading, BooksInlineMode.AddBook.toggleAdd())
        assertEquals(BooksInlineMode.AddBook, BooksInlineMode.Reading.openAdd())
        assertEquals(BooksInlineMode.ContentSearch, BooksInlineMode.AddBook.openContentSearch())
        assertEquals(BooksInlineMode.Reading, BooksInlineMode.ContentSearch.toggleContentSearch())
        assertEquals(BooksInlineMode.ContentSearch, BooksInlineMode.Reading.toggleContentSearch())
    }

    @Test
    fun emptyStateSubtitleInvitesRetryWhenCurrentBookHasNoViewpoints() {
        assertEquals(
            "这本书还没有观点，点击搜索重新添加并分析",
            booksEmptyStateSubtitle(hasCurrentBook = true),
        )
    }

    @Test
    fun emptyStateSubtitleKeepsAddBookPromptWhenNoCurrentBook() {
        assertEquals(
            "搜索并添加一本书开始阅读",
            booksEmptyStateSubtitle(hasCurrentBook = false),
        )
    }

    @Test
    fun inlineAddBookKeepsAnalysisMessageAfterSelectingBook() {
        assertEquals("已生成 6 个观点，可稍后重试补全", booksAnalysisBannerMessage(null, "已生成 6 个观点，可稍后重试补全"))
        assertEquals("路由消息", booksAnalysisBannerMessage("路由消息", "内联消息"))
    }

    @Test
    fun bottomSheetLabelsExplainSearchProgress() {
        assertEquals("添加书籍", booksAddSheetTitle())
        assertEquals("搜索读书内容", booksContentSearchSheetTitle())
        assertEquals("正在搜索全网书籍资料，通常需要 5-10 秒", booksAddSearchLoadingText())
        assertEquals("正在搜索本地书籍和观点", booksContentSearchLoadingText())
    }

    @Test
    fun booksReflectionActionTextIsRestrained() {
        assertEquals("深入想想", booksReflectionActionText())
    }

    @Test
    fun reflectionDraftClearsWhenOpeningAndDismissingSheet() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt").readText()
        val reflectionOpenBlock = source.extractBetween(
            start = "                            onReflect = {\n",
            end = "                                showReflectionSheet = true",
        )

        assertTrue(reflectionOpenBlock.contains("reflectionInput = \"\""))
        assertTrue(
            source.contains(
                "onDismissRequest = {\n" +
                    "                reflectionViewModel.stopGeneration()\n" +
                    "                reflectionInput = \"\"\n" +
                    "                showReflectionSheet = false\n" +
                    "            }",
            ),
        )
    }

    @Test
    fun keepsPreviousReadingLocationAfterOpeningSearchResult() {
        val location = BookReadingLocation(bookId = 7, page = 3)

        assertEquals("返回搜索前阅读", booksRestoreReadingText())
        assertEquals(location, rememberReadingLocation(currentBookId = 7, currentPage = 3))
        assertEquals(null, rememberReadingLocation(currentBookId = null, currentPage = 3))
    }

    @Test
    fun bookPickerSupportsDirectSwipeDelete() {
        assertEquals("删除", booksSwipeDeleteActionText())
        assertEquals(true, booksPickerUsesSwipeDelete())
        assertEquals(false, booksSwipeDeleteRequiresConfirmation())
        assertEquals(true, booksSwipeDeleteStateKeyedByBookId())
        assertEquals(72, booksSwipeDeleteActionWidthDp())
        assertEquals(false, booksSwipeDeleteUsesFullRowBackground())
        assertEquals(72, booksSwipeDeleteMaxRevealDp())
        assertEquals(72, booksPickerRowMinHeightDp())
        assertEquals(true, booksSwipeDeleteActionMatchesRowHeight())
        assertEquals(true, booksSwipeDeleteActionIsSquare())
        assertEquals(true, booksPickerRowUsesFixedHeight())
        assertEquals(true, booksPickerRowTextUsesSingleLine())
        assertEquals(true, booksSwipeDeleteUsesJoinedEdgeShapes())
    }

    private fun String.extractBetween(start: String, end: String): String {
        assertTrue(contains(start), "Missing start anchor: $start")
        val afterStart = substringAfter(start)
        assertTrue(afterStart.contains(end), "Missing end anchor after $start: $end")
        return afterStart.substringBefore(end)
    }

    private fun String.extractCallBlock(anchor: String): String {
        assertTrue(contains(anchor), "Missing call anchor: $anchor")
        val start = indexOf(anchor)
        val bodyStart = indexOf('{', start)
        assertTrue(bodyStart >= 0, "Missing block body for call anchor: $anchor")

        var depth = 0
        for (index in bodyStart until length) {
            when (this[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return substring(start, index + 1)
                }
            }
        }
        throw AssertionError("Missing closing brace for call anchor: $anchor")
    }
}
