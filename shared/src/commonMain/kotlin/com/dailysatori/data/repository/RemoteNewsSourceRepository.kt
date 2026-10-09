package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Remote_news_source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class RemoteNewsSourceRepository(
    private val db: DailySatoriDatabase,
) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): List<Remote_news_source> =
        q.selectRemoteNewsSources().executeAsList()

    fun observeAll(): Flow<List<Remote_news_source>> =
        q.selectRemoteNewsSources().asFlow().mapToList(Dispatchers.IO)

    fun getEnabled(): List<Remote_news_source> =
        q.selectEnabledRemoteNewsSources().executeAsList()

    fun observeEnabledCount(): Flow<Long> =
        q.countEnabledRemoteNewsSources().asFlow().mapToOne(Dispatchers.IO)

    fun getById(id: Long): Remote_news_source? =
        q.selectRemoteNewsSourceById(id).executeAsOneOrNull()

    fun save(id: Long?, name: String, baseUrl: String, apiToken: String, enabled: Boolean) {
        val now = Clock.System.now().toEpochMilliseconds()
        val enabledValue = if (enabled) 1L else 0L
        val token = apiToken.trim()
        if (id == null) {
            q.insertRemoteNewsSource(name.trim(), baseUrl.trim(), token, enabledValue, now, now)
            return
        }
        q.updateRemoteNewsSource(name.trim(), baseUrl.trim(), token, enabledValue, now, id)
    }

    fun delete(id: Long) = q.deleteRemoteNewsSource(id)


}
