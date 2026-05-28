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
        assertTrue(source.contains("TextButton(onClick = onDismiss)"))
    }

    @Test
    fun diaryEditorToolbarActionsDirectlyMutateMarkdownContent() {
        val sheetSource = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()
        val toolbarSource = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorToolbar.kt").readText()

        assertFalse(sheetSource.contains("showTagInput"))
        assertTrue(sheetSource.contains("DiaryTextEditDialog("))
        assertTrue(sheetSource.contains("onTitle = { insertLineStart(\"# \") }"))
        assertTrue(sheetSource.contains("insertFormat(\"*\", \"*\")"))
        assertTrue(sheetSource.contains("insertLineStart(\"> \")"))
        assertTrue(sheetSource.contains("insertLineStart(\"- [ ] \")"))
        assertTrue(sheetSource.contains("insertBlock(\"---\")"))
        assertTrue(sheetSource.contains("insertFormat(\"[\", \"](url)\")"))
        assertTrue(sheetSource.contains("private fun DiaryMoreFormatMenu("))
        assertTrue(toolbarSource.contains("onMore: () -> Unit"))
        assertFalse(toolbarSource.contains("onInlineCode: () -> Unit"))
        assertFalse(toolbarSource.contains("onImage: () -> Unit"))
    }
}
