package com.dailysatori.ui.feature.diary

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.dailysatori.service.diary.DiaryPolishedTranscript
import com.dailysatori.service.diary.adoptDiaryPolishVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryTranscriptPolishEditorTest {
    @Test fun onlyTheMatchingRecordingBlockIsReplaced() {
        val current = TextFieldValue("手写内容\n\n原始转写\n\n另一段录音")
        val snapshot = diaryTranscriptPolishSnapshot(current, "原始转写", null)
        assertEquals("原始转写", snapshot.selectedText)
        assertEquals("手写内容\n\n整理后\n\n另一段录音", replaceDiaryAssistantSelection(current, snapshot, "整理后").text)
    }

    @Test fun savedPolishedBlockCanBeReplacedAgainOrRestored() {
        val current = TextFieldValue("手写\n\n整理版")
        val snapshot = diaryTranscriptPolishSnapshot(current, "原文", DiaryPolishedTranscript("原文", "整理版"))
        assertEquals("手写\n\n新版", replaceDiaryAssistantSelection(current, snapshot, "新版").text)
        assertEquals("手写\n\n原文", replaceDiaryAssistantSelection(current, snapshot, "原文").text)
    }

    @Test fun ambiguousAndPartialMatchesDoNotAutomaticallyReplaceHandwriting() {
        for (text in listOf("原文\n\n原文", "手写中包含原文但不是录音块")) {
            val current = TextFieldValue(text)
            val snapshot = diaryTranscriptPolishSnapshot(current, "原文", null)
            assertFalse(canReplaceDiaryAssistantSelection(current, snapshot))
            assertEquals(text, replaceDiaryAssistantSelection(current, snapshot, "整理版").text)
        }
    }

    @Test fun explicitSelectionAllowsReplacingAManuallyEditedBlock() {
        val current = TextFieldValue("手写\n编辑过的录音", TextRange(9, 3))
        val snapshot = diaryTranscriptPolishSnapshot(current, "原文", null)
        assertEquals("编辑过的录音", snapshot.selectedText)
        assertEquals("手写\n整理版", replaceDiaryAssistantSelection(current, snapshot, "整理版").text)
    }

    @Test fun textUndoAndRedoKeepVersionFeedbackAndHistory() {
        val first = adoptDiaryPolishVersion("原文", "第一版")
        val second = adoptDiaryPolishVersion("原文", "第二版", first).withFeedback(1, "别删细节").withFeedback(2, "别改语气")
        val undone = restoreDiaryPolishHistory(mapOf(10L to second), mapOf(10L to first)).getValue(10)
        assertEquals("第一版", undone.content)
        assertEquals(listOf("别删细节", "别改语气"), undone.adoptedVersions().map { it.feedback })
        val original = restoreDiaryPolishHistory(mapOf(10L to second), emptyMap()).getValue(10)
        assertEquals("原文", original.content)
        assertEquals(2, original.adoptedVersions().size)
        assertEquals("第二版", restoreDiaryPolishHistory(mapOf(10L to original), mapOf(10L to second)).getValue(10).content)
    }

    @Test fun changedEditorTextAndChangedSourceInvalidateReplacement() {
        val current = TextFieldValue("整理版")
        val stale = diaryTranscriptPolishSnapshot(current, "新原文", DiaryPolishedTranscript("旧原文", "整理版"))
        assertFalse(canReplaceDiaryAssistantSelection(current, stale))
        val snapshot = diaryTranscriptPolishSnapshot(TextFieldValue("原文"), "原文", null)
        assertFalse(canReplaceDiaryAssistantSelection(TextFieldValue("原文加了手写"), snapshot))
        assertTrue(canReplaceDiaryAssistantSelection(TextFieldValue("原文"), snapshot))
    }
}
