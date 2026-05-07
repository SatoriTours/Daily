package com.dailysatori.ui.feature.article

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleDetailContentTest {
    @Test
    fun selectsSummaryAndOriginalContentForPagerPages() {
        assertEquals("summary", articleDetailPageContent(0, "summary", "original"))
        assertEquals("original", articleDetailPageContent(1, "summary", "original"))
    }

    @Test
    fun fallsBackWhenPagerContentIsMissing() {
        assertEquals("暂无摘要内容", articleDetailPageContent(0, null, "original"))
        assertEquals("暂无原文内容", articleDetailPageContent(1, "summary", null))
    }

    @Test
    fun trimsOuterWhitespaceBeforeRenderingMarkdown() {
        assertEquals("# 标题\n正文", articleDetailPageContent(0, "\n\n# 标题\n正文\n", null))
        assertEquals("原文", articleDetailPageContent(1, null, "\n\n原文\n"))
    }

    @Test
    fun allowsManualRefreshAtAnyProcessingState() {
        assertTrue(canManuallyRefreshArticle(isRefreshing = true, articleStatus = "error"))
        assertTrue(canManuallyRefreshArticle(isRefreshing = true, articleStatus = "pending"))
        assertTrue(canManuallyRefreshArticle(isRefreshing = true, articleStatus = "webContentFetched"))
        assertTrue(canManuallyRefreshArticle(isRefreshing = true, articleStatus = "aiProcessing"))
        assertTrue(canManuallyRefreshArticle(isRefreshing = false, articleStatus = "completed"))
    }

    @Test
    fun deleteArticleDialogCopyWarnsBeforeDeleting() {
        assertEquals("删除文章", articleDeleteDialogTitle())
        assertEquals("确定要删除这篇文章吗？", articleDeleteDialogMessage())
    }
}
