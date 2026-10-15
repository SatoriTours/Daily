package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.service.hotnews.HotNewsItem
import com.dailysatori.service.hotnews.HotNewsPlatform
import com.dailysatori.shared.db.Unified_news_source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UnifiedNewsArticlePreviewTest {
    private val source = Unified_news_source(1, 1, "H1", "hot_news", null, "v2ex:8",
        "https://v2ex.com/t/8", "工具更新", "", null)

    @Test fun processedArticleFactsTakePriorityOverLongPlatformDescriptions() {
        val summary = "**核心内容：** 工具新增本地保存和导出。\n\n**为什么重要（分析）：** 便于资料整理。\n\n来源：[原文](<https://v2ex.com/t/8>)"
        assertEquals("工具新增本地保存和导出。", unifiedNewsArticlePreview(
            source.copy(summary = "平台原有长简介。".repeat(50)), "", emptyList(), summary))
    }

    @Test fun emptySummaryUsesCachedArticleBody() {
        val body = "这个阅读工具新增本地保存和文档导出功能，可以按主题整理资料。"
        val item = HotNewsItem("8", HotNewsPlatform.V2EX, 2, source.title, source.source_url!!,
            originalContent = body)
        assertEquals(body, unifiedNewsArticlePreview(source, "", listOf(item)))
    }

    @Test fun shortHeadingOnlySummaryYieldsToReadableBody() {
        val body = "新增功能包括本地保存、按主题整理资料和文档导出，数据只保存在用户设备上。".repeat(3)
        val item = HotNewsItem("8", HotNewsPlatform.V2EX, 2, source.title, source.source_url!!,
            originalContent = body)
        assertEquals(body, unifiedNewsArticlePreview(source.copy(summary = "## 背景\n\n…"), "", listOf(item)))
    }

    @Test fun emptyCachedContentUsesOnlyTheExactCitation() {
        val briefing = "- 无关内容 [H10]\n- 工具支持本地保存资料和导出文档。 [H1]\n- 另一条新闻 [R1]"
        assertEquals("工具支持本地保存资料和导出文档。", unifiedNewsArticlePreview(source, briefing, emptyList()))
    }

    @Test fun previewRemovesLayoutMarkdownAndBlankParagraphs() {
        val text = "## 背景\n\n**新增** [导出](https://example.com)\n\n…\n- 支持 `Markdown`\n![图片](https://example.com/a.jpg)"
        assertEquals("新增 导出 支持 Markdown", unifiedNewsArticlePreview(source.copy(summary = text), "", emptyList()))
    }

    @Test fun titleOnlySourceDoesNotInventAnExcerpt() {
        assertEquals("", unifiedNewsArticlePreview(source.copy(summary = source.title), "", emptyList()))
    }

    @Test fun usableSummaryIsKeptAndPreviewIsBounded() {
        val text = "这是已有文章摘要，说明具体功能和适用场景。".repeat(40)
        val preview = unifiedNewsArticlePreview(source.copy(summary = text), "- 简报内容 [H1]", emptyList())
        assertEquals(text.take(600), preview)
        assertFalse(preview.contains("简报内容"))
    }
}
