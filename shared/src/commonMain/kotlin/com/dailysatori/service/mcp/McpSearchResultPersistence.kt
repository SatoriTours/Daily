package com.dailysatori.service.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private val mcpSearchResultJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun encodeMcpSearchResults(results: List<McpSearchResult>): String? {
    if (results.isEmpty()) return null
    return buildJsonArray {
        results.forEach { result ->
            add(buildJsonObject {
                put("id", result.id)
                put("type", result.type)
                put("title", result.title)
                result.summary?.let { put("summary", it) }
                result.createdAt?.let { put("createdAt", it) }
                result.tags?.let { tags ->
                    put("tags", JsonArray(tags.map { JsonPrimitive(it) }))
                }
                result.isFavorite?.let { put("isFavorite", it) }
            })
        }
    }.toString()
}

fun decodeMcpSearchResults(value: String?): List<McpSearchResult> {
    if (value.isNullOrBlank()) return emptyList()
    return runCatching {
        mcpSearchResultJson.parseToJsonElement(value).jsonArray.mapNotNull { item ->
            val obj = item.jsonObject
            McpSearchResult(
                id = obj.long("id") ?: return@mapNotNull null,
                type = obj.string("type") ?: return@mapNotNull null,
                title = obj.string("title") ?: return@mapNotNull null,
                summary = obj.string("summary"),
                createdAt = obj.string("createdAt"),
                tags = obj["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull },
                isFavorite = obj["isFavorite"]?.jsonPrimitive?.booleanOrNull,
            )
        }
    }.getOrDefault(emptyList())
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
