package com.dailysatori.core.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DiaryRecordingStoreTest {
    @Test
    fun followsTheCompleteRecordingLifecycle() {
        val clock = TestClock()
        val store = DiaryRecordingStore(clock::now)

        assertEquals(DiaryRecordingState.Idle, store.state.value)
        assertTrue(store.start(diaryId = 41, attachmentId = 73))
        assertIs<DiaryRecordingState.Starting>(store.state.value)
        assertTrue(store.markRecording())
        assertIs<DiaryRecordingState.Recording>(store.state.value)

        clock.advanceBy(2_500)
        store.refreshElapsed()
        assertEquals(2_500, store.state.value.elapsedMs)

        assertTrue(store.pause())
        assertIs<DiaryRecordingState.Paused>(store.state.value)
        assertTrue(store.resume())
        assertIs<DiaryRecordingState.Recording>(store.state.value)
        assertTrue(store.stop())
        assertIs<DiaryRecordingState.Stopping>(store.state.value)
        assertTrue(store.complete())
        assertEquals(DiaryRecordingState.Idle, store.state.value)
    }

    @Test
    fun rejectsInvalidTransitionsWithoutChangingState() {
        val store = DiaryRecordingStore { 1_000 }

        assertFalse(store.pause())
        assertFalse(store.resume())
        assertFalse(store.stop())
        assertFalse(store.complete())
        assertEquals(DiaryRecordingState.Idle, store.state.value)

        assertTrue(store.start(diaryId = 41, attachmentId = 73))
        val starting = store.state.value
        assertFalse(store.start(diaryId = 42, attachmentId = 74))
        assertFalse(store.pause())
        assertFalse(store.resume())
        assertEquals(starting, store.state.value)
    }

    @Test
    fun elapsedTimeDoesNotAdvanceWhilePaused() {
        val clock = TestClock()
        val store = DiaryRecordingStore(clock::now)
        store.start(diaryId = 41, attachmentId = 73)
        store.markRecording()
        clock.advanceBy(1_200)
        store.refreshElapsed()
        store.pause()

        val pausedElapsed = store.state.value.elapsedMs
        clock.advanceBy(8_000)
        store.refreshElapsed()

        assertIs<DiaryRecordingState.Paused>(store.state.value)
        assertEquals(1_200, pausedElapsed)
        assertEquals(pausedElapsed, store.state.value.elapsedMs)

        store.resume()
        clock.advanceBy(300)
        store.refreshElapsed()
        assertEquals(1_500, store.state.value.elapsedMs)
    }

    @Test
    fun failureUsesAStableCodeAndKeepsTheSessionIdentity() {
        val store = DiaryRecordingStore { 1_000 }
        store.start(diaryId = 41, attachmentId = 73)

        assertTrue(store.fail(DiaryRecordingErrorCode.PERMISSION_DENIED))
        val failed = assertIs<DiaryRecordingState.Failed>(store.state.value)
        assertEquals(41, failed.diaryId)
        assertEquals(73, failed.attachmentId)
        assertEquals(DiaryRecordingErrorCode.PERMISSION_DENIED, failed.errorCode)
    }

    @Test
    fun duplicateStartIsIdempotentForTheSameSessionAndBusyForAnotherSession() {
        val store = DiaryRecordingStore { 1_000 }

        assertEquals(
            DiaryRecordingStartResult.Accepted,
            store.requestStart(diaryId = 41, attachmentId = 73),
        )
        val active = store.state.value

        assertEquals(
            DiaryRecordingStartResult.AlreadyActive,
            store.requestStart(diaryId = 41, attachmentId = 73),
        )
        assertEquals(active, store.state.value)

        val busy = store.requestStart(diaryId = 42, attachmentId = 74)
        assertEquals(DiaryRecordingStartResult.Busy, busy)
        assertEquals(DiaryRecordingErrorCode.RECORDER_BUSY, busy.errorCode)
        assertEquals(active, store.state.value)
    }

    @Test
    fun stopCancelsStartingAndPreventsLateRecordingTransition() {
        val store = DiaryRecordingStore { 1_000 }
        store.requestStart(diaryId = 41, attachmentId = 73)

        assertTrue(store.stop())
        assertIs<DiaryRecordingState.Stopping>(store.state.value)
        assertFalse(store.markRecording())
        assertIs<DiaryRecordingState.Stopping>(store.state.value)
    }

    @Test
    fun failedSessionStaysBusyUntilItsPersistenceIsReleased() {
        val store = DiaryRecordingStore { 1_000 }
        store.requestStart(diaryId = 41, attachmentId = 73)
        store.fail(DiaryRecordingErrorCode.PERSIST_FAILED, "/voice.m4a")

        assertEquals(
            DiaryRecordingStartResult.AlreadyActive,
            store.requestStart(diaryId = 41, attachmentId = 73),
        )
        assertEquals(
            DiaryRecordingStartResult.Busy,
            store.requestStart(diaryId = 42, attachmentId = 74),
        )
        assertTrue(store.releaseFailedSession())
        assertEquals(
            DiaryRecordingStartResult.Accepted,
            store.requestStart(diaryId = 42, attachmentId = 74),
        )
    }

    @Test
    fun successfulCompletionRetryClearsPersistenceFailure() {
        val store = DiaryRecordingStore { 1_000 }
        store.requestStart(diaryId = 41, attachmentId = 73)
        store.markRecording()
        store.stop()
        store.fail(DiaryRecordingErrorCode.PERSIST_FAILED, "/voice.m4a")

        assertTrue(store.complete())
        assertEquals(DiaryRecordingState.Idle, store.state.value)
    }

    private class TestClock {
        private var value = 0L

        fun now(): Long = value

        fun advanceBy(milliseconds: Long) {
            value += milliseconds
        }
    }
}
