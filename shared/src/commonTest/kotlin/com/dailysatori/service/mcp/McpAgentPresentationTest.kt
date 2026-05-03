package com.dailysatori.service.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpAgentPresentationTest {
    @Test
    fun buildsFallbackAnswerFromArticleResultsWhenAiFinalAnswerFails() {
        val answer = buildFallbackAnswer(
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
    fun mapsSearchResultTypesToReadableLabels() {
        assertEquals("文章", searchResultTypeLabel("article"))
        assertEquals("日记", searchResultTypeLabel("diary"))
        assertEquals("书籍", searchResultTypeLabel("book"))
        assertEquals("内容", searchResultTypeLabel("unknown"))
    }

    @Test
    fun onlyArticleSearchResultsCanOpenDetails() {
        assertEquals(true, canOpenSearchResult("article"))
        assertEquals(false, canOpenSearchResult("diary"))
        assertEquals(false, canOpenSearchResult("book"))
    }
}
