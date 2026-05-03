package com.dailysatori.service.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpAgentPresentationTest {
    @Test
    fun buildsFallbackAnswerFromArticleResultsWhenAiFinalAnswerFails() {
        val answer = buildFallbackAnswer(
            query = "这两天有什么重要新闻",
            listOf(
                McpSearchResult(1, "article", "新闻一", "摘要一", "2026-05-02", isFavorite = true),
                McpSearchResult(2, "article", "新闻二", null, "2026-05-01"),
                McpSearchResult(3, "article", "新闻三", "摘要三", null),
            ),
        )

        assertTrue(answer.contains("找到 3 条相关内容"))
        assertTrue(answer.contains("## 结论"))
        assertTrue(answer.contains("## 重点内容"))
        assertTrue(answer.contains("## 可继续查看"))
        assertTrue(answer.contains("新闻一"))
        assertTrue(answer.contains("新闻二"))
        assertFalse(answer.contains("AI 请求失败"))
    }

    @Test
    fun answersSecondLatestDiaryDateFromFallbackResults() {
        val answer = buildFallbackAnswer(
            query = "倒数第二天的日记是哪一天的",
            results = listOf(
                McpSearchResult(11, "diary", "2026年05月03日的日记", "最新日记", "2026-05-03"),
                McpSearchResult(10, "diary", "2026年05月02日的日记", "倒数第二篇日记", "2026-05-02"),
            ),
        )

        assertTrue(answer.contains("2026-05-02"))
        assertTrue(answer.contains("倒数第二"))
        assertFalse(answer.contains("AI 总结暂时失败"))
    }

    @Test
    fun mapsSearchResultTypesToReadableLabels() {
        assertEquals("文章", searchResultTypeLabel("article"))
        assertEquals("日记", searchResultTypeLabel("diary"))
        assertEquals("书籍", searchResultTypeLabel("book"))
        assertEquals("内容", searchResultTypeLabel("unknown"))
    }

    @Test
    fun articleDiaryAndBookSearchResultsCanOpenDetails() {
        assertEquals(true, canOpenSearchResult("article"))
        assertEquals(true, canOpenSearchResult("diary"))
        assertEquals(true, canOpenSearchResult("book"))
        assertEquals(false, canOpenSearchResult("unknown"))
    }

    @Test
    fun mapsSearchResultsToOpenTargets() {
        assertEquals(SearchResultOpenTarget.Article, searchResultOpenTarget("article"))
        assertEquals(SearchResultOpenTarget.Diary, searchResultOpenTarget("diary"))
        assertEquals(SearchResultOpenTarget.Book, searchResultOpenTarget("book"))
        assertEquals(null, searchResultOpenTarget("unknown"))
    }

    @Test
    fun retriesAiSummaryThreeTimesBeforeFallback() {
        assertEquals(listOf(1, 2, 3), aiSummaryRetryAttempts())
    }
}
