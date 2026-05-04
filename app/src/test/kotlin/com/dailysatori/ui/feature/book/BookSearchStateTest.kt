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
}
