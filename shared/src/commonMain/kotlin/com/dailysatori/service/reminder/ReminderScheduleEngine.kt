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
        if (isSleepTime(localNow.time, input.profile) && (isDueBeforeWake(cycleInput, firstAt) || isSleepTime(input.firstReminderTime, input.profile))) {
            if (input.profile.sleepStart > input.profile.sleepEnd && localNow.time >= input.profile.sleepStart) {
                return scheduleCutoff(input, localNow.date)
            }
            return scheduleAt(input, localNow.date, input.profile.sleepEnd, ReminderDeliveryReason.WAKE_RECOVERY)
        }

        if (localNow.time >= input.profile.eveningStart) {
            return nextEveningTime(localNow.time, input.profile)?.let {
                scheduleAt(input, localNow.date, it, ReminderDeliveryReason.EVENING_REINFORCEMENT)
            } ?: scheduleCutoff(input, localNow.date)
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
            } ?: scheduleCutoff(input, date)
        return nextDaytime(input, input.now.plus(minutes, DateTimeUnit.MINUTE, input.timeZone), ReminderDeliveryReason.DISMISSAL_BACKOFF)
    }

    private fun nextDaytime(input: ReminderScheduleInput, candidate: Instant, reason: ReminderDeliveryReason): ReminderScheduleDecision {
        val local = candidate.toLocalDateTime(input.timeZone)
        val current = input.now.toLocalDateTime(input.timeZone)
        val evening = nextEveningTime(current.time, input.profile)
        if (local.date != current.date || local.time >= input.profile.eveningStart) {
            return evening?.let {
                scheduleAt(input, current.date, it, ReminderDeliveryReason.EVENING_REINFORCEMENT)
            } ?: scheduleCutoff(input, current.date)
        }
        return schedule(input, candidate, reason)
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
        val cutoffMinutes = cutoffMinutes(profile)
        profile.eveningTimes.filter { it > now && it.toMinutes() < cutoffMinutes }.minOrNull()?.let { return it }
        val interval = profile.eveningIntervalMinutes ?: return null
        if (now < profile.eveningStart) return profile.eveningStart.takeIf { it.toMinutes() < cutoffMinutes }
        val startMinutes = profile.eveningStart.hour * 60 + profile.eveningStart.minute
        val nowMinutes = now.hour * 60 + now.minute
        val nextMinutes = startMinutes + ((nowMinutes - startMinutes) / interval + 1) * interval
        return nextMinutes.takeIf { it < cutoffMinutes }?.let { LocalTime(it / 60, it % 60) }
    }

    private fun cutoffMinutes(profile: ReminderProfileSnapshot): Int =
        if (profile.dailyCutoff == LocalTime(0, 0)) 24 * 60 else profile.dailyCutoff.toMinutes()

    private fun LocalTime.toMinutes(): Int = hour * 60 + minute

    private fun scheduleAt(input: ReminderScheduleInput, date: LocalDate, time: LocalTime, reason: ReminderDeliveryReason): ReminderScheduleDecision {
        val wrapsToNextDay = input.profile.sleepStart > input.profile.sleepEnd && time >= input.profile.sleepStart
        val normalizedDate = if (isSleepTime(time, input.profile) && wrapsToNextDay) date.plus(1, DateTimeUnit.DAY) else date
        val normalizedTime = if (isSleepTime(time, input.profile)) input.profile.sleepEnd else time
        val normalizedReason = if (normalizedTime == time) reason else ReminderDeliveryReason.WAKE_RECOVERY
        if (normalizedDate > input.endDate) return scheduleCutoff(input, date)
        if (normalizedDate != date && !isActiveDate(normalizedDate, input)) return scheduleCutoff(input, date)
        return schedule(input, at(input, normalizedDate, normalizedTime), normalizedReason)
    }

    private fun scheduleCutoff(input: ReminderScheduleInput, date: LocalDate): ReminderScheduleDecision {
        val cutoffAt = if (input.profile.dailyCutoff == LocalTime(0, 0)) {
            date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(input.timeZone)
        } else {
            at(input, date, input.profile.dailyCutoff)
        }
        return ReminderScheduleDecision.Cutoff(cutoffAt, input.expectedVersion)
    }

    private fun schedule(input: ReminderScheduleInput, at: Instant, reason: ReminderDeliveryReason): ReminderScheduleDecision {
        val local = at.toLocalDateTime(input.timeZone)
        val workHours = local.date.dayOfWeek in input.profile.workDays && local.time >= input.profile.workStart && local.time < input.profile.workEnd
        val soundEnabled = input.profile.soundEnabled && !workHours
        val vibrationEnabled = input.profile.vibrationEnabled && !workHours
        return ReminderScheduleDecision.Schedule(at, !soundEnabled && !vibrationEnabled, reason, input.expectedVersion, soundEnabled, vibrationEnabled)
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

    private fun hasEveningPolicy(profile: ReminderProfileSnapshot): Boolean =
        profile.eveningIntervalMinutes != null || profile.eveningTimes.isNotEmpty()

    private companion object { val terminalStatuses = setOf(ReminderStatus.DRAFT, ReminderStatus.PAUSED, ReminderStatus.COMPLETED, ReminderStatus.EXPIRED) }
}
