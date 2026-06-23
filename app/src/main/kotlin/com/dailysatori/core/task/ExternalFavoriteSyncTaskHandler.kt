package com.dailysatori.core.task

import com.dailysatori.core.worker.ArticleProcessingScheduler
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.externalfavorites.FavoriteSyncMode
import com.dailysatori.service.externalfavorites.FavoriteSyncService
import com.dailysatori.service.externalfavorites.XFavoriteAuthException
import com.dailysatori.service.externalfavorites.XFavoriteRateLimitException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ExternalFavoriteSyncTaskPayload(
    val sourceId: Long,
    val mode: String,
)

class ExternalFavoriteSyncTaskHandler(
    private val syncService: FavoriteSyncService,
    private val articleProcessingScheduler: ArticleProcessingScheduler,
) : AsyncTaskHandler {
    override val type: String = TYPE

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult {
        val payload = runCatching { Json.decodeFromString<ExternalFavoriteSyncTaskPayload>(payloadJson) }
            .getOrElse {
                return AsyncTaskExecutionResult.PermanentFailure("invalid_payload", "外部收藏同步任务参数无效")
            }
        val mode = FavoriteSyncMode.entries.firstOrNull { it.name == payload.mode }
            ?: return AsyncTaskExecutionResult.PermanentFailure("invalid_mode", "外部收藏同步模式无效")
        if (payload.sourceId <= 0L) {
            return AsyncTaskExecutionResult.PermanentFailure("invalid_source", "外部收藏来源无效")
        }

        return try {
            reporter.report(0, 3, "准备同步外部收藏", checkpointJson = """{"phase":"queued"}""")
            syncService.syncSource(payload.sourceId, mode, taskId = taskId) { progress ->
                reporter.report(
                    current = progress.pagesSeen.toLong(),
                    total = progress.maxPages.toLong().coerceAtLeast(1),
                    message = externalFavoriteTaskProgressMessage(progress.phase, progress.itemsSeen),
                    checkpointJson = """{"phase":"${progress.phase}","pagesSeen":${progress.pagesSeen},"itemsSeen":${progress.itemsSeen},"historyComplete":${progress.historyComplete}}""",
                )
            }
            articleProcessingScheduler.enqueueResume()
            AsyncTaskExecutionResult.Success()
        } catch (error: XFavoriteAuthException) {
            AsyncTaskExecutionResult.PermanentFailure("auth_failed", error.message.orEmpty().ifBlank { "X 授权失败" })
        } catch (error: XFavoriteRateLimitException) {
            AsyncTaskExecutionResult.RetryableFailure(
                code = "rate_limited",
                message = error.message.orEmpty().ifBlank { "X 请求达到限流" },
                retryAfterMs = error.rateLimitResetAt?.let { it + 60_000L },
            )
        } catch (error: IllegalArgumentException) {
            AsyncTaskExecutionResult.PermanentFailure("invalid_source", error.message.orEmpty().ifBlank { "外部收藏来源无效" })
        } catch (error: Exception) {
            AsyncTaskExecutionResult.RetryableFailure("sync_failed", error.message.orEmpty().ifBlank { "外部收藏同步失败" })
        }
    }

    companion object {
        const val TYPE = "external_favorite_sync"
    }
}

fun externalFavoriteSyncTaskPayloadJson(sourceId: Long, mode: String): String =
    Json.encodeToString(ExternalFavoriteSyncTaskPayload(sourceId = sourceId, mode = mode))

private fun externalFavoriteTaskProgressMessage(phase: String, itemsSeen: Int): String = when (phase) {
    "latest" -> "正在读取最新收藏，已看到 $itemsSeen 条"
    "backfill" -> "正在补全历史收藏，已看到 $itemsSeen 条"
    "import" -> "正在导入收藏文章，已读取 $itemsSeen 条"
    "repair" -> "正在修复收藏文章状态"
    "organize" -> "正在整理收藏内容"
    "complete" -> "外部收藏读取完成"
    else -> "正在同步外部收藏"
}
