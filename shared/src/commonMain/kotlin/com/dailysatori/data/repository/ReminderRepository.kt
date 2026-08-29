package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderActiveDayRule
import com.dailysatori.service.reminder.ReminderDraft
import com.dailysatori.service.reminder.ReminderProfileKind
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderStatus
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Reminder_event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class ReminderState(val dismissalCount: Int, val stateDate: LocalDate?)

data class ReminderEdit(
    val expectedVersion: Long,
    val content: String? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val firstReminderTime: LocalTime? = null,
    val activeDayRule: ReminderActiveDayRule? = null,
    val profile: ReminderProfileSnapshot? = null,
)

class ReminderRepository(
    private val db: DailySatoriDatabase,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    private val q get() = db.dailySatoriQueries

    fun createConfirmed(draft: ReminderDraft, profileSnapshot: ReminderProfileSnapshot): Reminder {
        val startDate = requireNotNull(draft.startDate) { "Confirmed reminder requires a start date" }
        val endDate = requireNotNull(draft.endDate) { "Confirmed reminder requires an end date" }
        val firstTime = requireNotNull(draft.firstReminderTime) { "Confirmed reminder requires a first time" }
        require(draft.content.isNotBlank() && draft.content.length <= MAX_CONTENT_LENGTH)
        require(endDate >= startDate)
        val now = Clock.System.now().toEpochMilliseconds()
        val profileJson = profileSnapshot.toBoundedJson()
        q.insertReminder(
            id = draft.id,
            content = draft.content,
            status = ReminderStatus.ACTIVE.name,
            start_date = startDate.toString(),
            end_date = endDate.toString(),
            first_reminder_time = firstTime.toString(),
            active_day_rule = draft.activeDayRule.encode(),
            time_zone_id = timeZone.id,
            profile_json = profileJson,
            version = 0,
            state_date = null,
            dismissal_count = 0,
            last_notified_at = null,
            last_dismissed_at = null,
            completed_at = null,
            next_occurrence_at = null,
            created_at = now,
            updated_at = now,
        )
        recordEvent(draft.id, "confirmed", Instant.fromEpochMilliseconds(now))
        return requireNotNull(get(draft.id))
    }

    fun get(id: String): Reminder? = q.selectReminderById(id).executeAsOneOrNull()?.toReminder()

    fun observeAll(): Flow<List<Reminder>> = q.selectAllReminders().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { it.toReminder() } }

    fun activeAt(now: Instant): List<Reminder> = q.selectActiveRemindersAt(now.toLocalDateTime(timeZone).date.toString())
        .executeAsList().map { it.toReminder() }.filter { it.activeDayRule.isActiveOn(now.toLocalDateTime(it.timeZone).date) }

    fun state(id: String): ReminderState? = q.selectReminderById(id).executeAsOneOrNull()?.let {
        ReminderState(it.dismissal_count.toInt(), it.state_date?.let(LocalDate::parse))
    }

    fun markDelivered(id: String, expectedVersion: Long, at: Instant): Boolean = transition(id, expectedVersion, at) { row ->
        if (row.status !in deliverableStatuses) null else Lifecycle(
            status = ReminderStatus.NOTIFIED,
            stateDate = row.state_date?.let(LocalDate::parse),
            dismissalCount = row.dismissal_count.toInt(),
            lastNotifiedAt = at.toEpochMilliseconds(),
            lastDismissedAt = row.last_dismissed_at,
            completedAt = row.completed_at,
            nextOccurrenceAt = row.next_occurrence_at,
            eventType = "delivered",
        )
    }

    fun markDismissed(id: String, expectedVersion: Long, at: Instant): Boolean = transition(id, expectedVersion, at) { row ->
        if (row.status !in deliverableStatuses) return@transition null
        val date = at.toLocalDateTime(TimeZone.of(row.time_zone_id)).date
        val count = if (row.state_date == date.toString()) row.dismissal_count.toInt() + 1 else 1
        Lifecycle(ReminderStatus.DISMISSED, date, count, row.last_notified_at, at.toEpochMilliseconds(), row.completed_at, row.next_occurrence_at, "dismissed")
    }

    fun complete(id: String, at: Instant = Clock.System.now()): Boolean = terminalTransition(id, ReminderStatus.COMPLETED, at, "completed")

    fun expire(id: String, at: Instant = Clock.System.now()): Boolean = terminalTransition(id, ReminderStatus.EXPIRED, at, "expired")

    fun pause(id: String, at: Instant = Clock.System.now()): Boolean = simpleTransition(id, ReminderStatus.PAUSED, at, "paused")

    fun resume(id: String, at: Instant = Clock.System.now()): Boolean = simpleTransition(id, ReminderStatus.ACTIVE, at, "resumed")

    fun update(id: String, edit: ReminderEdit, at: Instant = Clock.System.now()): Boolean = q.transactionWithResult {
        val row = q.selectReminderById(id).executeAsOneOrNull() ?: return@transactionWithResult false
        if (row.version != edit.expectedVersion || row.status in terminalStatuses) return@transactionWithResult false
        val start = edit.startDate ?: LocalDate.parse(row.start_date)
        val end = edit.endDate ?: LocalDate.parse(row.end_date)
        if (end < start) return@transactionWithResult false
        if (q.updateReminderEditableIfVersion(edit.content ?: row.content, start.toString(), end.toString(), (edit.firstReminderTime ?: LocalTime.parse(row.first_reminder_time)).toString(), (edit.activeDayRule ?: row.active_day_rule.decode()).encode(), (edit.profile ?: row.profile_json.toProfile()).toBoundedJson(), row.version + 1, at.toEpochMilliseconds(), id, row.version).executeAsOneOrNull() == null) return@transactionWithResult false
        recordEvent(id, "edited", at)
        true
    }

    fun recordEvent(id: String, eventType: String, at: Instant, metadata: Map<String, String> = emptyMap(), scheduledAt: Instant? = null) {
        require(eventType.length in 1..64)
        q.transaction {
            insertBoundedEvent(id, eventType, at, emptyMap(), scheduledAt)
        }
    }

    fun events(id: String): List<Reminder_event> = q.selectReminderEvents(id, MAX_EVENT_HISTORY.toLong()).executeAsList()

    private fun transition(id: String, expectedVersion: Long, at: Instant, next: (com.dailysatori.shared.db.Reminder) -> Lifecycle?): Boolean = q.transactionWithResult {
        val row = q.selectReminderById(id).executeAsOneOrNull() ?: return@transactionWithResult false
        if (row.version != expectedVersion) return@transactionWithResult false
        val lifecycle = next(row) ?: return@transactionWithResult false
        saveLifecycle(row, lifecycle, at)
    }

    private fun terminalTransition(id: String, target: ReminderStatus, at: Instant, eventType: String): Boolean {
        repeat(MAX_CAS_RETRIES) {
            val result = q.transactionWithResult {
                val row = q.selectReminderById(id).executeAsOneOrNull() ?: return@transactionWithResult TerminalResult.TERMINAL
                if (row.status in terminalStatuses) return@transactionWithResult TerminalResult.TERMINAL
                if (saveLifecycle(row, Lifecycle(target, row.state_date?.let(LocalDate::parse), row.dismissal_count.toInt(), row.last_notified_at, row.last_dismissed_at, at.toEpochMilliseconds(), null, eventType), at)) TerminalResult.UPDATED else TerminalResult.RETRY
            }
            if (result != TerminalResult.RETRY) return result == TerminalResult.UPDATED
        }
        return false
    }

    private fun simpleTransition(id: String, target: ReminderStatus, at: Instant, eventType: String): Boolean = q.transactionWithResult {
        val row = q.selectReminderById(id).executeAsOneOrNull() ?: return@transactionWithResult false
        if (row.status in terminalStatuses || row.status == target.name) return@transactionWithResult false
        saveLifecycle(row, Lifecycle(target, row.state_date?.let(LocalDate::parse), row.dismissal_count.toInt(), row.last_notified_at, row.last_dismissed_at, row.completed_at, if (target == ReminderStatus.PAUSED) null else row.next_occurrence_at, eventType), at)
    }

    private fun saveLifecycle(row: com.dailysatori.shared.db.Reminder, state: Lifecycle, at: Instant): Boolean {
        if (q.updateReminderLifecycleIfVersion(state.status.name, row.version + 1, state.stateDate?.toString(), state.dismissalCount.toLong(), state.lastNotifiedAt, state.lastDismissedAt, state.completedAt, state.nextOccurrenceAt, at.toEpochMilliseconds(), row.id, row.version).executeAsOneOrNull() == null) return false
        insertBoundedEvent(row.id, state.eventType, at, emptyMap(), null)
        return true
    }

    private fun insertBoundedEvent(id: String, eventType: String, at: Instant, metadata: Map<String, String>, scheduledAt: Instant?) {
        q.insertReminderEvent(id, eventType, scheduledAt?.toEpochMilliseconds(), at.toEpochMilliseconds(), metadata.toJson())
        q.deleteReminderEventsAfterLimit(id, id, MAX_EVENT_HISTORY.toLong())
    }

    private fun com.dailysatori.shared.db.Reminder.toReminder() = Reminder(id, content, LocalDate.parse(start_date), LocalDate.parse(end_date), LocalTime.parse(first_reminder_time), active_day_rule.decode(), profile_json.toProfile(), ReminderStatus.valueOf(status), TimeZone.of(time_zone_id), version)

    private data class Lifecycle(val status: ReminderStatus, val stateDate: LocalDate?, val dismissalCount: Int, val lastNotifiedAt: Long?, val lastDismissedAt: Long?, val completedAt: Long?, val nextOccurrenceAt: Long?, val eventType: String)

    companion object {
        const val MAX_EVENT_HISTORY = 50
        private const val MAX_CONTENT_LENGTH = 2_000
        private const val MAX_CAS_RETRIES = 3
        private val terminalStatuses = setOf(ReminderStatus.COMPLETED.name, ReminderStatus.EXPIRED.name)
        private val deliverableStatuses = setOf(ReminderStatus.ACTIVE.name, ReminderStatus.NOTIFIED.name, ReminderStatus.DISMISSED.name)
    }

    private enum class TerminalResult { UPDATED, TERMINAL, RETRY }
}

