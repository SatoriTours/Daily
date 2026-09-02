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
            listOf("reminders", "favorites", "external_favorites", "remote_news", "tasks", "settings", "privacy"),
            state.destinations.map { it.id },
        )
        assertFalse(state.destinations.any { it.id in setOf("read_later", "history") })
    }

    @Test
    fun remoteNewsProjectionExposesCountsAndConfiguredSubtitle() {
        val state = ProfileUiState(
            remoteNewsArticleCount = 12,
            enabledRemoteNewsSourceCount = 3,
        )

        assertEquals(12, state.remoteNewsArticleCount)
        assertEquals(3, state.enabledRemoteNewsSourceCount)
        assertEquals("已同步 12 篇 · 3 个来源", remoteNewsProfileSubtitle(12, 3))
        assertEquals("尚未配置来源", remoteNewsProfileSubtitle(12, 0))
    }

    @Test
    fun taskProjectionExposesProgressAndFailureForTheProfile() {
        val now = 2 * 24 * 60 * 60 * 1000L
        val summary = profileTaskSummary(listOf(
            profileTask(status = "running", current = 2, total = 5, updatedAt = now),
            profileTask(status = "failed", current = 1, total = 5, updatedAt = now),
        ), nowMs = now)

        assertEquals(1, summary.activeCount)
        assertEquals(1, summary.failedCount)
        assertEquals("2/5", summary.progressLabel)
        assertTrue(summary.canOpenFailedTasks)
    }

    @Test
    fun profileDoesNotPresentOldFailuresAsCurrentFailures() {
        val day = 24 * 60 * 60 * 1000L
        val now = 10 * day
        val summary = profileTaskSummary(
            tasks = listOf(
                profileTask(status = "failed", current = 0, total = 0, updatedAt = now - day - 1),
                profileTask(status = "succeeded", current = 2, total = 2, updatedAt = now),
            ),
            nowMs = now,
        )

        assertEquals(0, summary.failedCount)
        assertFalse(summary.canOpenFailedTasks)
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

private fun profileTask(status: String, current: Long, total: Long, updatedAt: Long = 0) = AsyncTaskListItem(
    id = 1, type = "remote_article_sync", status = status, progressCurrent = current, progressTotal = total,
    progressMessage = "", checkpointJson = "", createdAt = 0, startedAt = null, finishedAt = null, updatedAt = updatedAt, lastErrorMessage = "",
)

private fun profileReminder(date: LocalDate, content: String, hour: Int, rule: ReminderActiveDayRule = ReminderActiveDayRule.Daily, dataIssue: ReminderDataIssue? = null, recurrence: ReminderRecurrence = ReminderRecurrence.Once) = Reminder(
    id = content, content = content, startDate = date, endDate = date, firstReminderTime = LocalTime(hour, 0),
    activeDayRule = rule, profile = ReminderProfileSnapshot.strong(), status = ReminderStatus.ACTIVE,
    timeZone = TimeZone.UTC, version = 1, dataIssue = dataIssue, recurrence = recurrence,
)
