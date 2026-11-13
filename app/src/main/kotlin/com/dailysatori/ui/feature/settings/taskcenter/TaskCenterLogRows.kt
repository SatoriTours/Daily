package com.dailysatori.ui.feature.settings.taskcenter

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * One line of the task log page: lifecycle events, real request records, standalone responses and
 * non-HTTP log lines. Only real request rows open a detail; nothing is merged by label or order.
 */
internal data class TaskCenterLogEntry(
    val key: String,
    val atMs: Long?,
    val title: String,
    val abnormal: Boolean,
    val request: TaskCenterRequestDetail? = null,
    /** Status of a response line that has no reliably matched request. */
    val responseStatus: String = "",
    /** A timed, non-HTTP log line; kept verbatim so nothing silently disappears. */
    val info: Boolean = false,
    /** Source for rows that have no request detail (standalone responses). */
    val source: TaskCenterRecordSource = TaskCenterRecordSource.Unknown,
)

internal enum class TaskCenterLogFilter { All, Abnormal }

internal enum class TaskCenterLogSort { Newest, Oldest }

internal val TaskCenterRequestDetail.isAbnormal: Boolean
    get() = httpOk == false || responseIncomplete

internal fun taskCenterLogEntries(
    events: List<TaskCenterLifecycleEvent>,
    http: TaskCenterHttpLogParse,
    fallbackTitle: String,
    capturedRequests: List<TaskCenterRequestDetail> = emptyList(),
): List<TaskCenterLogEntry> = buildList {
    events.forEachIndexed { index, event ->
        add(
            TaskCenterLogEntry(
                key = "event-" + index,
                atMs = event.atMs,
                title = event.summary,
                abnormal = event.abnormal,
            ),
        )
    }
    if (capturedRequests.isNotEmpty()) {
        capturedRequests.forEachIndexed { index, request ->
            add(
                TaskCenterLogEntry(
                    key = "captured-" + index,
                    atMs = request.requestedAtMs,
                    title = taskCenterRequestTitle(request, fallbackTitle),
                    abnormal = request.isAbnormal,
                    request = request,
                    source = request.source,
                ),
            )
        }
    } else {
        http.entries.forEachIndexed { index, entry ->
            when {
                entry.request.isNotBlank() -> {
                    val detail = entry.toRequestDetail()
                    add(
                        TaskCenterLogEntry(
                            key = "request-" + index,
                            atMs = detail.requestedAtMs,
                            title = taskCenterRequestTitle(detail, fallbackTitle),
                            abnormal = detail.isAbnormal,
                            request = detail,
                            source = detail.source,
                        ),
                    )
                }
                entry.response.isNotBlank() -> add(
                    TaskCenterLogEntry(
                        key = "response-" + index,
                        atMs = entry.responseAtMs,
                        title = "",
                        abnormal = entry.responseStatus.toIntOrNull()?.let { it !in 200..299 } == true,
                        responseStatus = entry.responseStatus,
                        source = entry.source,
                    ),
                )
            }
        }
    }
    http.infoLines.forEachIndexed { index, line ->
        add(
            TaskCenterLogEntry(
                key = "info-" + index,
                atMs = line.atMs,
                title = line.text,
                abnormal = false,
                info = true,
            ),
        )
    }
}

internal fun TaskCenterLogEntry.matches(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    val request = request
    if (request == null) {
        return title.contains(needle, ignoreCase = true) || responseStatus.contains(needle, ignoreCase = true)
    }
    return listOfNotNull(
        title,
        request.label,
        request.method,
        request.fullUrl,
        request.endpointLabel,
        request.responseStatus,
    ).any { it.contains(needle, ignoreCase = true) }
}

/** Rows grouped by the local date of their real timestamp; missing timestamps never claim a date. */
internal data class TaskCenterLogGroup(val date: LocalDate?, val entries: List<TaskCenterLogEntry>)

internal fun taskCenterLogGroups(
    entries: List<TaskCenterLogEntry>,
    filter: TaskCenterLogFilter,
    sort: TaskCenterLogSort,
    query: String,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): List<TaskCenterLogGroup> {
    val filtered = entries.filter { entry ->
        (filter == TaskCenterLogFilter.All || entry.abnormal) && entry.matches(query)
    }
    val timed = filtered.filter { it.atMs != null }.sortedBy { it.atMs }
    val ordered = if (sort == TaskCenterLogSort.Newest) timed.asReversed() else timed
    val groups = LinkedHashMap<LocalDate, MutableList<TaskCenterLogEntry>>()
    ordered.forEach { entry ->
        val date = Instant.fromEpochMilliseconds(entry.atMs ?: 0L).toLocalDateTime(zone).date
        groups.getOrPut(date) { mutableListOf() }.add(entry)
    }
    val result = groups.map { (date, rows) -> TaskCenterLogGroup(date, rows) }
    val untimed = filtered.filter { it.atMs == null }
    return if (untimed.isEmpty()) result else result + TaskCenterLogGroup(null, untimed)
}

internal fun taskCenterLogDateText(date: LocalDate, today: LocalDate, todayTemplate: String, dateTemplate: String): String {
    val text = String.format(dateTemplate, date.monthNumber, date.dayOfMonth)
    return if (date == today) String.format(todayTemplate, text) else text
}

internal fun taskCenterLogClockText(value: Long?, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    if (value == null || value <= 0L) return ""
    val time = Instant.fromEpochMilliseconds(value).toLocalDateTime(zone)
    return time.hour.toString().padStart(2, '0') + ":" +
        time.minute.toString().padStart(2, '0') + ":" +
        time.second.toString().padStart(2, '0')
}
