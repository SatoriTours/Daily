package com.dailysatori.core.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import com.dailysatori.core.startup.StartupWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dailysatori.core.task.AsyncTaskLogStore
import com.dailysatori.data.repository.AsyncTaskCancellationTarget
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ReminderAiBatchRepository
import com.dailysatori.core.task.ReminderAiParseTaskHandler
import com.dailysatori.core.task.reminderAiParseTaskPayloadJson
import com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry
import com.dailysatori.service.asynctask.AsyncTaskRunOutcome
import com.dailysatori.service.asynctask.AsyncTaskRunner
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.asynctask.isSerialAsyncTaskType
import com.dailysatori.service.asynctask.NoopAsyncTaskLogger
import com.dailysatori.shared.db.Async_task
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class AsyncTaskScheduler(private val context: Context) {
    fun enqueue(taskId: Long) {
        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        val task = repo.getById(taskId) ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        if (!task.isPending()) return
        val runAfter = task.run_after_ms
        if (runAfter != null && runAfter > now) {
            enqueueRetry(taskId, runAfter)
        }
        // Enqueue is also a queue kick: a superseded head may have left another target up next.
        val next = if (isSerialAsyncTaskType(task.type)) repo.serialHead(task, now) else task
        if (next != null && repo.canScheduleNow(next, now)) {
            enqueueWork(asyncTaskWorkName(next.id), next)
        }
    }

    fun notifyTerminal(ownerTaskId: Long, recoverOthers: Boolean = false) {
        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        val owner = repo.getById(ownerTaskId) ?: return
        if (owner.status in repo.activeStatusNames()) return
        val now = Clock.System.now().toEpochMilliseconds()
        val ownerIdentity = ownerTaskId.toString() + "-" + (owner.finished_at ?: 0L)
        val favoriteOwner = owner.type == AsyncTaskType.external_favorite_sync.name
        val targets = repo.successorIdsOf(ownerTaskId).toMutableSet()
        if (!favoriteOwner) repo.serialHead(owner, now)?.id?.let(targets::add)
        // The dedicated favourite worker drains its own lane; only explicit cross-type dependencies escape it.
        val woken = targets.mapNotNull(repo::getById)
            .filter { (!favoriteOwner || it.type != owner.type) && repo.canScheduleNow(it, now) }
        woken.forEach { enqueueWork(asyncTaskWakeUpWorkName(it.id, ownerIdentity), it) }
        // Some handlers insert follow-up tasks without scheduling them. Don't re-enqueue this release's targets.
        if (recoverOthers) recoverAndEnqueueRunnable(woken.mapTo(hashSetOf()) { it.id })
    }

    fun scheduleDatabaseRetry(taskId: Long) {
        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        val task = repo.getById(taskId)?.takeIf { it.status == AsyncTaskStatus.retrying.name } ?: return
        val deadline = task.run_after_ms ?: return
        enqueueRetry(taskId, deadline)
        val now = Clock.System.now().toEpochMilliseconds()
        val head = repo.serialHead(task, now)
        if (head != null && head.id != taskId && repo.canScheduleNow(head, now)) {
            enqueueWork(asyncTaskWakeUpWorkName(head.id, "$taskId-retry-$deadline"), head)
        }
    }

    fun enqueueRetry(taskId: Long, runAfterMs: Long) {
        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        val task = repo.getById(taskId)?.takeIf { it.isPending() } ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        enqueueWork(asyncTaskRetryWorkName(taskId, runAfterMs), task, asyncTaskRetryDelayMs(runAfterMs, now))
    }

    // Release wakes use a name independent of an older WorkManager attempt that's still finishing.
    private fun enqueueWork(name: String, task: Async_task, delayMs: Long = 0) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            name,
            ExistingWorkPolicy.KEEP,
            buildAsyncTaskWorkRequest(task.id, task.type, initialDelayMs = delayMs),
        )
    }

    fun enqueueSequential(chainName: String, taskIds: List<Long>) {
        if (taskIds.isEmpty()) return
        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        // Durable predecessors govern ordering across restarts and retries.
        // Each task has its own unique work, so KEEP cannot discard a new chain tail.
        repo.linkSequentialTasks(taskIds)
        taskIds.forEach(::enqueue)
    }

    fun cancel(taskId: Long) {
        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        val finishedAt = Clock.System.now().toEpochMilliseconds()
        val cancelled = repo.cancelActive(taskId, finishedAt)
        val type = cancelled?.type
            ?: repo.getById(taskId)?.takeIf { it.status == AsyncTaskStatus.cancelled.name }?.type
            ?: return
        if (type == AsyncTaskType.external_favorite_sync.name) {
            if (cancelled != null) completeCancellation(repo, listOf(cancelled), finishedAt)
            return
        }
        // Some other callers persist a cancellation before asking the scheduler to stop the work.
        stopExecution(taskId)
        notifyTerminal(taskId)
    }

    fun cancelSnapshot(taskIds: Set<Long>) {
        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        val finishedAt = Clock.System.now().toEpochMilliseconds()
        completeCancellation(repo, repo.cancelActiveSnapshot(taskIds, finishedAt), finishedAt)
    }

    private fun stopExecution(taskId: Long) {
        ExternalFavoriteTaskCancellationRegistry.cancel(taskId)
        WorkManager.getInstance(context).cancelAllWorkByTag(asyncTaskWorkTag(taskId))
    }

    private fun completeCancellation(
        repo: AsyncTaskRepository,
        cancelled: List<AsyncTaskCancellationTarget>,
        finishedAt: Long,
    ) {
        if (cancelled.isEmpty()) return
        cancelled.forEach { stopExecution(it.id) }
        val now = Clock.System.now().toEpochMilliseconds()
        val targets = linkedMapOf<Long, Pair<Long, Async_task>>()
        // Discover explicit successors once, after the whole snapshot has transitioned.
        repo.dueSuccessorsOf(cancelled.mapTo(hashSetOf()) { it.id }, now).forEach { (ownerId, task) ->
            targets[task.id] = ownerId to task
        }
        cancelled.distinctBy { it.type }.forEach { owner ->
            repo.serialHead(owner.type, now)?.let { targets.putIfAbsent(it.id, owner.id to it) }
        }
        var wakeFavorites = false
        targets.values.forEach { (ownerId, task) ->
            if (!repo.canScheduleNow(task, now)) return@forEach
            if (task.type == AsyncTaskType.external_favorite_sync.name) wakeFavorites = true
            else enqueueWork(asyncTaskWakeUpWorkName(task.id, "$ownerId-$finishedAt"), task)
        }
        if (wakeFavorites) ExternalFavoriteSyncScheduler(context).wake()
    }

    fun recoverAfterProcessStart() {
        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        GlobalContext.get().get<ReminderAiBatchRepository>().reconcileScheduleIntents()
        GlobalContext.get().get<ReminderAiBatchRepository>().reconcileOrphanedActiveBatches(
            taskType = ReminderAiParseTaskHandler.TYPE,
            payloadForBatch = ::reminderAiParseTaskPayloadJson,
            uniqueKeyForBatch = { "reminder_ai_parse:$it" },
        )
        val now = Clock.System.now().toEpochMilliseconds()
        repo.markRunningForRetryAfterProcessRestart(now)
        // Repair leases before any UI or worker can start new tasks. Enqueue on IO afterwards.
    }

    fun recoverAndEnqueueRunnable() = recoverAndEnqueueRunnable(emptySet())

    private fun recoverAndEnqueueRunnable(alreadyWoken: Set<Long>) {
        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        val now = Clock.System.now().toEpochMilliseconds()
        repo.markExpiredRunningForRetry(now)
        val serialTypes = hashSetOf<String>()
        repo.runnableTasks(now, Long.MAX_VALUE).forEach { task ->
            if (task.type == AsyncTaskType.external_favorite_sync.name) return@forEach
            if (isSerialAsyncTaskType(task.type) && !serialTypes.add(task.type)) return@forEach
            if (task.id !in alreadyWoken && repo.canScheduleNow(task, now)) {
                enqueueWork(asyncTaskWorkName(task.id), task)
            }
        }
        repo.futureRetryingTasks(now).forEach { task ->
            if (task.type == AsyncTaskType.external_favorite_sync.name) return@forEach
            task.run_after_ms?.let { enqueueRetry(task.id, it) }
        }
    }

    private fun AsyncTaskRepository.serialHead(owner: Async_task, now: Long): Async_task? = serialHead(owner.type, now)

    private fun AsyncTaskRepository.serialHead(type: String, now: Long): Async_task? =
        if (isSerialAsyncTaskType(type)) runnableTasksByType(type, now, 1).firstOrNull() else null

    private fun AsyncTaskRepository.canScheduleNow(task: Async_task, now: Long): Boolean =
        task.isPending() && (task.run_after_ms ?: Long.MIN_VALUE) <= now &&
            !waitingForPredecessor(task.id) &&
            (!isSerialAsyncTaskType(task.type) || serialClaimBlocker(task.id, now) == null)

    private fun Async_task.isPending(): Boolean =
        status == AsyncTaskStatus.queued.name || status == AsyncTaskStatus.retrying.name
}

