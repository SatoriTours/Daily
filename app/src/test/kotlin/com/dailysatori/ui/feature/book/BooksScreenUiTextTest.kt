package com.dailysatori.ui.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals

class BooksScreenUiTextTest {
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
}
