package com.dailysatori.ui.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals

class BookSearchUiTextTest {
    @Test
    fun primaryBookActionAddsAndAnalyzes() {
        assertEquals("添加并分析", bookSearchPrimaryActionText(isAnalyzing = false))
        assertEquals("分析中...", bookSearchPrimaryActionText(isAnalyzing = true))
    }

    @Test
    fun addBookSearchUsesSingleInputIconAndCompactAction() {
        assertEquals(false, bookAddSearchShowsTrailingSearchButton())
        assertEquals("添加并分析", compactBookAddActionText(isAnalyzing = false))
        assertEquals("分析中", compactBookAddActionText(isAnalyzing = true))
    }

    @Test
    fun bookSearchShowsTimeoutInsteadOfLoadingForever() {
        assertEquals("搜索超时，请换个关键词再试", bookSearchTimeoutMessage())
        kotlin.test.assertTrue(bookSearchTimeoutMs() >= 30_000L)
        kotlinx.coroutines.runBlocking {
            val error = try {
                kotlinx.coroutines.withTimeout(1) { kotlinx.coroutines.delay(10) }
                error("expected timeout")
            } catch (error: Exception) {
                error
            }
            assertEquals("搜索超时，请换个关键词再试", bookSearchFailureMessage(error))
        }
    }

    @Test
    fun candidatePromptPrefersChineseResultsForChineseQuery() {
        val prompt = buildChineseBookSearchInstruction("孔子")

        kotlin.test.assertTrue(prompt.contains("优先返回中文"))
        kotlin.test.assertTrue(prompt.contains("孔子"))
    }

    @Test
    fun hidesSearchResultsWhenQueryIsBlank() {
        val state = BookSearchState(
            query = "",
            results = listOf(
                com.dailysatori.service.book.BookSearchResult(
                    title = "原则",
                    author = "Ray Dalio",
                    category = "管理",
                    introduction = "",
                    coverUrl = "",
                    sourceSummary = "",
                ),
            ),
        )

        assertEquals(emptyList(), state.visibleResults)
    }

    @Test
    fun buildsDoubanSearchUrlFromBookResult() {
        val result = com.dailysatori.service.book.BookSearchResult(title = "原则", author = "Ray Dalio")

        assertEquals("https://www.douban.com/search?q=%E5%8E%9F%E5%88%99+Ray+Dalio", doubanBookSearchUrl(result))
    }

    @Test
    fun bookResultActionsUseIconsWithAccessibleLabels() {
        assertEquals("打开豆瓣介绍", bookResultDoubanActionDescription())
        assertEquals("添加并分析", bookResultAddActionDescription())
        assertEquals("重新搜索", bookSearchRetryActionText())
        assertEquals(true, bookResultActionsUseBottomRow())
    }
}
