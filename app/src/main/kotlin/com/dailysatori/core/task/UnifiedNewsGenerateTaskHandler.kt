package com.dailysatori.core.task

import android.content.Context
import com.dailysatori.core.worker.UnifiedNewsScheduler
import com.dailysatori.core.worker.shouldRetryUnifiedNews
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryService
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class UnifiedNewsGenerateTaskPayload(
    val force: Boolean,
    val ignoreSourceTimeFilter: Boolean,
    val mode: String,
)

class UnifiedNewsGenerateTaskHandler(
    private val summaryService: UnifiedNewsSummaryService,
    private val context: Context,
) : AsyncTaskHandler {
    override val type: String = TYPE

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult {
        val payload = runCatching { Json.decodeFromString<UnifiedNewsGenerateTaskPayload>(payloadJson) }
            .getOrElse {
                return AsyncTaskExecutionResult.PermanentFailure("invalid_payload", "远程新闻任务参数无效")
            }
        reporter.report(0, 2, "正在收集远程新闻", checkpointJson = """{"stage":"collecting"}""")
        val result = summaryService.generateDaily(
            force = payload.force,
            ignoreSourceTimeFilter = payload.ignoreSourceTimeFilter,
        )
        reporter.report(1, 2, result.message ?: "正在生成新闻汇总", checkpointJson = """{"stage":"generated"}""")

        if (result.success) {
            if (payload.mode == MODE_DUE) {
                UnifiedNewsScheduler(context).scheduleNext(Clock.System.now())
            }
            reporter.report(2, 2, result.message ?: "远程新闻已更新", checkpointJson = """{"stage":"completed"}""")
            return AsyncTaskExecutionResult.Success()
        }

        val message = result.message ?: "远程新闻获取失败"
        return if (shouldRetryUnifiedNews(result)) {
            AsyncTaskExecutionResult.RetryableFailure("remote_news_fetch_failed", message)
        } else {
            AsyncTaskExecutionResult.PermanentFailure("remote_news_fetch_failed", message)
        }
    }

    companion object {
        const val TYPE = "remote_news_fetch"
        const val MODE_DUE = "due"
        const val MODE_BACKFILL = "backfill"
    }
}

fun unifiedNewsGenerateTaskPayloadJson(
    force: Boolean,
    ignoreSourceTimeFilter: Boolean,
    mode: String,
): String = Json.encodeToString(
    UnifiedNewsGenerateTaskPayload(
        force = force,
        ignoreSourceTimeFilter = ignoreSourceTimeFilter,
        mode = mode,
    ),
)
