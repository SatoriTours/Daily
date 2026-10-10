package com.dailysatori.service.reminder

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

data class ReminderOccurrence(
    val reminderId: String,
    val date: LocalDate,
    val startAt: Instant,
)

fun Reminder.nextOccurrenceOnOrAfter(onOrAfter: LocalDate): LocalDate? {
    if (isUnscheduledSmsTodo) return onOrAfter.coerceAtLeast(startDate)
    deadlineAt?.let { return it.toLocalDateTime(timeZone).date.takeIf { date -> date >= onOrAfter } }
    val cycleStart = stateDate?.let { onOrAfter.coerceAtLeast(it) } ?: onOrAfter
    return when (val rule = recurrence) {
        ReminderRecurrence.Once -> startDate.takeIf { it >= onOrAfter }
        is ReminderRecurrence.Monthly -> nextMonthlyOccurrence(cycleStart, rule.dayOfMonth)
        is ReminderRecurrence.Yearly -> nextYearlyOccurrence(cycleStart, rule)
    }
}

fun Reminder.latestOccurrenceOnOrBefore(onOrBefore: LocalDate): LocalDate? {
    val limit = if (recurrence == ReminderRecurrence.Once) onOrBefore.coerceAtMost(endDate) else onOrBefore
    if (limit < startDate || activeDayRule is ReminderActiveDayRule.SelectedWeekdays && activeDayRule.days.isEmpty()) return null
    when (val rule = recurrence) {
        ReminderRecurrence.Once -> {
            var date = limit
            while (date >= startDate) {
                if (activeDayRule.includes(date)) return date
                date = date.minus(1, DateTimeUnit.DAY)
            }
        }
        is ReminderRecurrence.Monthly -> {
            var month = LocalDate(limit.year, limit.monthNumber, 1)
            val firstMonth = LocalDate(startDate.year, startDate.monthNumber, 1)
            while (month >= firstMonth) {
                val date = validDateOrNull(month.year, month.monthNumber, rule.dayOfMonth)
                if (date != null && date in startDate..limit && activeDayRule.includes(date)) return date
                month = month.minus(1, DateTimeUnit.MONTH)
            }
        }
        is ReminderRecurrence.Yearly -> {
            for (year in limit.year downTo startDate.year) {
                val date = yearlyDate(year, rule)
                if (date in startDate..limit && activeDayRule.includes(date)) return date
            }
        }
    }
    return null
}

private fun ReminderActiveDayRule.includes(date: LocalDate): Boolean = when (this) {
    ReminderActiveDayRule.Daily, ReminderActiveDayRule.ConsecutiveDateRange -> true
    ReminderActiveDayRule.Weekdays -> date.dayOfWeek.value <= 5
    is ReminderActiveDayRule.SelectedWeekdays -> date.dayOfWeek in days
}

internal fun nextMonthlyOccurrence(onOrAfter: LocalDate, dayOfMonth: Int): LocalDate {
    var month = LocalDate(onOrAfter.year, onOrAfter.monthNumber, 1)
    while (true) {
        validDateOrNull(month.year, month.monthNumber, dayOfMonth)?.takeIf { it >= onOrAfter }?.let { return it }
        month = month.plus(1, DateTimeUnit.MONTH)
    }
}

internal fun nextYearlyOccurrence(onOrAfter: LocalDate, rule: ReminderRecurrence.Yearly): LocalDate {
    var year = onOrAfter.year
    while (true) {
        yearlyDate(year, rule).takeIf { it >= onOrAfter }?.let { return it }
        year += 1
    }
}

private fun yearlyDate(year: Int, rule: ReminderRecurrence.Yearly): LocalDate =
    validDateOrNull(year, rule.month, rule.dayOfMonth) ?: when (rule.leapDayPolicy) {
        LeapDayPolicy.FEBRUARY_28 -> LocalDate(year, 2, 28)
        LeapDayPolicy.MARCH_1 -> LocalDate(year, 3, 1)
    }

private fun validDateOrNull(year: Int, month: Int, dayOfMonth: Int): LocalDate? {
    val maximumDay = when (month) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    return if (dayOfMonth <= maximumDay) LocalDate(year, month, dayOfMonth) else null
}

private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
