package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Mcp_server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class McpServerRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Mcp_server>> =
        q.selectAllMcpServers().asFlow().mapToList(Dispatchers.IO)

    fun getById(id: Long) = q.selectMcpServerById(id).executeAsOneOrNull()

    fun getByServerUrl(serverUrl: String) = q.selectMcpServerByUrl(serverUrl).executeAsOneOrNull()

    fun getEnabled() = q.selectEnabledMcpServers().executeAsList()

    fun insert(
        name: String,
        serverUrl: String,
        apiKey: String,
        enabled: Long = 1,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertMcpServer(name, serverUrl, apiKey, enabled, now, now)
    }

    fun update(
        id: Long,
        name: String,
        serverUrl: String,
        apiKey: String,
        enabled: Long,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateMcpServer(name, serverUrl, apiKey, enabled, now, id)
    }

    fun insertPreset(
        name: String,
        serverUrl: String,
        apiKey: String,
        provider: String,
        templateId: String,
        templateType: String,
        configJson: String,
        enabled: Long = 1,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertMcpServerPreset(
            name,
            serverUrl,
            apiKey,
            enabled,
            provider,
            templateId,
            templateType,
            configJson,
            now,
            now,
        )
    }

    fun delete(id: Long) = q.deleteMcpServer(id)
}
