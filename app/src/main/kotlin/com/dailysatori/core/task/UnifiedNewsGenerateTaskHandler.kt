package com.dailysatori.core.task

import com.dailysatori.core.worker.shouldRetryUnifiedNews
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.diagnostics.DiagnosticCode
import com.dailysatori.service.diagnostics.DiagnosticLog
import com.dailysatori.service.diagnostics.DiagnosticSource
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryStatus
import com.dailysatori.service.unifiednews.UnifiedNewsGenerationResult
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryService
import com.dailysatori.service.unifiednews.UnifiedNewsTickDecision
import com.dailysatori.service.unifiednews.dueUnifiedNewsWindows
import com.dailysatori.service.unifiednews.unifiedNewsTickDecision
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class UnifiedNewsGenerateTaskPayload(
    val force: Boolean,
    val ignoreSourceTimeFilter: Boolean,
    val mode: String,
    /** Tick identity of the scheduled window; null for manual refresh and legacy tasks. */
    val tickDueAtMs: Long? = null,
)

class UnifiedNewsGenerateTaskHandler(
    summaryService: UnifiedNewsSummaryService? = null,
    articleQueue: UnifiedNewsArticleQueue? = null,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val latestSuccessfulAt: () -> Long? = { summaryService?.latestSuccessfulGeneratedAt() },
    /** Execution-identity check: only the run that still owns the task lease may write its result. */
    private val leaseIsHeld: (Long, String) -> Boolean = { _, _ -> true },
    private val generateDaily: suspend (
        force: Boolean,
        ignoreSourceTimeFilter: Boolean,
        persistGuard: (() -> Boolean)?,
        onStage: (suspend (String) -> Unit)?,
    ) -> UnifiedNewsGenerationResult = { force, ignoreSourceTimeFilter, persistGuard, onStage ->
        requireNotNull(summaryService) { "summary service is required for generation" }
            .generateDaily(
                force = force, ignoreSourceTimeFilter = ignoreSourceTimeFilter,
                persistGuard = persistGuard, onStage = onStage,
            )
    },
) : AsyncTaskHandler {
    private val articleQueue = articleQueue
    override val type: String = TYPE

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult {
        val payload = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<UnifiedNewsGenerateTaskPayload>(payloadJson)
        }
            .getOrElse {
                return AsyncTaskExecutionResult.PermanentFailure("invalid_payload", "远程新闻任务参数无效")
            }
        val duplicate = duplicateTickDecision(payload)
        if (duplicate != null) {
            // Final guard: a tick that a newer tick replaced, or that already produced a summary,
            // must not spend another AI call.
            reporter.report(2, 2, "本次窗口已覆盖，跳过重复生成",
                checkpointJson = """{"stage":"skipped","tickDecision":"${duplicate.name}"}""")
            return AsyncTaskExecutionResult.Success()
        }
        reporter.report(0, 2, "正在收集远程新闻", checkpointJson = """{"stage":"collecting"}""")
        val lease = reporter.executionLease
        val persistGuard: () -> Boolean = { lease == null || leaseIsHeld(lease.taskId, lease.owner) }
        // Every stage below is a real boundary the service reports; collection completing is the first one,
        // and the AI stages are only claimed once the service actually reached them.
        val beganNanos = System.nanoTime()
        val result = try {
            generateDaily(payload.force, payload.ignoreSourceTimeFilter, persistGuard) { stage ->
                // Reported as it happens, with a monotonic elapsed so the export shows start/end and duration.
                val elapsedMs = (System.nanoTime() - beganNanos) / 1_000_000
                reporter.report(1, 2, unifiedNewsStageMessage(stage),
                    checkpointJson = "{\"stage\":\"" + stage + "\",\"runElapsedMs\":" + elapsedMs + "}")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // A raw exception message must never reach the task log/export: classify it into safe constants.
            return safeGenerationFailure(error)
        }
        if (result.deferred) {
            // Congestion deferred the whole generation: keep the message visible, retry soon, spend no attempt.
            reporter.report(1, 2, result.message ?: "AI 服务繁忙，稍后自动继续",
                checkpointJson = """{"stage":"deferred"}""")
            return AsyncTaskExecutionResult.Yield(delayMs = QUEUE_DEFERRED_RESUME_MS)
        }
        summaryStage(result, forced = payload.force)
        reporter.report(1, 2, result.message ?: "正在生成新闻汇总", checkpointJson = """{"stage":"generated"}""")

        if (result.success) {
            articleQueue?.enqueueLatest()
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

    /** Distinguishes a written summary, an empty window, a failure and a superseded discard. */
    private fun summaryStage(result: UnifiedNewsGenerationResult, forced: Boolean) {
        val stage = when {
            result.success && result.status == UnifiedNewsSummaryStatus.SUCCESS && !forced -> "already_present"
            result.success && result.status == UnifiedNewsSummaryStatus.SUCCESS -> "persisted"
            result.success && result.status == UnifiedNewsSummaryStatus.EMPTY -> "empty"
            result.message?.contains("已被更新的新闻窗口取代") == true -> "skipped_superseded"
            else -> "failed"
        }
        DiagnosticLog.diagnostics.emit(
            DiagnosticCode.OPERATION_PROGRESS, DiagnosticSource.APP,
            fields = mapOf("newsSummaryStage" to stage),
        )
    }

    private fun duplicateTickDecision(payload: UnifiedNewsGenerateTaskPayload): UnifiedNewsTickDecision? {
        val tick = payload.tickDueAtMs ?: return null
        val latestDue = dueUnifiedNewsWindows(Instant.fromEpochMilliseconds(nowMs()))
            .maxByOrNull { it.endMs }?.endMs ?: tick
        val decision = unifiedNewsTickDecision(
            tickDueAtMs = tick,
            latestDueAtMs = latestDue,
            latestSuccessfulAtMs = latestSuccessfulAt(),
            manual = payload.mode == MODE_MANUAL,
        )
        return decision.takeIf { it != UnifiedNewsTickDecision.GENERATE }
    }

    /** Safe classification: stable code plus a fixed message, never the raw exception text. */
    private fun safeGenerationFailure(error: Throwable): AsyncTaskExecutionResult = when (error) {
        is com.dailysatori.service.ai.AiQueueWaitTimeout ->
            AsyncTaskExecutionResult.RetryableFailure("ai_queue_timeout", "AI 服务繁忙，未发送，稍后自动继续")
        is kotlinx.coroutines.TimeoutCancellationException ->
            AsyncTaskExecutionResult.RetryableFailure("ai_timeout", "AI 生成超时，请稍后重试")
        is com.dailysatori.service.ai.AiHttpFailure ->
            AsyncTaskExecutionResult.RetryableFailure("ai_http_" + error.status, "AI 服务返回错误，请稍后重试")
        is android.database.SQLException ->
            AsyncTaskExecutionResult.RetryableFailure("db_error", "本地数据库繁忙，稍后自动继续")
        else -> AsyncTaskExecutionResult.RetryableFailure("generation_error", "新闻汇总生成失败，请稍后重试")
    }

    companion object {
        const val TYPE = "remote_news_fetch"
        /** A deferred generation resumes on a slow cadence: no fast loop and no attempt budget spent. */
        private const val QUEUE_DEFERRED_RESUME_MS = 5 * 60_000L
        const val MODE_DUE = "due"
        const val MODE_BACKFILL = "backfill"
        const val MODE_MANUAL = "manual"
    }
}

private fun unifiedNewsStageMessage(stage: String): String = when (stage) {
    "collected" -> "已收集来源"
    "ai_waiting" -> "准备调用 AI 生成新闻汇总"
    "ai_queued" -> "等待 AI 发送许可（排队中）"
    "ai_permit" -> "已获得 AI 发送许可"
    "ai_requesting" -> "AI 请求进行中"
    "ai_received" -> "AI 已返回，正在校验"
    "validated" -> "校验通过，正在保存"
    "persisted" -> "新闻汇总已保存"
    else -> "正在生成新闻汇总"
}

fun unifiedNewsGenerateTaskPayloadJson(
    force: Boolean,
    ignoreSourceTimeFilter: Boolean,
    mode: String,
    tickDueAtMs: Long? = null,
): String = Json.encodeToString(
    UnifiedNewsGenerateTaskPayload(
        force = force,
        ignoreSourceTimeFilter = ignoreSourceTimeFilter,
        mode = mode,
        tickDueAtMs = tickDueAtMs,
    ),
)
