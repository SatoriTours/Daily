package com.dailysatori.ui.feature.article

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArticleProcessingUiTextTest {
    @Test
    fun mapsArticleStatusesToReadableMessages() {
        assertEquals("正在打开网页...", articleProcessingMessage("pending"))
        assertEquals("网页内容已获取，正在整理...", articleProcessingMessage("webContentFetched"))
        assertEquals("正在处理文章...", articleProcessingMessage("aiProcessing"))
        assertEquals("文章已更新", articleProcessingMessage("completed"))
        assertEquals("处理失败，请稍后重试", articleProcessingMessage("error"))
    }

    @Test
    fun mapsAiProgressToReadableMessages() {
        assertEquals("正在优化标题...", articleProcessingMessage("aiProcessing", "Generating title"))
        assertEquals("正在生成摘要...", articleProcessingMessage("aiProcessing", "Generating summary"))
        assertEquals("正在整理原文排版...", articleProcessingMessage("aiProcessing", "Converting to Markdown"))
        assertEquals("正在保存封面图...", articleProcessingMessage("aiProcessing", "Downloading cover image"))
        assertEquals("正在处理文章...", articleProcessingMessage("aiProcessing", "Starting AI tasks"))
    }

    @Test
    fun detectsProcessingStatuses() {
        assertTrue(isArticleProcessing("pending"))
        assertTrue(isArticleProcessing("webContentFetched"))
        assertTrue(isArticleProcessing("aiProcessing"))
        assertFalse(isArticleProcessing("completed"))
        assertFalse(isArticleProcessing("error"))
        assertFalse(isArticleProcessing(null))
    }

    @Test
    fun reloadsArticleWhenProcessingReachesTerminalState() {
        assertTrue(shouldReloadArticleAfterProcessingState("completed"))
        assertTrue(shouldReloadArticleAfterProcessingState("error"))
        assertFalse(shouldReloadArticleAfterProcessingState("aiProcessing"))
        assertFalse(shouldReloadArticleAfterProcessingState(null))
    }

    @Test
    fun ignoresUnknownBlankAndCompletedStatusForPersistentCardMessage() {
        assertNull(articleProcessingCardMessage("completed"))
        assertNull(articleProcessingCardMessage("error"))
        assertNull(articleProcessingCardMessage(""))
        assertNull(articleProcessingCardMessage("archived"))
    }

    @Test
    fun mapsProcessingStateToStepIndex() {
        assertEquals(0, articleProcessingStepIndex("pending"))
        assertEquals(1, articleProcessingStepIndex("webContentFetched"))
        assertEquals(2, articleProcessingStepIndex("aiProcessing", "Generating title"))
        assertEquals(3, articleProcessingStepIndex("aiProcessing", "Generating summary"))
        assertEquals(4, articleProcessingStepIndex("aiProcessing", "Converting to Markdown"))
        assertEquals(5, articleProcessingStepIndex("aiProcessing", "Downloading cover image"))
        assertEquals(6, articleProcessingStepIndex("completed"))
        assertEquals(-1, articleProcessingStepIndex("error"))
    }

    @Test
    fun exposesProcessingStepLabelsInOrder() {
        assertEquals(
            listOf("打开网页", "提取正文", "优化标题", "生成摘要", "整理原文", "保存封面", "完成更新"),
            articleProcessingStepLabels,
        )
    }

    @Test
    fun mapsProcessingStateToDeterminateProgress() {
        assertEquals(1f / 7f, articleProcessingProgress("pending"))
        assertEquals(5f / 7f, articleProcessingProgress("aiProcessing", "Converting to Markdown"))
        assertEquals(1f, articleProcessingProgress("completed"))
        assertEquals(0f, articleProcessingProgress("error"))
    }
}
