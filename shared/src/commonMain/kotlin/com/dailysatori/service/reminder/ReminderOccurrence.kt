package com.dailysatori.service.reminder

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

data class ReminderOccurrence(
    val reminderId: String,
    val date: LocalDate,
    val startAt: Instant,
)

fun Reminder.nextOccurrenceOnOrAfter(onOrAfter: LocalDate): LocalDate? = when (val rule = recurrence) {
    ReminderRecurrence.Once -> startDate.takeIf { it >= onOrAfter }
    is ReminderRecurrence.Monthly -> nextMonthlyOccurrence(onOrAfter, rule.dayOfMonth)
    is ReminderRecurrence.Yearly -> nextYearlyOccurrence(onOrAfter, rule)
}

private fun nextMonthlyOccurrence(onOrAfter: LocalDate, dayOfMonth: Int): LocalDate {
    var month = LocalDate(onOrAfter.year, onOrAfter.monthNumber, 1)
    while (true) {
        validDateOrNull(month.year, month.monthNumber, dayOfMonth)?.takeIf { it >= onOrAfter }?.let { return it }
        month = month.plus(1, DateTimeUnit.MONTH)
    }
}

private fun nextYearlyOccurrence(onOrAfter: LocalDate, rule: ReminderRecurrence.Yearly): LocalDate {
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
