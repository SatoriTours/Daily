package com.dailysatori.data.repository

import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Remote_news_source
import kotlinx.datetime.Clock

class RemoteNewsSourceRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): List<Remote_news_source> =
        q.selectRemoteNewsSources().executeAsList()

    fun getEnabled(): List<Remote_news_source> =
        q.selectEnabledRemoteNewsSources().executeAsList()

    fun getById(id: Long): Remote_news_source? =
        q.selectRemoteNewsSourceById(id).executeAsOneOrNull()

    fun save(id: Long?, name: String, baseUrl: String, apiToken: String, enabled: Boolean) {
        val now = Clock.System.now().toEpochMilliseconds()
        val enabledValue = if (enabled) 1L else 0L
        if (id == null) {
            q.insertRemoteNewsSource(name.trim(), baseUrl.trim(), apiToken.trim(), enabledValue, now, now)
            return
        }
        q.updateRemoteNewsSource(name.trim(), baseUrl.trim(), apiToken.trim(), enabledValue, now, id)
    }

    fun delete(id: Long) = q.deleteRemoteNewsSource(id)
}
