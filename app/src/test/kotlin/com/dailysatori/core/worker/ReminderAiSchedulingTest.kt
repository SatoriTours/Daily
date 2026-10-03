package com.dailysatori.core.worker

import androidx.work.OutOfQuotaPolicy
import com.dailysatori.core.task.ReminderAiParseTaskHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderAiSchedulingTest {
    @Test
    fun userSubmittedReminderStartsAsExpeditedWorkWithQuotaFallback() {
        val request = buildAsyncTaskWorkRequest(42, ReminderAiParseTaskHandler.TYPE)

        assertTrue(request.workSpec.expedited)
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, request.workSpec.outOfQuotaPolicy)
    }

    @Test
    fun delayedReminderRetryKeepsItsBackoffInsteadOfExpediting() {
        val request = buildAsyncTaskWorkRequest(42, ReminderAiParseTaskHandler.TYPE, initialDelayMs = 30_000)

        assertFalse(request.workSpec.expedited)
        assertEquals(30_000L, request.workSpec.initialDelay)
    }

    @Test
    fun backgroundSyncDoesNotCompeteForExpeditedQuota() {
        val request = buildAsyncTaskWorkRequest(43, "remote_news_fetch")

        assertFalse(request.workSpec.expedited)
    }
}