private fun ReminderActiveDayRule.encode(): String = when (this) {
    ReminderActiveDayRule.Daily -> "daily"
    ReminderActiveDayRule.Weekdays -> "weekdays"
    ReminderActiveDayRule.ConsecutiveDateRange -> "range"
    is ReminderActiveDayRule.SelectedWeekdays -> "selected:" + days.sortedBy { it.ordinal }.joinToString(",") { it.name }
}

private fun String.decode(): ReminderActiveDayRule = when {
    this == "daily" -> ReminderActiveDayRule.Daily
    this == "weekdays" -> ReminderActiveDayRule.Weekdays
    this == "range" -> ReminderActiveDayRule.ConsecutiveDateRange
    startsWith("selected:") -> ReminderActiveDayRule.SelectedWeekdays(removePrefix("selected:").split(',').filter { it.isNotBlank() }.map(DayOfWeek::valueOf).toSet())
    else -> error("Unknown reminder active-day rule")
}

private fun ReminderActiveDayRule.isActiveOn(date: LocalDate): Boolean = when (this) {
    ReminderActiveDayRule.Daily, ReminderActiveDayRule.ConsecutiveDateRange -> true
    ReminderActiveDayRule.Weekdays -> date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    is ReminderActiveDayRule.SelectedWeekdays -> date.dayOfWeek in days
}

