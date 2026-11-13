package com.dailysatori.ui.feature.settings.taskcenter

import com.dailysatori.service.asynctask.AsyncTaskType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * One execution round reconstructed from the lifecycle log. Rounds are delimited only by real
 * `TASK started` events: the log carries no round number, so nothing here invents one.
 */
internal data class TaskCenterLifecycleRound(
    val startMs: Long?,
    val endMs: Long?,
    val events: List<TaskCenterLifecycleEvent>,
)

internal fun taskCenterLifecycleRounds(events: List<TaskCenterLifecycleEvent>): List<TaskCenterLifecycleRound> {
    if (events.isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<TaskCenterLifecycleEvent>>()
    events.forEach { event ->
        if (groups.isEmpty() || event.kind == TaskCenterLifecycleEventKind.Started) {
            groups += mutableListOf(event)
        } else {
            groups.last() += event
        }
    }
    return groups.map { group ->
        TaskCenterLifecycleRound(
            startMs = group.firstNotNullOfOrNull { it.atMs },
            endMs = group.lastOrNull { it.atMs != null }?.atMs,
            events = group,
        )
    }
}

/** Real outcome counters that only exist for AI organization tasks; every other type returns null. */
internal data class TaskCenterOutcomeCounts(val succeeded: Long, val failed: Long, val deferred: Long)

internal fun taskCenterOutcomeCounts(type: String, checkpointJson: String): TaskCenterOutcomeCounts? {
    if (type != AsyncTaskType.external_favorite_organize.name) return null
    val processed = taskCenterCheckpointLong(checkpointJson, "processed") ?: return null
    val failed = taskCenterCheckpointLong(checkpointJson, "failed") ?: 0L
    val deferred = taskCenterCheckpointLong(checkpointJson, "deferred") ?: 0L
    if (failed < 0L || deferred < 0L || processed < failed + deferred) return null
    return TaskCenterOutcomeCounts(processed - failed - deferred, failed, deferred)
}

/** Model name comes only from a captured request body; an absent or unreadable body is not guessed. */
internal fun taskCenterRequestModel(detail: TaskCenterRequestDetail): String? {
    if (!detail.requestBody.collected) return null
    return runCatching {
        Json.parseToJsonElement(detail.requestBody.text).jsonObject["model"]?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

/** Redacted full record for the request detail screen; copying it never carries credentials. */
internal fun taskCenterRequestFullJson(detail: TaskCenterRequestDetail): String = buildJsonObject {
    put("method", detail.method)
    put("url", taskCenterRedactSensitive(detail.fullUrl))
    put("requestHeaders", buildJsonObject {
        detail.requestHeaders.forEach { (key, value) ->
            put(taskCenterRedactSensitive(key), taskCenterRedactSensitive(value))
        }
    })
    putBody("requestBody", detail.requestBody)
    put("status", detail.responseStatus.toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull)
    put("responseHeaders", buildJsonObject {
        detail.responseHeaders.forEach { (key, value) ->
            put(taskCenterRedactSensitive(key), taskCenterRedactSensitive(value))
        }
    })
    putBody("responseBody", detail.responseBody)
    put("durationMs", detail.durationMs?.let { JsonPrimitive(it) } ?: JsonNull)
    put("incomplete", JsonPrimitive(detail.responseIncomplete))
    put("endpoint", detail.endpointLabel)
    put("attempt", detail.attemptNumber.takeIf { detail.attemptKnown }?.let { JsonPrimitive(it) } ?: JsonNull)
    put("requestedAtMs", detail.requestedAtMs?.let { JsonPrimitive(it) } ?: JsonNull)
    put("respondedAtMs", detail.respondedAtMs?.let { JsonPrimitive(it) } ?: JsonNull)
    put("associationUnknown", JsonPrimitive(detail.associationUnknown))
    put("source", taskCenterSourceKey(detail.source))
    put("fromCapture", JsonPrimitive(detail.fromCapture))
}.let { body -> taskCenterFullJsonFormat.encodeToString(JsonObject.serializer(), body) }

private val taskCenterFullJsonFormat = Json { prettyPrint = true }

private fun JsonObjectBuilder.putBody(key: String, field: TaskCenterLogField) {
    if (!field.collected) {
        put(key + "State", field.state.ifBlank { "NOT_CAPTURED" })
        return
    }
    val parsed = runCatching { Json.parseToJsonElement(field.text) }.getOrNull()
    if (parsed != null) put(key, parsed) else put(key, taskCenterRedactSensitive(field.text))
}
