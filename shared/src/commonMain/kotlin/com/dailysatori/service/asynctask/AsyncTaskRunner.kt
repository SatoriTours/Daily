package com.dailysatori.service.asynctask

import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.diagnostics.*
import com.dailysatori.shared.db.Async_task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*

interface AsyncTaskLogger {
    fun append(taskId: Long, message: String)
}

object NoopAsyncTaskLogger : AsyncTaskLogger {
    override fun append(taskId: Long, message: String) = Unit
}

sealed interface AsyncTaskRunOutcome {
    data object Succeeded : AsyncTaskRunOutcome
    data object Failed : AsyncTaskRunOutcome
    data object RetryScheduled : AsyncTaskRunOutcome
    data object Skipped : AsyncTaskRunOutcome
}

class AsyncTaskRunner(
    private val repository: AsyncTaskRepository,
    private val registry: AsyncTaskHandlerRegistry,
    private val logger: AsyncTaskLogger = NoopAsyncTaskLogger,
    private val leaseOwnerProvider: () -> String = {
        "async-task-runner-${Clock.System.now().toEpochMilliseconds()}-${kotlin.random.Random.nextLong()}"
    },
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val leaseMs: Long = DEFAULT_LEASE_MS,
    private val executionTimeoutMs: (String) -> Long = ::asyncTaskExecutionTimeoutMs,
) {
    suspend fun run(taskId: Long): AsyncTaskRunOutcome = DiagnosticLog.diagnostics.operation(
        DiagnosticSource.TASK, taskId = taskId, isFailure = { it == AsyncTaskRunOutcome.Failed },
    ) { runRecorded(taskId) }

    private suspend fun runRecorded(taskId: Long): AsyncTaskRunOutcome {
        if (taskId <= 0L) return AsyncTaskRunOutcome.Failed
        repository.markExpiredRunningForRetry(nowMs())
        val task = repository.getById(taskId) ?: return AsyncTaskRunOutcome.Skipped
        if (repository.waitingForPredecessor(taskId)) return AsyncTaskRunOutcome.RetryScheduled
        val executionPayload = runCatching {
            JsonObject(Json.parseToJsonElement(task.payload_json).jsonObject - "_afterTaskId").toString()
        }.getOrDefault(task.payload_json)
        val leaseOwner = leaseOwnerProvider()
        val claimed = if (task.type in SERIAL_TASK_TYPES) {
            repository.claimForSerialRun(taskId, task.type, leaseOwner, nowMs() + leaseMs)
        } else {
            repository.claimForRun(taskId, leaseOwner, nowMs() + leaseMs)
        }
        if (!claimed) {
            return AsyncTaskRunOutcome.RetryScheduled
        }
        log(taskId, "TASK started type=${task.type}")

        val handler = registry.get(task.type)
        if (handler == null) {
            return finishWithLease(task, leaseOwner, AsyncTaskExecutionResult.PermanentFailure(
                "handler_missing", "没有注册 ${task.type} 任务处理器",
            ))
        }

        val reporter = object : AsyncTaskProgressReporter {
            override val executionLease = AsyncTaskExecutionLease(taskId, leaseOwner)

            override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) {
                repository.withRunningLease(taskId, leaseOwner) {
                    repository.updateProgress(taskId, current, total, message, checkpointJson)
                    repository.renewLease(taskId, leaseOwner, nowMs() + leaseMs)
                } ?: throw AsyncTaskCancelledException()
                DiagnosticLog.diagnostics.emit(DiagnosticCode.OPERATION_PROGRESS, DiagnosticSource.TASK,
                    fields = mapOf("count" to current.toString()))
                val safeCheckpoint = if (task.type.startsWith("assistant_")) "[助手执行状态]" else checkpointJson
                log(taskId, "TASK progress current=$current total=$total message=$message checkpoint=$safeCheckpoint")
            }
        }

        return try {
            val result = withTimeout(executionTimeoutMs(task.type).coerceAtLeast(1L)) {
                executeWithLeaseHeartbeat(taskId, leaseOwner) {
                    handler.execute(taskId, executionPayload, task.checkpoint_json, reporter)
                }
            }
            finishWithLease(task, leaseOwner, result)
        } catch (_: AsyncTaskCancelledException) {
            log(taskId, "TASK cancelled")
            AsyncTaskRunOutcome.Skipped
        } catch (_: TimeoutCancellationException) {
            if (!repository.hasRunningLease(taskId, leaseOwner)) return AsyncTaskRunOutcome.Skipped
            val result = handler.onExecutionTimeout(taskId, executionPayload, task.checkpoint_json, reporter)
                ?: AsyncTaskExecutionResult.RetryableFailure("timeout", "任务执行超时，已停止并等待重试")
            finishWithLease(task, leaseOwner, result)
        } catch (error: CancellationException) {
            repository.withRunningLease(taskId, leaseOwner) {
                repository.markRetry(
                    taskId, "interrupted", "任务被系统中断，将自动重试",
                    nowMs() + asyncTaskNextRetryDelayMs(task.attempt_count), consumeAttempt = false,
                )
                log(taskId, "TASK interrupted; retry scheduled")
            }
            throw error
        } catch (error: Throwable) {
            finishWithLease(task, leaseOwner, AsyncTaskExecutionResult.RetryableFailure(
                "exception", error.message.orEmpty().ifBlank { "任务执行失败" },
            ))
        }
    }

    private suspend fun <T> executeWithLeaseHeartbeat(
        taskId: Long,
        leaseOwner: String,
        block: suspend () -> T,
    ): T = coroutineScope {
        val heartbeat = launch {
            while (true) {
                delay((leaseMs / 3).coerceAtLeast(MIN_HEARTBEAT_MS))
                if (!repository.hasRunningLease(taskId, leaseOwner)) return@launch
                repository.renewLease(taskId, leaseOwner, nowMs() + leaseMs)
            }
        }
        try {
            block()
        } finally {
            heartbeat.cancelAndJoin()
        }
    }

    private fun finishWithLease(
        task: Async_task,
        owner: String,
        result: AsyncTaskExecutionResult,
    ): AsyncTaskRunOutcome = repository.withRunningLease(task.id, owner) {
        when (result) {
            is AsyncTaskExecutionResult.Yield -> {
                repository.markRetry(task.id, "", "", nowMs() + result.delayMs.coerceAtLeast(0), consumeAttempt = false)
                log(task.id, "TASK yielded after productive work")
                AsyncTaskRunOutcome.RetryScheduled
            }
            is AsyncTaskExecutionResult.Success -> {
                repository.finishSuccess(task.id, result.resultJson)
                log(task.id, "TASK succeeded")
                AsyncTaskRunOutcome.Succeeded
            }
            is AsyncTaskExecutionResult.PermanentFailure -> {
                repository.finishFailure(task.id, result.code, result.message)
                log(task.id, "TASK failed code=${result.code} message=${result.message}")
                AsyncTaskRunOutcome.Failed
            }
            is AsyncTaskExecutionResult.RetryableFailure ->
                handleRetryableFailure(task.id, task, result.code, result.message, result.retryAfterMs)
        }
    } ?: AsyncTaskRunOutcome.Skipped.also {
        log(task.id, if (repository.getById(task.id)?.status == "cancelled") "TASK cancelled" else "TASK lease lost")
    }

    private fun handleRetryableFailure(
        taskId: Long,
        task: Async_task,
        code: String,
        message: String,
        retryAfterMs: Long?,
    ): AsyncTaskRunOutcome {
        val latest = repository.getById(taskId)
        val attempts = latest?.attempt_count ?: task.attempt_count
        if (attempts + 1 >= task.max_attempts) {
            repository.finishFailure(taskId, code, message)
            log(taskId, "TASK failed code=$code message=$message")
            return AsyncTaskRunOutcome.Failed
        }

        val runAfter = retryAfterMs ?: nowMs() + asyncTaskNextRetryDelayMs(attempts)
        repository.markRetry(taskId, code, message, runAfter)
        log(taskId, "TASK retry code=$code message=$message runAfterMs=$runAfter")
        return AsyncTaskRunOutcome.RetryScheduled
    }

    private fun log(taskId: Long, message: String) {
        runCatching { logger.append(taskId, message) }
    }

    private companion object {
        const val DEFAULT_LEASE_MS = 30 * 60 * 1000L
        const val MIN_HEARTBEAT_MS = 30_000L
        val SERIAL_TASK_TYPES = setOf(AsyncTaskType.external_favorite_sync.name, AsyncTaskType.remote_news_fetch.name, AsyncTaskType.assistant_turn.name, "assistant_history_backfill", "assistant_summary")
    }
}

fun asyncTaskExecutionTimeoutMs(type: String): Long = when (type) {
    "remote_article_sync" -> 5 * 60_000L
    "remote_news_fetch" -> 4 * 60_000L
    "external_favorite_sync" -> 15 * 60_000L
    "diary_attachment_transcribe" -> 20 * 60_000L
    "reminder_ai_parse" -> 3 * 60_000L
    else -> 10 * 60_000L
}

private class AsyncTaskCancelledException : CancellationException("任务已取消")
