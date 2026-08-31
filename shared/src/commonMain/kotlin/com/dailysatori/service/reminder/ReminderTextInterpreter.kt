package com.dailysatori.service.reminder

import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.plus
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

data class ReminderInterpretation(
    val draft: ReminderDraft,
    val requiresConfirmation: Boolean,
    val failure: String? = null,
)

interface ReminderInterpretationRemote {
    suspend fun interpret(text: String, now: Instant, zone: TimeZone): String
    suspend fun interpretBatch(fragments: List<ReminderInputFragment>, now: Instant, zone: TimeZone): String
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

    override suspend fun interpretBatch(fragments: List<ReminderInputFragment>, now: Instant, zone: TimeZone): String {
        val config = configRepository.getDefault() ?: error("AI is not configured")
        val input = buildJsonArray {
            fragments.forEach { fragment ->
                add(buildJsonObject {
                    put("source_index", fragment.index)
                    put("text", fragment.text)
                })
            }
        }
        return aiService.complete(
            prompt = """Convert every structured reminder below into a strict JSON array only. Each array element must include source_index copied exactly from the input plus these required fields: content, start_date (YYYY-MM-DD), end_date (YYYY-MM-DD), first_reminder_time (HH:MM), active_day_rule (daily), recurrence_rule (once|monthly:<day>|yearly:<month>:<day>:FEBRUARY_28). Current instant: $now. Timezone: ${zone.id}. Input: $input""",
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
    private val batchCache = mutableMapOf<BatchCacheKey, ReminderBatchInterpretation>()
    private val batchMutex = Mutex()
    private val batchCodec = ReminderBatchCodec(codec)

    suspend fun interpret(text: String, now: Instant, zone: TimeZone): ReminderInterpretation {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        val key = "${normalized.hashCode()}:${zone.id}:$normalized"
        cache[key]?.let { return it }
        val result = parseLocally(normalized, now, zone) ?: parseRemotely(normalized, now, zone)
        if (result.failure == null && !result.requiresConfirmation) cache[key] = result
        return result
    }

    suspend fun interpretBatch(text: String, now: Instant, zone: TimeZone): ReminderBatchInterpretation {
        val fragments = splitReminderInput(text)
        val normalized = normalize(text)
        val key = batchCacheKey(fragments, now, zone)
        return batchMutex.withLock {
            batchCache[key]?.let { return@withLock it }
            if (fragments.isEmpty()) return@withLock failedBatch(normalized, "Reminder text is empty")
            val local = fragments.associateWith { parseLocally(it.text, now, zone) }
            val unresolved = fragments.filter { local[it] == null }
            val remote = if (unresolved.isEmpty()) RemoteBatchParse(emptyMap()) else parseBatchRemotely(unresolved, now, zone)
            mergeBatch(fragments, local, remote, normalized, now, zone).also { result ->
                if (result.failure == null && result.items.none { it.interpretation.failure != null }) batchCache[key] = result
            }
        }
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

    private suspend fun parseBatchRemotely(
        fragments: List<ReminderInputFragment>,
        now: Instant,
        zone: TimeZone,
    ): RemoteBatchParse {
        val response = runCatching { remote?.interpretBatch(fragments, now, zone) ?: error("AI is not configured") }
            .getOrElse { exception ->
                return RemoteBatchParse(fragments.associate { fragment ->
                    fragment.index to failedDraft(fragment.text, now, zone, exception.message ?: "AI parsing failed")
                }, exception.message ?: "AI parsing failed")
            }
        val decoded = batchCodec.decode(response, zone)
        val expected = fragments.map { it.index }.toSet()
        val duplicateIndexes = decoded.drafts.groupingBy { it.sourceIndex }.eachCount().filterValues { it > 1 }.keys
        val invalidIndexes = decoded.drafts.map { it.sourceIndex }.filter { it !in expected }.toSet()
        val usable = decoded.drafts.filter { it.sourceIndex !in duplicateIndexes && it.sourceIndex !in invalidIndexes }
            .associateBy { it.sourceIndex }
        val results = fragments.associate { fragment ->
            val failure = when {
                fragment.index in duplicateIndexes -> "Batch response contains duplicate source_index ${fragment.index}"
                usable[fragment.index] == null -> "Batch response is missing source_index ${fragment.index}"
                else -> null
            }
            fragment.index to (failure?.let { failedDraft(fragment.text, now, zone, it) }
                ?: ReminderInterpretation(usable.getValue(fragment.index).draft, usable.getValue(fragment.index).draft.validationErrors.isNotEmpty()))
        }
        val failure = listOfNotNull(
            decoded.failure,
            invalidIndexes.takeIf { it.isNotEmpty() }?.let { "Batch response contains out-of-range source_index ${it.sorted().joinToString()}" },
        ).joinToString("; ").takeIf { it.isNotEmpty() }
        return RemoteBatchParse(results, failure)
    }

    private fun mergeBatch(
        fragments: List<ReminderInputFragment>,
        local: Map<ReminderInputFragment, ReminderInterpretation?>,
        remote: RemoteBatchParse,
        normalized: String,
        now: Instant,
        zone: TimeZone,
    ): ReminderBatchInterpretation {
        val batchId = "reminder_batch_${batchCacheKey(fragments, now, zone).hashCode()}"
        return ReminderBatchInterpretation(
            batchId = batchId,
            normalizedInput = normalized,
            items = fragments.map { fragment ->
                ReminderBatchItem(
                    id = "${batchId}_${fragment.index}",
                    sourceIndex = fragment.index,
                    sourceText = fragment.text,
                    interpretation = local[fragment] ?: remote.results[fragment.index]
                        ?: failedDraft(fragment.text, now, zone, "Batch response is missing source_index ${fragment.index}"),
                )
            },
            failure = remote.failure,
        )
    }

    private fun failedBatch(normalized: String, failure: String) = ReminderBatchInterpretation(
        batchId = "reminder_batch_${normalized.hashCode()}",
        normalizedInput = normalized,
        items = emptyList(),
        failure = failure,
    )

    private fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), " ")

    private fun batchCacheKey(fragments: List<ReminderInputFragment>, now: Instant, zone: TimeZone) =
        BatchCacheKey(
            fragments = fragments.map { it.index to normalize(it.text) },
            localDate = now.toLocalDateTime(zone).date,
            zoneId = zone.id,
        )

    private data class BatchCacheKey(
        val fragments: List<Pair<Int, String>>,
        val localDate: LocalDate,
        val zoneId: String,
    )

    private data class RemoteBatchParse(
        val results: Map<Int, ReminderInterpretation>,
        val failure: String? = null,
    )

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
