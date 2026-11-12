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
    ) -> UnifiedNewsGenerationResult = { force, ignoreSourceTimeFilter, persistGuard ->
        requireNotNull(summaryService) { "summary service is required for generation" }
            .generateDaily(force = force, ignoreSourceTimeFilter = ignoreSourceTimeFilter, persistGuard = persistGuard)
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
        val result = generateDaily(payload.force, payload.ignoreSourceTimeFilter, persistGuard)
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

    companion object {
        const val TYPE = "remote_news_fetch"
        const val MODE_DUE = "due"
        const val MODE_BACKFILL = "backfill"
        const val MODE_MANUAL = "manual"
    }
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
