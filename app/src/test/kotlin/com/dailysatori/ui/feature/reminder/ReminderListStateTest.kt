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

    @Test
    fun recentSplitsTodayAndTomorrowFromNextSevenDays() {
        val state = buildReminderListState(
            reminders = listOf(
                reminder("today", ReminderRecurrence.Monthly(14)),
                reminder("tomorrow", ReminderRecurrence.Monthly(15)),
                reminder("in-three-days", ReminderRecurrence.Monthly(17)),
                reminder("later-this-month", ReminderRecurrence.Monthly(28)),
                reminder("next-month", ReminderRecurrence.Monthly(5)),
            ),
            now = LocalDate(2026, 9, 14),
            mode = ReminderListMode.RECENT,
            filter = ReminderListFilter(),
        )

        assertEquals(
            listOf("today", "tomorrow", "next_week", "later_this_month", "later"),
            state.sections.map { it.key },
        )
        assertEquals(listOf("today"), state.sections[0].items.map { it.id })
        assertEquals(listOf("tomorrow"), state.sections[1].items.map { it.id })
        assertEquals(listOf("in-three-days"), state.sections[2].items.map { it.id })
        assertEquals(listOf("later-this-month"), state.sections[3].items.map { it.id })
        assertEquals(listOf("next-month"), state.sections[4].items.map { it.id })
    }

    @Test
    fun emptyRecentGroupsAreOmitted() {
        val state = buildReminderListState(
            reminders = listOf(reminder("next-month", ReminderRecurrence.Monthly(5))),
            now = LocalDate(2026, 9, 14),
            mode = ReminderListMode.RECENT,
            filter = ReminderListFilter(),
        )

        assertEquals(listOf("later"), state.sections.map { it.key })
    }

    @Test
    fun finishedSplitsCompletedAndExpiredGroups() {
        val state = buildReminderListState(
            reminders = listOf(
                reminder("completed", ReminderRecurrence.Once, ReminderStatus.COMPLETED),
                reminder("expired", ReminderRecurrence.Once, ReminderStatus.EXPIRED),
            ),
            now = LocalDate(2026, 9, 14),
            mode = ReminderListMode.FINISHED,
            filter = ReminderListFilter(),
        )

        assertEquals(listOf("completed", "expired"), state.sections.map { it.key })
        assertEquals(listOf("completed"), state.sections[0].items.map { it.id })
        assertEquals(listOf("expired"), state.sections[1].items.map { it.id })
    }

    @Test
    fun summaryExposesNextItemAndPausedCountForTheHero() {
        val state = buildReminderListState(
            reminders = listOf(
                reminder("tomorrow", ReminderRecurrence.Monthly(15)),
                reminder("paused-today", ReminderRecurrence.Monthly(14), ReminderStatus.PAUSED),
                reminder("beyond-thirty-days", yearly(11, 11)),
            ),
            now = LocalDate(2026, 9, 14),
            mode = ReminderListMode.RECENT,
            filter = ReminderListFilter(),
        )

        assertEquals("paused-today", state.summary.nextItem?.id)
        assertEquals(2, state.summary.upcomingInThirtyDays)
        assertEquals(1, state.summary.pausedUpcoming)
    }

    @Test
    fun repeatLabelCombinesRecurrenceWithActiveDayRule() {
        val now = LocalDate(2026, 9, 14)
        fun label(rule: ReminderActiveDayRule, recurrence: ReminderRecurrence = ReminderRecurrence.Once) =
            buildReminderListState(
                reminders = listOf(reminder("x", recurrence).copy(activeDayRule = rule, startDate = now, endDate = now)),
                now = now,
                mode = ReminderListMode.RECENT,
                filter = ReminderListFilter(),
            ).sections.single().items.single().repeatLabel()

        assertEquals(ReminderRepeatLabel.DAILY, label(ReminderActiveDayRule.Daily))
        assertEquals(ReminderRepeatLabel.WEEKDAYS, label(ReminderActiveDayRule.Weekdays))
        assertEquals(ReminderRepeatLabel.WEEKLY, label(ReminderActiveDayRule.SelectedWeekdays(setOf(kotlinx.datetime.DayOfWeek.SUNDAY))))
        assertEquals(ReminderRepeatLabel.ONCE, label(ReminderActiveDayRule.ConsecutiveDateRange))
        assertEquals(ReminderRepeatLabel.MONTHLY, label(ReminderActiveDayRule.Daily, ReminderRecurrence.Monthly(3)))
        assertEquals(ReminderRepeatLabel.YEARLY, label(ReminderActiveDayRule.Daily, yearly(9, 2)))
    }

    @Test
    fun heroSummaryKeepsUpcomingItemsOutsideRecentMode() {
        val state = buildReminderListState(
            reminders = listOf(
                reminder("upcoming", ReminderRecurrence.Monthly(20)),
                reminder("done", ReminderRecurrence.Once, ReminderStatus.COMPLETED),
            ),
            now = LocalDate(2026, 9, 14),
            mode = ReminderListMode.FINISHED,
            filter = ReminderListFilter(),
        )

        assertEquals(listOf("done"), state.sections.flatMap { it.items }.map { it.id })
        assertEquals("upcoming", state.summary.nextItem?.id)
        assertEquals(1, state.summary.upcomingInThirtyDays)
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
