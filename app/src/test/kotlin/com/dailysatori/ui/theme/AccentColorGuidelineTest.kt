package com.dailysatori.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AccentColorGuidelineTest {
    @Test
    fun styleGuideDocumentsPrimaryBlueForSelectionAndSectionAccents() {
        val styleGuide = File("../docs/04-style-guide.md").readText()

        assertTrue(
            styleGuide.contains("选中态、导航态、AI 助手图标和轻量分区标题统一使用 `MaterialTheme.colorScheme.primary`"),
        )
        assertTrue(styleGuide.contains("禁止页面内自行造橙/紫/蓝硬编码色"))
        assertTrue(
            Regex("""Color\(0xFF[0-9A-F]{6}\)\s*// 应使用 MaterialTheme\.colorScheme\.primary""")
                .containsMatchIn(styleGuide),
        )
    }
}
