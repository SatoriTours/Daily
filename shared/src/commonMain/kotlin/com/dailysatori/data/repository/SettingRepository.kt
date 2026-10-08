package com.dailysatori.data.repository

import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Setting

class SettingRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun get(key: String): String? =
        q.selectSettingByKey(key).executeAsOneOrNull()?.value_

    fun getAll(): List<Setting> =
        q.selectAllSettings().executeAsList()

    fun getAllKeys(): List<String> = q.selectSettingKeys().executeAsList()

    /** SQL slices by Unicode code point, keeping every returned cell below 128 KiB. */
    fun getLargeValue(key: String): String? = q.transactionWithResult {
        var offset = 1L
        val first = q.selectSettingValuePart(offset.toString(), VALUE_PART_SIZE.toString(), key).executeAsOneOrNull()
            ?: return@transactionWithResult null
        buildString {
            var part = first
            while (part.isNotEmpty()) {
                append(part)
                offset += VALUE_PART_SIZE
                part = q.selectSettingValuePart(offset.toString(), VALUE_PART_SIZE.toString(), key).executeAsOne()
            }
        }
    }

    fun <T> transaction(block: () -> T): T = q.transactionWithResult { block() }

    fun deleteByKeyPrefix(prefix: String) {
        require(prefix.isNotEmpty())
        val pattern = prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        q.deleteSettingsByKeyPrefix(pattern)
    }

    fun upsert(key: String, value: String) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.upsertSetting(key, value, now, now)
    }

    fun delete(key: String) = q.deleteSetting(key)

    private companion object { const val VALUE_PART_SIZE = 32_768L }
}
