package com.dailysatori.service.remotenews

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun remoteArticleViewpointsFromJsonElement(element: JsonElement): List<String> = when (element) {
    JsonNull -> emptyList()
    is JsonArray -> element.mapNotNull { item -> (item as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
    is JsonPrimitive -> element.contentOrNull
        ?.split('\n')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
    else -> emptyList()
}
