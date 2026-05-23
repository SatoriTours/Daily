package com.dailysatori.data.repository

import com.dailysatori.service.security.SecretCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Remote_news_source
import kotlinx.datetime.Clock

class RemoteNewsSourceRepository(
    private val db: DailySatoriDatabase,
    private val secretCipher: SecretCipher,
) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): List<Remote_news_source> =
        q.selectRemoteNewsSources().executeAsList().map(::decryptSource)

    fun getEnabled(): List<Remote_news_source> =
        q.selectEnabledRemoteNewsSources().executeAsList().map(::decryptSource)

    fun getById(id: Long): Remote_news_source? =
        q.selectRemoteNewsSourceById(id).executeAsOneOrNull()?.let(::decryptSource)

    fun save(id: Long?, name: String, baseUrl: String, apiToken: String, enabled: Boolean) {
        val now = Clock.System.now().toEpochMilliseconds()
        val enabledValue = if (enabled) 1L else 0L
        val encryptedToken = secretCipher.encrypt(apiToken.trim())
        if (id == null) {
            q.insertRemoteNewsSource(name.trim(), baseUrl.trim(), encryptedToken, enabledValue, now, now)
            return
        }
        q.updateRemoteNewsSource(name.trim(), baseUrl.trim(), encryptedToken, enabledValue, now, id)
    }

    fun delete(id: Long) = q.deleteRemoteNewsSource(id)

    fun encryptStoredSecrets() {
        q.selectRemoteNewsSources().executeAsList()
            .filterNot { secretCipher.isEncrypted(it.api_token) }
            .forEach { source ->
                q.updateRemoteNewsSource(
                    source.name,
                    source.base_url,
                    secretCipher.encrypt(source.api_token),
                    source.enabled,
                    Clock.System.now().toEpochMilliseconds(),
                    source.id,
                )
            }
    }

    private fun decryptSource(source: Remote_news_source): Remote_news_source =
        source.copy(api_token = secretCipher.decrypt(source.api_token))
}
