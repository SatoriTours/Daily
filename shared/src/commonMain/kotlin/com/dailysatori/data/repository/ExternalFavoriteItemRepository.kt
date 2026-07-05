package com.dailysatori.data.repository

import com.dailysatori.service.externalfavorites.ExternalFavoriteItemDraft
import com.dailysatori.service.externalfavorites.ExternalItemAiStatus
import com.dailysatori.service.externalfavorites.ExternalItemImportStatus
import com.dailysatori.service.externalfavorites.ExternalItemSyncStatus
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.External_favorite_item
import kotlinx.datetime.Clock

class ExternalFavoriteItemRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun upsertDraft(sourceId: Long, draft: ExternalFavoriteItemDraft): Pair<External_favorite_item, Boolean> {
        val existing = q.selectExternalFavoriteItemBySourceExternalId(sourceId, draft.externalId).executeAsOneOrNull()
        val now = Clock.System.now().toEpochMilliseconds()
        return if (existing == null) {
            q.insertExternalFavoriteItem(
                sourceId,
                draft.provider,
                draft.externalId,
                draft.canonicalUrl,
                draft.title,
                draft.text,
                draft.authorName,
                draft.sourceCreatedAt,
                draft.favoritedAt,
                draft.normalizedJson,
                draft.debugJson,
                draft.contentHash,
                draft.aiInputHash,
                null,
                ExternalItemSyncStatus.seen.name,
                ExternalItemImportStatus.not_imported.name,
                ExternalItemAiStatus.pending.name,
                "",
                "",
                now,
                now,
                now,
                now,
            )
            val inserted = q.selectExternalFavoriteItemBySourceExternalId(sourceId, draft.externalId).executeAsOne()
            inserted to true
        } else {
            val changed = existing.hasChangedDraftContent(draft)
            q.updateExternalFavoriteItem(
                draft.canonicalUrl,
                draft.title,
                draft.text,
                draft.authorName,
                draft.sourceCreatedAt,
                draft.favoritedAt,
                draft.normalizedJson,
                draft.debugJson,
                draft.contentHash,
                draft.aiInputHash,
                ExternalItemSyncStatus.seen.name,
                "",
                "",
                now,
                now,
                existing.id,
            )
            if (changed) {
                q.updateExternalFavoriteItemImportState(
                    existing.article_id,
                    ExternalItemImportStatus.not_imported.name,
                    ExternalItemAiStatus.pending.name,
                    "",
                    "",
                    now,
                    existing.id,
                )
            }
            q.selectExternalFavoriteItemBySourceExternalId(sourceId, draft.externalId).executeAsOne() to changed
        }
    }

    fun getBySource(sourceId: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsBySource(sourceId).executeAsList()

    fun latestNumericExternalIdBySource(sourceId: Long): String? =
        q.selectLatestNumericExternalFavoriteExternalIdBySource(sourceId).executeAsOneOrNull()

    fun getBySourceExternalId(sourceId: Long, externalId: String): External_favorite_item? =
        q.selectExternalFavoriteItemBySourceExternalId(sourceId, externalId).executeAsOneOrNull()

    fun count(): Long = q.countExternalFavoriteItems().executeAsOne()

    fun countBySource(sourceId: Long): Long =
        q.countExternalFavoriteItemsBySource(sourceId).executeAsOne()

    fun markSeen(itemId: Long, favoritedAt: Long?) {
        val now = Clock.System.now().toEpochMilliseconds()
        q.markExternalFavoriteItemSeen(
            sync_status = ExternalItemSyncStatus.seen.name,
            value = favoritedAt,
            last_seen_at = now,
            updated_at = now,
            id = itemId,
        )
    }

    fun pendingImport(limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsPendingImport(limit).executeAsList()

    fun pendingImportBySource(sourceId: Long, limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsPendingImportBySource(sourceId, limit).executeAsList()

    fun pendingAi(limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsPendingAi(limit, ::externalFavoriteItemWithArticle).executeAsList()

    fun pendingAiBySource(sourceId: Long, limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsPendingAiBySource(sourceId, limit, ::externalFavoriteItemWithArticle).executeAsList()

    fun retryableAi(limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsRetryableAi(limit, ::externalFavoriteItemWithArticle).executeAsList()

    fun retryableAiBySource(sourceId: Long, limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsRetryableAiBySource(sourceId, limit, ::externalFavoriteItemWithArticle).executeAsList()

    fun importedWithMissingArticleCover(limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsImportedWithArticleMissingCover(limit, ::externalFavoriteItemWithArticle).executeAsList()

    fun importedWithPlaceholderArticle(limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsImportedWithPlaceholderArticle(limit, ::externalFavoriteItemWithArticle).executeAsList()

    fun importedXLongArticlePending(limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsImportedXLongArticlePending(limit, ::externalFavoriteItemWithArticle).executeAsList()

    fun markImported(
        itemId: Long,
        articleId: Long,
        duplicateLinked: Boolean,
        aiStatus: ExternalItemAiStatus = ExternalItemAiStatus.pending,
    ) {
        q.updateExternalFavoriteItemImportState(
            articleId,
            if (duplicateLinked) ExternalItemImportStatus.duplicate_linked.name else ExternalItemImportStatus.imported.name,
            aiStatus.name,
            "",
            "",
            Clock.System.now().toEpochMilliseconds(),
            itemId,
        )
    }

    fun markImportFailed(itemId: Long, code: String, message: String) {
        q.updateExternalFavoriteItemImportState(
            null,
            ExternalItemImportStatus.failed.name,
            ExternalItemAiStatus.not_needed.name,
            code,
            message,
            Clock.System.now().toEpochMilliseconds(),
            itemId,
        )
    }

    fun markAiState(itemId: Long, status: String, code: String = "", message: String = "") {
        q.updateExternalFavoriteItemAiState(
            status,
            code,
            message,
            Clock.System.now().toEpochMilliseconds(),
            itemId,
        )
    }

    private fun External_favorite_item.hasChangedDraftContent(draft: ExternalFavoriteItemDraft): Boolean =
        content_hash != draft.contentHash ||
            ai_input_hash != draft.aiInputHash ||
            canonical_url != draft.canonicalUrl ||
            title != draft.title ||
            text != draft.text ||
            author_name != draft.authorName ||
            source_created_at != draft.sourceCreatedAt ||
            favorited_at != draft.favoritedAt ||
            normalized_json != draft.normalizedJson ||
            debug_json != draft.debugJson

    private fun externalFavoriteItemWithArticle(
        id: Long,
        sourceId: Long,
        provider: String,
        externalId: String,
        canonicalUrl: String?,
        title: String,
        text: String,
        authorName: String,
        sourceCreatedAt: Long?,
        favoritedAt: Long?,
        normalizedJson: String,
        debugJson: String,
        contentHash: String,
        aiInputHash: String,
        articleId: Long,
        syncStatus: String,
        importStatus: String,
        aiStatus: String,
        lastErrorCode: String,
        lastErrorMessage: String,
        firstSeenAt: Long,
        lastSeenAt: Long,
        createdAt: Long,
        updatedAt: Long,
    ): External_favorite_item =
        External_favorite_item(
            id = id,
            source_id = sourceId,
            provider = provider,
            external_id = externalId,
            canonical_url = canonicalUrl,
            title = title,
            text = text,
            author_name = authorName,
            source_created_at = sourceCreatedAt,
            favorited_at = favoritedAt,
            normalized_json = normalizedJson,
            debug_json = debugJson,
            content_hash = contentHash,
            ai_input_hash = aiInputHash,
            article_id = articleId,
            sync_status = syncStatus,
            import_status = importStatus,
            ai_status = aiStatus,
            last_error_code = lastErrorCode,
            last_error_message = lastErrorMessage,
            first_seen_at = firstSeenAt,
            last_seen_at = lastSeenAt,
            created_at = createdAt,
            updated_at = updatedAt,
        )
}
