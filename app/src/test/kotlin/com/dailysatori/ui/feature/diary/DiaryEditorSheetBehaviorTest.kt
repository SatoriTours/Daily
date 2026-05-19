package com.dailysatori.ui.feature.diary

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryEditorSheetBehaviorTest {
    @Test
    fun diaryEditorUsesDialogDismissalInsteadOfModalSheetToAvoidBlockingTabsAfterSideBack() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()

        assertTrue(source.contains("Dialog("))
        assertTrue(source.contains("onDismissRequest = onDismiss"))
        assertTrue(source.contains("DialogProperties(usePlatformDefaultWidth = false)"))
        assertFalse(source.contains("ModalBottomSheet"))
        assertTrue(source.contains("IconButton(onClick = onDismiss"))
    }

    @Test
    fun diaryEditorToolbarActionsDirectlyMutateMarkdownContent() {
        val sheetSource = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()
        val toolbarSource = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorToolbar.kt").readText()

        assertFalse(sheetSource.contains("showTagInput"))
        assertFalse(sheetSource.contains("OutlinedTextField"))
        assertTrue(sheetSource.contains("onTitle = { insertLineStart(\"# \") }"))
        assertTrue(sheetSource.contains("onItalic = { insertFormat(\"*\", \"*\") }"))
        assertTrue(sheetSource.contains("onQuote = { insertLineStart(\"> \") }"))
        assertTrue(sheetSource.contains("onTaskList = { insertLineStart(\"- [ ] \") }"))
        assertTrue(sheetSource.contains("onInlineCode = { insertFormat(\"`\", \"`\") }"))
        assertTrue(sheetSource.contains("onDivider = { insertBlock(\"---\") }"))
        assertTrue(sheetSource.contains("onLink = { insertFormat(\"[\", \"](url)\") }"))
        assertTrue(sheetSource.contains("onImage = { insertFormat(\"![\", \"](url)\") }"))
        assertTrue(toolbarSource.contains("onItalic: () -> Unit"))
        assertTrue(toolbarSource.contains("onTaskList: () -> Unit"))
        assertTrue(toolbarSource.contains("onInlineCode: () -> Unit"))
    }
}
