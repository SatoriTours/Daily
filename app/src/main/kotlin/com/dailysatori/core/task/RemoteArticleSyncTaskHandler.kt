package com.dailysatori.core.task

import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.diagnostics.DiagnosticCode
import com.dailysatori.service.diagnostics.DiagnosticLog
import com.dailysatori.service.diagnostics.DiagnosticSource
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

        reporter.report(0, sources.size.toLong(), "准备同步远程文章", checkpointJson = """{"mode":"${payload.mode}"}""")
        sources.forEachIndexed { index, source ->
            // One owner per source: a concurrent task for the same source is merged into this run, not
            // fetched twice, while other sources keep running in parallel.
            when (val shared = coordinator.share(
                sourceId = source.id,
                // Only a source that was fetched and synced without failures may be reused or counted.
                isSuccess = { fetched -> fetched.successful > 0 && fetched.failures.isEmpty() },
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
                }
                // Another task already synced this source: the source is in the repository, not refetched.
                NewsSourceFetchShare.Merged -> successfulSources += 1
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

        val resultJson = """{"inserted":$inserted,"updated":$updated,"skipped":$skipped,"failureCount":${failures.size},"pendingCatchUp":${pendingCatchUpSources > 0},"gap":${gapSources > 0}}"""
        return remoteArticleSyncOutcome(
            successfulSources = successfulSources,
            failures = failures,
            resultJson = resultJson,
            retryable = transientFailures > 0,
        )
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
            ) { progress ->
                // The source is still incomplete, so current/total stay source-level; the message shows the
                // real article stage and a failed article is never counted as a processed success.
                reporter.report(
                    current = sourceIndex.toLong(),
                    total = sourceCount.toLong(),
                    message = "已处理 ${progress.processed}/${progress.total} 篇，新增 ${progress.inserted} 篇，跳过 ${progress.skipped} 篇",
                    checkpointJson = """{"inserted":${progress.inserted},"updated":${progress.updated},"skipped":${progress.skipped}}""",
                )
            }
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
                    (sync.message?.let { listOf(source.name + ": " + it) }.orEmpty()),
                // A failing document keeps its retry class even when this run stored no article at all.
                transient = sync.transient,
                pendingCatchUp = sync.pending,
                gap = sync.gap,
            )
        }
        is RemoteNewsResult.Failure -> SourceFetch(
            0, 0, 0, 0, listOf(source.name + ": " + config.message), transient = config.transient,
        )
    }


    companion object {
        const val TYPE = "remote_article_sync"
    }
}

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
): AsyncTaskExecutionResult = when {
    successfulSources > 0 || failures.isEmpty() -> AsyncTaskExecutionResult.Success(resultJson)
    retryable -> AsyncTaskExecutionResult.RetryableFailure("remote_article_sync_failed", failures.joinToString("\n"))
    else -> AsyncTaskExecutionResult.PermanentFailure(
        "remote_article_sync_failed",
        failures.joinToString("\n") + "\n这些来源无法自动恢复，已停止重试；修正远程新闻设置后可手动重试",
    )
}

fun remoteArticleSyncTaskPayloadJson(mode: String, sourceId: Long? = null): String =
    Json.encodeToString(RemoteArticleSyncTaskPayload(mode = mode, sourceId = sourceId))
