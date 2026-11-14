package com.dailysatori.core.worker

import com.dailysatori.service.asynctask.AsyncTaskRunOutcome
import com.dailysatori.service.diagnostics.DiagnosticCode
import com.dailysatori.service.diagnostics.DiagnosticLevel
import com.dailysatori.service.diagnostics.DiagnosticLog
import com.dailysatori.service.diagnostics.DiagnosticSource
import com.dailysatori.service.diagnostics.DiagnosticTrace

/**
 * dataSync 前台入口的最小生命周期诊断：只记请求、setForeground 调用完成/失败、Worker 取消或结束。
 * 成功只代表 setForeground API 返回，**不代表系统服务真正启动或停止**；系统侧的停止失败本进程观察不到，
 * 因此这里不产生 foreground_stop 事件，也不虚构停止原因。
 */
/**
 * 任务终态到诊断枚举的同构映射：Blocked（被占用/等待）、RetryScheduled 与 Skipped 不能冒充 completed。
 * 单一来源，避免各 Worker 各写一份。
 */
internal fun AsyncTaskRunOutcome.foregroundOutcomeName(): String = when (this) {
    AsyncTaskRunOutcome.Succeeded -> "completed"
    AsyncTaskRunOutcome.Skipped -> "skipped"
    is AsyncTaskRunOutcome.Blocked -> "retry_scheduled"
    AsyncTaskRunOutcome.Failed -> "failed"
    AsyncTaskRunOutcome.RetryScheduled -> "retry_scheduled"
}

internal object ForegroundDiagnostics {
    const val ASYNC_TASK = "async_task"
    const val EXTERNAL_FAVORITE_QUEUE = "external_favorite_queue"
    const val DIARY_THOUGHT = "diary_thought"

    fun workerStarted(worker: String, taskId: Long?, taskType: String?, runAttemptCount: Int) =
        emit(DiagnosticCode.OPERATION_START, worker, taskId, taskType, runAttemptCount,
            mapOf("foregroundStage" to "worker_started"))

    fun requestStarted(
        worker: String, notificationId: Int, serviceType: Int,
        taskId: Long?, taskType: String?, runAttemptCount: Int,
    ) = emit(DiagnosticCode.OPERATION_START, worker, taskId, taskType, runAttemptCount,
        mapOf(
            "foregroundStage" to "request",
            "notificationId" to notificationId.toString(),
            "foregroundServiceType" to serviceType.toString(),
        ))

    fun requestSucceeded(worker: String, notificationId: Int, taskId: Long?, taskType: String?) =
        emit(DiagnosticCode.OPERATION_PROGRESS, worker, taskId, taskType, null,
            mapOf("foregroundStage" to "request_ok", "notificationId" to notificationId.toString()))

    fun requestFailed(worker: String, notificationId: Int, taskId: Long?, taskType: String?, error: Exception) =
        emit(DiagnosticCode.OPERATION_FAILED, worker, taskId, taskType, null,
            mapOf(
                "foregroundStage" to "request_failed",
                "notificationId" to notificationId.toString(),
                "foregroundStartFailure" to startFailure(error),
            ),
            DiagnosticLevel.WARNING)

    fun workerFinished(worker: String, outcome: String, taskId: Long?, taskType: String?) =
        emit(DiagnosticCode.OPERATION_END, worker, taskId, taskType, null, mapOf("workerOutcome" to outcome))

    fun workerCancelled(worker: String, taskId: Long?) =
        emit(DiagnosticCode.OPERATION_CANCELLED, worker, taskId, null, null,
            mapOf("foregroundStage" to "worker_cancelled"))

    /** 只保留固定枚举，不回填异常类名或消息。 */
    private fun startFailure(error: Exception): String {
        val name = error::class.java.name
        return when {
            name.endsWith("ForegroundServiceStartNotAllowedException") -> "start_not_allowed"
            error is IllegalStateException -> "illegal_state"
            else -> "other"
        }
    }

    /** 与应用内任务类型白名单一致；未识别类型诚实记为 unknown。 */
    private val KNOWN_TASK_TYPES = setOf("assistant_turn", "sms_reminder_parse", "external_favorite_organize",
        "save_article", "external_favorite_sync", "remote_article_reprocess", "remote_article_sync",
        "remote_news_fetch", "reminder_ai_parse", "diary_attachment_transcribe", "diary_thread_summarize",
        "article_memory_extract", "book_viewpoint_generate", "diary_knowledge_extract")

    private fun emit(
        code: DiagnosticCode, worker: String, taskId: Long?, taskType: String?, runAttemptCount: Int?,
        fields: Map<String, String>, level: DiagnosticLevel = DiagnosticLevel.INFO,
    ) {
        val safeTaskId = taskId?.takeIf { it > 0L }
        DiagnosticLog.diagnostics.emit(
            code, DiagnosticSource.TASK, level = level,
            fields = buildMap {
                put("foregroundWorker", worker)
                putAll(fields)
                safeTaskId?.let { put("taskId", it.toString()) }
                put("foregroundTaskType", (taskType ?: "unknown").takeIf { it in KNOWN_TASK_TYPES } ?: "unknown")
                runAttemptCount?.let { put("runAttemptCount", it.toString()) }
            },
            trace = DiagnosticLog.currentTrace() ?: safeTaskId?.let { DiagnosticTrace(DiagnosticLog.newId(), it) },
        )
    }
}
