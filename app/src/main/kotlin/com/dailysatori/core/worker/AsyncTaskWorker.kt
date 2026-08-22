package com.dailysatori.core.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
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
        val taskType = GlobalContext.get().get<AsyncTaskRepository>().getById(taskId)?.type
        WorkManager.getInstance(context).enqueueUniqueWork(
            asyncTaskWorkName(taskId),
            ExistingWorkPolicy.KEEP,
            buildAsyncTaskWorkRequest(taskId, taskType),
        )
    }

    fun enqueueSequential(chainName: String, taskIds: List<Long>) {
        if (taskIds.isEmpty()) return
        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        val requests = taskIds.map { taskId ->
            buildAsyncTaskWorkRequest(
                taskId = taskId,
                taskType = repo.getById(taskId)?.type,
                continueChainOnFailure = taskId != taskIds.last(),
            )
        }
        var continuation = WorkManager.getInstance(context)
            .beginUniqueWork(chainName, ExistingWorkPolicy.KEEP, requests.first())
        requests.drop(1).forEach { request -> continuation = continuation.then(request) }
        continuation.enqueue()
    }

    fun cancel(taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(asyncTaskWorkName(taskId))
    }

    fun recoverAfterProcessStart() {
        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        val now = Clock.System.now().toEpochMilliseconds()
        repo.markRunningForRetryAfterProcessRestart(now)
        enqueueRunnable(repo, now)
    }

    fun recoverAndEnqueueRunnable() {
        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        val now = Clock.System.now().toEpochMilliseconds()
        repo.markExpiredRunningForRetry(now)
        enqueueRunnable(repo, now)
    }

    private fun enqueueRunnable(repo: AsyncTaskRepository, now: Long) =
        repo.runnableTasks(now).forEach { task -> enqueue(task.id) }
}

class GenericAsyncTaskWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId <= 0L) return Result.failure()

        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        val taskType = inputData.getString(KEY_TASK_TYPE) ?: repo.getById(taskId)?.type
        if (taskType == "external_favorite_sync") {
            GlobalContext.get().get<ExternalFavoriteSyncScheduler>().wake()
            return Result.success()
        }
        if (taskType in LONG_RUNNING_TASK_TYPES) {
            setForeground(createAsyncTaskForegroundInfo(applicationContext, taskId, taskType.orEmpty()))
        }
        val registry = GlobalContext.get().get<AsyncTaskHandlerRegistry>()
        val logStore = runCatching { GlobalContext.get().get<AsyncTaskLogStore>() }.getOrNull()
        val runner = AsyncTaskRunner(
            repository = repo,
            registry = registry,
            logger = logStore ?: NoopAsyncTaskLogger,
        )
        return when (runner.run(taskId)) {
            AsyncTaskRunOutcome.Succeeded -> {
                AsyncTaskScheduler(applicationContext).recoverAndEnqueueRunnable()
                Result.success()
            }
            AsyncTaskRunOutcome.Skipped -> Result.success()
            AsyncTaskRunOutcome.Failed -> {
                if (inputData.getBoolean(KEY_CONTINUE_CHAIN_ON_FAILURE, false)) Result.success() else Result.failure()
            }
            AsyncTaskRunOutcome.RetryScheduled -> Result.retry()
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_TASK_TYPE = "task_type"
        const val KEY_CONTINUE_CHAIN_ON_FAILURE = "continue_chain_on_failure"
    }
}

internal fun asyncTaskWorkName(taskId: Long): String = "async-task-$taskId"

internal fun buildAsyncTaskWorkRequest(
    taskId: Long,
    taskType: String? = null,
    continueChainOnFailure: Boolean = false,
): OneTimeWorkRequest {
    val builder = OneTimeWorkRequestBuilder<GenericAsyncTaskWorker>()
        .setInputData(
            workDataOf(
                GenericAsyncTaskWorker.KEY_TASK_ID to taskId,
                GenericAsyncTaskWorker.KEY_TASK_TYPE to taskType,
                GenericAsyncTaskWorker.KEY_CONTINUE_CHAIN_ON_FAILURE to continueChainOnFailure,
            ),
        )
    if (taskType in NETWORK_TASK_TYPES) {
        builder.setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
    }
    return builder.build()
}

private fun createAsyncTaskForegroundInfo(context: Context, taskId: Long, taskType: String): ForegroundInfo {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(ASYNC_TASK_CHANNEL_ID, "后台任务", NotificationManager.IMPORTANCE_LOW),
        )
    }
    val notification = NotificationCompat.Builder(context, ASYNC_TASK_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Daily Satori")
        .setContentText(asyncTaskNotificationText(taskType))
        .setOngoing(true)
        .setSilent(true)
        .build()
    val notificationId = ASYNC_TASK_NOTIFICATION_BASE + (taskId % 1000).toInt()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(notificationId, notification)
    }
}

private fun asyncTaskNotificationText(taskType: String): String = when (taskType) {
    "external_favorite_sync" -> "正在同步外部收藏…"
    "save_article" -> "正在保存并整理文章…"
    "remote_article_reprocess" -> "正在整理收藏文章…"
    else -> "正在执行后台任务…"
}

private val NETWORK_TASK_TYPES = setOf(
    "save_article",
    "remote_article_sync",
    "remote_news_fetch",
    "external_favorite_sync",
    "article_memory_extract",
    "remote_article_reprocess",
    "book_viewpoint_generate",
    "diary_attachment_transcribe",
    "diary_knowledge_extract",
)

private val LONG_RUNNING_TASK_TYPES = setOf(
    "save_article",
    "external_favorite_sync",
    "remote_article_reprocess",
)

private const val ASYNC_TASK_CHANNEL_ID = "async_tasks"
private const val ASYNC_TASK_NOTIFICATION_BASE = 2000
