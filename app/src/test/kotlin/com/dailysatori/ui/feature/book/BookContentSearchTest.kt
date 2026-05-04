package com.dailysatori.ui.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookContentSearchTest {
    @Test
    fun matchesBookAndViewpointText() {
        val result = BookContentSearchResultItem(
            viewpointId = 1,
            bookId = 2,
            bookTitle = "穷查理宝典",
            author = "查理·芒格",
            title = "逆向思考",
            content = "先想清楚什么会失败。",
            example = "投资前列出风险。",
        )

        assertTrue(result.matches("查理"))
        assertTrue(result.matches("逆向"))
        assertTrue(result.matches("风险"))
        assertFalse(result.matches("不存在"))
    }

    @Test
    fun formatsSearchResultBookLine() {
        assertEquals("《原则》 · Ray Dalio", bookContentSearchBookLine("原则", "Ray Dalio"))
        assertEquals("《原则》", bookContentSearchBookLine("原则", ""))
    }

    @Test
    fun hidesResultsWhenQueryIsBlank() {
        val state = BookContentSearchState(
            query = "",
            results = listOf(
                BookContentSearchResultItem(1, 2, "书", "作者", "观点", "内容", "例子"),
            ),
        )

        assertEquals(emptyList(), state.visibleResults)
    }

    @Test
    fun formatsContentSearchPreview() {
        assertEquals("先想清楚什么会失败。", bookContentSearchPreview("先想清楚什么会失败。", 20))
        assertEquals("先想清楚什么会失败...", bookContentSearchPreview("先想清楚什么会失败，然后反过来避免它。", 10))
    }
}
