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
        assertTrue(source.contains("DialogProperties("))
        assertTrue(source.contains("usePlatformDefaultWidth = false"))
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

    @Test
    fun diaryEditorAvoidsKeyboardAndKeepsLongContentScrollable() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()

        assertTrue(source.contains("imePadding()"))
        assertTrue(source.contains("imeNestedScroll()"))
        assertTrue(source.contains("rememberScrollState()"))
        assertTrue(source.contains(".verticalScroll(editorScrollState)"))
    }

    @Test
    fun diaryEditorKeepsNewEndInputVisibleInLongContent() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()

        assertTrue(source.contains("LaunchedEffect(content.text, content.selection)"))
        assertTrue(source.contains("content.selection.end == content.text.length"))
        assertTrue(source.contains("withFrameNanos { }"))
        assertTrue(source.contains("editorScrollState.scrollTo(editorScrollState.maxValue)"))
    }

    @Test
    fun diaryEditorPresentsMetadataAndTagsBeforeWritingArea() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()

        assertTrue(source.contains("DiaryEditorMetaRow("))
        assertTrue(source.contains("DiaryEditorTagRow("))
        assertTrue(source.indexOf("DiaryEditorMetaRow(") < source.indexOf("DiaryEditorTagRow("))
        assertTrue(source.indexOf("DiaryEditorTagRow(") < source.indexOf("BasicTextField("))
        assertTrue(source.contains("private fun diaryEditorDateText("))
    }

    @Test
    fun diaryEditorMetadataAndTagsMatchRedesignHierarchy() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()
        val toolbarSource = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorToolbar.kt").readText()

        assertTrue(source.contains(".height(30.dp)"))
        assertTrue(source.contains("color = colors.chip"))
        assertTrue(source.contains("Calendar"))
        assertTrue(source.contains("contentAlignment = Alignment.Center"))
        assertFalse(source.contains("新建日记"))
        assertFalse(source.contains("HorizontalDivider("))
        assertTrue(source.contains("DiaryEditorAddTagChip"))
        assertTrue(toolbarSource.contains("selectedBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)"))
        assertTrue(toolbarSource.contains("verticalAlignment = Alignment.CenterVertically"))
    }

    @Test
    fun diaryEditorShowsAddTagEntryFromToolbarWithoutOpeningDialogImmediately() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()

        assertTrue(source.contains("var showTagEntry by remember"))
        assertTrue(source.contains("onTags = { showTagEntry = true }"))
        assertFalse(source.contains("onTags = { showTagEditor = true }"))
        assertTrue(source.contains("if (tags.isNotEmpty() || showAddEntry)"))
        assertTrue(source.contains("DiaryEditorAddTagChip"))
    }

    @Test
    fun diaryEditorUsesThemeTokensAndImeAwareDialogWindow() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()
        val toolbarSource = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorToolbar.kt").readText()

        assertTrue(source.contains("decorFitsSystemWindows = false"))
        assertTrue(source.contains("DiaryEditorColors"))
        assertTrue(source.contains("MaterialTheme.colorScheme.surface"))
        assertTrue(source.contains("MaterialTheme.colorScheme.primary"))
        assertFalse(source.contains("Color(0xFFFFFDF8)"))
        assertFalse(source.contains("Color(0xFF2F7D5A)"))
        assertTrue(toolbarSource.contains("MaterialTheme.colorScheme.surfaceContainer"))
        assertTrue(toolbarSource.contains("MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)"))
        assertTrue(source.contains("Modifier.navigationBarsPadding().imePadding()"))
    }
}
