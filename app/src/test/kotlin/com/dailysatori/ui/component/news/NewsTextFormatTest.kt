package com.dailysatori.ui.component.news

import kotlin.test.Test
import kotlin.test.assertEquals

class NewsTextFormatTest {
    @Test
    fun introTextKeepsExistingWhitespaceAndLengthRules() {
        assertEquals("第一段 第二段 第三段", "  第一段\n第二段\t第三段  ".cleanNewsIntroText())
        assertEquals(160, "内容".repeat(100).cleanNewsIntroText().length)
        assertEquals(
            "事实内容。 分析内容。",
            "**核心内容：** 事实内容。 **为什么重要（分析）：** 分析内容。".cleanNewsIntroText(),
        )
    }
}
