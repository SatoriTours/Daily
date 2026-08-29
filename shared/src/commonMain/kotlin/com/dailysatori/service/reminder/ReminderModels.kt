package com.dailysatori.service.reminder

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

enum class ReminderStatus { DRAFT, ACTIVE, NOTIFIED, DISMISSED, PAUSED, COMPLETED, EXPIRED }

enum class ReminderProfileKind { STRONG, STANDARD, GENTLE, CUSTOM }

sealed interface ReminderActiveDayRule {
    data object Daily : ReminderActiveDayRule
    data object Weekdays : ReminderActiveDayRule
    data class SelectedWeekdays(val days: Set<DayOfWeek>) : ReminderActiveDayRule
    data object ConsecutiveDateRange : ReminderActiveDayRule
}

data class ReminderProfileSnapshot(
    val kind: ReminderProfileKind,
    val daytimeDismissalBackoffMinutes: List<Int>,
    val eveningStart: LocalTime = LocalTime(22, 0),
    val eveningIntervalMinutes: Int? = 60,
    val eveningTimes: Set<LocalTime> = emptySet(),
    val dailyCutoff: LocalTime = LocalTime(0, 0),
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val sleepStart: LocalTime = LocalTime(0, 0),
    val sleepEnd: LocalTime = LocalTime(9, 0),
    val workDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
    val workStart: LocalTime = LocalTime(9, 0),
    val workEnd: LocalTime = LocalTime(18, 0),
) {
    companion object {
        fun strong() = ReminderProfileSnapshot(ReminderProfileKind.STRONG, listOf(120, 240))
        fun standard() = ReminderProfileSnapshot(ReminderProfileKind.STANDARD, listOf(120, 240), eveningIntervalMinutes = null, eveningTimes = setOf(LocalTime(22, 0), LocalTime(23, 0)))
        fun gentle() = ReminderProfileSnapshot(ReminderProfileKind.GENTLE, emptyList(), eveningIntervalMinutes = null, eveningTimes = setOf(LocalTime(22, 0)), soundEnabled = false, vibrationEnabled = false)
    }
}

data class ReminderDraft(
    val id: String,
    val content: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val firstReminderTime: LocalTime?,
    val activeDayRule: ReminderActiveDayRule = ReminderActiveDayRule.Daily,
    val profile: ReminderProfileSnapshot? = null,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    val validationErrors: List<String> = emptyList(),
)

data class Reminder(
    val id: String,
    val content: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val firstReminderTime: LocalTime,
    val activeDayRule: ReminderActiveDayRule,
    val profile: ReminderProfileSnapshot,
    val status: ReminderStatus,
    val timeZone: TimeZone,
    val version: Long,
)

enum class ReminderDeliveryReason { INITIAL, HOURLY_REPEAT, DISMISSAL_BACKOFF, EVENING_REINFORCEMENT, WAKE_RECOVERY, NEXT_ACTIVE_DATE }

data class ReminderScheduleInput(
    val now: Instant,
    val timeZone: TimeZone,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val firstReminderTime: LocalTime,
    val activeDayRule: ReminderActiveDayRule,
    val profile: ReminderProfileSnapshot,
    val status: ReminderStatus,
    val dismissalCount: Int = 0,
    val stateDate: LocalDate? = null,
    val expectedVersion: Long,
)

sealed interface ReminderScheduleDecision {
    data class Schedule(
        val at: Instant,
        val silent: Boolean,
        val reason: ReminderDeliveryReason,
        val expectedVersion: Long,
        val soundEnabled: Boolean = !silent,
        val vibrationEnabled: Boolean = !silent,
    ) : ReminderScheduleDecision
    data class None(val status: ReminderStatus) : ReminderScheduleDecision
}
