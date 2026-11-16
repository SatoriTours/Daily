package com.dailysatori.core.task

import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.RemoteFeedHistoryRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.diagnostics.DiagnosticCode
import com.dailysatori.service.diagnostics.DiagnosticLog
import com.dailysatori.service.diagnostics.DiagnosticSource
import com.dailysatori.service.remotenews.RemoteArticleSyncProgress
import com.dailysatori.service.remotenews.RemoteFeedHistoryPolicy
import com.dailysatori.service.remotenews.RemoteFeedSourceSyncService
import com.dailysatori.service.remotenews.RemoteNewsResult
import com.dailysatori.service.remotenews.RemoteNewsService
import com.dailysatori.service.unifiednews.dailyUnifiedNewsWindowFor
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RemoteArticleSyncTaskPayload(
    val mode: String,
    val sourceId: Long? = null,
)

class RemoteArticleSyncTaskHandler(
    private val sourceRepo: RemoteNewsSourceRepository,
    private val remoteNewsService: RemoteNewsService,
    private val remoteFeedSyncService: RemoteFeedSourceSyncService,
    private val clock: Clock = Clock.System,
    private val coordinator: RemoteArticleSyncCoordinator = RemoteArticleSyncCoordinator(),
    /** Durable task store used to chain one bounded history round after another. */
    private val taskRepo: AsyncTaskRepository? = null,
    private val scheduleTask: ((Long) -> Unit)? = null,
    /** Stored history walk; a command that could not own the source still wakes a pending walk. */
    private val historyRepo: RemoteFeedHistoryRepository? = null,
) : AsyncTaskHandler {
    override val type: String = TYPE

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult {
        val payload = runCatching { Json.decodeFromString<RemoteArticleSyncTaskPayload>(payloadJson) }
            .getOrElse {
                return AsyncTaskExecutionResult.PermanentFailure("invalid_payload", "远程文章同步任务参数无效")
            }
        val sources = payload.sourceId?.let { sourceId ->
            sourceRepo.getById(sourceId)?.takeIf { it.enabled == 1L }?.let(::listOf).orEmpty()
        } ?: sourceRepo.getEnabled()
        if (sources.isEmpty()) return AsyncTaskExecutionResult.Success()

        val sourceDate = dailyUnifiedNewsWindowFor().summaryDate
        val now = clock.now().toEpochMilliseconds()
        var inserted = 0
        var updated = 0
        var skipped = 0
        var successfulSources = 0
        var transientFailures = 0
        var pendingCatchUpSources = 0
        var gapSources = 0
        val failures = mutableListOf<String>()
        // Sources whose first-import history stored pages and can continue in another bounded round.
        val historyContinuations = mutableListOf<Long>()
        var historyPendingSources = 0
        var historyTransientSources = 0

        reporter.report(0, sources.size.toLong(), "准备同步远程文章", checkpointJson = """{"mode":"${payload.mode}"}""")
        sources.forEachIndexed { index, source ->
            // One owner per source: a concurrent task for the same source is merged into this run, not
            // fetched twice, while other sources keep running in parallel.
            when (val shared = coordinator.share(
                sourceId = source.id,
                // Only a source that was fetched and synced without failures may be reused or counted.
                isSuccess = { fetched -> fetched.successful > 0 && fetched.failures.isEmpty() },
                // A user command (source sync / history round) owns its own fetch: reusing the previous
                // successful run would silently drop a pause/resume intent or hide walk progress.
                reuseSuccessfulFetch = payload.mode != MODE_HISTORY && payload.mode != MODE_MANUAL_SOURCE,
            ) { fetchSource(source, sourceDate, now, reporter, index, sources.size) }) {
                is NewsSourceFetchShare.Owned -> {
                    successfulSources += shared.value.successful
                    inserted += shared.value.inserted
                    updated += shared.value.updated
                    skipped += shared.value.skipped
                    failures += shared.value.failures
                    if (shared.value.transient) transientFailures += 1
                    if (shared.value.pendingCatchUp) pendingCatchUpSources += 1
                    if (shared.value.gap) gapSources += 1
                    if (shared.value.historyPending) {
                        historyPendingSources += 1
                        historyContinuations += source.id
                    }
                    if (shared.value.historyTransient) historyTransientSources += 1
                }
                // Another task already synced this source: the source is in the repository, not refetched.
                // An open history walk still needs its next bounded round, so it is chained explicitly
                // instead of relying on the other owner to finish the whole walk.
                NewsSourceFetchShare.Merged -> {
                    successfulSources += 1
                    if (historyRepo?.find(source.id)?.status?.open == true) historyContinuations += source.id
                }
                is NewsSourceFetchShare.Failed -> {
                    // A failed shared fetch has no classified cause here: keep it retryable.
                    transientFailures += 1
                    failures += source.name + ": 同步失败，稍后重试"
                }
            }
            reporter.report(
                current = (index + 1).toLong(),
                total = sources.size.toLong(),
                message = "已同步 $inserted 篇新文章，更新 $updated 篇",
                checkpointJson = """{"inserted":$inserted,"updated":$updated,"skipped":$skipped}""",
            )
        }

        val resultJson = """{"inserted":$inserted,"updated":$updated,"skipped":$skipped,"failureCount":${failures.size},"pendingCatchUp":${pendingCatchUpSources > 0},"gap":${gapSources > 0},"historyPending":${historyPendingSources > 0}}"""
        val outcome = remoteArticleSyncOutcome(
            successfulSources = successfulSources,
            failures = failures,
            resultJson = resultJson,
            retryable = transientFailures > 0,
            // A readable entry document must not hide a failing history page: the bounded task retry is
            // the recovery path, so it is forced even when every source entry document was stored.
            forceRetry = historyTransientSources > 0,
        )
        if (outcome !is AsyncTaskExecutionResult.Success) return outcome
        if (payload.mode == MODE_HISTORY && payload.sourceId != null) {
            // One bounded history round per task: continue itself while it stored pages and the source
            // still has unread history. The same task row carries the progress, so no task is swallowed.
            return if (payload.sourceId in historyContinuations) {
                AsyncTaskExecutionResult.Yield(HISTORY_CONTINUATION_DELAY_MS)
            } else {
                outcome
            }
        }
        historyContinuations.forEach(::enqueueHistoryContinuation)
        return outcome
    }

    /**
     * Chains the next bounded history round for one source. The unique key is scoped to the source with a
     * trailing separator, so source 5 can never absorb source 50 and a repeated trigger reuses the task
     * that is already carrying the same walk instead of duplicating it.
     */
    private fun enqueueHistoryContinuation(sourceId: Long) {
        val repo = taskRepo ?: return
        val schedule = scheduleTask ?: return
        val walk = historyRepo?.find(sourceId) ?: return
        if (!walk.status.open) return
        // The ownership epoch is part of the task identity: the same walk reuses one task, while a newer
        // user control action always gets its own, so a finishing task can never swallow the next round.
        val taskId = repo.enqueueUniqueFamily(
            type = TYPE,
            payloadJson = remoteArticleSyncTaskPayloadJson(mode = MODE_HISTORY, sourceId = sourceId),
            uniqueKey = remoteArticleSyncHistoryTaskKey(sourceId, walk.controlEpoch),
            uniqueKeyPrefix = remoteArticleSyncHistoryTaskPrefix(sourceId, walk.controlEpoch),
        )
        schedule(taskId)
    }

    private class SourceFetch(
        val successful: Int,
        val inserted: Int,
        val updated: Int,
        val skipped: Int,
        val failures: List<String>,
        /** True only for failures that can heal by waiting (5xx, timeout, rate limit). */
        val transient: Boolean = false,
        /** Older documents are still unread; the next run continues the bounded catch-up. */
        val pendingCatchUp: Boolean = false,
        /** The paging/archive chain could not be verified, so the stored history has a known hole. */
        val gap: Boolean = false,
        /** The first-import history walk still has unread documents and may continue. */
        val historyPending: Boolean = false,
        /** The history round failed on something that may heal later. */
        val historyTransient: Boolean = false,
        /** The history round failed; the reason is part of [failures]. */
        val historyFailed: Boolean = false,
    )

    /**
     * Stage evidence for one source. The export redactor keeps only allow-listed stage values, and these carry
     * no URL, token or content.
     */
    private fun syncStage(stage: String) {
        DiagnosticLog.diagnostics.emit(
            DiagnosticCode.OPERATION_PROGRESS, DiagnosticSource.TASK,
            fields = mapOf("syncStage" to stage),
        )
    }

    private suspend fun fetchSource(
        source: com.dailysatori.shared.db.Remote_news_source,
        sourceDate: String,
        now: Long,
        reporter: AsyncTaskProgressReporter,
        sourceIndex: Int,
        sourceCount: Int,
    ): SourceFetch = when (val config = remoteNewsService.configOrFailure(source.base_url, source.api_token)) {
        is RemoteNewsResult.Success -> {
            syncStage("fetch_start")
            // The entry page is read first on every run; the bounded RFC 5005 catch-up follows afterwards,
            // and it resumes from the stored position instead of restarting the subscription history.
            val sync = remoteFeedSyncService.sync(
                remoteSourceId = source.id,
                entryUrl = source.base_url,
                config = config.value,
                sourceDate = sourceDate,
                now = now,
                // A source that was just added imports its chosen range; an existing source keeps its
                // articles until the user explicitly starts a history backfill.
                historyPolicy = RemoteFeedHistoryPolicy.strategyOf(source.history_policy),
                onArticleProgress = { progress: RemoteArticleSyncProgress ->
                    // The source is still incomplete, so current/total stay source-level; the message shows the
                    // real article stage and a failed article is never counted as a processed success.
                    reporter.report(
                        current = sourceIndex.toLong(),
                        total = sourceCount.toLong(),
                        message = "已处理 ${progress.processed}/${progress.total} 篇，新增 ${progress.inserted} 篇，跳过 ${progress.skipped} 篇",
                        checkpointJson = """{"inserted":${progress.inserted},"updated":${progress.updated},"skipped":${progress.skipped}}""",
                    )
                },
            )
            syncStage("sync_done")
            SourceFetch(
                // Only a stored entry document proves the subscription is reachable; a chain failure
                // alone never turns a reachable source into a failing one.
                successful = if (sync.entryRead) 1 else 0,
                inserted = sync.inserted,
                updated = sync.updated,
                skipped = sync.skipped,
                // Article-level failures and a chain failure both keep the source out of the reuse window.
                failures = sync.failures.map { source.name + ": " + it } +
                    (sync.message?.let { listOf(source.name + ": " + it) }.orEmpty()) +
                    // A budget message is progress, not a failure; only a real history failure is reported.
                    (sync.historyMessage?.takeIf { sync.historyFailed }?.let { listOf(source.name + ": " + it) }.orEmpty()),
                // A failing document keeps its retry class even when this run stored no article at all.
                transient = sync.transient,
                pendingCatchUp = sync.pending,
                gap = sync.gap,
                historyPending = sync.historyPending,
                historyTransient = sync.historyTransient,
                historyFailed = sync.historyFailed,
            )
        }
        is RemoteNewsResult.Failure -> SourceFetch(
            0, 0, 0, 0, listOf(source.name + ": " + config.message), transient = config.transient,
        )
    }


    companion object {
        const val TYPE = "remote_article_sync"

        /** One source-scoped bounded history round; it continues itself until the walk finishes. */
        const val MODE_HISTORY = "history"

        /** A user-issued single-source sync (settings / source list); it always owns its fetch. */
        const val MODE_MANUAL_SOURCE = "manual_source"

        /** Small pause between bounded rounds keeps a long backfill from hammering the publisher. */
        const val HISTORY_CONTINUATION_DELAY_MS = 2_000L
    }
}

