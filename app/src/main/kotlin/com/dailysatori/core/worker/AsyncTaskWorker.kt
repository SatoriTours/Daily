package com.dailysatori.core.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dailysatori.core.task.AsyncTaskLogStore
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry
import com.dailysatori.service.asynctask.AsyncTaskRunOutcome
import com.dailysatori.service.asynctask.AsyncTaskRunner
import com.dailysatori.service.asynctask.NoopAsyncTaskLogger
import kotlinx.datetime.Clock
import org.koin.core.context.GlobalContext

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
        val logStore = runCatching { GlobalContext.get().get<AsyncTaskLogStore>() }.getOrNull()
        val runner = AsyncTaskRunner(
            repository = repo,
            registry = registry,
            logger = logStore ?: NoopAsyncTaskLogger,
        )
        return when (runner.run(taskId)) {
            AsyncTaskRunOutcome.Succeeded,
            AsyncTaskRunOutcome.Skipped -> Result.success()
            AsyncTaskRunOutcome.Failed -> Result.failure()
            AsyncTaskRunOutcome.RetryScheduled -> Result.retry()
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
    }
}

internal fun asyncTaskWorkName(taskId: Long): String = "async-task-$taskId"

internal fun buildAsyncTaskWorkRequest(taskId: Long): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<GenericAsyncTaskWorker>()
        .setInputData(workDataOf(GenericAsyncTaskWorker.KEY_TASK_ID to taskId))
        .build()
