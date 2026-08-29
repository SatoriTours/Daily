package com.dailysatori.ui.feature.diary

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.dailysatori.service.diary.DiaryAssistantResult
import com.dailysatori.service.diary.DiaryAssistantVerification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryAssistantEditorStateTest {
    @Test fun pasteDetectionDoesNotInvokeAnyLoader() {
        assertEquals("https://example.com/a", detectNewlyPastedDiaryUrl("before", "before https://example.com/a"))
        assertNull(detectNewlyPastedDiaryUrl("same https://example.com/a", "same https://example.com/a"))
    }

    @Test fun duplicatePastedUrlStillTriggersPrompt() {
        assertEquals(
            "https://example.com/a",
            detectNewlyPastedDiaryUrl("note https://example.com/a", "note https://example.com/a https://example.com/a"),
        )
    }

    @Test fun changedOriginalSelectionCannotBeReplaced() {
        val snapshot = DiaryAssistantSelectionSnapshot("hello world", TextRange(6, 11), "world")
        assertFalse(canReplaceDiaryAssistantSelection(TextFieldValue("hello earth"), snapshot))
    }

    @Test fun insertionAndReplacementAreSingleUndoableValues() {
        val snapshot = DiaryAssistantSelectionSnapshot("hello world", TextRange(6, 11), "world")
        assertEquals("hello world\n\nbackground", insertDiaryAssistantResult(TextFieldValue("hello world"), snapshot, "background").text)
        assertEquals("hello background", replaceDiaryAssistantSelection(TextFieldValue("hello world"), snapshot, "background").text)
    }

    @Test fun reversedSelectionUsesItsBounds() {
        val snapshot = DiaryAssistantSelectionSnapshot("hello world", TextRange(11, 6), "world")
        assertTrue(canReplaceDiaryAssistantSelection(TextFieldValue("hello world"), snapshot))
        assertEquals("hello earth", replaceDiaryAssistantSelection(TextFieldValue("hello world"), snapshot, "earth").text)
    }

    @Test fun outOfBoundsSelectionCannotBeReplaced() {
        val snapshot = DiaryAssistantSelectionSnapshot("hello", TextRange(0, 99), "hello")
        assertFalse(canReplaceDiaryAssistantSelection(TextFieldValue("hello"), snapshot))
        assertEquals("hello", replaceDiaryAssistantSelection(TextFieldValue("hello"), snapshot, "new").text)
    }

    @Test fun invalidSelectionFallsBackToCurrentCursorForInsertion() {
        val snapshot = DiaryAssistantSelectionSnapshot("hello world", TextRange(6, 11), "world")
        val current = TextFieldValue("hello changed", TextRange(5))
        assertEquals("hello\n\nbackground changed", insertDiaryAssistantResult(current, snapshot, "background").text)
    }

    @Test fun reversedCurrentSelectionUsesItsActiveCursorAfterConflict() {
        val snapshot = DiaryAssistantSelectionSnapshot("hello world", TextRange(6, 11), "world")
        val current = TextFieldValue("hello changed", TextRange(13, 5))
        assertEquals("hello\n\nbackground changed", insertDiaryAssistantResult(current, snapshot, "background").text)
    }

    @Test fun whitespaceIsNotDuplicatedAroundInsertion() {
        val snapshot = DiaryAssistantSelectionSnapshot("hello  ", TextRange(7), "")
        assertEquals("hello\n\nbackground", insertDiaryAssistantResult(TextFieldValue("hello  "), snapshot, "  background ").text)
    }

    @Test fun cacheNormalizesUrlsAndEvictsOldestAfterTenEntries() {
        val cache = DiaryAssistantSessionCache()
        val result = DiaryAssistantResult("answer", emptyList(), DiaryAssistantVerification.MODEL_ONLY)
        cache.put("https://example.com/a。", result)
        assertEquals(result, cache.get(" https://example.com/a"))
        assertNull(cache.get("https://missing.example"))
        repeat(10) { cache.put("https://example.com/$it", result) }
        assertNull(cache.get("https://example.com/a"))
        assertEquals(10, cache.size)
    }

    @Test fun snapshotBuildsBoundedRequest() {
        val snapshot = DiaryAssistantSelectionSnapshot("x".repeat(300) + "term" + "y".repeat(300), TextRange(300, 304), "term")
        val request = snapshot.toDiaryAssistantRequest()
        assertEquals("term", request.selectedText)
        assertEquals(240, request.contextBefore.length)
        assertEquals(240, request.contextAfter.length)
    }
}
