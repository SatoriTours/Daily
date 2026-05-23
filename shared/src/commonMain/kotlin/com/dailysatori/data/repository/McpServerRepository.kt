package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Mcp_server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class McpServerRepository(
    private val db: DailySatoriDatabase,
    private val secretCipher: SecretCipher,
) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Mcp_server>> =
        q.selectAllMcpServers().asFlow().mapToList(Dispatchers.IO).map { servers ->
            servers.map(::decryptServer)
        }

    fun getById(id: Long) = q.selectMcpServerById(id).executeAsOneOrNull()?.let(::decryptServer)

    fun getByServerUrl(serverUrl: String) = q.selectMcpServerByUrl(serverUrl).executeAsOneOrNull()?.let(::decryptServer)

    fun getEnabled() = q.selectEnabledMcpServers().executeAsList().map(::decryptServer)

    fun insert(
        name: String,
        serverUrl: String,
        apiKey: String,
        enabled: Long = 1,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertMcpServer(name, serverUrl, secretCipher.encrypt(apiKey), enabled, now, now)
    }

    fun update(
        id: Long,
        name: String,
        serverUrl: String,
        apiKey: String,
        enabled: Long,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateMcpServer(name, serverUrl, secretCipher.encrypt(apiKey), enabled, now, id)
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
            secretCipher.encrypt(apiKey),
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

    fun encryptStoredSecrets() {
        q.selectAllMcpServers().executeAsList()
            .filterNot { secretCipher.isEncrypted(it.api_key) }
            .forEach { server ->
                q.updateMcpServer(
                    server.name,
                    server.server_url,
                    secretCipher.encrypt(server.api_key),
                    server.enabled,
                    kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                    server.id,
                )
            }
    }

    private fun decryptServer(server: Mcp_server): Mcp_server =
        server.copy(api_key = secretCipher.decrypt(server.api_key))
}
