package com.dailysatori.ui.feature.unifiednews

import kotlin.test.Test
import kotlin.test.assertEquals

class UnifiedNewsInlineMarkdownTest {
    @Test
    fun basicMarkdownAndPunctuationCleaningKeepExistingRules() {
        val cleaned = "**粗体**、__重点__ 和 `代码` ，继续"
            .withoutUnifiedNewsBasicMarkdown()
            .normalizeUnifiedNewsPunctuationSpacing()

        assertEquals("粗体、重点 和 代码，继续", cleaned)
    }
}
