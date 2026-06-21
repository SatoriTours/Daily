package com.dailysatori.data.repository

import com.dailysatori.service.externalfavorites.ExternalFavoriteItemDraft
import com.dailysatori.service.externalfavorites.ExternalItemAiStatus
import com.dailysatori.service.externalfavorites.ExternalItemImportStatus
import com.dailysatori.service.externalfavorites.ExternalItemSyncStatus
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.External_favorite_item
import com.dailysatori.shared.db.SelectExternalFavoriteItemsImportedWithArticleMissingCover
import com.dailysatori.shared.db.SelectExternalFavoriteItemsImportedWithPlaceholderArticle
import com.dailysatori.shared.db.SelectExternalFavoriteItemsPendingAi
import com.dailysatori.shared.db.SelectExternalFavoriteItemsPendingAiBySource
import com.dailysatori.shared.db.SelectExternalFavoriteItemsRetryableAi
import com.dailysatori.shared.db.SelectExternalFavoriteItemsRetryableAiBySource
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

    fun pendingImport(limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsPendingImport(limit).executeAsList()

    fun pendingImportBySource(sourceId: Long, limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsPendingImportBySource(sourceId, limit).executeAsList()

    fun pendingAi(limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsPendingAi(limit).executeAsList().map { item ->
            item.toExternalFavoriteItem()
        }

    fun pendingAiBySource(sourceId: Long, limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsPendingAiBySource(sourceId, limit).executeAsList().map { item ->
            item.toExternalFavoriteItem()
        }

    fun retryableAi(limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsRetryableAi(limit).executeAsList().map { item ->
            item.toExternalFavoriteItem()
        }

    fun retryableAiBySource(sourceId: Long, limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsRetryableAiBySource(sourceId, limit).executeAsList().map { item ->
            item.toExternalFavoriteItem()
        }

    fun importedWithMissingArticleCover(limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsImportedWithArticleMissingCover(limit).executeAsList().map { item ->
            item.toExternalFavoriteItem()
        }

    fun importedWithPlaceholderArticle(limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsImportedWithPlaceholderArticle(limit).executeAsList().map { item ->
            item.toExternalFavoriteItem()
        }

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

    private fun SelectExternalFavoriteItemsPendingAi.toExternalFavoriteItem(): External_favorite_item =
        External_favorite_item(
            id = id,
            source_id = source_id,
            provider = provider,
            external_id = external_id,
            canonical_url = canonical_url,
            title = title,
            text = text,
            author_name = author_name,
            source_created_at = source_created_at,
            favorited_at = favorited_at,
            normalized_json = normalized_json,
            debug_json = debug_json,
            content_hash = content_hash,
            ai_input_hash = ai_input_hash,
            article_id = article_id,
            sync_status = sync_status,
            import_status = import_status,
            ai_status = ai_status,
            last_error_code = last_error_code,
            last_error_message = last_error_message,
            first_seen_at = first_seen_at,
            last_seen_at = last_seen_at,
            created_at = created_at,
            updated_at = updated_at,
        )

    private fun SelectExternalFavoriteItemsPendingAiBySource.toExternalFavoriteItem(): External_favorite_item =
        External_favorite_item(
            id = id,
            source_id = source_id,
            provider = provider,
            external_id = external_id,
            canonical_url = canonical_url,
            title = title,
            text = text,
            author_name = author_name,
            source_created_at = source_created_at,
            favorited_at = favorited_at,
            normalized_json = normalized_json,
            debug_json = debug_json,
            content_hash = content_hash,
            ai_input_hash = ai_input_hash,
            article_id = article_id,
            sync_status = sync_status,
            import_status = import_status,
            ai_status = ai_status,
            last_error_code = last_error_code,
            last_error_message = last_error_message,
            first_seen_at = first_seen_at,
            last_seen_at = last_seen_at,
            created_at = created_at,
            updated_at = updated_at,
        )

    private fun SelectExternalFavoriteItemsRetryableAi.toExternalFavoriteItem(): External_favorite_item =
        External_favorite_item(
            id = id,
            source_id = source_id,
            provider = provider,
            external_id = external_id,
            canonical_url = canonical_url,
            title = title,
            text = text,
            author_name = author_name,
            source_created_at = source_created_at,
            favorited_at = favorited_at,
            normalized_json = normalized_json,
            debug_json = debug_json,
            content_hash = content_hash,
            ai_input_hash = ai_input_hash,
            article_id = article_id,
            sync_status = sync_status,
            import_status = import_status,
            ai_status = ai_status,
            last_error_code = last_error_code,
            last_error_message = last_error_message,
            first_seen_at = first_seen_at,
            last_seen_at = last_seen_at,
            created_at = created_at,
            updated_at = updated_at,
        )

    private fun SelectExternalFavoriteItemsRetryableAiBySource.toExternalFavoriteItem(): External_favorite_item =
        External_favorite_item(
            id = id,
            source_id = source_id,
            provider = provider,
            external_id = external_id,
            canonical_url = canonical_url,
            title = title,
            text = text,
            author_name = author_name,
            source_created_at = source_created_at,
            favorited_at = favorited_at,
            normalized_json = normalized_json,
            debug_json = debug_json,
            content_hash = content_hash,
            ai_input_hash = ai_input_hash,
            article_id = article_id,
            sync_status = sync_status,
            import_status = import_status,
            ai_status = ai_status,
            last_error_code = last_error_code,
            last_error_message = last_error_message,
            first_seen_at = first_seen_at,
            last_seen_at = last_seen_at,
            created_at = created_at,
            updated_at = updated_at,
        )

    private fun SelectExternalFavoriteItemsImportedWithArticleMissingCover.toExternalFavoriteItem(): External_favorite_item =
        External_favorite_item(
            id = id,
            source_id = source_id,
            provider = provider,
            external_id = external_id,
            canonical_url = canonical_url,
            title = title,
            text = text,
            author_name = author_name,
            source_created_at = source_created_at,
            favorited_at = favorited_at,
            normalized_json = normalized_json,
            debug_json = debug_json,
            content_hash = content_hash,
            ai_input_hash = ai_input_hash,
            article_id = article_id,
            sync_status = sync_status,
            import_status = import_status,
            ai_status = ai_status,
            last_error_code = last_error_code,
            last_error_message = last_error_message,
            first_seen_at = first_seen_at,
            last_seen_at = last_seen_at,
            created_at = created_at,
            updated_at = updated_at,
        )

    private fun SelectExternalFavoriteItemsImportedWithPlaceholderArticle.toExternalFavoriteItem(): External_favorite_item =
        External_favorite_item(
            id = id,
            source_id = source_id,
            provider = provider,
            external_id = external_id,
            canonical_url = canonical_url,
            title = title,
            text = text,
            author_name = author_name,
            source_created_at = source_created_at,
            favorited_at = favorited_at,
            normalized_json = normalized_json,
            debug_json = debug_json,
            content_hash = content_hash,
            ai_input_hash = ai_input_hash,
            article_id = article_id,
            sync_status = sync_status,
            import_status = import_status,
            ai_status = ai_status,
            last_error_code = last_error_code,
            last_error_message = last_error_message,
            first_seen_at = first_seen_at,
            last_seen_at = last_seen_at,
            created_at = created_at,
            updated_at = updated_at,
        )
}
