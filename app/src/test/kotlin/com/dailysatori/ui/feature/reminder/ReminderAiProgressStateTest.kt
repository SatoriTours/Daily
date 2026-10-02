package com.dailysatori.ui.feature.reminder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderAiProgressStateTest {
    @Test
    fun runningTaskShowsActualReportedStage() {
        assertEquals(ReminderAiStage.ANALYZING, reminderAiProgress("running", 1, 4).stage)
        assertEquals(ReminderAiStage.VALIDATING, reminderAiProgress("running", 2, 4).stage)
        assertEquals(ReminderAiStage.GENERATING, reminderAiProgress("running", 3, 4).stage)
    }

    @Test
    fun retryResetsStageInsteadOfShowingPreviousAttemptAsRunning() {
        val progress = reminderAiProgress("retrying", 2, 4, retryAtMillis = 20_000)
        assertTrue(progress.isRetrying)
        assertEquals(ReminderAiStage.QUEUED, progress.stage)
        assertEquals(2L, progress.retrySecondsRemaining(18_001))
        assertEquals(0L, progress.retrySecondsRemaining(21_000))
    }

    @Test
    fun queuedOrMissingTaskDoesNotClaimAiRequestHasStarted() {
        assertEquals(ReminderAiStage.QUEUED, reminderAiProgress(null).stage)
        assertEquals(ReminderAiStage.QUEUED, reminderAiProgress("queued", 2, 4).stage)
        assertNull(reminderAiProgress("running", retryAtMillis = 20_000).retrySecondsRemaining(0))
    }

    @Test
    fun taskStartedBeforeAppUpgradeStillShowsValidationStage() {
        assertEquals(ReminderAiStage.VALIDATING, reminderAiProgress("running", 0, 1, "正在校验 AI 返回").stage)
        assertEquals(ReminderAiStage.GENERATING, reminderAiProgress("running", 4, 4).stage)
    }
}