internal fun remoteArticleSyncHistoryTaskKey(sourceId: Long, controlEpoch: Long): String =
    remoteArticleSyncHistoryTaskPrefix(sourceId, controlEpoch) + "continue"

/**
 * Trailing separator keeps source 5 apart from source 50, and the control epoch keeps one user intent
 * apart from the next while the older task is still finishing.
 */
internal fun remoteArticleSyncHistoryTaskPrefix(sourceId: Long, controlEpoch: Long): String =
    "remote_article_sync:history:" + sourceId + ":epoch:" + controlEpoch + ":"

/**
 * Outcome of one sync run. A failure that cannot heal by waiting (404, unsupported body, broken
 * configuration) ends the task instead of retrying in the background, so it leaves the running list and
 * its successors are no longer blocked; only genuinely transient failures keep the bounded retry.
 */
internal fun remoteArticleSyncOutcome(
    successfulSources: Int,
    failures: List<String>,
    resultJson: String,
    retryable: Boolean,
    /** A failure that must use the bounded retry even when another part of the same run succeeded. */
    forceRetry: Boolean = false,
): AsyncTaskExecutionResult = when {
    forceRetry -> AsyncTaskExecutionResult.RetryableFailure(
        "remote_article_sync_failed",
        failures.joinToString("\n"),
    )
    successfulSources > 0 || failures.isEmpty() -> AsyncTaskExecutionResult.Success(resultJson)
    retryable -> AsyncTaskExecutionResult.RetryableFailure("remote_article_sync_failed", failures.joinToString("\n"))
    else -> AsyncTaskExecutionResult.PermanentFailure(
        "remote_article_sync_failed",
        failures.joinToString("\n") + "\n这些来源无法自动恢复，已停止重试；修正远程新闻设置后可手动重试",
    )
}

fun remoteArticleSyncTaskPayloadJson(mode: String, sourceId: Long? = null): String =
    Json.encodeToString(RemoteArticleSyncTaskPayload(mode = mode, sourceId = sourceId))
