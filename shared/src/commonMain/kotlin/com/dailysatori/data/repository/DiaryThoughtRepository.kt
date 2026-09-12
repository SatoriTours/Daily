package com.dailysatori.data.repository

import com.dailysatori.service.diary.DiaryThoughtArchive
import kotlinx.serialization.json.Json

/** 单份可重建的思想档案，沿用本地键值存储；用户修正独立保存，不被生成结果覆盖。 */
class DiaryThoughtRepository(private val settings: SettingRepository) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): DiaryThoughtArchive = settings.get(ARCHIVE_KEY)?.let {
        runCatching { json.decodeFromString<DiaryThoughtArchive>(it) }.getOrNull()
    } ?: DiaryThoughtArchive()

    fun save(archive: DiaryThoughtArchive) = settings.upsert(ARCHIVE_KEY, Json.encodeToString(archive))

    fun corrections(): String = settings.get(CORRECTIONS_KEY).orEmpty()

    fun useInChat(): Boolean = settings.get(CHAT_KEY) != "false"

    fun setUseInChat(enabled: Boolean) = settings.upsert(CHAT_KEY, enabled.toString())

    fun saveCorrections(text: String) {
        require(text.length <= 2_000) { "补充与修正不能超过 2,000 字" }
        settings.upsert(CORRECTIONS_KEY, text.trim())
    }

    private companion object {
        const val ARCHIVE_KEY = "diary_thought_archive_v1"
        const val CORRECTIONS_KEY = "diary_thought_corrections"
        const val CHAT_KEY = "diary_thought_use_in_chat"
    }
}
