package com.dailysatori.core.task

import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
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
        val failures = mutableListOf<String>()

        reporter.report(0, sources.size.toLong(), "准备同步远程文章", checkpointJson = """{"mode":"${payload.mode}"}""")
        sources.forEachIndexed { index, source ->
            when (val config = remoteNewsService.configOrFailure(source.base_url, source.api_token)) {
                is RemoteNewsResult.Success -> when (val result = remoteNewsService.fetchTopArticlesToday(config.value, page = 1, limit = 50)) {
                    is RemoteNewsResult.Success -> {
                        val sync = remoteArticleSyncService.syncSourceArticles(source.id, sourceDate, result.value.articles, now)
                        inserted += sync.inserted
                        updated += sync.updated
                        skipped += sync.skipped
                    }
                    is RemoteNewsResult.Failure -> failures += "${source.name}: ${result.message}"
                }
                is RemoteNewsResult.Failure -> failures += "${source.name}: ${config.message}"
            }
            reporter.report(
                current = (index + 1).toLong(),
                total = sources.size.toLong(),
                message = "已同步 $inserted 篇新文章，更新 $updated 篇",
                checkpointJson = """{"inserted":$inserted,"updated":$updated,"skipped":$skipped}""",
            )
        }

        return if (failures.isEmpty()) {
            AsyncTaskExecutionResult.Success("""{"inserted":$inserted,"updated":$updated,"skipped":$skipped}""")
        } else {
            AsyncTaskExecutionResult.RetryableFailure("remote_article_sync_failed", failures.joinToString("\n"))
        }
    }

    companion object {
        const val TYPE = "remote_article_sync"
    }
}

fun remoteArticleSyncTaskPayloadJson(mode: String, sourceId: Long? = null): String =
    Json.encodeToString(RemoteArticleSyncTaskPayload(mode = mode, sourceId = sourceId))
