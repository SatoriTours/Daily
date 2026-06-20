package com.dailysatori.service.externalfavorites

enum class ExternalFavoriteProvider(val id: String) { X("x") }
enum class FavoriteSyncMode { sync, recent, history, full_rescan, retry_failed }
enum class ExternalSourceStatus { idle, syncing, auth_required, auth_check_required, rate_limited, paused, failed }
enum class ExternalSourceHealth { healthy, needs_auth, limited, paused, failing, never_synced }
enum class ExternalItemSyncStatus { seen, skipped, stale, deleted_remote_unknown, failed }
enum class ExternalItemImportStatus { not_imported, imported, duplicate_linked, failed }
enum class ExternalItemAiStatus { not_needed, pending, processing, completed, failed }

data class FavoriteConnectorCapabilities(
    val maxPageSize: Int,
    val defaultBackoffMinutes: Int,
    val maxPagesPerRun: Int,
    val maxItemsPerRun: Int,
    val supportsFolders: Boolean,
    val supportsFavoritedAt: Boolean,
    val supportsWriteBack: Boolean,
    val supportsRefreshToken: Boolean,
)

data class ExternalFavoriteItemDraft(
    val provider: String,
    val externalId: String,
    val canonicalUrl: String?,
    val title: String,
    val text: String,
    val authorName: String,
    val sourceCreatedAt: Long?,
    val favoritedAt: Long?,
    val normalizedJson: String,
    val debugJson: String = "",
    val contentHash: String,
    val aiInputHash: String,
)

data class FavoriteFetchPage(
    val items: List<ExternalFavoriteItemDraft>,
    val nextCursor: String?,
    val rateLimitResetAt: Long? = null,
) {
    val exhausted: Boolean get() = nextCursor == null
}

data class FavoriteSyncProgress(
    val phase: String,
    val pagesSeen: Int,
    val maxPages: Int,
    val itemsSeen: Int,
    val historyComplete: Boolean,
)

fun sourceHealth(status: String, lastSuccessAt: Long?, lastErrorCode: String): ExternalSourceHealth = when (status) {
    "auth_required", "auth_check_required" -> ExternalSourceHealth.needs_auth
    "rate_limited" -> ExternalSourceHealth.limited
    "paused" -> ExternalSourceHealth.paused
    "failed" -> ExternalSourceHealth.failing
    else -> if (lastSuccessAt == null && lastErrorCode.isBlank()) {
        ExternalSourceHealth.never_synced
    } else {
        ExternalSourceHealth.healthy
    }
}