private fun ReminderProfileSnapshot.toBoundedJson(): String {
    require(daytimeDismissalBackoffMinutes.size <= 8 && daytimeDismissalBackoffMinutes.all { it in 1..1_440 })
    require(eveningTimes.size <= 8 && workDays.size <= 7)
    val json = buildJsonObject {
        put("kind", kind.name); put("eveningStart", eveningStart.toString()); put("eveningInterval", eveningIntervalMinutes); put("dailyCutoff", dailyCutoff.toString()); put("sound", soundEnabled); put("vibration", vibrationEnabled); put("sleepStart", sleepStart.toString()); put("sleepEnd", sleepEnd.toString()); put("workStart", workStart.toString()); put("workEnd", workEnd.toString())
        putJsonArray("backoff") { daytimeDismissalBackoffMinutes.forEach { add(JsonPrimitive(it)) } }
        putJsonArray("eveningTimes") { eveningTimes.sorted().forEach { add(JsonPrimitive(it.toString())) } }
        putJsonArray("workDays") { workDays.sortedBy { it.ordinal }.forEach { add(JsonPrimitive(it.name)) } }
    }
    return json.toString().also { require(it.length <= 4_096) }
}

private fun String.toProfile(): ReminderProfileSnapshot {
    val root = Json.parseToJsonElement(this).jsonObject
    fun string(name: String) = requireNotNull(root.getValue(name).jsonPrimitive.contentOrNull)
    fun strings(name: String): JsonArray = root.getValue(name).jsonArray
    return ReminderProfileSnapshot(ReminderProfileKind.valueOf(string("kind")), strings("backoff").map { requireNotNull(it.jsonPrimitive.contentOrNull).toInt() }, LocalTime.parse(string("eveningStart")), root["eveningInterval"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(), strings("eveningTimes").map { LocalTime.parse(requireNotNull(it.jsonPrimitive.contentOrNull)) }.toSet(), LocalTime.parse(string("dailyCutoff")), string("sound").toBoolean(), string("vibration").toBoolean(), LocalTime.parse(string("sleepStart")), LocalTime.parse(string("sleepEnd")), strings("workDays").map { DayOfWeek.valueOf(requireNotNull(it.jsonPrimitive.contentOrNull)) }.toSet(), LocalTime.parse(string("workStart")), LocalTime.parse(string("workEnd")))
}

private fun Map<String, String>.toJson(): String = buildJsonObject { forEach { (key, value) -> put(key, value) } }.toString()
