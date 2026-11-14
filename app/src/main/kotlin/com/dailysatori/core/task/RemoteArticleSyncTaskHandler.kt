package com.dailysatori.core.task

import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.diagnostics.DiagnosticCode
import com.dailysatori.service.diagnostics.DiagnosticLog
import com.dailysatori.service.diagnostics.DiagnosticSource
import com.dailysatori.service.remotenews.RemoteArticleSyncService
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
    private val remoteArticleSyncService: RemoteArticleSyncService,
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
                }
                // Another task already synced this source: the source is in the repository, not refetched.
                NewsSourceFetchShare.Merged -> successfulSources += 1
                is NewsSourceFetchShare.Failed -> failures += source.name + ": 同步失败，稍后重试"
            }
            reporter.report(
                current = (index + 1).toLong(),
                total = sources.size.toLong(),
                message = "已同步 $inserted 篇新文章，更新 $updated 篇",
                checkpointJson = """{"inserted":$inserted,"updated":$updated,"skipped":$skipped}""",
            )
        }

        val resultJson = """{"inserted":$inserted,"updated":$updated,"skipped":$skipped,"failureCount":${failures.size}}"""
        return remoteArticleSyncOutcome(successfulSources, failures, resultJson)
    }

    private class SourceFetch(
        val successful: Int,
        val inserted: Int,
        val updated: Int,
        val skipped: Int,
        val failures: List<String>,
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
        is RemoteNewsResult.Success -> when (val result = run {
            syncStage("fetch_start")
            remoteNewsService.fetchSourceArticles(config.value, limit = 50)
        }) {
            is RemoteNewsResult.Success -> {
                syncStage("sync_start")
                val sync = remoteArticleSyncService.syncSourceArticles(
                    remoteSourceId = source.id,
                    sourceDate = sourceDate,
                    articles = result.value.articles,
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
                SourceFetch(1, sync.inserted, sync.updated, sync.skipped,
                    sync.failures.map { source.name + ": " + it })
            }
            is RemoteNewsResult.Failure -> SourceFetch(0, 0, 0, 0, listOf(source.name + ": " + result.message))
        }
        is RemoteNewsResult.Failure -> SourceFetch(0, 0, 0, 0, listOf(source.name + ": " + config.message))
    }

    companion object {
        const val TYPE = "remote_article_sync"
    }
}

internal fun remoteArticleSyncOutcome(
    successfulSources: Int,
    failures: List<String>,
    resultJson: String,
): AsyncTaskExecutionResult = if (successfulSources == 0 && failures.isNotEmpty()) {
    AsyncTaskExecutionResult.RetryableFailure("remote_article_sync_failed", failures.joinToString("\n"))
} else {
    AsyncTaskExecutionResult.Success(resultJson)
}

fun remoteArticleSyncTaskPayloadJson(mode: String, sourceId: Long? = null): String =
    Json.encodeToString(RemoteArticleSyncTaskPayload(mode = mode, sourceId = sourceId))
