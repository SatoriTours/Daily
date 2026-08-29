package com.dailysatori.service.reminder

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class ReminderScheduleEngine {
    fun next(input: ReminderScheduleInput): ReminderScheduleDecision {
        if (input.status in terminalStatuses) return ReminderScheduleDecision.None(input.status)
        val localNow = input.now.toLocalDateTime(input.timeZone)
        val cycleInput = if (input.stateDate != null && input.stateDate != localNow.date) {
            input.copy(status = ReminderStatus.ACTIVE, dismissalCount = 0)
        } else input
        if (localNow.date > input.endDate) return ReminderScheduleDecision.None(ReminderStatus.EXPIRED)
        if (!isActiveDate(localNow.date, input)) return scheduleNextActiveDate(input, localNow.date, inclusive = true)
        if (localNow.time >= input.profile.dailyCutoff && input.profile.dailyCutoff != LocalTime(0, 0)) {
            return scheduleNextActiveDate(input, localNow.date, inclusive = false)
        }
        val firstAt = at(input, localNow.date, input.firstReminderTime)
        if (isSleepTime(localNow.time, input.profile) && isDueBeforeWake(cycleInput, firstAt)) {
            return scheduleAt(input, localNow.date, input.profile.sleepEnd, ReminderDeliveryReason.WAKE_RECOVERY)
        }

        if (localNow.time >= input.profile.eveningStart) {
            return nextEveningTime(localNow.time, input.profile)?.let {
                scheduleAt(input, localNow.date, it, ReminderDeliveryReason.EVENING_REINFORCEMENT)
            } ?: scheduleNextActiveDate(input, localNow.date, inclusive = false)
        }
        if (input.now < firstAt) return schedule(input, firstAt, ReminderDeliveryReason.INITIAL)
        return when (cycleInput.status) {
            ReminderStatus.NOTIFIED -> nextDaytime(input, input.now.plus(1, DateTimeUnit.HOUR, input.timeZone), ReminderDeliveryReason.HOURLY_REPEAT)
            ReminderStatus.DISMISSED -> nextDismissal(cycleInput, localNow.date)
            ReminderStatus.ACTIVE -> schedule(input, input.now, ReminderDeliveryReason.INITIAL)
            else -> ReminderScheduleDecision.None(input.status)
        }
    }

    private fun nextDismissal(input: ReminderScheduleInput, date: LocalDate): ReminderScheduleDecision {
        val minutes = input.profile.daytimeDismissalBackoffMinutes.getOrNull(input.dismissalCount - 1)
            ?: input.profile.daytimeDismissalBackoffMinutes.lastOrNull()
            ?: return nextEveningTime(input.now.toLocalDateTime(input.timeZone).time, input.profile)?.let {
                scheduleAt(input, date, it, ReminderDeliveryReason.EVENING_REINFORCEMENT)
            } ?: ReminderScheduleDecision.None(ReminderStatus.ACTIVE)
        return nextDaytime(input, input.now.plus(minutes, DateTimeUnit.MINUTE, input.timeZone), ReminderDeliveryReason.DISMISSAL_BACKOFF)
    }

    private fun nextDaytime(input: ReminderScheduleInput, candidate: Instant, reason: ReminderDeliveryReason): ReminderScheduleDecision {
        val local = candidate.toLocalDateTime(input.timeZone)
        return if (local.date == input.now.toLocalDateTime(input.timeZone).date && local.time >= input.profile.eveningStart) {
            scheduleAt(input, local.date, input.profile.eveningStart, ReminderDeliveryReason.EVENING_REINFORCEMENT)
        } else schedule(input, candidate, reason)
    }

    private fun scheduleNextActiveDate(input: ReminderScheduleInput, from: LocalDate, inclusive: Boolean): ReminderScheduleDecision {
        var date = if (inclusive) from else from.plus(1, DateTimeUnit.DAY)
        while (date <= input.endDate) {
            if (isActiveDate(date, input)) return scheduleAt(input, date, input.firstReminderTime, ReminderDeliveryReason.NEXT_ACTIVE_DATE)
            date = date.plus(1, DateTimeUnit.DAY)
        }
        return ReminderScheduleDecision.None(ReminderStatus.EXPIRED)
    }

    private fun nextEveningTime(now: LocalTime, profile: ReminderProfileSnapshot): LocalTime? {
        val interval = profile.eveningIntervalMinutes ?: return if (profile.kind == ReminderProfileKind.GENTLE && now < profile.eveningStart) profile.eveningStart else null
        if (now < profile.eveningStart) return null
        val startMinutes = profile.eveningStart.hour * 60 + profile.eveningStart.minute
        val nowMinutes = now.hour * 60 + now.minute
        val nextMinutes = startMinutes + ((nowMinutes - startMinutes) / interval + 1) * interval
        val cutoffMinutes = if (profile.dailyCutoff == LocalTime(0, 0)) 24 * 60 else profile.dailyCutoff.hour * 60 + profile.dailyCutoff.minute
        return nextMinutes.takeIf { it < cutoffMinutes }?.let { LocalTime(it / 60, it % 60) }
    }

    private fun scheduleAt(input: ReminderScheduleInput, date: LocalDate, time: LocalTime, reason: ReminderDeliveryReason): ReminderScheduleDecision =
        schedule(input, at(input, date, time), reason)

    private fun schedule(input: ReminderScheduleInput, at: Instant, reason: ReminderDeliveryReason): ReminderScheduleDecision {
        val local = at.toLocalDateTime(input.timeZone)
        return ReminderScheduleDecision.Schedule(at, local.date.dayOfWeek in input.profile.workDays && local.time >= input.profile.workStart && local.time < input.profile.workEnd, reason, input.expectedVersion)
    }

    private fun at(input: ReminderScheduleInput, date: LocalDate, time: LocalTime): Instant = LocalDateTime(date, time).toInstant(input.timeZone)

    private fun isActiveDate(date: LocalDate, input: ReminderScheduleInput): Boolean = date >= input.startDate && date <= input.endDate && when (val rule = input.activeDayRule) {
        ReminderActiveDayRule.Daily, ReminderActiveDayRule.ConsecutiveDateRange -> true
        ReminderActiveDayRule.Weekdays -> date.dayOfWeek.value <= 5
        is ReminderActiveDayRule.SelectedWeekdays -> date.dayOfWeek in rule.days
    }

    private fun isSleepTime(time: LocalTime, profile: ReminderProfileSnapshot): Boolean =
        if (profile.sleepStart <= profile.sleepEnd) time >= profile.sleepStart && time < profile.sleepEnd else time >= profile.sleepStart || time < profile.sleepEnd

    private fun isDueBeforeWake(input: ReminderScheduleInput, firstAt: Instant): Boolean =
        input.status == ReminderStatus.NOTIFIED || (input.status == ReminderStatus.DISMISSED && (input.dismissalCount > 0 || input.now >= firstAt))

    private companion object { val terminalStatuses = setOf(ReminderStatus.DRAFT, ReminderStatus.PAUSED, ReminderStatus.COMPLETED, ReminderStatus.EXPIRED) }
}
