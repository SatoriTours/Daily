package com.dailysatori.service.hotnews

import com.dailysatori.data.repository.SettingRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.ktor.http.Url

@Serializable
data class HotNewsSourceConfig(val enabled: Boolean = true, val feedUrl: String = "", val version: Long = 0)

@Serializable
data class HotNewsSourcePreferences(val sources: Map<String, HotNewsSourceConfig> = emptyMap()) {
    fun source(platform: HotNewsPlatform): HotNewsSourceConfig = sources[platform.key]
        ?: HotNewsSourceConfig(enabled = platform.core, feedUrl = platform.defaultFeedUrl)
}

class HotNewsStore(private val settings: SettingRepository) {
    private val json = Json { ignoreUnknownKeys = true }
    private val settingsMutex = Mutex()
    private val mutablePreferences = MutableStateFlow(settings.getLargeValue(SOURCES_KEY)?.let {
        runCatching { json.decodeFromString<HotNewsSourcePreferences>(it) }.getOrNull()
    } ?: HotNewsSourcePreferences())
    val preferences = mutablePreferences.asStateFlow()

    suspend fun configure(platform: HotNewsPlatform, enabled: Boolean, feedUrl: String) = settingsMutex.withLock {
        val url = normalizeNewsFeedUrl(feedUrl)
        val current = mutablePreferences.value
        val previous = current.source(platform)
        val next = current.copy(sources = current.sources + (platform.key to
            HotNewsSourceConfig(enabled, url, previous.version + 1)))
        settings.upsert(SOURCES_KEY, json.encodeToString(next))
        mutablePreferences.value = next
    }
    fun load(platform: HotNewsPlatform): HotNewsBoard? = settings.getLargeValue("hot_news.v1:${platform.key}")?.let {
        runCatching { json.decodeFromString<HotNewsBoard>(it) }.getOrNull()
    }?.takeIf { it.platform == platform }?.copy(refreshing = false)

    fun save(board: HotNewsBoard) {
        settings.upsert("hot_news.v1:${board.platform.key}", json.encodeToString(board.copy(refreshing = false)))
    }

    private companion object { const val SOURCES_KEY = "hot_news.sources.v1" }
}

fun normalizeNewsFeedUrl(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return ""
    require(trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true))
    val url = Url(trimmed)
    require(url.protocol.name in setOf("http", "https") && url.host.isNotBlank())
    require(url.user.isNullOrBlank() && url.password.isNullOrBlank())
    return url.toString()
}
