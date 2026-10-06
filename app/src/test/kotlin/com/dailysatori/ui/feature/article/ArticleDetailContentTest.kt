package com.dailysatori.ui.feature.article

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleDetailContentTest {
    @Test
    fun generatedSourceFooterIsHiddenWithoutRemovingAnalysisOrOtherReferences() {
        val brief = "**新闻事实：** 服务已恢复。\n\n**为什么重要（分析）：** 需核查失败任务。"
        assertEquals(brief, articleDetailPageContent(0,
            "$brief\n\n来源：[原文](<https://example.com/incident>)", null))
        assertEquals(brief, articleDetailPageContent(0,
            "$brief\n\n来源：[原文](https://example.com/incident)", null))
        val references = "$brief\n\n来源：[官方公告](https://example.com/notice) · [报告](https://example.com/report)"
        assertEquals(references, articleDetailPageContent(0, references, null))
        val original = "原文正文。\n\n来源：[原文](https://example.com/incident)"
        assertEquals(original, articleDetailPageContent(1, brief, original))
    }

    @Test
    fun remoteOriginalShowsBodyWithoutSnapshotSummaryAndViewpoints() {
        assertEquals("Full original", articleDetailPageContent(
            page = 1, summary = "Summary", original = null,
            storedOriginal = "## 摘要\n\nSummary\n\n## 关键观点\n\n- Point\n\n## 原文\n\nFull original",
            isRemoteSnapshot = true,
        ))
        assertEquals("暂无原文内容", articleDetailPageContent(
            page = 1, summary = "Summary", original = null,
            storedOriginal = "## 摘要\n\nSummary\n\n## 关键观点\n\n- Point",
            isRemoteSnapshot = true,
        ))
    }

    @Test
    fun selectsSummaryAndOriginalContentForPagerPages() {
        assertEquals("summary", articleDetailPageContent(0, "summary", "original"))
        assertEquals("original", articleDetailPageContent(1, "summary", "original"))
    }

    @Test
    fun originalPagePrefersStoredOriginalMarkdownOverAiMarkdown() {
        assertEquals(
            "# remote original",
            articleDetailPageContent(
                page = 1,
                summary = "summary",
                original = "AI markdown",
                storedOriginal = "# remote original",
            ),
        )
    }

    @Test
    fun fallsBackWhenPagerContentIsMissing() {
        assertEquals("暂无摘要内容", articleDetailPageContent(0, null, "original"))
        assertEquals("暂无原文内容", articleDetailPageContent(1, "summary", null))
    }

    @Test
    fun trimsOuterWhitespaceBeforeRenderingMarkdown() {
        assertEquals("正文", articleDetailPageContent(0, "\n\n# 标题\n正文\n", null))
        assertEquals("原文", articleDetailPageContent(1, null, "\n\n原文\n"))
    }

    @Test
    fun summaryContentRemovesGeneratedTitleAndGuideHeadingsBeforeRendering() {
        assertEquals(
            "这是一段摘要。\n\n1. 第一个要点\n2. 第二个要点",
            articleDetailPageContent(
                page = 0,
                summary = """
                    # AI 浏览器正在重新定义个人知识入口

                    ## 核心内容
                    这是一段摘要。

                    ## 核心观点
                    1. 第一个要点
                    2. 第二个要点
                """.trimIndent(),
                original = null,
            ),
        )
    }

    @Test
    fun repairsOriginalImagePlaceholdersWithArticleImageUrlsBeforeRendering() {
        assertEquals(
            "# 原文\n\n![图像](https://example.com/image.jpg)",
            articleDetailPageContent(
                page = 1,
                summary = null,
                original = "# 原文\n\n！[图像](https：//example.com/image.jpg？format=jpg)",
                originalImageUrls = listOf("https://example.com/image.jpg"),
            ),
        )
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
