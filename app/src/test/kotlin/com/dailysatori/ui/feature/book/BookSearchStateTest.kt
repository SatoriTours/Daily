package com.dailysatori.ui.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals

class BookSearchStateTest {
    @Test
    fun formatsPartialViewpointMessage() {
        assertEquals("已生成 6 个观点，可稍后重试补全", bookAnalysisPartialMessage(6))
    }

    @Test
    fun formatsAnalysisFailureMessage() {
        assertEquals("分析失败，可重新生成观点", bookAnalysisFailureMessage())
    }

    @Test
    fun formatsAnalysisSuccessMessage() {
        assertEquals("已生成 10 个观点", bookAnalysisSuccessMessage())
    }

    @Test
    fun formatsVisibleAddAndCompletionStatus() {
        assertEquals("正在添加《供应链架构师》", bookAnalysisStartStep("供应链架构师"))
        assertEquals("正在生成观点卡片", bookAnalysisGeneratingStep())
        assertEquals("《供应链架构师》已添加，10 个观点已生成", bookAnalysisCompletionNotice("供应链架构师", 10))
        assertEquals(true, bookAnalysisShowsProgressIndicator(isAnalyzing = true))
    }

    @Test
    fun analysisStatusStaysVisibleWithoutSearchResults() {
        assertEquals(true, bookAnalysisStatusVisible(isAnalyzing = true, analysisMessage = null))
        assertEquals(true, bookAnalysisStatusVisible(isAnalyzing = false, analysisMessage = "《供应链架构师》已添加，10 个观点已生成"))
        assertEquals(false, bookAnalysisStatusVisible(isAnalyzing = false, analysisMessage = null))
    }
}
