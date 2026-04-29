package com.dailysatori.service.setting

import com.dailysatori.data.repository.SettingRepository

class SettingService(private val repo: SettingRepository) {
    fun get(key: String): String? = repo.get(key)
    fun set(key: String, value: String) = repo.upsert(key, value)
    fun getString(key: String, default: String = ""): String = get(key) ?: default
    fun getLong(key: String, default: Long = 0): Long = get(key)?.toLongOrNull() ?: default
    fun getBool(key: String, default: Boolean = false): Boolean = get(key)?.toBooleanStrictOrNull() ?: default
    fun remove(key: String) = repo.delete(key)
}
