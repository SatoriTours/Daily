package com.dailysatori.service.reminder

import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiService
import kotlinx.datetime.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.plus
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ReminderInterpretation(
    val draft: ReminderDraft,
    val requiresConfirmation: Boolean,
    val failure: String? = null,
)

interface ReminderInterpretationRemote {
    suspend fun interpret(text: String, now: Instant, zone: TimeZone): String
}

class ReminderAiInterpretationRemote(
    private val aiService: AiService,
    private val configRepository: AIConfigRepository,
) : ReminderInterpretationRemote {
    override suspend fun interpret(text: String, now: Instant, zone: TimeZone): String {
        val config = configRepository.getDefault() ?: error("AI is not configured")
        return aiService.complete(
            prompt = """Convert this reminder into strict JSON only. Required fields: content, start_date (YYYY-MM-DD), end_date (YYYY-MM-DD), first_reminder_time (HH:MM), active_day_rule (daily), recurrence_rule (once|monthly:<day>|yearly:<month>:<day>:FEBRUARY_28). Current instant: $now. Timezone: ${zone.id}. Text: $text""",
            apiAddress = config.api_address,
            apiToken = config.api_token,
            modelName = config.model_name,
            provider = config.provider,
            temperature = 0.0,
        )
    }
}

class ReminderTextInterpreter(
    private val codec: ReminderDraftCodec,
    private val remote: ReminderInterpretationRemote? = null,
) {
    private val cache = mutableMapOf<String, ReminderInterpretation>()

    suspend fun interpret(text: String, now: Instant, zone: TimeZone): ReminderInterpretation {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        val key = "${normalized.hashCode()}:${zone.id}:$normalized"
        cache[key]?.let { return it }
        val result = parseLocally(normalized, now, zone) ?: parseRemotely(normalized, now, zone)
        if (result.failure == null && !result.requiresConfirmation) cache[key] = result
        return result
    }

    private fun parseLocally(text: String, now: Instant, zone: TimeZone): ReminderInterpretation? {
        val yearly = YEARLY_PATTERN.matchEntire(text)
        val monthly = MONTHLY_PATTERN.matchEntire(text)
        val dated = DATE_PATTERN.matchEntire(text)
        if (yearly == null && monthly == null && dated == null) return null
        val match = requireNotNull(yearly ?: monthly ?: dated)
        val isYearly = yearly != null
        val isMonthly = monthly != null
        val month = if (isMonthly) now.toLocalDateTime(zone).monthNumber else match.groups[1]?.value?.toIntOrNull()
        val day = if (isMonthly) match.groups[1]?.value?.toIntOrNull() else match.groups[2]?.value?.toIntOrNull()
        val rawHour = match.groups[if (isMonthly) 2 else 3]?.value?.toIntOrNull()
        val isPm = text.contains("晚上") || text.contains("下午")
        val hour = rawHour?.let { if (isPm && it in 1..11) it + 12 else it }
        val minute = match.groups[if (isMonthly) 3 else 4]?.value?.toIntOrNull() ?: 0
        val content = match.groups[if (isMonthly) 4 else 5]?.value?.trim().orEmpty()
        if (content.isBlank() || hour != null && hour !in 0..23 || minute !in 0..59) return null
        val year = now.toLocalDateTime(zone).year
        val localToday = now.toLocalDateTime(zone).date
        val requestedMonth = requireNotNull(month)
        val requestedDay = requireNotNull(day)
        val leapFallback = isYearly && requestedMonth == 2 && requestedDay == 29 && !isLeapYear(year)
        val initialDate = when {
            isMonthly -> nextValidMonthlyDate(year, requestedMonth, requestedDay, localToday) ?: return null
            leapFallback -> LocalDate(year, 2, 28)
            else -> runCatching { LocalDate(year, requestedMonth, requestedDay) }.getOrNull() ?: return null
        }
        val date = when {
            isMonthly && initialDate < localToday -> initialDate.plus(DatePeriod(months = 1))
            !isYearly && initialDate < localToday -> initialDate.plus(DatePeriod(years = 1))
            else -> initialDate
        }
        val draft = codec.decodeInterpretationResponse(
            buildJsonObject {
                put("content", content)
                put("start_date", date.toString())
                put("end_date", date.toString())
                put("first_reminder_time", hour?.toString()?.padStart(2, '0')?.plus(":" + minute.toString().padStart(2, '0')) ?: "")
                put("active_day_rule", "daily")
                put("recurrence_rule", when {
                    isYearly -> "yearly:$month:$day:FEBRUARY_28"
                    isMonthly -> "monthly:$day"
                    else -> "once"
                })
            }.toString(),
            zone,
        )
        return ReminderInterpretation(draft, draft.validationErrors.isNotEmpty() || leapFallback)
    }

    private suspend fun parseRemotely(text: String, now: Instant, zone: TimeZone): ReminderInterpretation {
        if (text.isBlank()) return failedDraft(text, now, zone, "Reminder text is empty")
        val response = runCatching { remote?.interpret(text, now, zone) ?: error("AI is not configured") }
            .getOrElse { return failedDraft(text, now, zone, it.message ?: "AI parsing failed") }
        val draft = codec.decodeInterpretationResponse(response, zone)
        val requiresConfirmation = draft.validationErrors.isNotEmpty()
        return ReminderInterpretation(
            draft = if (requiresConfirmation && draft.content.isBlank()) draft.copy(content = text) else draft,
            requiresConfirmation = requiresConfirmation,
        )
    }

    private fun failedDraft(text: String, now: Instant, zone: TimeZone, failure: String): ReminderInterpretation {
        val draft = codec.decodeInterpretationResponse(
            buildJsonObject {
                put("content", text)
                put("start_date", "")
                put("end_date", "")
                put("first_reminder_time", "")
                put("active_day_rule", "daily")
                put("recurrence_rule", "once")
                put("timezone", zone.id)
            }.toString(), zone,
        )
        return ReminderInterpretation(draft, requiresConfirmation = true, failure = failure)
    }

    private fun nextValidMonthlyDate(year: Int, month: Int, day: Int, today: LocalDate): LocalDate? {
        var cursor = LocalDate(year, month, 1)
        repeat(24) {
            val candidate = runCatching { LocalDate(cursor.year, cursor.monthNumber, day) }.getOrNull()
            if (candidate != null && candidate >= today) return candidate
            cursor = cursor.plus(DatePeriod(months = 1))
        }
        return null
    }

    private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    private companion object {
        val YEARLY_PATTERN = Regex("""每年\s*(\d{1,2})月(\d{1,2})日(?:(?:晚上|下午)?(\d{1,2})点(?:(\d{1,2})分?)?)?提醒我(.+)""")
        val MONTHLY_PATTERN = Regex("""每月\s*(\d{1,2})日(?:(?:晚上|下午)?(\d{1,2})点(?:(\d{1,2})分?)?)?提醒我(.+)""")
        val DATE_PATTERN = Regex("""(\d{1,2})月(\d{1,2})日(?:(?:晚上|下午)?(\d{1,2})点(?:(\d{1,2})分?)?)?提醒我(.+)""")
    }
}
