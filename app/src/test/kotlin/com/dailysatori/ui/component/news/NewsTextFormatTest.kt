package com.dailysatori.ui.component.news

import kotlin.test.Test
import kotlin.test.assertEquals

class NewsTextFormatTest {
    @Test
    fun introTextKeepsExistingWhitespaceAndLengthRules() {
        assertEquals("第一段 第二段 第三段", "  第一段\n第二段\t第三段  ".cleanNewsIntroText())
        assertEquals(160, "内容".repeat(100).cleanNewsIntroText().length)
    }
}
