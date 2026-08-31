package com.dailysatori.service.reminder

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

class ReminderBatchCodec(
    private val draftCodec: ReminderDraftCodec,
) {
    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    fun decode(response: String, zone: TimeZone): List<ReminderBatchRemoteDraft> {
        val array = json.parseToJsonElement(response) as? JsonArray
            ?: error("Batch response must be a JSON array")
        return array.map { element ->
            val item = element as? JsonObject ?: error("Batch response entries must be JSON objects")
            val sourceIndex = item["source_index"]?.jsonPrimitive?.intOrNull
                ?: error("Batch response entry is missing a valid source_index")
            val draftJson = JsonObject(item.filterKeys { it != "source_index" })
            ReminderBatchRemoteDraft(sourceIndex, draftCodec.decodeInterpretationResponse(draftJson.toString(), zone))
        }
    }
}
