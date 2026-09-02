package com.dailysatori.ui.feature.diary

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.dailysatori.service.diary.DiaryAssistantResult
import com.dailysatori.service.diary.DiaryAssistantMissingConfigurationException
import com.dailysatori.service.diary.DiaryAssistantVerification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertIs
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
        assertEquals("hello\n\nbackground\n\n changed", insertDiaryAssistantResult(current, snapshot, "background").text)
    }

    @Test fun reversedCurrentSelectionUsesItsActiveCursorAfterConflict() {
        val snapshot = DiaryAssistantSelectionSnapshot("hello world", TextRange(6, 11), "world")
        val current = TextFieldValue("hello changed", TextRange(13, 5))
        assertEquals("hello\n\nbackground\n\n changed", insertDiaryAssistantResult(current, snapshot, "background").text)
    }

    @Test fun whitespaceIsNotDuplicatedAroundInsertion() {
        val snapshot = DiaryAssistantSelectionSnapshot("hello  ", TextRange(7), "")
        val current = TextFieldValue("hello  ", TextRange(7))
        assertEquals("hello  \n\n  background ", insertDiaryAssistantResult(current, snapshot, "  background ").text)
    }

    @Test fun insertionPreservesTrailingIndentationExactly() {
        val current = TextFieldValue("paragraph\n    ", TextRange(14))
        val snapshot = DiaryAssistantSelectionSnapshot(current.text, current.selection, "")

        assertEquals("paragraph\n    \n\nbackground", insertDiaryAssistantResult(current, snapshot, "background").text)
    }

    @Test fun insertionAddsOnlyMissingBoundaryNewlines() {
        val empty = TextFieldValue("", TextRange(0))
        val emptySnapshot = DiaryAssistantSelectionSnapshot("", TextRange(0), "")
        assertEquals("result", insertDiaryAssistantResult(empty, emptySnapshot, "result").text)

        val middle = TextFieldValue("before\nafter", TextRange(7))
        val middleSnapshot = DiaryAssistantSelectionSnapshot(middle.text, middle.selection, "")
        assertEquals("before\n\nresult\n\nafter", insertDiaryAssistantResult(middle, middleSnapshot, "result").text)
    }

    @Test fun replacementPreservesEditedDraftWhitespace() {
        val current = TextFieldValue("hello world", TextRange(6, 11))
        val snapshot = DiaryAssistantSelectionSnapshot(current.text, current.selection, "world")

        assertEquals("hello   replacement \n", replaceDiaryAssistantSelection(current, snapshot, "  replacement \n").text)
    }

    @Test fun insertionCountsCrLfAsExistingBoundaryNewline() {
        val current = TextFieldValue("before", TextRange(6))
        val snapshot = DiaryAssistantSelectionSnapshot(current.text, current.selection, "")

        assertEquals("before\n\r\nresult", insertDiaryAssistantResult(current, snapshot, "\r\nresult").text)
    }

    @Test fun collapsedPastedUrlSnapshotCannotReplaceExistingText() {
        val text = "note https://example.com/post"
        val snapshot = DiaryAssistantSelectionSnapshot(text, TextRange(text.length), "")

        assertFalse(canReplaceDiaryAssistantSelection(TextFieldValue(text), snapshot))
        assertEquals(text, replaceDiaryAssistantSelection(TextFieldValue(text), snapshot, "summary").text)
    }

    @Test fun blankSelectionCannotBeReplaced() {
        val snapshot = DiaryAssistantSelectionSnapshot("hello   world", TextRange(5, 8), "   ")

        assertFalse(canReplaceDiaryAssistantSelection(TextFieldValue("hello   world"), snapshot))
    }

    @Test fun cacheNormalizesUrlsAndEvictsOldestAfterTenEntries() {
        val cache = DiaryAssistantSessionCache()
        val result = DiaryAssistantResult("answer", emptyList(), DiaryAssistantVerification.MODEL_ONLY)
        cache.put("https://example.com/a。", result)
        assertEquals(result, cache.get("https://example.com/a"))
        assertNull(cache.get("https://missing.example"))
        repeat(10) { cache.put("https://example.com/$it", result) }
        assertNull(cache.get("https://example.com/a"))
        assertEquals(10, cache.size)
    }

    @Test fun explicitRefreshBypassesSuccessfulSessionCache() {
        val cache = DiaryAssistantSessionCache()
        val result = DiaryAssistantResult("cached", emptyList(), DiaryAssistantVerification.PAGE_EXTRACTED)
        cache["https://example.com/article"] = result

        assertEquals(result, cache.resultFor("https://example.com/article", forceRefresh = false))
        assertNull(cache.resultFor("https://example.com/article", forceRefresh = true))
    }

    @Test fun cacheRejectsWhitespaceUrlsAndKeepsCaseSensitivePathsDistinct() {
        val cache = DiaryAssistantSessionCache()
        val upper = DiaryAssistantResult("upper", emptyList(), DiaryAssistantVerification.PAGE_EXTRACTED)
        val lower = DiaryAssistantResult("lower", emptyList(), DiaryAssistantVerification.PAGE_EXTRACTED)

        cache[" https://example.com/Private"] = upper
        assertNull(cache["https://example.com/Private"])

        cache["https://example.com/Private"] = upper
        cache["https://example.com/private"] = lower
        assertEquals(upper, cache["https://example.com/Private"])
        assertEquals(lower, cache["https://example.com/private"])
    }

    @Test fun snapshotBuildsBoundedRequest() {
        val snapshot = DiaryAssistantSelectionSnapshot("x".repeat(300) + "term" + "y".repeat(300), TextRange(300, 304), "term")
        val request = snapshot.toDiaryAssistantRequest()
        assertEquals("term", request.selectedText)
        assertEquals(240, request.contextBefore.length)
        assertEquals(240, request.contextAfter.length)
    }

    @Test fun missingAiConfigurationMapsToDedicatedRecoveryState() {
        val snapshot = DiaryAssistantSelectionSnapshot("selected", TextRange(0, 8), "selected")

        val state = diaryAssistantFailurePreview(
            snapshot = snapshot,
            url = null,
            allowModelKnowledgeFallback = false,
            error = DiaryAssistantMissingConfigurationException(),
        )

        assertIs<DiaryAssistantPreviewState.MissingConfiguration>(state)
    }
}
