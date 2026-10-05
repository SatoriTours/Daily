package com.dailysatori.service.reminder

import com.dailysatori.service.diagnostics.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

data class ReminderBatchRemoteDraft(
    val sourceIndex: Int,
    val draft: ReminderDraft,
)

data class ReminderBatchDecodedResponse(
    val drafts: List<ReminderBatchRemoteDraft>,
    val failure: String? = null,
)

class ReminderBatchCodec(
    private val draftCodec: ReminderDraftCodec,
) {
    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    fun decode(response: String, zone: TimeZone): ReminderBatchDecodedResponse {
        val array = runCatching { json.parseToJsonElement(unwrapReminderAiJson(response)) as? JsonArray }.getOrNull()
            ?: return ReminderBatchDecodedResponse(emptyList(), "Batch response must be a JSON array")
        val errors = mutableListOf<String>()
        val drafts = array.mapIndexedNotNull { position, element ->
            val item = element as? JsonObject
                ?: return@mapIndexedNotNull errors.add("Batch response entry $position must be a JSON object").let { null }
            val sourceIndex = runCatching { item["source_index"]?.jsonPrimitive?.intOrNull }.getOrNull()
                ?: return@mapIndexedNotNull errors.add("Batch response entry $position is missing a valid source_index").let { null }
            val draftJson = JsonObject(item.filterKeys { it != "source_index" })
            val draft = draftCodec.decodeInterpretationResponse(draftJson.toString(), zone)
            draft.validationErrors.forEach { error ->
                val field = validationFields.firstOrNull { error.startsWith(it) || error.startsWith("缺少 $it") } ?: "other"
                DiagnosticLog.diagnostics.emit(DiagnosticCode.OPERATION_FAILED, DiagnosticSource.PARSER,
                    DiagnosticLevel.WARNING, fields = mapOf("count" to sourceIndex.toString(), "reminderField" to field))
            }
            ReminderBatchRemoteDraft(sourceIndex, draft)
        }
        return ReminderBatchDecodedResponse(drafts, errors.takeIf { it.isNotEmpty() }?.joinToString("; "))
    }

    fun encode(draft: ReminderBatchRemoteDraft): String = draftCodec.encode(draft.draft)

    private companion object {
        val validationFields = listOf("content", "start_date", "end_date", "first_reminder_time",
            "active_day_rule", "recurrence_rule", "selected_weekdays", "profile", "timezone")
    }
}
