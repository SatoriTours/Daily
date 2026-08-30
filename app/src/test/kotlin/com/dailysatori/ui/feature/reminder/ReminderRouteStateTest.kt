package com.dailysatori.ui.feature.reminder

import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderRecurrence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderRouteStateTest {
    @Test
    fun yearlyEditorSummaryExplainsActualBehavior() {
        val editor = ReminderEditorState(
            content = "生日",
            startDate = LocalDate(2026, 9, 2),
            endDate = LocalDate(2026, 9, 4),
            firstReminderTime = LocalTime(20, 0),
            recurrence = ReminderRecurrence.Yearly(9, 2, com.dailysatori.service.reminder.LeapDayPolicy.FEBRUARY_28),
            profile = ReminderProfileSnapshot.standard(),
        )

        assertEquals("每年9月2日至4日，20:00开始提醒；工作时间仅显示通知，不播放声音。", editor.actualBehaviorSummary())
    }

    @Test
    fun leapDayYearlyReminderRequiresAnExplicitFallback() {
        val editor = ReminderEditorState(
            content = "生日",
            startDate = LocalDate(2028, 2, 29),
            endDate = LocalDate(2028, 2, 29),
            firstReminderTime = LocalTime(9, 0),
            recurrence = ReminderRecurrence.Yearly(2, 29, com.dailysatori.service.reminder.LeapDayPolicy.FEBRUARY_28),
            profile = ReminderProfileSnapshot.standard(),
            leapDayFallbackChosen = false,
        )

        assertTrue(editor.validationMessage != null)
    }
}