class GenericAsyncTaskWorker(
    appContext: Context,
    params: WorkerParameters,
) : StartupWorker(appContext, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = createAsyncTaskForegroundInfo(
        applicationContext,
        inputData.getLong(KEY_TASK_ID, -1L),
        inputData.getString(KEY_TASK_TYPE).orEmpty(),
    )

    override suspend fun doReadyWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId <= 0L) return Result.failure()

        val repo = GlobalContext.get().get<AsyncTaskRepository>()
        val taskType = inputData.getString(KEY_TASK_TYPE) ?: repo.getById(taskId)?.type
        if (taskType == "external_favorite_sync") {
            if (repo.getById(taskId)?.status in setOf(AsyncTaskStatus.queued.name, AsyncTaskStatus.retrying.name)) {
                GlobalContext.get().get<ExternalFavoriteSyncScheduler>().wake()
            }
            return Result.success()
        }
        ForegroundDiagnostics.workerStarted(
            ForegroundDiagnostics.ASYNC_TASK, taskId, taskType, runAttemptCount,
        )
        val scheduler = AsyncTaskScheduler(applicationContext)
        val runner = AsyncTaskRunner(
            repository = repo,
            registry = GlobalContext.get().get<AsyncTaskHandlerRegistry>(),
            logger = runCatching { GlobalContext.get().get<AsyncTaskLogStore>() }.getOrNull() ?: NoopAsyncTaskLogger,
            beforeExecute = {
                if (isLongRunningAsyncTask(taskType)) enterForeground(taskId, taskType.orEmpty())
            },
        )
        val outcome = try {
            runner.run(taskId)
        } catch (error: CancellationException) {
            ForegroundDiagnostics.workerCancelled(ForegroundDiagnostics.ASYNC_TASK, taskId)
            scheduler.scheduleDatabaseRetry(taskId)
            throw error
        }
        val result = when (outcome) {
            AsyncTaskRunOutcome.Succeeded -> {
                scheduler.notifyTerminal(taskId, recoverOthers = true)
                Result.success()
            }
            AsyncTaskRunOutcome.Skipped -> {
                if (repo.getById(taskId)?.status == AsyncTaskStatus.cancelled.name) scheduler.notifyTerminal(taskId)
                Result.success()
            }
            is AsyncTaskRunOutcome.Blocked -> Result.success()
            AsyncTaskRunOutcome.Failed -> {
                scheduler.notifyTerminal(taskId)
                if (inputData.getBoolean(KEY_CONTINUE_CHAIN_ON_FAILURE, false)) Result.success() else Result.failure()
            }
            AsyncTaskRunOutcome.RetryScheduled -> {
                scheduler.scheduleDatabaseRetry(taskId)
                Result.success()
            }
        }
        ForegroundDiagnostics.workerFinished(
            ForegroundDiagnostics.ASYNC_TASK, outcome.foregroundOutcomeName(), taskId, taskType,
        )
        return result
    }

    private suspend fun enterForeground(taskId: Long, taskType: String) {
        val foreground = createAsyncTaskForegroundInfo(applicationContext, taskId, taskType)
        ForegroundDiagnostics.requestStarted(
            ForegroundDiagnostics.ASYNC_TASK, foreground.notificationId, foreground.foregroundServiceType,
            taskId, taskType, runAttemptCount,
        )
        try {
            setForeground(foreground)
            ForegroundDiagnostics.requestSucceeded(
                ForegroundDiagnostics.ASYNC_TASK, foreground.notificationId, taskId, taskType,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ForegroundDiagnostics.requestFailed(
                ForegroundDiagnostics.ASYNC_TASK, foreground.notificationId, taskId, taskType, error,
            )
            throw error
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_TASK_TYPE = "task_type"
        const val KEY_CONTINUE_CHAIN_ON_FAILURE = "continue_chain_on_failure"
    }
}

internal fun asyncTaskWorkName(taskId: Long): String = "async-task-$taskId"
internal fun asyncTaskWorkTag(taskId: Long): String = "async-task-id-$taskId"
internal fun asyncTaskRetryWorkName(taskId: Long, runAfterMs: Long): String = "async-task-retry-$taskId-$runAfterMs"
/** Independent of [asyncTaskWorkName]: a delayed or running attempt of the same task cannot swallow it. */
internal fun asyncTaskWakeUpWorkName(successorTaskId: Long, ownerIdentity: String): String =
    "async-task-wake-" + ownerIdentity + "-" + successorTaskId
internal fun asyncTaskRetryDelayMs(runAfterMs: Long, nowMs: Long): Long = (runAfterMs - nowMs).coerceAtLeast(0L)

internal fun buildAsyncTaskWorkRequest(
    taskId: Long,
    taskType: String? = null,
    continueChainOnFailure: Boolean = false,
    initialDelayMs: Long = 0L,
): OneTimeWorkRequest {
    val builder = OneTimeWorkRequestBuilder<GenericAsyncTaskWorker>()
        .addTag(asyncTaskWorkTag(taskId))
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
    if (initialDelayMs > 0L) {
        builder.setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
    } else if (taskType == ReminderAiParseTaskHandler.TYPE) {
        builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
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
    "assistant_turn" -> "个人助手正在处理，可切换到其他应用…"
    ReminderAiParseTaskHandler.TYPE -> "正在解析提醒…"
    "external_favorite_sync" -> "正在同步外部收藏…"
    "save_article" -> "正在保存并整理文章…"
    "remote_article_reprocess" -> "正在整理收藏文章…"
    "diary_thread_summarize" -> "正在整理日记续写…"
    else -> "正在执行后台任务…"
}

private val NETWORK_TASK_TYPES = setOf(
    "assistant_turn",
    "article_cover_download",
    "sms_reminder_parse",
    "external_favorite_organize",
    "save_article",
    "remote_article_sync",
    "remote_news_fetch",
    "external_favorite_sync",
    "article_memory_extract",
    "remote_article_reprocess",
    "book_viewpoint_generate",
    "diary_attachment_transcribe",
    "diary_knowledge_extract",
    "diary_thread_summarize",
    "reminder_ai_parse",
)

private val LONG_RUNNING_TASK_TYPES = setOf(
    "assistant_turn",
    "sms_reminder_parse",
    "external_favorite_organize",
    "save_article",
    "external_favorite_sync",
    "remote_article_reprocess",
    "remote_article_sync",
    "remote_news_fetch",
    "reminder_ai_parse",
)

internal fun isLongRunningAsyncTask(type: String?): Boolean = type in LONG_RUNNING_TASK_TYPES

private const val ASYNC_TASK_CHANNEL_ID = "async_tasks"
private const val ASYNC_TASK_NOTIFICATION_BASE = 2000
