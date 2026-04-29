package com.dailysatori.data.repository

import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Setting

class SettingRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun get(key: String): String? =
        q.selectSettingByKey(key).executeAsOneOrNull()?.value_

    fun getAll(): List<Setting> =
        q.selectAllSettings().executeAsList()

    fun getAllKeys(): List<String> =
        q.selectAllSettings().executeAsList().mapNotNull { it.key }

    fun upsert(key: String, value: String) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.upsertSetting(key, value, now, now)
    }

    fun delete(key: String) = q.deleteSetting(key)
}
