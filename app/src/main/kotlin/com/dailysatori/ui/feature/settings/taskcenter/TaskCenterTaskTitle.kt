package com.dailysatori.ui.feature.settings.taskcenter

import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.asynctask.asyncTaskTypeDisplayName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class TaskCenterSourceNames(
    val remote: Map<Long, String> = emptyMap(),
    val enabledRemote: Map<Long, String> = emptyMap(),
    val favorites: Map<Long, String> = emptyMap(),
)

internal fun taskCenterTaskTitle(type: String, payload: String, sources: TaskCenterSourceNames): String {
    val title = asyncTaskTypeDisplayName(type)
    val sourceId = runCatching {
        Json.parseToJsonElement(payload).jsonObject["sourceId"]?.jsonPrimitive?.longOrNull
    }.getOrNull()
    val sourceName = when (type) {
        AsyncTaskType.remote_article_sync.name -> sourceId?.let {
            sources.remote[it]?.takeIf(String::isNotBlank) ?: "新闻源 #$it"
        } ?: sources.enabledRemote.values.joinToString("、").ifBlank { "全部已启用新闻源" }
        AsyncTaskType.remote_news_fetch.name ->
            (sources.enabledRemote.values + "本地收藏").joinToString("、")
        AsyncTaskType.external_favorite_sync.name, AsyncTaskType.external_favorite_organize.name -> sourceId?.let {
            sources.favorites[it]?.takeIf(String::isNotBlank) ?: "收藏源 #$it"
        }
        else -> null
    }
    return sourceName?.let { "$title · $it" } ?: title
}

internal fun taskCenterFavoriteSourceName(provider: String, displayName: String, accountName: String): String {
    val providerName = when (provider) {
        "x" -> "X"
        "github" -> "GitHub"
        else -> provider
    }
    val name = displayName.trim().ifBlank { accountName.trim() }
    return when {
        name.isBlank() -> providerName
        name.equals(providerName, ignoreCase = true) || name.startsWith("$providerName ", ignoreCase = true) -> name
        else -> "$providerName · $name"
    }
}
