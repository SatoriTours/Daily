package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.dailysatori.service.remotenews.RemoteFeedHistoryState
import com.dailysatori.service.remotenews.RemoteFeedHistoryStatus
import com.dailysatori.service.remotenews.RemoteFeedHistoryStrategy
import com.dailysatori.service.remotenews.RemoteFeedPagingMode
import com.dailysatori.service.remotenews.remoteFeedPagingModeOfName
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Remote_feed_history_state
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * First-import history of one subscription, stored apart from the incremental RFC 5005 gap state.
 *
 * Ownership is explicit: every user control action (start / pause / resume) runs in one transaction and
 * replaces the walk's [RemoteFeedHistoryState.controlEpoch]. The running walk may only persist progress
 * while it still presents that epoch, so a snapshot taken before a pause/restart can never revive or
 * overwrite the newer intent, and an update never re-creates a removed subscription's row.
 */
class RemoteFeedHistoryRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun find(remoteSourceId: Long): RemoteFeedHistoryState? = select(remoteSourceId)

    fun observe(remoteSourceId: Long): Flow<RemoteFeedHistoryState?> =
        q.selectRemoteFeedHistoryState(remoteSourceId).asFlow().mapToOneOrNull(Dispatchers.IO).map { it?.let(::toState) }

    /** Full write; used for seeding and repair, never by a running walk. */
    fun save(remoteSourceId: Long, state: RemoteFeedHistoryState, now: Long) {
        write(remoteSourceId, state, now)
    }

    /**
     * One transaction that reconciles the stored walk with the document just read: a changed address or a
     * changed layout drops the incompatible cursor, an untouched subscription starts its walk when a
     * first-import range is supplied, and a paused/finished/blocked walk is returned unchanged.
     */
    fun prepareWalk(
        remoteSourceId: Long,
        entryUrl: String,
        mode: RemoteFeedPagingMode,
        autoStrategy: RemoteFeedHistoryStrategy?,
        now: Long,
    ): RemoteFeedHistoryState? = q.transactionWithResult {
        var stored = select(remoteSourceId)
        if (stored == null) {
            if (autoStrategy == null) return@transactionWithResult null
            write(
                remoteSourceId,
                RemoteFeedHistoryState(
                    entryUrl = entryUrl,
                    mode = mode,
                    strategy = autoStrategy,
                    status = RemoteFeedHistoryStatus.PENDING,
                    targetCount = autoStrategy.targetCount ?: 0,
                    startedAt = now,
                ),
                now,
            )
            return@transactionWithResult select(remoteSourceId)
        }
        if (stored.entryUrl.isNotEmpty() && stored.entryUrl != entryUrl) {
            stored = stored.copy(
                entryUrl = entryUrl,
                mode = mode,
                startUrl = null,
                cursorUrl = null,
                lastPageUrl = null,
                lastPageFingerprint = null,
                importedCount = 0,
                targetCount = stored.strategy.targetCount ?: 0,
                // A finished walk does not silently restart; an unfinished intent follows the new address.
                status = if (stored.status.finished || stored.status == RemoteFeedHistoryStatus.OFF) {
                    RemoteFeedHistoryStatus.OFF
                } else {
                    RemoteFeedHistoryStatus.PENDING
                },
                lastError = "",
                blockedReason = "",
                startedAt = stored.startedAt ?: now,
                completedAt = null,
            )
            write(remoteSourceId, stored, now)
        } else if (stored.mode != mode) {
            stored = stored.copy(
                mode = mode,
                startUrl = null,
                cursorUrl = null,
                lastPageUrl = null,
                lastPageFingerprint = null,
                lastError = "",
                blockedReason = "",
                completedAt = null,
                status = when (stored.status) {
                    RemoteFeedHistoryStatus.COMPLETE -> RemoteFeedHistoryStatus.COMPLETE
                    RemoteFeedHistoryStatus.PAUSED -> RemoteFeedHistoryStatus.PAUSED
                    RemoteFeedHistoryStatus.BLOCKED -> RemoteFeedHistoryStatus.BLOCKED
                    RemoteFeedHistoryStatus.OFF -> RemoteFeedHistoryStatus.OFF
                    else -> RemoteFeedHistoryStatus.PENDING
                },
            )
            write(remoteSourceId, stored, now)
        }
        if (stored.status == RemoteFeedHistoryStatus.OFF) {
            val auto = autoStrategy ?: return@transactionWithResult stored
            stored = stored.copy(
                strategy = auto,
                targetCount = auto.targetCount ?: 0,
                status = RemoteFeedHistoryStatus.PENDING,
                startedAt = stored.startedAt ?: now,
            )
            write(remoteSourceId, stored, now)
        }
        select(remoteSourceId)
    }

    /**
     * Explicit history backfill / restart. Every call is a user control action: it replaces the ownership
     * epoch and clears the progress, so a run that is still in flight can never persist its old snapshot.
     */
    fun start(remoteSourceId: Long, strategy: RemoteFeedHistoryStrategy, now: Long): RemoteFeedHistoryState =
        q.transactionWithResult {
            val current = select(remoteSourceId)
            val started = RemoteFeedHistoryState(
                entryUrl = current?.entryUrl.orEmpty(),
                mode = current?.mode ?: RemoteFeedPagingMode.PLAIN,
                strategy = strategy,
                status = RemoteFeedHistoryStatus.PENDING,
                controlEpoch = (current?.controlEpoch ?: 0L) + 1L,
                targetCount = strategy.targetCount ?: 0,
                startedAt = now,
            )
            write(remoteSourceId, started, now)
            started
        }

    /**
     * Stops the walk after the page in flight: the in-flight page may still commit its articles, but its
     * progress write is rejected because the epoch moved. Stored articles, read flags and AI state stay.
     */
    fun pause(remoteSourceId: Long, now: Long): Boolean = q.transactionWithResult {
        val current = select(remoteSourceId) ?: return@transactionWithResult false
        if (!current.status.open) return@transactionWithResult false
        write(
            remoteSourceId,
            current.copy(status = RemoteFeedHistoryStatus.PAUSED, controlEpoch = current.controlEpoch + 1L),
            now,
        )
        true
    }

    /** Continues a paused or blocked walk from its stored cursor under a fresh ownership epoch. */
    fun resume(remoteSourceId: Long, now: Long): Boolean = q.transactionWithResult {
        val current = select(remoteSourceId) ?: return@transactionWithResult false
        if (current.status !in setOf(
                RemoteFeedHistoryStatus.PAUSED,
                RemoteFeedHistoryStatus.BLOCKED,
                RemoteFeedHistoryStatus.PENDING,
            )
        ) {
            return@transactionWithResult false
        }
        write(
            remoteSourceId,
            current.copy(
                status = RemoteFeedHistoryStatus.PENDING,
                controlEpoch = current.controlEpoch + 1L,
                lastError = "",
                blockedReason = "",
                completedAt = null,
            ),
            now,
        )
        true
    }

    /**
     * Records one page (or a terminal state) of the walk that still owns [expectedEpoch]. False means a
     * user control action, a newer run or a removed subscription already took the walk away; the caller
     * must stop instead of continuing with its stale snapshot.
     */
    fun compareAndSetProgress(
        sourceId: Long,
        expectedEpoch: Long,
        state: RemoteFeedHistoryState,
        now: Long,
    ): Boolean = q.transactionWithResult {
        q.updateRemoteFeedHistoryStateIfEpoch(
            entry_url = state.entryUrl,
            paging_mode = state.mode.name.lowercase(),
            strategy = state.strategy.dbValue,
            status = state.status.dbValue,
            start_url = state.startUrl,
            cursor_url = state.cursorUrl,
            last_page_url = state.lastPageUrl,
            last_page_fingerprint = state.lastPageFingerprint,
            imported_count = state.importedCount.toLong(),
            target_count = state.targetCount.toLong(),
            pages_read = state.pagesRead.toLong(),
            requests = state.requests.toLong(),
            last_error = state.lastError,
            blocked_reason = state.blockedReason,
            started_at = state.startedAt,
            completed_at = state.completedAt,
            updated_at = now,
            remote_source_id = sourceId,
            control_epoch = expectedEpoch,
        ).value > 0L
    }

    fun delete(remoteSourceId: Long) = q.deleteRemoteFeedHistoryState(remoteSourceId)

    private fun select(remoteSourceId: Long): RemoteFeedHistoryState? =
        q.selectRemoteFeedHistoryState(remoteSourceId).executeAsOneOrNull()?.let(::toState)

    private fun write(remoteSourceId: Long, state: RemoteFeedHistoryState, now: Long) {
        q.upsertRemoteFeedHistoryState(
            remote_source_id = remoteSourceId,
            entry_url = state.entryUrl,
            paging_mode = state.mode.name.lowercase(),
            strategy = state.strategy.dbValue,
            status = state.status.dbValue,
            control_epoch = state.controlEpoch,
            start_url = state.startUrl,
            cursor_url = state.cursorUrl,
            last_page_url = state.lastPageUrl,
            last_page_fingerprint = state.lastPageFingerprint,
            imported_count = state.importedCount.toLong(),
            target_count = state.targetCount.toLong(),
            pages_read = state.pagesRead.toLong(),
            requests = state.requests.toLong(),
            last_error = state.lastError,
            blocked_reason = state.blockedReason,
            started_at = state.startedAt,
            completed_at = state.completedAt,
            updated_at = now,
        )
    }

    private fun toState(row: Remote_feed_history_state): RemoteFeedHistoryState =
        RemoteFeedHistoryState(
            entryUrl = row.entry_url,
            mode = remoteFeedPagingModeOfName(row.paging_mode),
            strategy = RemoteFeedHistoryStrategy.ofDbValue(row.strategy) ?: RemoteFeedHistoryStrategy.ALL_HISTORY,
            status = RemoteFeedHistoryStatus.ofDbValue(row.status),
            controlEpoch = row.control_epoch,
            startUrl = row.start_url,
            cursorUrl = row.cursor_url,
            lastPageUrl = row.last_page_url,
            lastPageFingerprint = row.last_page_fingerprint,
            importedCount = row.imported_count.toInt(),
            targetCount = row.target_count.toInt(),
            pagesRead = row.pages_read.toInt(),
            requests = row.requests.toInt(),
            lastError = row.last_error,
            blockedReason = row.blocked_reason,
            startedAt = row.started_at,
            completedAt = row.completed_at,
        )
}

