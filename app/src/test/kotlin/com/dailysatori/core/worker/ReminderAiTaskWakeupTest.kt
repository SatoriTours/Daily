package com.dailysatori.core.worker

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderAiTaskWakeupTest {
    @Test
    fun retriesTransientWakeupWithoutBlockingSubmissionOwner() = runTest {
        var attempts = 0
        val pauses = mutableListOf<Long>()

        val success = wakeReminderAiTask(42, enqueue = {
            attempts++
            if (attempts < 3) error("WorkManager unavailable")
        }, pause = { pauses += it })

        assertTrue(success)
        assertEquals(3, attempts)
        assertEquals(listOf(1_000L, 5_000L), pauses)
    }
}
