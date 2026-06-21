package com.dailysatori.core.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.asynctask.asyncTaskNextRetryDelayMs
import com.dailysatori.shared.db.Async_task
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import org.koin.core.context.GlobalContext
import java.util.UUID

class AsyncTaskScheduler(private val context: Context) {
    fun enqueue(taskId: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            asyncTaskWorkName(taskId),
            ExistingWorkPolicy.KEEP,
            buildAsyncTaskWorkRequest(taskId),
        )
    }

    fun recoverAndEnqueueRunnable() {
        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        val now = Clock.System.now().toEpochMilliseconds()
        repo.markExpiredRunningForRetry(now)
        repo.runnableTasks(now).forEach { task -> enqueue(task.id) }
    }
}

class GenericAsyncTaskWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId <= 0L) return Result.failure()

        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        val registry = GlobalContext.get().get<AsyncTaskHandlerRegistry>()
        val task = repo.getById(taskId) ?: return Result.success()
        val leaseOwner = UUID.randomUUID().toString()
        val leaseUntil = Clock.System.now().toEpochMilliseconds() + LEASE_MS
        if (!repo.claimForRun(taskId, leaseOwner, leaseUntil)) return Result.retry()

        val handler = registry.get(task.type)
        if (handler == null) {
            repo.finishFailure(taskId, "handler_missing", "没有注册 ${task.type} 任务处理器")
            return Result.failure()
        }

        val reporter = object : AsyncTaskProgressReporter {
            override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) {
                repo.updateProgress(taskId, current, total, message, checkpointJson)
            }
        }

        return try {
            when (val result = handler.execute(taskId, task.payload_json, task.checkpoint_json, reporter)) {
                is AsyncTaskExecutionResult.Success -> {
                    repo.finishSuccess(taskId, result.resultJson)
                    Result.success()
                }
                is AsyncTaskExecutionResult.PermanentFailure -> {
                    repo.finishFailure(taskId, result.code, result.message)
                    Result.failure()
                }
                is AsyncTaskExecutionResult.RetryableFailure -> {
                    handleRetryableFailure(repo, taskId, task, result.code, result.message, result.retryAfterMs)
                }
            }
        } catch (error: CancellationException) {
            handleRetryableFailure(
                repo = repo,
                taskId = taskId,
                task = task,
                code = "cancelled",
                message = error.message.orEmpty().ifBlank { "任务被系统中断" },
                retryAfterMs = null,
            )
        } catch (error: Exception) {
            handleRetryableFailure(
                repo = repo,
                taskId = taskId,
                task = task,
                code = "exception",
                message = error.message.orEmpty().ifBlank { "任务执行失败" },
                retryAfterMs = null,
            )
        }
    }

    private fun handleRetryableFailure(
        repo: AsyncTaskRepository,
        taskId: Long,
        task: Async_task,
        code: String,
        message: String,
        retryAfterMs: Long?,
    ): Result {
        val latest = repo.getById(taskId)
        val attempts = latest?.attempt_count ?: task.attempt_count
        if (attempts + 1 >= task.max_attempts) {
            repo.finishFailure(taskId, code, message)
            return Result.failure()
        }

        val runAfter = retryAfterMs
            ?: Clock.System.now().toEpochMilliseconds() + asyncTaskNextRetryDelayMs(attempts)
        repo.markRetry(taskId, code, message, runAfter)
        return Result.retry()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val LEASE_MS = 10 * 60 * 1000L
    }
}

internal fun asyncTaskWorkName(taskId: Long): String = "async-task-$taskId"

internal fun buildAsyncTaskWorkRequest(taskId: Long): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<GenericAsyncTaskWorker>()
        .setInputData(workDataOf(GenericAsyncTaskWorker.KEY_TASK_ID to taskId))
        .build()
