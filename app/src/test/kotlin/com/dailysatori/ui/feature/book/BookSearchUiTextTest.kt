package com.dailysatori.ui.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals

class BookSearchUiTextTest {
    @Test
    fun primaryBookActionAddsAndAnalyzes() {
        assertEquals("添加并分析", bookSearchPrimaryActionText(isAnalyzing = false))
        assertEquals("分析中...", bookSearchPrimaryActionText(isAnalyzing = true))
    }
}
