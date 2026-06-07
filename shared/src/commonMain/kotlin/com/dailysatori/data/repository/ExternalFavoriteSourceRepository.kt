package com.dailysatori.data.repository

import com.dailysatori.service.externalfavorites.ExternalSourceStatus
import com.dailysatori.service.externalfavorites.FavoriteSyncMode
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.External_favorite_source
import kotlinx.datetime.Clock

class ExternalFavoriteSourceRepository(
    private val db: DailySatoriDatabase,
    private val encryptSecret: (String) -> String,
    private val decryptSecret: (String) -> String,
    private val isSecretEncrypted: (String) -> Boolean = { false },
) {
    constructor(db: DailySatoriDatabase, secretCipher: SecretCipher) : this(
        db = db,
        encryptSecret = { value -> secretCipher.encrypt(value) },
        decryptSecret = { value -> secretCipher.decrypt(value) },
        isSecretEncrypted = { value -> secretCipher.isEncrypted(value) },
    )

    private val q get() = db.dailySatoriQueries

    fun getAll(): List<External_favorite_source> =
        q.selectExternalFavoriteSources().executeAsList().map(::decryptSource)

    fun getEnabled(): List<External_favorite_source> =
        q.selectEnabledExternalFavoriteSources().executeAsList().map(::decryptSource)

    fun getById(id: Long): External_favorite_source? =
        q.selectExternalFavoriteSourceById(id).executeAsOneOrNull()?.let(::decryptSource)

    fun getByProviderAccount(provider: String, accountId: String): External_favorite_source? =
        q.selectExternalFavoriteSourceByProviderAccount(provider, accountId).executeAsOneOrNull()?.let(::decryptSource)

    fun save(
        id: Long? = null,
        provider: String,
        displayName: String,
        accountId: String,
        accountName: String,
        authJson: String,
        enabled: Boolean = true,
        syncIntervalMinutes: Long = 720,
        status: String = ExternalSourceStatus.idle.name,
        configJson: String = "",
        capabilitiesJson: String = "",
    ): Long {
        val now = Clock.System.now().toEpochMilliseconds()
        val enabledValue = if (enabled) 1L else 0L
        val encryptedAuth = encryptSecret(authJson.trim())
        if (id == null) {
            q.insertExternalFavoriteSource(
                provider.trim(),
                displayName.trim(),
                accountId.trim(),
                accountName.trim(),
                enabledValue,
                syncIntervalMinutes,
                status,
                encryptedAuth,
                configJson,
                capabilitiesJson,
                now,
                now,
            )
            return q.selectExternalFavoriteSourceByProviderAccount(provider.trim(), accountId.trim()).executeAsOne().id
        }

        q.updateExternalFavoriteSource(
            displayName.trim(),
            accountName.trim(),
            enabledValue,
            syncIntervalMinutes,
            status,
            encryptedAuth,
            configJson,
            capabilitiesJson,
            now,
            id,
        )
        return id
    }

    fun delete(id: Long) = q.deleteExternalFavoriteSource(id)

    fun encryptStoredSecrets() {
        q.selectExternalFavoriteSources().executeAsList()
            .filter { it.auth_json.isNotBlank() && !isSecretEncrypted(it.auth_json) }
            .forEach { source ->
                q.updateExternalFavoriteSource(
                    source.display_name,
                    source.account_name,
                    source.enabled,
                    source.sync_interval_minutes,
                    source.status,
                    encryptSecret(source.auth_json),
                    source.config_json,
                    source.capabilities_json,
                    Clock.System.now().toEpochMilliseconds(),
                    source.id,
                )
            }
    }

    fun markAuthCheckRequiredAfterRestore() {
        q.selectExternalFavoriteSources().executeAsList()
            .filter { it.auth_json.isNotBlank() }
            .forEach { source ->
                q.updateExternalFavoriteSource(
                    source.display_name,
                    source.account_name,
                    source.enabled,
                    source.sync_interval_minutes,
                    ExternalSourceStatus.auth_check_required.name,
                    source.auth_json,
                    source.config_json,
                    source.capabilities_json,
                    Clock.System.now().toEpochMilliseconds(),
                    source.id,
                )
            }
    }

    fun markSyncStarted(id: Long, mode: String = FavoriteSyncMode.recent.name) {
        val source = q.selectExternalFavoriteSourceById(id).executeAsOneOrNull() ?: return
        q.updateExternalFavoriteSourceSyncState(
            Clock.System.now().toEpochMilliseconds(),
            source.last_sync_completed_at,
            source.last_success_at,
            source.last_sync_window_started_at,
            source.last_items_seen_count,
            source.last_pages_seen_count,
            "",
            "",
            "",
            ExternalSourceStatus.syncing.name,
            mode,
            source.rate_limit_reset_at,
            Clock.System.now().toEpochMilliseconds(),
            id,
        )
    }

    fun markSyncSucceeded(id: Long, itemsSeen: Long, pagesSeen: Long, syncWindowStartedAt: Long? = null) {
        val source = q.selectExternalFavoriteSourceById(id).executeAsOneOrNull() ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        q.updateExternalFavoriteSourceSyncState(
            source.last_sync_started_at,
            now,
            now,
            syncWindowStartedAt ?: source.last_sync_window_started_at,
            itemsSeen,
            pagesSeen,
            "",
            "",
            "",
            ExternalSourceStatus.idle.name,
            source.last_sync_mode,
            null,
            now,
            id,
        )
    }

    fun markSyncFailed(id: Long, code: String, message: String, status: String = ExternalSourceStatus.failed.name) {
        val source = q.selectExternalFavoriteSourceById(id).executeAsOneOrNull() ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        q.updateExternalFavoriteSourceSyncState(
            source.last_sync_started_at,
            now,
            source.last_success_at,
            source.last_sync_window_started_at,
            source.last_items_seen_count,
            source.last_pages_seen_count,
            message,
            code,
            message,
            status,
            source.last_sync_mode,
            source.rate_limit_reset_at,
            now,
            id,
        )
    }

    private fun decryptSource(source: External_favorite_source): External_favorite_source =
        source.copy(auth_json = decryptSecret(source.auth_json))
}
