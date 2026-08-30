package com.dailysatori.service.reminder

import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ReminderDraftCodec(
    private val now: () -> Instant = { Clock.System.now() },
    private val currentTimeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    fun create(arguments: String, zone: TimeZone = currentTimeZone()): ReminderDraft {
        val errors = mutableListOf<String>()
        val args = parseArguments(arguments, errors)
        val content = boundedContent(args.string("content"), errors)
        val startDate = absoluteDate(args.string("start_date"), "start_date", errors)
        val endDate = absoluteDate(args.string("end_date"), "end_date", errors)
        val firstTime = absoluteTime(args.string("first_reminder_time"), errors)
        val rule = activeDayRule(args, errors)
        val recurrence = recurrence(args.string("recurrence_rule"), errors)
        validateRange(startDate, endDate, zone, errors)
        args.string("timezone")?.takeIf { it != zone.id }?.let { errors += "timezone 必须使用设备当前时区" }
        return ReminderDraft(
            id = "reminder_draft_${now().toEpochMilliseconds()}", content = content,
            startDate = startDate, endDate = endDate, firstReminderTime = firstTime,
            activeDayRule = rule, profile = profile(args.string("profile"), errors), recurrence = recurrence,
            timeZone = zone, validationErrors = errors.distinct(),
        )
    }

    /** Decodes AI output through the same strict validation used by reminder tools. */
    fun decodeInterpretationResponse(arguments: String, zone: TimeZone = currentTimeZone()): ReminderDraft = create(arguments, zone)

    fun encode(draft: ReminderDraft): String = buildJsonObject {
        put("draft_id", draft.id)
        put("content", draft.content)
        put("start_date", draft.startDate?.toString() ?: "")
        put("end_date", draft.endDate?.toString() ?: "")
        put("first_reminder_time", draft.firstReminderTime?.toString() ?: "")
        put("active_day_rule", draft.activeDayRule.encode())
        put("recurrence_rule", draft.recurrence.encode())
        if (draft.activeDayRule is ReminderActiveDayRule.SelectedWeekdays) {
            put("selected_weekdays", JsonArray(draft.activeDayRule.days.sortedBy { it.ordinal }.map { JsonPrimitive(it.name) }))
        }
        draft.profile?.let { put("profile", it.kind.name.lowercase()) }
        put("timezone", draft.timeZone.id)
        put("validation_errors", JsonArray(draft.validationErrors.map(::JsonPrimitive)))
    }.toString()

    private fun parseArguments(value: String, errors: MutableList<String>): JsonObject = try {
        val objectValue = json.parseToJsonElement(value) as? JsonObject
        requireNotNull(objectValue) { "参数必须是 JSON 对象" }.also { args ->
            (args.keys - allowedFields).forEach { errors += "不支持字段: $it" }
        }
    } catch (_: Exception) {
        errors += "参数必须是严格 JSON 对象"
        JsonObject(emptyMap())
    }

    private fun boundedContent(value: String?, errors: MutableList<String>): String = when {
        value.isNullOrBlank() -> { errors += "缺少 content"; "" }
        value.trim().length > MAX_CONTENT_LENGTH -> { errors += "content 最多 $MAX_CONTENT_LENGTH 个字符"; value.trim().take(MAX_CONTENT_LENGTH) }
        else -> value.trim()
    }

    private fun absoluteDate(value: String?, field: String, errors: MutableList<String>): LocalDate? = try {
        require(!value.isNullOrBlank())
        LocalDate.parse(value)
    } catch (_: Exception) {
        errors += "${field} 必须是绝对日期 YYYY-MM-DD"
        null
    }

    private fun absoluteTime(value: String?, errors: MutableList<String>): LocalTime? = try {
        require(value != null && HH_MM.matches(value))
        LocalTime.parse(value)
    } catch (_: Exception) {
        errors += "first_reminder_time 必须是本地时间 HH:MM"
        null
    }

    private fun validateRange(start: LocalDate?, end: LocalDate?, zone: TimeZone, errors: MutableList<String>) {
        if (start != null && end != null && end < start) errors += "end_date 不能早于 start_date"
        val today = now().toLocalDateTime(zone).date
        if (end != null && end < today) errors += "end_date 已过期"
    }

    private fun activeDayRule(args: JsonObject, errors: MutableList<String>): ReminderActiveDayRule = when (args.string("active_day_rule")) {
        "daily" -> ReminderActiveDayRule.Daily
        "weekdays" -> ReminderActiveDayRule.Weekdays
        "consecutive_date_range" -> ReminderActiveDayRule.ConsecutiveDateRange
        "selected_weekdays" -> selectedWeekdays(args, errors)
        else -> { errors += "active_day_rule 必须是 daily、weekdays、selected_weekdays 或 consecutive_date_range"; ReminderActiveDayRule.Daily }
    }

    private fun selectedWeekdays(args: JsonObject, errors: MutableList<String>): ReminderActiveDayRule {
        val values = runCatching { args["selected_weekdays"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull().orEmpty()
        val days = values.mapNotNull { value -> dayOfWeek[value.uppercase()] }.toSet()
        if (values.isEmpty() || days.size != values.size) errors += "selected_weekdays 必须是有效星期列表"
        return ReminderActiveDayRule.SelectedWeekdays(days)
    }

    private fun recurrence(value: String?, errors: MutableList<String>): ReminderRecurrence = runCatching {
        when {
            value == null || value == "once" -> ReminderRecurrence.Once
            value.startsWith("monthly:") -> ReminderRecurrence.Monthly(value.removePrefix("monthly:").toInt())
            value.startsWith("yearly:") -> value.split(':').let { parts ->
                require(parts.size == 4)
                ReminderRecurrence.Yearly(parts[1].toInt(), parts[2].toInt(), LeapDayPolicy.valueOf(parts[3]))
            }
            else -> error("Unknown recurrence rule")
        }
    }.getOrElse {
        errors += "recurrence_rule 必须是 once、monthly:<day> 或 yearly:<month>:<day>:<policy>"
        ReminderRecurrence.Once
    }

    private fun profile(value: String?, errors: MutableList<String>): ReminderProfileSnapshot? = when (value) {
        null, "" -> null
        "strong" -> ReminderProfileSnapshot.strong()
        "standard" -> ReminderProfileSnapshot.standard()
        "gentle" -> ReminderProfileSnapshot.gentle()
        else -> { errors += "profile 必须是 strong、standard 或 gentle"; null }
    }

    private fun JsonObject.string(name: String): String? = runCatching {
        this[name]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun ReminderActiveDayRule.encode(): String = when (this) {
        ReminderActiveDayRule.Daily -> "daily"
        ReminderActiveDayRule.Weekdays -> "weekdays"
        ReminderActiveDayRule.ConsecutiveDateRange -> "consecutive_date_range"
        is ReminderActiveDayRule.SelectedWeekdays -> "selected_weekdays"
    }

    private fun ReminderRecurrence.encode(): String = when (this) {
        ReminderRecurrence.Once -> "once"
        is ReminderRecurrence.Monthly -> "monthly:$dayOfMonth"
        is ReminderRecurrence.Yearly -> "yearly:$month:$dayOfMonth:${leapDayPolicy.name}"
    }

    private companion object {
        const val MAX_CONTENT_LENGTH = 500
        val HH_MM = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")
        val allowedFields = setOf("draft_id", "content", "start_date", "end_date", "first_reminder_time", "active_day_rule", "recurrence_rule", "selected_weekdays", "profile", "timezone", "validation_errors")
        val dayOfWeek = DayOfWeek.entries.associateBy { it.name }
    }
}
