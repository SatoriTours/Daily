package com.dailysatori.service.asynctask

import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.shared.db.Async_task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.datetime.Clock

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
    private val leaseOwnerProvider: () -> String = { "async-task-runner-${Clock.System.now().toEpochMilliseconds()}" },
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val leaseMs: Long = DEFAULT_LEASE_MS,
    private val executionTimeoutMs: (String) -> Long = ::asyncTaskExecutionTimeoutMs,
) {
    suspend fun run(taskId: Long): AsyncTaskRunOutcome {
        if (taskId <= 0L) return AsyncTaskRunOutcome.Failed
        repository.markExpiredRunningForRetry(nowMs())
        val task = repository.getById(taskId) ?: return AsyncTaskRunOutcome.Skipped
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
            repository.finishFailure(taskId, "handler_missing", "没有注册 ${task.type} 任务处理器")
            log(taskId, "TASK failed code=handler_missing message=没有注册 ${task.type} 任务处理器")
            return AsyncTaskRunOutcome.Failed
        }

        val reporter = object : AsyncTaskProgressReporter {
            override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) {
                if (!repository.isRunning(taskId)) throw AsyncTaskCancelledException()
                repository.updateProgress(taskId, current, total, message, checkpointJson)
                repository.renewLease(taskId, leaseOwner, nowMs() + leaseMs)
                log(taskId, "TASK progress current=$current total=$total message=$message checkpoint=$checkpointJson")
            }
        }

        return try {
            val result = withTimeout(executionTimeoutMs(task.type).coerceAtLeast(1L)) {
                executeWithLeaseHeartbeat(taskId, leaseOwner) {
                    handler.execute(taskId, task.payload_json, task.checkpoint_json, reporter)
                }
            }
            if (!repository.isRunning(taskId)) {
                log(taskId, "TASK cancelled")
                return AsyncTaskRunOutcome.Skipped
            }
            when (result) {
                is AsyncTaskExecutionResult.Success -> {
                    repository.finishSuccess(taskId, result.resultJson)
                    log(taskId, "TASK succeeded")
                    AsyncTaskRunOutcome.Succeeded
                }
                is AsyncTaskExecutionResult.PermanentFailure -> {
                    repository.finishFailure(taskId, result.code, result.message)
                    log(taskId, "TASK failed code=${result.code} message=${result.message}")
                    AsyncTaskRunOutcome.Failed
                }
                is AsyncTaskExecutionResult.RetryableFailure -> {
                    handleRetryableFailure(taskId, task, result.code, result.message, result.retryAfterMs)
                }
            }
        } catch (_: AsyncTaskCancelledException) {
            log(taskId, "TASK cancelled")
            AsyncTaskRunOutcome.Skipped
        } catch (_: TimeoutCancellationException) {
            when (val timeoutResult = handler.onExecutionTimeout(taskId, task.payload_json, task.checkpoint_json, reporter)) {
                is AsyncTaskExecutionResult.Success -> { repository.finishSuccess(taskId, timeoutResult.resultJson); AsyncTaskRunOutcome.Succeeded }
                is AsyncTaskExecutionResult.PermanentFailure -> { repository.finishFailure(taskId, timeoutResult.code, timeoutResult.message); AsyncTaskRunOutcome.Failed }
                is AsyncTaskExecutionResult.RetryableFailure -> handleRetryableFailure(taskId, task, timeoutResult.code, timeoutResult.message, timeoutResult.retryAfterMs)
                null -> handleRetryableFailure(taskId, task, "timeout", "任务执行超时，已停止并等待重试", null)
            }
        } catch (error: CancellationException) {
            if (repository.isRunning(taskId)) {
                handleRetryableFailure(
                    taskId = taskId,
                    task = task,
                    code = "interrupted",
                    message = error.message.orEmpty().ifBlank { "任务被系统中断" },
                    retryAfterMs = null,
                )
            }
            throw error
        } catch (error: Throwable) {
            handleRetryableFailure(
                taskId = taskId,
                task = task,
                code = "exception",
                message = error.message.orEmpty().ifBlank { "任务执行失败" },
                retryAfterMs = null,
            )
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
                if (!repository.isRunning(taskId)) return@launch
                repository.renewLease(taskId, leaseOwner, nowMs() + leaseMs)
            }
        }
        try {
            block()
        } finally {
            heartbeat.cancelAndJoin()
        }
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
        val SERIAL_TASK_TYPES = setOf(AsyncTaskType.external_favorite_sync.name)
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
