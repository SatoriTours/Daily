package com.dailysatori.ui.feature.reminder

import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderRecurrence
import com.dailysatori.service.reminder.ReminderStatus
import com.dailysatori.service.reminder.nextOccurrenceOnOrAfter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

enum class ReminderListMode { RECENT, MONTHS, FINISHED }

enum class ReminderRecurrenceKind { ONCE, MONTHLY, YEARLY }

data class ReminderListFilter(
    val statuses: Set<ReminderStatus> = emptySet(),
    val recurrences: Set<ReminderRecurrenceKind> = emptySet(),
    val query: String = "",
    val isPanelOpen: Boolean = false,
    val displayYear: Int? = null,
    val expandedMonth: Int? = null,
)

data class ReminderListItemUi(
    val id: String,
    val content: String,
    val occurrenceDate: LocalDate,
    val firstReminderTime: String,
    val daysUntil: Int,
    val recurrence: ReminderRecurrenceKind,
    val status: ReminderStatus,
)

data class ReminderListSectionUi(val key: String, val items: List<ReminderListItemUi>)

data class ReminderMonthUi(val month: Int, val count: Int, val items: List<ReminderListItemUi>)

data class ReminderListSummaryUi(val upcomingInThirtyDays: Int, val nextOccurrence: LocalDate?)

data class ReminderListState(
    val sections: List<ReminderListSectionUi>,
    val months: List<ReminderMonthUi>,
    val summary: ReminderListSummaryUi,
    val listIdentity: String,
)

fun buildReminderListState(
    reminders: List<Reminder>,
    now: LocalDate,
    mode: ReminderListMode,
    filter: ReminderListFilter,
): ReminderListState {
    val filtered = reminders.filter { it.matchesFilters(filter) }
    val items = (when (mode) {
        ReminderListMode.FINISHED -> filtered.finishedItems(now)
        else -> filtered.upcomingItems(now)
    }).filter { it.matchesQuery(filter.query, now) }
    val sorted = items.sortedWith(compareBy<ReminderListItemUi> { it.occurrenceDate }.thenBy { it.firstReminderTime }.thenBy { it.id })
    val year = filter.displayYear ?: now.year
    return ReminderListState(
        sections = when (mode) {
            ReminderListMode.RECENT -> sorted.groupForRecent(now)
            ReminderListMode.MONTHS -> sorted.groupByMonth(year)
            ReminderListMode.FINISHED -> listOfNotNull(ReminderListSectionUi("finished", sorted).takeIf { sorted.isNotEmpty() })
        },
        months = if (mode == ReminderListMode.MONTHS) filtered.monthsFor(year, now, filter.query) else emptyList(),
        summary = ReminderListSummaryUi(
            upcomingInThirtyDays = sorted.count { it.daysUntil in 0..30 },
            nextOccurrence = sorted.firstOrNull()?.occurrenceDate,
        ),
        listIdentity = "$mode|${filter.statuses.sortedBy { it.name }}|${filter.recurrences.sortedBy { it.name }}|${filter.query.trim().lowercase()}|$year|${filter.expandedMonth}",
    )
}

private fun Reminder.matchesFilters(filter: ReminderListFilter): Boolean =
    (filter.statuses.isEmpty() || status in filter.statuses) &&
        (filter.recurrences.isEmpty() || recurrence.kind() in filter.recurrences)

private fun List<Reminder>.upcomingItems(now: LocalDate): List<ReminderListItemUi> = mapNotNull { reminder ->
    if (reminder.recurrence == ReminderRecurrence.Once && reminder.status.isTerminal()) return@mapNotNull null
    reminder.nextOccurrenceOnOrAfter(now)?.let { reminder.toItem(it, now) }
}

private fun List<Reminder>.finishedItems(now: LocalDate): List<ReminderListItemUi> = mapNotNull { reminder ->
    if (reminder.recurrence != ReminderRecurrence.Once || !reminder.status.isTerminal()) return@mapNotNull null
    reminder.toItem(reminder.endDate, now)
}

private fun List<Reminder>.monthsFor(year: Int, now: LocalDate, query: String): List<ReminderMonthUi> = (1..12).map { month ->
    val firstDay = LocalDate(year, month, 1)
    val items = asSequence()
        .filter { it.recurrence != ReminderRecurrence.Once || !it.status.isTerminal() }
        .mapNotNull { reminder -> reminder.nextOccurrenceOnOrAfter(firstDay)?.takeIf { it.year == year && it.monthNumber == month }?.let { reminder.toItem(it, now) } }
        .filter { it.matchesQuery(query, now) }
        .sortedWith(compareBy<ReminderListItemUi> { it.occurrenceDate }.thenBy { it.firstReminderTime }.thenBy { it.id })
        .toList()
    ReminderMonthUi(month, items.size, items)
}

private fun List<ReminderListItemUi>.groupForRecent(now: LocalDate): List<ReminderListSectionUi> = listOfNotNull(
    ReminderListSectionUi("next_week", filter { it.daysUntil in 0..7 }).takeIf { it.items.isNotEmpty() },
    ReminderListSectionUi("later_this_month", filter { it.occurrenceDate.year == now.year && it.occurrenceDate.monthNumber == now.monthNumber && it.daysUntil > 7 }).takeIf { it.items.isNotEmpty() },
    ReminderListSectionUi("later", filter { it.daysUntil > 7 && (it.occurrenceDate.year != now.year || it.occurrenceDate.monthNumber != now.monthNumber) }).takeIf { it.items.isNotEmpty() },
)

private fun List<ReminderListItemUi>.groupByMonth(year: Int): List<ReminderListSectionUi> =
    filter { it.occurrenceDate.year == year }
        .groupBy { it.occurrenceDate.monthNumber }
        .toSortedMap()
        .map { (month, items) -> ReminderListSectionUi("month_$month", items) }

private fun Reminder.toItem(date: LocalDate, now: LocalDate) = ReminderListItemUi(
    id = id,
    content = content,
    occurrenceDate = date,
    firstReminderTime = firstReminderTime.toString(),
    daysUntil = now.daysUntil(date),
    recurrence = recurrence.kind(),
    status = status,
)

private fun ReminderListItemUi.matchesQuery(query: String, now: LocalDate): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return true
    return content.contains(normalized, ignoreCase = true) ||
        recurrence.name.lowercase().contains(normalized) ||
        occurrenceDate.toString().contains(normalized) ||
        normalized == occurrenceDate.monthNumber.toString() ||
        normalized == "next year" && occurrenceDate.year == now.year + 1
}

private fun ReminderRecurrence.kind(): ReminderRecurrenceKind = when (this) {
    ReminderRecurrence.Once -> ReminderRecurrenceKind.ONCE
    is ReminderRecurrence.Monthly -> ReminderRecurrenceKind.MONTHLY
    is ReminderRecurrence.Yearly -> ReminderRecurrenceKind.YEARLY
}

private fun ReminderStatus.isTerminal(): Boolean = this == ReminderStatus.COMPLETED || this == ReminderStatus.EXPIRED
