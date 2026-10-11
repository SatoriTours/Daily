package com.dailysatori.service.hotnews

import com.dailysatori.data.repository.SettingRepository
import kotlinx.serialization.json.Json

class HotNewsStore(private val settings: SettingRepository) {
    private val json = Json { ignoreUnknownKeys = true }
    fun load(platform: HotNewsPlatform): HotNewsBoard? = settings.getLargeValue("hot_news.v1:${platform.key}")?.let {
        runCatching { json.decodeFromString<HotNewsBoard>(it) }.getOrNull()
    }?.takeIf { it.platform == platform }?.copy(refreshing = false)

    fun save(board: HotNewsBoard) {
        settings.upsert("hot_news.v1:${board.platform.key}", json.encodeToString(board.copy(refreshing = false)))
    }
}
