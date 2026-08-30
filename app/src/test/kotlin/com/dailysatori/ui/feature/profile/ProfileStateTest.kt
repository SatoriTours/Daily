package com.dailysatori.ui.feature.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import com.dailysatori.service.asynctask.AsyncTaskListItem
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderStatus
import com.dailysatori.service.reminder.ReminderDataIssue
import com.dailysatori.service.reminder.ReminderRecurrence
import kotlinx.datetime.DayOfWeek

class ProfileStateTest {
    @Test
    fun profileContainsOnlyRealDestinations() {
        val state = ProfileUiState()

        assertEquals(
            listOf("reminders", "favorites", "external_favorites", "tasks", "settings", "privacy"),
            state.destinations.map { it.id },
        )
        assertFalse(state.destinations.any { it.id in setOf("read_later", "history") })
    }

    @Test
    fun taskProjectionExposesProgressAndFailureForTheProfile() {
        val summary = profileTaskSummary(listOf(
            profileTask(status = "running", current = 2, total = 5),
            profileTask(status = "failed", current = 1, total = 5),
        ))

        assertEquals(1, summary.activeCount)
        assertEquals(1, summary.failedCount)
        assertEquals("2/5", summary.progressLabel)
        assertTrue(summary.canOpenFailedTasks)
    }

    @Test
    fun reminderSummaryRecalculatesForTheProvidedDay() {
        val reminder = profileReminder(date = LocalDate(2026, 9, 2), content = "续费", hour = 8)

        assertEquals("续费", profileReminderSummary(listOf(reminder), LocalDate(2026, 9, 2)).nextContent)
        assertEquals(null, profileReminderSummary(listOf(reminder), LocalDate(2026, 9, 3)).nextContent)
    }

    @Test
    fun nextReminderUsesTheSameTodayOccurrenceRulesAsTheBadge() {
        val weekday = profileReminder(LocalDate(2026, 9, 5), "weekday", 8, ReminderActiveDayRule.Weekdays)
        val selected = profileReminder(LocalDate(2026, 9, 5), "selected", 9, ReminderActiveDayRule.SelectedWeekdays(setOf(DayOfWeek.SATURDAY)))
        val corrupt = profileReminder(LocalDate(2026, 9, 5), "corrupt", 7, dataIssue = ReminderDataIssue.CORRUPT_PROFILE)
        val monthly = profileReminder(LocalDate(2026, 9, 5), "monthly", 10, recurrence = ReminderRecurrence.Monthly(6))

        assertEquals("selected", profileReminderSummary(listOf(weekday, selected, corrupt, monthly), LocalDate(2026, 9, 5)).nextContent)
    }
}

private fun profileTask(status: String, current: Long, total: Long) = AsyncTaskListItem(
    id = 1, type = "remote_article_sync", status = status, progressCurrent = current, progressTotal = total,
    progressMessage = "", checkpointJson = "", createdAt = 0, startedAt = null, finishedAt = null, updatedAt = 0, lastErrorMessage = "",
)

private fun profileReminder(date: LocalDate, content: String, hour: Int, rule: ReminderActiveDayRule = ReminderActiveDayRule.Daily, dataIssue: ReminderDataIssue? = null, recurrence: ReminderRecurrence = ReminderRecurrence.Once) = Reminder(
    id = content, content = content, startDate = date, endDate = date, firstReminderTime = LocalTime(hour, 0),
    activeDayRule = rule, profile = ReminderProfileSnapshot.strong(), status = ReminderStatus.ACTIVE,
    timeZone = TimeZone.UTC, version = 1, dataIssue = dataIssue, recurrence = recurrence,
)
