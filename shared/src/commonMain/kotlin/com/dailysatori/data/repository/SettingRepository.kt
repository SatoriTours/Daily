package com.dailysatori.data.repository

import com.dailysatori.shared.db.DailySatoriDatabase

class SettingRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun get(key: String): String? =
        q.selectSettingByKey(key).executeAsOneOrNull()?.value_

    fun upsert(key: String, value: String) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.upsertSetting(key, value, now, now)
    }

    fun delete(key: String) = q.deleteSetting(key)
}
