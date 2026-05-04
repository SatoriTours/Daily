package com.dailysatori.ui.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals

class BooksScreenUiTextTest {
    @Test
    fun topBarActionsUseAddAndContentSearchLabels() {
        assertEquals("添加新书", booksAddActionContentDescription())
        assertEquals("搜索读书内容", booksContentSearchActionContentDescription())
        assertEquals("筛选书籍", booksFilterMenuText())
        assertEquals(3, booksTopLevelActionCount())
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
}
