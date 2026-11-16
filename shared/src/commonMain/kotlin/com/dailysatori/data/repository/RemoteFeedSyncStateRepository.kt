package com.dailysatori.data.repository

import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.remotenews.RemoteFeedStagedArticle
import com.dailysatori.service.remotenews.RemoteFeedSyncState
import com.dailysatori.service.remotenews.remoteFeedPagingModeOfName
import com.dailysatori.shared.db.DailySatoriDatabase

/**
 * Persists the four separated facts of one subscription: the confirmed checkpoint, the active gap
 * generation with its scan progress, the visited documents and the staged candidates. Everything is
 * keyed by source, so deleting the source removes it and a changed entry address starts over.
 */
class RemoteFeedSyncStateRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun find(remoteSourceId: Long): RemoteFeedSyncState? =
        q.selectRemoteFeedSyncState(remoteSourceId).executeAsOneOrNull()?.let { row ->
            RemoteFeedSyncState(
                entryUrl = row.entry_url,
                mode = remoteFeedPagingModeOfName(row.paging_mode),
                baselineReady = row.baseline_ready == 1L,
                checkpointIds = row.checkpoint_ids.split(',').mapNotNull { it.toLongOrNull() },
                targetEntryIds = row.target_entry_ids.split(',').mapNotNull { it.toLongOrNull() },
                confirmedAnchorUrl = row.confirmed_anchor_url,
                confirmedAt = row.confirmed_at,
                generation = row.generation,
                gapOpen = row.gap_open == 1L,
                scanStartUrl = row.scan_start_url,
                scanStartFingerprint = row.scan_start_fingerprint,
                cursorUrl = row.cursor_url,
                lastPageUrl = row.last_page_url,
                lastPageFingerprint = row.last_page_fingerprint,
                scanPages = row.scan_pages.toInt(),
                stagedBytes = row.staged_bytes.toInt(),
                closureReached = row.closure_reached == 1L,
                blocked = row.blocked == 1L,
                blockedReason = row.blocked_reason,
            )
        }

    fun save(remoteSourceId: Long, state: RemoteFeedSyncState, now: Long) {
        q.upsertRemoteFeedSyncState(
            remote_source_id = remoteSourceId,
            entry_url = state.entryUrl,
            paging_mode = state.mode.name.lowercase(),
            baseline_ready = if (state.baselineReady) 1L else 0L,
            checkpoint_ids = state.checkpointIds.joinToString(","),
            target_entry_ids = state.targetEntryIds.joinToString(","),
            confirmed_anchor_url = state.confirmedAnchorUrl,
            confirmed_at = state.confirmedAt,
            generation = state.generation,
            gap_open = if (state.gapOpen) 1L else 0L,
            scan_start_url = state.scanStartUrl,
            scan_start_fingerprint = state.scanStartFingerprint,
            cursor_url = state.cursorUrl,
            last_page_url = state.lastPageUrl,
            last_page_fingerprint = state.lastPageFingerprint,
            scan_pages = state.scanPages.toLong(),
            staged_bytes = state.stagedBytes.toLong(),
            closure_reached = if (state.closureReached) 1L else 0L,
            blocked = if (state.blocked) 1L else 0L,
            blocked_reason = state.blockedReason,
            updated_at = now,
        )
    }

    /** Remote ids of [ids] that are already part of the article store of this source. */
    fun knownRemoteArticleIds(remoteSourceId: Long, ids: List<Long>): Set<Long> {
        if (ids.isEmpty()) return emptySet()
        return q.selectKnownRemoteArticleIds(remoteSourceId, ids).executeAsList().toSet()
    }

    fun stagedArticles(remoteSourceId: Long, generation: Long, includeStale: Boolean = false): List<RemoteFeedStagedArticle> {
        val rows = if (includeStale) {
            q.selectStagedRemoteArticles(remoteSourceId, generation).executeAsList()
        } else {
            q.selectActiveStagedRemoteArticles(remoteSourceId, generation).executeAsList()
        }
        return rows.map { row ->
            RemoteFeedStagedArticle(
                id = row.id,
                generation = row.generation,
                sequence = row.sequence.toInt(),
                stale = row.stale == 1L,
                pageUrl = row.page_url,
                article = RemoteArticle(
                    id = row.remote_article_id,
                    title = row.title,
                    url = row.url,
                    summary = row.summary,
                    content = row.content,
                    publishedAt = row.published_at,
                ),
            )
        }
    }

    fun stagedBytes(remoteSourceId: Long, generation: Long): Int =
        q.selectActiveStagedRemoteArticles(remoteSourceId, generation).executeAsList().sumOf { it.bytes.toInt() }

    /** Stages one page of candidates; returns the number of newly staged bytes. */
    fun stageArticles(
        remoteSourceId: Long,
        generation: Long,
        pageUrl: String,
        sequenceBase: Int,
        articles: List<RemoteArticle>,
        now: Long,
    ): Int {
        var added = 0
        articles.forEachIndexed { index, article ->
            val bytes = stagedBytesOf(article)
            val existing = q.selectStagedRemoteArticle(remoteSourceId, generation, article.id).executeAsOneOrNull()
            q.upsertStagedRemoteArticle(
                remote_source_id = remoteSourceId,
                generation = generation,
                remote_article_id = article.id,
                page_url = pageUrl,
                sequence = (sequenceBase + index).toLong(),
                title = article.title,
                url = article.url,
                summary = article.summary,
                content = article.content,
                published_at = article.publishedAt,
                bytes = bytes.toLong(),
                created_at = now,
            )
            if (existing == null) added += bytes
        }
        return added
    }

    fun markStagedStale(remoteSourceId: Long, generation: Long) = q.markStagedRemoteArticleStale(remoteSourceId, generation)

    fun deleteStagedArticle(id: Long) = q.deleteStagedRemoteArticle(id)

    fun deleteGenerationStaging(remoteSourceId: Long, generation: Long) = q.deleteStagedGeneration(remoteSourceId, generation)

    fun scanNodeUrls(remoteSourceId: Long, generation: Long): Set<String> =
        q.selectRemoteFeedScanNodes(remoteSourceId, generation).executeAsList().toSet()

    fun scanNodeFingerprint(remoteSourceId: Long, generation: Long, url: String): String? =
        q.selectRemoteFeedScanNode(remoteSourceId, generation, url).executeAsOneOrNull()?.fingerprint

    fun recordScanNode(remoteSourceId: Long, generation: Long, url: String, fingerprint: String, now: Long) {
        q.upsertRemoteFeedScanNode(remoteSourceId, generation, url, fingerprint, now)
    }

    fun deleteScanNodes(remoteSourceId: Long, generation: Long) = q.deleteRemoteFeedScanNodes(remoteSourceId, generation)

    private fun stagedBytesOf(article: RemoteArticle): Int = listOf(
        article.title.orEmpty(),
        article.url.orEmpty(),
        article.summary.orEmpty(),
        article.content.orEmpty(),
        article.publishedAt.orEmpty(),
    ).sumOf { it.encodeToByteArray().size }
}
