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

internal fun taskCenterTaskName(type: String): String = asyncTaskTypeDisplayName(type)

/** Where "open source" should take the user for one async task; null when the task has no owned source. */
internal sealed interface TaskCenterSourceTarget {
    data class Article(val articleId: Long) : TaskCenterSourceTarget
    data object RemoteNewsSettings : TaskCenterSourceTarget
    data object Opportunities : TaskCenterSourceTarget
}

private val articleTaskTypes = setOf(
    AsyncTaskType.save_article.name,
    AsyncTaskType.article_cover_download.name,
    AsyncTaskType.article_memory_extract.name,
    AsyncTaskType.remote_article_reprocess.name,
    AsyncTaskType.article_ai.name,
)

internal fun taskCenterSourceTarget(type: String, payload: String): TaskCenterSourceTarget? = when {
    type in articleTaskTypes -> taskCenterArticleId(payload)?.let(TaskCenterSourceTarget::Article)
    type == AsyncTaskType.remote_article_sync.name || type == AsyncTaskType.remote_news_fetch.name ->
        TaskCenterSourceTarget.RemoteNewsSettings
    type == AsyncTaskType.news_opportunity_analysis.name -> TaskCenterSourceTarget.Opportunities
    else -> null
}

/** Article handlers write `articleId`; historical payloads may still carry `article_id`. */
internal fun taskCenterArticleId(payload: String): Long? = runCatching {
    val json = Json.parseToJsonElement(payload).jsonObject
    (json["articleId"] ?: json["article_id"])?.jsonPrimitive?.longOrNull?.takeIf { it > 0 }
}.getOrNull()

internal fun taskCenterTaskTitle(type: String, payload: String, sources: TaskCenterSourceNames): String {
    val title = taskCenterTaskName(type)
    return taskCenterTaskSource(type, payload, sources)?.let { "$title · $it" } ?: title
}

/** The compact source line a task detail shows under its name; null when the task has no source. */
internal fun taskCenterTaskSource(type: String, payload: String, sources: TaskCenterSourceNames): String? {
    val sourceId = runCatching {
        Json.parseToJsonElement(payload).jsonObject["sourceId"]?.jsonPrimitive?.longOrNull
    }.getOrNull()
    return when (type) {
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
