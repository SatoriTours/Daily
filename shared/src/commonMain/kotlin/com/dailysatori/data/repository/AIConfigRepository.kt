package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.service.ai.canDeleteAiConfig
import com.dailysatori.shared.db.Ai_config
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class AIConfigRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Ai_config>> =
        q.selectAllAiConfigs().asFlow().mapToList(Dispatchers.IO)

    fun getById(id: Long) = q.selectAiConfigById(id).executeAsOneOrNull()

    fun getDefault() = q.selectDefaultAiConfig().executeAsOneOrNull()

    fun insert(
        provider: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        isDefault: Long = 0,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        if (isDefault == 1L) q.clearDefaultAiConfig()
        q.insertAiConfig(provider, apiAddress, apiToken, modelName, isDefault, now, now)
    }

    fun update(
        id: Long,
        provider: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        isDefault: Long,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        if (isDefault == 1L) q.clearDefaultAiConfig()
        q.updateAiConfig(provider, apiAddress, apiToken, modelName, isDefault, now, id)
    }

    fun delete(id: Long) {
        val config = getById(id) ?: return
        if (canDeleteAiConfig(config.is_default)) q.deleteAiConfig(id)
    }
}
