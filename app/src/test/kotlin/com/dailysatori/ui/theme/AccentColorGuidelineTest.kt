package com.dailysatori.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AccentColorGuidelineTest {
    @Test
    fun styleGuideDocumentsPrimaryBlueForSelectionAndSectionAccents() {
        val styleGuide = File("../docs/04-style-guide.md").readText()

        assertTrue(styleGuide.contains("选择态、导航态和轻量分区标题统一使用 `MaterialTheme.colorScheme.primary`"))
        assertTrue(styleGuide.contains("禁止为同类强调状态引入新的非主题色"))
    }
}
