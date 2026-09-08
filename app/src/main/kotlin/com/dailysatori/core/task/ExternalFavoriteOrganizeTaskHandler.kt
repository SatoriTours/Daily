package com.dailysatori.core.task

import com.dailysatori.core.worker.ExternalFavoriteSyncScheduler
import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.externalfavorites.ExternalFavoriteAiOrganizer
import com.dailysatori.service.externalfavorites.FavoriteSyncHttpLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ExternalFavoriteOrganizePayload(val sourceId: Long)

class ExternalFavoriteOrganizeTaskHandler(
    private val organizer: ExternalFavoriteAiOrganizer,
    private val items: ExternalFavoriteItemRepository,
    private val sources: ExternalFavoriteSourceRepository,
    private val scheduler: ExternalFavoriteSyncScheduler,
    private val httpLogger: FavoriteSyncHttpLogger,
) : AsyncTaskHandler {
    override val type = "external_favorite_organize"

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult {
        val payload = runCatching { Json.decodeFromString<ExternalFavoriteOrganizePayload>(payloadJson) }.getOrNull()
            ?: return AsyncTaskExecutionResult.PermanentFailure("invalid_payload", "收藏整理参数无效")
        val source = sources.getById(payload.sourceId)
            ?: return AsyncTaskExecutionResult.PermanentFailure("missing_source", "收藏来源已删除")
        if (source.enabled == 0L) return AsyncTaskExecutionResult.PermanentFailure("source_disabled", "收藏来源已停用")
        var failed = 0
        reporter.report(0, 0, "收藏已保存，准备 AI 整理（每批最多 20 条，单条最多 90 秒）")
        val processed = organizer.organizePendingForSource(
            payload.sourceId, limit = 20,
            httpLogger = httpLogger, taskId = taskId,
        ) { progress ->
            failed = progress.failed
            reporter.report(progress.processed.toLong(), progress.total.toLong(),
                "已整理 ${progress.processed - failed} 条，失败 $failed 条 · 最近处理收藏 #${progress.itemId}",
                checkpointJson = """{"processed":${progress.processed},"failed":$failed,"itemId":${progress.itemId}}""",
            )
        }
        val hasMore = items.pendingAiBySource(payload.sourceId, 1).isNotEmpty()
        if (hasMore) scheduler.enqueueOrganization(payload.sourceId, afterTaskId = taskId)
        val suffix = if (hasMore) "，剩余内容已安排下一批" else ""
        reporter.report(processed.toLong(), processed.toLong(), "本批整理结束：成功 ${processed - failed} 条，失败 $failed 条$suffix")
        return AsyncTaskExecutionResult.Success("""{"processed":$processed,"failed":$failed}""")
    }
}
