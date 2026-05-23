package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.service.ai.canDeleteAiConfig
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.shared.db.Ai_config
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AIConfigRepository(
    private val db: DailySatoriDatabase,
    private val secretCipher: SecretCipher,
) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Ai_config>> =
        q.selectAllAiConfigs().asFlow().mapToList(Dispatchers.IO).map { configs ->
            configs.map(::decryptConfig)
        }

    fun getById(id: Long) = q.selectAiConfigById(id).executeAsOneOrNull()?.let(::decryptConfig)

    fun getDefault() = q.selectDefaultAiConfig().executeAsOneOrNull()?.let(::decryptConfig)

    fun insert(
        provider: String,
        apiAddress: String,
        apiToken: String,
        modelName: String,
        isDefault: Long = 0,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        if (isDefault == 1L) q.clearDefaultAiConfig()
        q.insertAiConfig(provider, apiAddress, secretCipher.encrypt(apiToken), modelName, isDefault, now, now)
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
        q.updateAiConfig(provider, apiAddress, secretCipher.encrypt(apiToken), modelName, isDefault, now, id)
    }

    fun delete(id: Long) {
        val config = getById(id) ?: return
        if (canDeleteAiConfig(config.is_default)) q.deleteAiConfig(id)
    }

    fun encryptStoredSecrets() {
        q.selectAllAiConfigs().executeAsList()
            .filterNot { secretCipher.isEncrypted(it.api_token) }
            .forEach { config ->
                q.updateAiConfig(
                    config.provider,
                    config.api_address,
                    secretCipher.encrypt(config.api_token),
                    config.model_name,
                    config.is_default,
                    kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                    config.id,
                )
            }
    }

    private fun decryptConfig(config: Ai_config): Ai_config =
        config.copy(api_token = secretCipher.decrypt(config.api_token))
}
