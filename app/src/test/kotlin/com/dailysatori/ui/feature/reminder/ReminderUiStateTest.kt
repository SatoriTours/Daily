package com.dailysatori.ui.feature.reminder

import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderDraft
import com.dailysatori.service.reminder.ReminderProfileKind
import com.dailysatori.service.reminder.ReminderImportance
import com.dailysatori.service.reminder.ReminderLockScreenVisibility
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderStatus
import com.dailysatori.data.repository.ReminderProfile
import com.dailysatori.ui.feature.settings.reminder.ReminderSettingsState
import com.dailysatori.ui.feature.settings.reminder.ReminderProfileEditorState
import com.dailysatori.ui.feature.settings.reminder.ReminderDeliveryAccess
import com.dailysatori.ui.feature.settings.reminder.ReminderDeliveryAccessChecker
import com.dailysatori.ui.feature.settings.reminder.ReminderDeliveryAccessController
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReminderUiStateTest {
    @Test
    fun detailEditValidatesContentDatesAndSelectedWeekdays() {
        assertFalse(isValidReminderDetailEdit(" ", LocalDate(2026, 9, 2), LocalDate(2026, 9, 2), ReminderActiveDayRule.Daily))
        assertFalse(isValidReminderDetailEdit("x".repeat(2_001), LocalDate(2026, 9, 2), LocalDate(2026, 9, 2), ReminderActiveDayRule.Daily))
        assertFalse(isValidReminderDetailEdit("pay", LocalDate(2026, 9, 3), LocalDate(2026, 9, 2), ReminderActiveDayRule.Daily))
        assertFalse(isValidReminderDetailEdit("pay", LocalDate(2026, 9, 2), LocalDate(2026, 9, 2), ReminderActiveDayRule.SelectedWeekdays(emptySet())))
        assertTrue(isValidReminderDetailEdit("pay", LocalDate(2026, 9, 2), LocalDate(2026, 9, 2), ReminderActiveDayRule.SelectedWeekdays(setOf(DayOfWeek.MONDAY))))
    }

    @Test
    fun detailSelectedWeekdayTogglePreservesTheRuleAndUpdatesDays() {
        val initial = ReminderActiveDayRule.SelectedWeekdays(setOf(DayOfWeek.MONDAY))

        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), toggleReminderDetailWeekday(initial, DayOfWeek.FRIDAY).days)
        assertEquals(emptySet(), toggleReminderDetailWeekday(initial, DayOfWeek.MONDAY).days)
    }

    @Test
    fun selectedWeekdaysMustContainAtLeastOneDay() {
        val state = validDraftState().editActiveDayRule(ReminderActiveDayRule.SelectedWeekdays(emptySet()))

        assertFalse(state.canConfirm)
        assertTrue(ReminderDraftField.ACTIVE_DAY_RULE in state.validationErrors)
    }

    @Test
    fun requiredFieldsAndInvalidRangesDisableConfirmation() {
        val missing = ReminderDraftUiState.from(ReminderDraft("d", "", null, null, null))
        val reversed = validDraftState().copy(endDate = LocalDate(2026, 9, 1))

        assertFalse(missing.canConfirm)
        assertTrue(missing.validationErrors.containsAll(listOf(ReminderDraftField.CONTENT, ReminderDraftField.START_DATE, ReminderDraftField.END_DATE, ReminderDraftField.FIRST_TIME)))
        assertFalse(reversed.canConfirm)
        assertTrue(ReminderDraftField.END_DATE in reversed.validationErrors)
    }

    @Test
    fun cardUsesAbsoluteDateTimeAndEveryOptionIsEditable() {
        val initial = validDraftState()
        val custom = ReminderProfileSnapshot.strong().copy(
            soundEnabled = false,
            vibrationEnabled = false,
            sleepStart = LocalTime(1, 0),
            sleepEnd = LocalTime(8, 30),
            dailyCutoff = LocalTime(23, 30),
            importance = ReminderImportance.LOW,
            lockScreenVisibility = ReminderLockScreenVisibility.SECRET,
        )
        val edited = initial
            .editContent("交报告")
            .editDates(LocalDate(2026, 9, 3), LocalDate(2026, 9, 5))
            .editFirstTime(LocalTime(8, 15))
            .editActiveDayRule(ReminderActiveDayRule.Weekdays)
            .editProfile(custom)

        assertEquals("2026-09-03 08:15 — 2026-09-05", edited.absoluteDateTimeText)
        assertEquals("交报告", edited.content)
        assertEquals(ReminderActiveDayRule.Weekdays, edited.activeDayRule)
        assertEquals(custom, edited.profile)
        assertNotEquals(initial, edited)
    }

    @Test
    fun confirmationBeginsOnceAndSavingDisablesTheButton() {
        val first = validDraftState().beginConfirmation()
        val second = first.state.beginConfirmation()

        assertTrue(first.accepted)
        assertTrue(first.state.saving)
        assertFalse(first.state.canConfirm)
        assertFalse(second.accepted)
    }

    @Test
    fun cancelNeverRequestsPersistence() {
        val cancelled = validDraftState().cancel()

        assertTrue(cancelled.cancelled)
        assertFalse(cancelled.shouldPersist)
        assertFalse(cancelled.canConfirm)
    }

    @Test
    fun confirmedProfileIsASnapshotAndCardOverridesStayIsolated() {
        val global = ReminderProfileSnapshot.standard()
        val card = validDraftState().editProfile(global.copy(soundEnabled = false))
        val confirmed = card.confirmationPayload()!!
        val laterGlobal = global.copy(sleepEnd = LocalTime(10, 0))

        assertFalse(confirmed.profileSnapshot.soundEnabled)
        assertEquals(LocalTime(9, 0), confirmed.profileSnapshot.sleepEnd)
        assertEquals(LocalTime(10, 0), laterGlobal.sleepEnd)
    }

    @Test
    fun listFiltersAndDetailActionsCoverTheLifecycle() {
        val active = reminder(ReminderStatus.NOTIFIED)
        val paused = reminder(ReminderStatus.PAUSED)
        val completed = reminder(ReminderStatus.COMPLETED)
        val expired = reminder(ReminderStatus.EXPIRED)

        assertEquals(listOf(active), filterReminders(listOf(active, paused, completed, expired), ReminderFilter.ACTIVE))
        assertEquals(setOf(ReminderAction.PAUSE, ReminderAction.EDIT, ReminderAction.COMPLETE, ReminderAction.DELETE, ReminderAction.APPLY_LATEST_PROFILE), reminderActions(active).toSet())
        assertEquals(setOf(ReminderAction.RESUME, ReminderAction.EDIT, ReminderAction.COMPLETE, ReminderAction.DELETE, ReminderAction.APPLY_LATEST_PROFILE), reminderActions(paused).toSet())
        assertEquals(listOf(ReminderAction.DELETE), reminderActions(completed))
    }

    @Test
    fun settingsProvideBuiltInsDefaultsAndValidateCustomProfiles() {
        val defaults = ReminderSettingsState.defaults()
        val invalid = ReminderProfileEditorState(
            name = "",
            kind = ReminderProfileKind.CUSTOM,
            daytimeBackoffMinutes = listOf(0, 1_441),
            eveningStart = LocalTime(22, 0),
            eveningIntervalMinutes = 0,
            dailyCutoff = LocalTime(0, 0),
            soundEnabled = true,
            vibrationEnabled = true,
        )

        assertEquals(setOf(ReminderProfileKind.STRONG, ReminderProfileKind.STANDARD, ReminderProfileKind.GENTLE), defaults.profiles.map { it.snapshot.kind }.toSet())
        assertEquals(LocalTime(0, 0), defaults.sleepStart)
        assertEquals(LocalTime(9, 0), defaults.sleepEnd)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), defaults.workDays)
        assertEquals(LocalTime(9, 0), defaults.workStart)
        assertEquals(LocalTime(18, 0), defaults.workEnd)
        assertFalse(invalid.isValid)
    }

    @Test
    fun customBackoffRejectsMalformedEmptyAndOutOfRangeTokens() {
        val base = ReminderProfileEditorState(
            name = "Focus",
            daytimeBackoffMinutes = listOf(120),
            daytimeBackoffInput = "120",
            eveningStart = LocalTime(22, 0),
            eveningIntervalMinutes = 60,
            dailyCutoff = LocalTime(0, 0),
            soundEnabled = true,
            vibrationEnabled = true,
        )

        assertFalse(base.editBackoffInput("120,abc").isValid)
        assertFalse(base.editBackoffInput("120,,240").isValid)
        assertFalse(base.editBackoffInput("0,240").isValid)
        assertEquals(listOf(120, 240), base.editBackoffInput("120,240").daytimeBackoffMinutes)
        assertTrue(base.editBackoffInput("120,240").isValid)
    }

    @Test
    fun draftAdvancedInputsPreserveMalformedTextAndDisableConfirmation() {
        val malformedBackoff = validDraftState().editBackoffInput("120,abc")
        val malformedEvening = validDraftState().editEveningIntervalInput("oops")
        val outOfRangeEvening = validDraftState().editEveningIntervalInput("1441")

        assertEquals("120,abc", malformedBackoff.daytimeBackoffInput)
        assertFalse(malformedBackoff.canConfirm)
        assertEquals("oops", malformedEvening.eveningIntervalInput)
        assertFalse(malformedEvening.canConfirm)
        assertFalse(outOfRangeEvening.canConfirm)
    }

    @Test
    fun persistedCustomProfileCanBeSelectedAndConfirmationKeepsItsSnapshot() {
        val customSnapshot = ReminderProfileSnapshot.strong().copy(
            kind = ReminderProfileKind.CUSTOM,
            daytimeDismissalBackoffMinutes = listOf(15, 45),
        )
        val custom = ReminderProfile("custom-focus", "Focus", ReminderProfileKind.CUSTOM, customSnapshot)
        val selected = validDraftState().selectProfile(custom)
        val laterEdited = custom.copy(snapshot = custom.snapshot.copy(daytimeDismissalBackoffMinutes = listOf(90)))

        assertEquals("custom-focus", selected.profileId)
        assertEquals(listOf(15, 45), selected.confirmationPayload()!!.profileSnapshot.daytimeDismissalBackoffMinutes)
        assertEquals(listOf(90), laterEdited.snapshot.daytimeDismissalBackoffMinutes)
    }

    @Test
    fun selectingBuiltInProfilePreservesCurrentGlobalScheduleRules() {
        val current = validDraftState().updateProfile(
            ReminderProfileSnapshot.standard().copy(
                sleepStart = LocalTime(22, 0),
                sleepEnd = LocalTime(8, 30),
                workDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                workStart = LocalTime(10, 0),
                workEnd = LocalTime(16, 0),
                dailyCutoff = LocalTime(23, 30),
            ),
        )
        val selected = current.selectProfile(
            ReminderProfile("builtin-strong", "Strong", ReminderProfileKind.STRONG, ReminderProfileSnapshot.strong()),
        )

        assertEquals(listOf(120, 240), selected.profile!!.daytimeDismissalBackoffMinutes)
        assertEquals(LocalTime(22, 0), selected.profile.sleepStart)
        assertEquals(LocalTime(8, 30), selected.profile.sleepEnd)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), selected.profile.workDays)
        assertEquals(LocalTime(10, 0), selected.profile.workStart)
        assertEquals(LocalTime(16, 0), selected.profile.workEnd)
        assertEquals(LocalTime(23, 30), selected.profile.dailyCutoff)
    }

    @Test
    fun deliveryAccessControllerCombinesRuntimeAndSystemNotificationStateAndRefreshes() {
        var runtimePermission = true
        var systemEnabled = false
        val checker = ReminderDeliveryAccessChecker {
            ReminderDeliveryAccess(
                notificationsAllowed = runtimePermission && systemEnabled,
                exactAlarmsAllowed = true,
            )
        }
        val controller = ReminderDeliveryAccessController(checker)

        assertFalse(controller.current.notificationsAllowed)
        systemEnabled = true
        assertTrue(controller.refresh().notificationsAllowed)
        runtimePermission = false
        assertFalse(controller.refresh().notificationsAllowed)
    }

    private fun validDraftState() = ReminderDraftUiState.from(
        ReminderDraft(
            id = "draft-1",
            content = "还信用卡",
            startDate = LocalDate(2026, 9, 2),
            endDate = LocalDate(2026, 9, 4),
            firstReminderTime = LocalTime(18, 0),
            profile = ReminderProfileSnapshot.standard(),
        ),
    )

    private fun reminder(status: ReminderStatus) = Reminder(
        id = status.name,
        content = status.name,
        startDate = LocalDate(2026, 9, 2),
        endDate = LocalDate(2026, 9, 4),
        firstReminderTime = LocalTime(18, 0),
        activeDayRule = ReminderActiveDayRule.Daily,
        profile = ReminderProfileSnapshot.standard(),
        status = status,
        timeZone = TimeZone.UTC,
        version = 0,
    )
}
