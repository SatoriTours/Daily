package com.dailysatori.ui.feature.reminder

import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.LeapDayPolicy
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderRecurrence
import com.dailysatori.service.reminder.ReminderStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderListStateTest {
    @Test
    fun yearSwitcherOnlyAppearsForMonthsMode() {
        assertTrue(ReminderListMode.MONTHS.showsYearSwitcher())
        assertFalse(ReminderListMode.RECENT.showsYearSwitcher())
        assertFalse(ReminderListMode.FINISHED.showsYearSwitcher())
    }

    @Test
    fun monthCardStateDistinguishesCurrentSelectedAndEmptyMonths() {
        val selected = ReminderMonthUi(month = 9, count = 3, items = emptyList()).toCardUi(currentMonth = 9, expandedMonth = 9)
        val empty = ReminderMonthUi(month = 10, count = 0, items = emptyList()).toCardUi(currentMonth = 9, expandedMonth = 9)

        assertTrue(selected.isCurrent)
        assertTrue(selected.isExpanded)
        assertEquals(3, selected.count)
        assertTrue(selected.hasReminders)
        assertFalse(empty.isCurrent)
        assertFalse(empty.isExpanded)
        assertFalse(empty.hasReminders)
    }

    @Test
    fun monthsOnlyExposeDetailsForExpandedMonth() {
        val reminders = listOf(
            reminder(id = "september", recurrence = yearly(9, 5)),
            reminder(id = "october", recurrence = yearly(10, 5)),
        )

        val collapsed = buildReminderListState(reminders, LocalDate(2026, 9, 1), ReminderListMode.MONTHS, ReminderListFilter(displayYear = 2026))
        val expanded = buildReminderListState(reminders, LocalDate(2026, 9, 1), ReminderListMode.MONTHS, ReminderListFilter(displayYear = 2026, expandedMonth = 10))

        assertTrue(collapsed.sections.isEmpty())
        assertEquals(listOf("month_10"), expanded.sections.map { it.key })
        assertEquals(listOf("october"), expanded.sections.single().items.map { it.id })
    }
    @Test
    fun recentSortsByNextOccurrenceAcrossYearBoundary() {
        val state = buildReminderListState(
            reminders = listOf(
                reminder("january-next-year", yearly(1, 2)),
                reminder("september", yearly(9, 2)),
            ),
            now = LocalDate(2026, 8, 31),
            mode = ReminderListMode.RECENT,
            filter = ReminderListFilter(),
        )

        assertEquals(listOf("september", "january-next-year"), state.sections.flatMap { it.items }.map { it.id })
    }

    @Test
    fun filterPanelStateDoesNotChangeListScrollKey() {
        val closed = buildReminderListState(emptyList(), LocalDate(2026, 9, 1), ReminderListMode.RECENT, ReminderListFilter())
        val opened = buildReminderListState(emptyList(), LocalDate(2026, 9, 1), ReminderListMode.RECENT, ReminderListFilter(isPanelOpen = true))

        assertEquals(closed.listIdentity, opened.listIdentity)
    }

    @Test
    fun finishedOnlyContainsTerminalOneTimeReminders() {
        val state = buildReminderListState(
            reminders = listOf(
                reminder("one-time", ReminderRecurrence.Once, ReminderStatus.COMPLETED),
                reminder("yearly", yearly(9, 2), ReminderStatus.COMPLETED),
            ),
            now = LocalDate(2026, 9, 3),
            mode = ReminderListMode.FINISHED,
            filter = ReminderListFilter(),
        )

        assertEquals(listOf("one-time"), state.sections.single().items.map { it.id })
    }

    @Test
    fun terminalRecurringRemindersProjectIntoRecentAndNeverFinished() {
        val reminders = listOf(
            reminder("monthly", ReminderRecurrence.Monthly(3), ReminderStatus.EXPIRED),
            reminder("yearly", yearly(9, 2), ReminderStatus.COMPLETED),
        )

        val recent = buildReminderListState(reminders, LocalDate(2026, 9, 1), ReminderListMode.RECENT, ReminderListFilter())
        val months = buildReminderListState(reminders, LocalDate(2026, 9, 1), ReminderListMode.MONTHS, ReminderListFilter())
        val finished = buildReminderListState(reminders, LocalDate(2026, 9, 1), ReminderListMode.FINISHED, ReminderListFilter())

        assertEquals(listOf("yearly", "monthly"), recent.sections.flatMap { it.items }.map { it.id })
        assertEquals(listOf("yearly", "monthly"), months.months.single { it.month == 9 }.items.map { it.id })
        assertTrue(finished.sections.isEmpty())
    }

    @Test
    fun filtersApplyCategoryRepeatAndSearchBeforeProjection() {
        val state = buildReminderListState(
            reminders = listOf(
                reminder("yearly-pay", yearly(9, 3)),
                reminder("monthly-pay", ReminderRecurrence.Monthly(3), ReminderStatus.PAUSED),
            ),
            now = LocalDate(2026, 9, 1),
            mode = ReminderListMode.RECENT,
            filter = ReminderListFilter(
                statuses = setOf(ReminderStatus.ACTIVE),
                recurrences = setOf(ReminderRecurrenceKind.YEARLY),
                query = "pay",
            ),
        )

        assertEquals(listOf("yearly-pay"), state.sections.flatMap { it.items }.map { it.id })
        assertTrue(state.summary.upcomingInThirtyDays == 1)
    }

    private fun reminder(id: String, recurrence: ReminderRecurrence, status: ReminderStatus = ReminderStatus.ACTIVE) = Reminder(
        id = id,
        content = id,
        startDate = LocalDate(2026, 1, 2),
        endDate = LocalDate(2026, 1, 2),
        firstReminderTime = LocalTime(9, 0),
        activeDayRule = ReminderActiveDayRule.Daily,
        profile = ReminderProfileSnapshot.standard(),
        status = status,
        timeZone = TimeZone.UTC,
        version = 1,
        recurrence = recurrence,
    )

    private fun yearly(month: Int, day: Int) = ReminderRecurrence.Yearly(month, day, LeapDayPolicy.FEBRUARY_28)
}
