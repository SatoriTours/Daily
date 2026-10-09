package com.dailysatori.ui.feature.diary

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiaryThreadHeaderLayoutTest {
    private val header = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryThreadSheet.kt")
        .readText()
        .substringAfter("private fun DiaryThreadHeader(")
        .substringBefore("private fun DiaryThreadSummarySection(")

    @Test
    fun threadHeaderKeepsCloseWithTitleAndWrapsActionsSeparately() {
        assertTrue(header.contains("Column("), "Title and actions must not compete in the same row")
        val actionsIndex = header.indexOf("FlowRow(")
        assertTrue(actionsIndex > header.indexOf("diary_thread_title"), "Actions need a separate wrapping row")
        assertTrue(header.indexOf("IconButton(onClick = onDismiss)") in 0 until actionsIndex,
            "Close must stay visible beside the title, not after the action buttons")
        assertTrue(header.indexOf("OutlinedButton(onClick = onEditOriginal)") > actionsIndex)
        assertTrue(header.indexOf("OutlinedButton(onClick = onVoiceContinue)") > actionsIndex)
        assertTrue(header.indexOf("Button(onClick = onContinue)") > actionsIndex)
    }

    @Test
    fun threadActionLabelsNeverWrapIntoVerticalText() {
        val actions = header.substringAfter("FlowRow(")
        assertEquals(3, Regex("maxLines = 1").findAll(actions).count())
        assertEquals(3, Regex("softWrap = false").findAll(actions).count())
    }
}
