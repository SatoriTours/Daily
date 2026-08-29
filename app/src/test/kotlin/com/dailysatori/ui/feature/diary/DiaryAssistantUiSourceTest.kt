package com.dailysatori.ui.feature.diary

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryAssistantUiSourceTest {
    @Test
    fun editorOffersExplicitAssistantActions() {
        val editor = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()
        val preview = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryAssistantPreviewSheet.kt").readText()

        assertTrue(editor.contains("提取核心内容"))
        assertTrue(editor.contains("assistantService.run"))
        assertTrue(preview.contains("插入到原文后"))
        assertTrue(preview.contains("替换原文"))
        assertTrue(preview.contains("未联网查证"))
    }

    @Test
    fun pastePathCannotCallAssistantUntilConfirmation() {
        val editor = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()

        assertTrue(editor.contains("pendingPastedUrl"))
        assertTrue(editor.contains("onExtractConfirmed"))
        val valueChange = editor
            .substringAfter("BasicTextField(")
            .substringAfter("onValueChange = {")
            .substringBefore("},")
        assertFalse(valueChange.contains("assistantService.run"))
    }

    @Test
    fun disposalInvalidatesRequestBeforeCancellationAndLatePublicationIsGated() {
        val editor = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()
        val disposal = editor.substringAfter("onDispose {").substringBefore("}")

        assertTrue(disposal.contains("assistantRequestGate.invalidate()"))
        assertTrue(disposal.indexOf("assistantRequestGate.invalidate()") < disposal.indexOf("assistantJob?.cancel()"))
        assertTrue(editor.contains("assistantRequestGate.isCurrent(requestGeneration)"))
    }

    @Test
    fun readyPreviewIsBoundedInsideEditorViewportWithScrollableBodyAndPinnedActions() {
        val editor = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt").readText()
        val preview = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryAssistantPreviewSheet.kt").readText()
        val viewport = editor.substringAfter("DiaryAssistantEditorViewport {")
            .substringBefore("Spacer(modifier = Modifier.height(Spacing.xs))")
        val ready = preview.substringAfter("private fun DiaryAssistantReadyContent(")
            .substringBefore("private fun DiaryAssistantPreviewHeader(")

        assertTrue(editor.contains("private fun ColumnScope.DiaryAssistantEditorViewport("))
        assertTrue(viewport.contains("DiaryAssistantPreviewSheet("))
        assertTrue(preview.contains("fillMaxHeight(DiaryAssistantPreviewMaxHeightFraction)"))
        assertTrue(ready.contains("Modifier.weight(1f).verticalScroll(rememberScrollState())"))
        assertTrue(ready.indexOf("verticalScroll") < ready.indexOf("DiaryAssistantEditActions("))
    }
}
