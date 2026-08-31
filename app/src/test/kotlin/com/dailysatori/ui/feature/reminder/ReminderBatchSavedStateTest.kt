package com.dailysatori.ui.feature.reminder

import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderRecurrence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class ReminderBatchSavedStateTest {
    @Test
    fun `round trip preserves edited batch and degrades interrupted save to retryable`() {
        val draft = ReminderDraftUiState(
            id = "batch-1", content = "还信用卡", startDate = LocalDate(2026, 9, 2),
            endDate = LocalDate(2026, 9, 2), firstReminderTime = LocalTime(10, 30),
            activeDayRule = ReminderActiveDayRule.Daily, recurrence = ReminderRecurrence.Monthly(2),
            profile = ReminderProfileSnapshot.standard(), profileId = "builtin-standard",
        )
        val original = ReminderAiParseState(
            prompt = "明天提醒我还信用卡", submitCount = 1, requestToken = 8, batchGeneration = 4,
            batch = ReminderBatchUiState("batch", linkedMapOf("batch-1" to ReminderBatchUiItem(
                id = "batch-1", sourceText = "明天提醒我还信用卡", draft = draft,
                selected = true, saveStatus = BatchSaveStatus.SAVING,
            ))),
        )

        val restored = assertNotNull(decodeReminderBatchState(encodeReminderBatchState(original)))
        val item = assertNotNull(restored.batch?.items?.get("batch-1"))
        assertEquals(original.prompt, restored.prompt)
        assertEquals(ReminderRecurrence.Monthly(2), item.draft.recurrence)
        assertEquals(BatchSaveStatus.FAILED, item.saveStatus)
        assertEquals(true, item.selected)
        assertEquals(ReminderBatchErrorCode.SAVE_FAILED, item.saveError)
        assertEquals(9, restored.requestToken)
    }

    @Test
    fun `saved item cannot be removed after partial save`() {
        val draft = ReminderDraftUiState(
            id = "saved", content = "已保存", startDate = LocalDate(2026, 9, 2), endDate = LocalDate(2026, 9, 2),
            firstReminderTime = LocalTime(9, 0), activeDayRule = ReminderActiveDayRule.Daily,
            profile = ReminderProfileSnapshot.standard(),
        )
        val batch = ReminderBatchUiState("batch", mapOf("saved" to ReminderBatchUiItem(
            id = "saved", sourceText = "已保存", draft = draft, saveStatus = BatchSaveStatus.SAVED,
        )))
        assertSame(batch, batch.removeItem("saved"))
    }
}
