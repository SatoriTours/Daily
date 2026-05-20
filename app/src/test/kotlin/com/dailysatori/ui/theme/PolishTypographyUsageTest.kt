package com.dailysatori.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolishTypographyUsageTest {
    @Test
    fun polishSensitiveScreensDoNotHardcodeFontSizes() {
        val hardcodedSpUnit = Regex("""\b\d+(?:\.\d+)?\.sp""")
        val files = listOf(
            "src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt",
            "src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt",
            "src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt",
            "src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt",
            "src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt",
            "src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt",
        )

        files.forEach { path ->
            val source = File(path).readText()
            assertFalse(source.contains("fontSize ="), path)
            assertFalse(hardcodedSpUnit.containsMatchIn(source), path)
            assertTrue(source.contains("MaterialTheme.typography") || source.contains("MarkdownStyles"), path)
        }
    }
}
