package com.dailysatori.data.repository

import com.dailysatori.service.externalfavorites.ExternalFavoriteItemDraft
import com.dailysatori.service.externalfavorites.ExternalItemAiStatus
import com.dailysatori.service.externalfavorites.ExternalItemImportStatus
import com.dailysatori.service.externalfavorites.ExternalItemSyncStatus
import com.dailysatori.service.externalfavorites.ExternalFavoriteSupplement
import com.dailysatori.service.externalfavorites.favoriteContentCacheJson
import com.dailysatori.service.externalfavorites.mergeFavoriteContentCache
import com.dailysatori.service.externalfavorites.sourceFavoriteMetadata
import com.dailysatori.service.externalfavorites.favoriteMetadata
import com.dailysatori.service.externalfavorites.cachedFavoriteContent
import com.dailysatori.service.externalfavorites.favoriteContentRefreshJson
import com.dailysatori.service.externalfavorites.favoriteContentWithoutRefreshFlag
import com.dailysatori.service.externalfavorites.canonicalizeXArticleUrl
import com.dailysatori.service.externalfavorites.xArticleOwningPostId
import com.dailysatori.service.externalfavorites.previousCachedArticleUrl
import com.dailysatori.service.externalfavorites.textValue
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
                mergeFavoriteContentCache(existing.normalized_json, draft.normalizedJson, draft.contentHash.takeIf { !changed && cachedFavoriteContent(existing, allowExpired = true) != null }),
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

    fun favoriteOrderStateBySource(sourceId: Long): ExternalFavoriteOrderState {
        val items = getBySource(sourceId)
        val values = items.map { it.favorited_at ?: it.first_seen_at }
        return ExternalFavoriteOrderState(
            oldest = values.minOrNull(),
            newest = values.maxOrNull(),
            hasMissingFavoriteTime = items.any { it.favorited_at == null },
        )
    }

    fun latestNumericExternalIdBySource(sourceId: Long): String? =
        q.selectLatestNumericExternalFavoriteExternalIdBySource(sourceId).executeAsOneOrNull()

    fun getBySourceExternalId(sourceId: Long, externalId: String): External_favorite_item? =
        q.selectExternalFavoriteItemBySourceExternalId(sourceId, externalId).executeAsOneOrNull()

    fun saveAiResultIfUnchanged(item: External_favorite_item, save: () -> Boolean): Boolean = q.transactionWithResult {
        val current = getBySourceExternalId(item.source_id, item.external_id)
        if (current?.content_hash != item.content_hash || current?.ai_input_hash != item.ai_input_hash || current?.article_id != item.article_id ||
            current?.import_status != item.import_status
        ) false else save()
    }

    fun cacheContentIfUnchanged(item: External_favorite_item, content: ExternalFavoriteSupplement, saveOriginal: () -> Boolean): Boolean =
        saveAiResultIfUnchanged(item) {
            if (!saveOriginal()) false else {
                q.updateExternalFavoriteItemContentCache(favoriteContentCacheJson(item, content), Clock.System.now().toEpochMilliseconds(), item.id)
                true
            }
        }

    fun markContentRefreshPending(item: External_favorite_item) {
        q.updateExternalFavoriteItemContentCache(favoriteContentRefreshJson(item), Clock.System.now().toEpochMilliseconds(), item.id)
        markAiState(item.id, ExternalItemAiStatus.pending.name)
    }

    fun clearContentRefreshFlag(item: External_favorite_item) {
        val current = getBySourceExternalId(item.source_id, item.external_id) ?: return
        val cleared = favoriteContentWithoutRefreshFlag(current.normalized_json)
        if (cleared != current.normalized_json) q.updateExternalFavoriteItemContentCache(cleared, Clock.System.now().toEpochMilliseconds(), item.id)
    }

    fun findXArticleReference(url: String): External_favorite_item? {
        val canonical = canonicalizeXArticleUrl(url) ?: return null
        return q.selectExternalFavoriteXArticleReferences("%/i/article/${canonical.substringAfterLast('/')}%",
            "%/i/article/${canonical.substringAfterLast('/')}%").executeAsList().firstOrNull {
            xArticleOwningPostId(it) != null && (canonicalizeXArticleUrl(it.canonical_url.orEmpty()) == canonical ||
                canonicalizeXArticleUrl(favoriteMetadata(it.normalized_json).textValue("primary_url").orEmpty()) == canonical ||
                canonicalizeXArticleUrl(previousCachedArticleUrl(it).orEmpty()) == canonical)
        }
    }

    fun findXPostReference(postId: String): External_favorite_item? =
        q.selectExternalFavoriteXPostReferences(postId, "%/status/$postId\"%").executeAsList()
            .firstOrNull { it.external_id == postId || xArticleOwningPostId(it) == postId }

    fun importedXArticleRepairBatch(sourceId: Long, afterId: Long, limit: Long): List<External_favorite_item> =
        q.selectExternalFavoriteItemsXArticleRepairBatch(sourceId, afterId, limit, ::externalFavoriteItemWithArticle).executeAsList()

    fun requeueFailedAiBySource(sourceId: Long) = q.transaction {
        retryableAiBySource(sourceId, Long.MAX_VALUE).filter { it.ai_status == ExternalItemAiStatus.failed.name }
            .forEach { markAiState(it.id, ExternalItemAiStatus.pending.name) }
    }

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

    fun hasDeferredContentBySource(sourceId: Long): Boolean =
        q.countExternalFavoriteItemsDeferredContentBySource(sourceId).executeAsOne() > 0

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

    private fun External_favorite_item.hasChangedDraftContent(draft: ExternalFavoriteItemDraft): Boolean {
        val bodyChanged = ai_input_hash != draft.aiInputHash ||
            canonical_url != draft.canonicalUrl ||
            title != draft.title ||
            text != draft.text ||
            author_name != draft.authorName ||
            source_created_at != draft.sourceCreatedAt ||
            favorited_at != draft.favoritedAt ||
            sourceFavoriteMetadata(normalized_json) != sourceFavoriteMetadata(draft.normalizedJson) ||
            debug_json != draft.debugJson
        val onlyMetricsChanged = provider == "x" && !bodyChanged &&
            favoriteMetadata(normalized_json)["public_metrics"] != favoriteMetadata(draft.normalizedJson)["public_metrics"]
        return bodyChanged || (content_hash != draft.contentHash && !onlyMetricsChanged)
    }

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

data class ExternalFavoriteOrderState(
    val oldest: Long?,
    val newest: Long?,
    val hasMissingFavoriteTime: Boolean,
)
