package com.dailysatori.ui.feature.reminder

import com.dailysatori.service.reminder.LeapDayPolicy
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderRecurrence
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderEditorFormTest {
    private fun editor() = ReminderEditorState(
        content = "提醒内容",
        startDate = LocalDate(2028, 2, 29),
        endDate = LocalDate(2028, 3, 2),
        firstReminderTime = LocalTime(20, 30),
        activeDayRule = ReminderActiveDayRule.SelectedWeekdays(setOf(DayOfWeek.TUESDAY)),
        recurrence = ReminderRecurrence.Yearly(2, 29, LeapDayPolicy.MARCH_1),
        profile = ReminderProfileSnapshot.standard().copy(soundEnabled = false, vibrationEnabled = false),
    )

    @Test
    fun editingContentPreservesParsedScheduleAndCustomProfile() {
        val original = editor()
        val changed = original.applyFormState(original.toFormState("id").editContent("修改后的内容"))
        assertEquals(original.copy(content = "修改后的内容"), changed)
        assertEquals(original.profile, changed.toFormState("id").confirmationPayload()?.profileSnapshot)
    }

    @Test
    fun incompleteAiDraftIsNotFilledWithManualDefaults() {
        val draft = editor().toFormState("ai-id").copy(startDate = null, firstReminderTime = null)
        assertNull(draft.toEditorStateOrNull())
        assertFalse(draft.canConfirm)
        assertNull(draft.confirmationPayload())
        assertNull(draft.editContent("修正内容").firstReminderTime)
    }

    @Test
    fun invalidAdvancedInputSurvivesRecompositionAndBlocksSavingUntilCorrected() {
        val original = editor()
        val invalid = original.applyFormState(original.toFormState("id").editBackoffInput("invalid"))
        assertEquals("invalid", invalid.toFormState("id").daytimeBackoffInput)
        assertFalse(invalid.canSave)
        val corrected = invalid.applyFormState(invalid.toFormState("id").editBackoffInput("10,20"))
        assertTrue(corrected.canSave)
        assertEquals(listOf(10, 20), corrected.profile.daytimeDismissalBackoffMinutes)
    }

    @Test
    fun editingOtherFieldsDoesNotSilentlyConfirmLeapDayFallback() {
        val original = editor().copy(leapDayFallbackChosen = false)
        val changed = original.applyFormState(original.toFormState("id").editContent("新内容"))
        assertFalse(changed.leapDayFallbackChosen)
        assertFalse(changed.canSave)
    }
}
