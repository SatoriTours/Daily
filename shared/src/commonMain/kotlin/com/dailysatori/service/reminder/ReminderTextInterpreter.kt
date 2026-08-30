package com.dailysatori.service.reminder

import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiService
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ReminderInterpretation(
    val draft: ReminderDraft,
    val requiresConfirmation: Boolean,
    val failure: String? = null,
)

interface ReminderInterpretationRemote {
    suspend fun interpret(text: String): String
}

class ReminderAiInterpretationRemote(
    private val aiService: AiService,
    private val configRepository: AIConfigRepository,
) : ReminderInterpretationRemote {
    override suspend fun interpret(text: String): String {
        val config = configRepository.getDefault() ?: error("AI is not configured")
        return aiService.complete(
            prompt = """Convert this reminder into strict JSON only. Required fields: content, start_date (YYYY-MM-DD), end_date (YYYY-MM-DD), first_reminder_time (HH:MM), active_day_rule (daily), recurrence_rule (once|monthly:<day>|yearly:<month>:<day>:FEBRUARY_28). Text: $text""",
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
        val match = LOCAL_PATTERN.matchEntire(text) ?: return null
        val month = match.groups[1]?.value?.toIntOrNull() ?: return null
        val day = match.groups[2]?.value?.toIntOrNull() ?: return null
        val rawHour = match.groups[3]?.value?.toIntOrNull() ?: return null
        val isPm = text.contains("晚上") || text.contains("下午")
        val hour = if (isPm && rawHour in 1..11) rawHour + 12 else rawHour
        val minute = match.groups[4]?.value?.toIntOrNull() ?: 0
        val content = match.groups[5]?.value?.trim().orEmpty()
        if (content.isBlank() || hour !in 0..23 || minute !in 0..59) return null
        val year = now.toLocalDateTime(zone).year
        val date = runCatching { LocalDate(year, month, day) }.getOrNull() ?: return null
        val draft = codec.decodeInterpretationResponse(
            buildJsonObject {
                put("content", content)
                put("start_date", date.toString())
                put("end_date", date.toString())
                put("first_reminder_time", hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0'))
                put("active_day_rule", "daily")
                put("recurrence_rule", "yearly:$month:$day:FEBRUARY_28")
            }.toString(),
        )
        return ReminderInterpretation(draft, draft.validationErrors.isNotEmpty())
    }

    private suspend fun parseRemotely(text: String, now: Instant, zone: TimeZone): ReminderInterpretation {
        if (text.isBlank()) return failedDraft(text, now, zone, "Reminder text is empty")
        val response = runCatching { remote?.interpret(text) ?: error("AI is not configured") }
            .getOrElse { return failedDraft(text, now, zone, it.message ?: "AI parsing failed") }
        val draft = codec.decodeInterpretationResponse(response)
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
            }.toString(),
        )
        return ReminderInterpretation(draft, requiresConfirmation = true, failure = failure)
    }

    private companion object {
        val LOCAL_PATTERN = Regex("""每年\s*(\d{1,2})月(\d{1,2})日(?:晚上|下午)?(\d{1,2})点(?:(\d{1,2})分?)?提醒我(.+)""")
    }
}
