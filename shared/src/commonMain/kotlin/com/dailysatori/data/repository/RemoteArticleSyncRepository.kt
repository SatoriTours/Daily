package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Remote_article_sync_item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock

class RemoteArticleSyncRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun findByRemoteIdentity(remoteSourceId: Long, remoteArticleId: Long): Remote_article_sync_item? =
        q.selectRemoteArticleSyncItemByRemoteIdentity(remoteSourceId, remoteArticleId).executeAsOneOrNull()

    fun findByUrl(remoteSourceId: Long, url: String): Remote_article_sync_item? =
        q.selectRemoteArticleSyncItemByUrl(remoteSourceId, url).executeAsOneOrNull()

    fun findByArticleId(articleId: Long): Remote_article_sync_item? =
        q.selectRemoteArticleSyncItemByArticleId(articleId).executeAsOneOrNull()

    fun getMappingsBySourceDate(remoteSourceId: Long, sourceDate: String): List<Remote_article_sync_item> =
        q.selectRemoteArticleSyncMappingsBySourceDate(remoteSourceId, sourceDate).executeAsList()

    fun getLatestMappingsBySource(remoteSourceId: Long, limit: Long = 50): List<Remote_article_sync_item> =
        q.selectLatestRemoteArticleSyncMappingsBySource(remoteSourceId, limit).executeAsList()

    fun getArticlesBySourceDate(remoteSourceId: Long, sourceDate: String): List<Article> =
        q.selectRemoteArticleSyncItemsBySourceDate(remoteSourceId, sourceDate).executeAsList()

    fun getArticlesBySource(remoteSourceId: Long, limit: Long, offset: Long): List<Article> =
        q.selectRemoteArticleSyncItemsBySource(remoteSourceId, limit, offset).executeAsList()

    fun count(): Long = q.countRemoteArticleSyncItems().executeAsOne()

    fun observeCount(): Flow<Long> =
        q.countRemoteArticleSyncItems().asFlow().mapToOne(Dispatchers.IO)

    fun countBySource(remoteSourceId: Long): Long =
        q.countRemoteArticleSyncItemsBySource(remoteSourceId).executeAsOne()

    fun upsertMapping(
        remoteSourceId: Long,
        remoteArticleId: Long,
        articleId: Long,
        url: String?,
        sourceDate: String,
        now: Long,
    ) {
        val existing = findByRemoteIdentity(remoteSourceId, remoteArticleId)
        q.upsertRemoteArticleSyncItem(
            remote_source_id = remoteSourceId,
            remote_article_id = remoteArticleId,
            article_id = articleId,
            url = cleanRemoteArticleText(url),
            source_date = sourceDate,
            first_seen_at = existing?.first_seen_at ?: now,
            last_seen_at = now,
        )
    }

    fun recordSourceSnapshot(articleId: Long, sourceHash: String, requiresProcessing: Boolean) {
        val mapping = findByArticleId(articleId) ?: return
        val alreadyProcessed = mapping.processed_content_hash == sourceHash && sourceHash.isNotBlank()
        val processedHash = when {
            alreadyProcessed -> mapping.processed_content_hash
            !requiresProcessing -> sourceHash
            else -> mapping.processed_content_hash
        }
        val state = when {
            alreadyProcessed || !requiresProcessing -> REMOTE_PROCESSING_READY
            mapping.processed_content_hash.isBlank() -> REMOTE_PROCESSING_PENDING
            else -> REMOTE_PROCESSING_STALE
        }
        q.updateRemoteArticleSyncProcessingState(
            source_content_hash = sourceHash,
            processed_content_hash = processedHash,
            processing_state = state,
            processing_error = "",
            processing_version = mapping.processing_version,
            id = mapping.id,
        )
    }

    fun markProcessing(articleId: Long) {
        val mapping = findByArticleId(articleId) ?: return
        q.updateRemoteArticleSyncProcessingState(
            mapping.source_content_hash,
            mapping.processed_content_hash,
            REMOTE_PROCESSING_RUNNING,
            "",
            mapping.processing_version,
            mapping.id,
        )
    }

    fun markProcessingSuccess(articleId: Long, expectedSourceHash: String, version: String): Boolean {
        val mapping = findByArticleId(articleId) ?: return true
        if (mapping.source_content_hash != expectedSourceHash) {
            markProcessingFailure(articleId, "原文已更新，已丢弃过期处理结果", REMOTE_PROCESSING_STALE)
            return false
        }
        q.updateRemoteArticleSyncProcessingState(
            mapping.source_content_hash,
            expectedSourceHash,
            REMOTE_PROCESSING_READY,
            "",
            version,
            mapping.id,
        )
        return true
    }

    fun markProcessingFailure(articleId: Long, message: String, state: String = REMOTE_PROCESSING_FAILED) {
        val mapping = findByArticleId(articleId) ?: return
        q.updateRemoteArticleSyncProcessingState(
            mapping.source_content_hash,
            mapping.processed_content_hash,
            state,
            message.take(500),
            mapping.processing_version,
            mapping.id,
        )
    }

    fun markFavorited(articleId: Long, favoritedAt: Long = Clock.System.now().toEpochMilliseconds()) {
        q.markRemoteArticleFavorited(favoritedAt, articleId)
    }
}

const val REMOTE_PROCESSING_PENDING = "pending"
const val REMOTE_PROCESSING_RUNNING = "processing"
const val REMOTE_PROCESSING_READY = "ready"
const val REMOTE_PROCESSING_STALE = "stale"
const val REMOTE_PROCESSING_FAILED = "failed"
