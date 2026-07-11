package com.dailysatori.core.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class DiaryRecordingRecoveryCoordinatorTest {
    @Test
    fun persistenceRetriesUntilAWriteSucceeds() = runTest {
        var attempts = 0
        val delays = mutableListOf<Long>()

        val result = retryDiaryRecordingPersistence(
            retryDelaysMs = listOf(1_000, 2_000, 4_000),
            delayBeforeRetry = delays::add,
        ) {
            attempts++
            if (attempts < 3) error("database locked")
        }

        assertEquals(3, attempts)
        assertEquals(listOf(1_000L, 2_000L), delays)
        assertEquals(3, assertIs<DiaryRecordingPersistenceResult.Succeeded>(result).attempts)
    }

    @Test
    fun persistenceReturnsStableFailureAfterAllRetriesAreExhausted() = runTest {
        var attempts = 0
        val delays = mutableListOf<Long>()

        val result = retryDiaryRecordingPersistence(
            retryDelaysMs = listOf(1_000, 2_000, 4_000),
            delayBeforeRetry = delays::add,
        ) {
            attempts++
            error("database locked")
        }

        assertEquals(4, attempts)
        assertEquals(listOf(1_000L, 2_000L, 4_000L), delays)
        val failed = assertIs<DiaryRecordingPersistenceResult.Failed>(result)
        assertEquals(4, failed.attempts)
        assertEquals("database locked", failed.cause.message)
    }

    @Test
    fun api26BackgroundStartIllegalStateMapsToStableLaunchFailure() {
        assertEquals(
            DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED,
            foregroundLaunchFailureCode(26, IllegalStateException("background start denied")),
        )
        assertEquals(
            DiaryRecordingErrorCode.FOREGROUND_START_NOT_ALLOWED,
            foregroundLaunchFailureCode(30, IllegalStateException("background start denied")),
        )
        assertNull(foregroundLaunchFailureCode(25, IllegalStateException("unrelated")))
        assertNull(foregroundLaunchFailureCode(26, IllegalArgumentException("unrelated")))
    }
}
