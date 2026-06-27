package com.dailysatori.service.asynctask

import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.shared.db.Async_task
import kotlinx.coroutines.CancellationException
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
) {
    suspend fun run(taskId: Long): AsyncTaskRunOutcome {
        if (taskId <= 0L) return AsyncTaskRunOutcome.Failed
        val task = repository.getById(taskId) ?: return AsyncTaskRunOutcome.Skipped
        val leaseOwner = leaseOwnerProvider()
        if (!repository.claimForRun(taskId, leaseOwner, nowMs() + leaseMs)) {
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
                repository.updateProgress(taskId, current, total, message, checkpointJson)
                log(taskId, "TASK progress current=$current total=$total message=$message checkpoint=$checkpointJson")
            }
        }

        return try {
            when (val result = handler.execute(taskId, task.payload_json, task.checkpoint_json, reporter)) {
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
        } catch (error: CancellationException) {
            handleRetryableFailure(
                taskId = taskId,
                task = task,
                code = "cancelled",
                message = error.message.orEmpty().ifBlank { "任务被系统中断" },
                retryAfterMs = null,
            )
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
        const val DEFAULT_LEASE_MS = 10 * 60 * 1000L
    }
}
