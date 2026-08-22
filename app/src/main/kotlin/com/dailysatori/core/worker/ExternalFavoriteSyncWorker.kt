package com.dailysatori.core.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.lifecycle.Observer
import com.dailysatori.core.task.externalFavoriteSyncTaskPayloadJson
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.shared.db.External_favorite_source
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry
import com.dailysatori.service.asynctask.AsyncTaskRunOutcome
import com.dailysatori.service.asynctask.AsyncTaskRunner
import com.dailysatori.core.task.AsyncTaskLogStore
import com.dailysatori.service.externalfavorites.FavoriteSyncMode
import com.dailysatori.service.externalfavorites.FavoriteSyncProgress
import com.dailysatori.service.externalfavorites.FavoriteSyncService
import com.dailysatori.service.externalfavorites.FavoriteAuthException
import com.dailysatori.service.externalfavorites.FavoriteRateLimitException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

internal object ExternalFavoriteTaskCancellationRegistry {
    private val runningJobs = ConcurrentHashMap<Long, Job>()

    fun register(taskId: Long, job: Job) {
        runningJobs[taskId] = job
    }

    fun unregister(taskId: Long, job: Job) {
        runningJobs.remove(taskId, job)
    }

    fun cancel(taskId: Long): Boolean = runningJobs[taskId]?.let { job ->
        job.cancel(CancellationException("外部收藏同步已取消"))
        true
    } ?: false
}

class ExternalFavoriteSyncScheduler(
    private val context: Context,
    private val asyncTaskRepo: AsyncTaskRepository? = null,
    private val asyncTaskScheduler: AsyncTaskScheduler? = null,
) {
    fun enqueue(sourceId: Long, mode: String = FavoriteSyncMode.sync.name): Long? {
        if (asyncTaskRepo != null && asyncTaskScheduler != null) {
            val taskId = asyncTaskRepo.enqueueUniqueFamily(
                type = AsyncTaskType.external_favorite_sync.name,
                payloadJson = externalFavoriteSyncTaskPayloadJson(sourceId, mode),
                uniqueKey = externalFavoriteSyncUniqueKey(sourceId, mode),
                uniqueKeyPrefix = externalFavoriteSyncUniqueKeyPrefix(sourceId),
            )
            wake()
            return taskId
        }
        val request = buildExternalFavoriteSyncWorkRequest(sourceId, mode)
        WorkManager.getInstance(context).enqueueUniqueWork(
            externalFavoriteSyncWorkName(sourceId, mode),
            ExistingWorkPolicy.KEEP,
            request,
        )
        return null
    }

    fun wake() {
        val request = buildExternalFavoriteQueueWorkRequest()
        WorkManager.getInstance(context).enqueueUniqueWork(
            EXTERNAL_FAVORITE_QUEUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    fun wakeAfter(delayMs: Long) {
        WorkManager.getInstance(context).enqueue(buildExternalFavoriteQueueDelayWorkRequest(delayMs))
    }

    fun recover() {
        val repository = asyncTaskRepo ?: return
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        if (repository.runnableTasksByType(AsyncTaskType.external_favorite_sync.name, now, 1).isNotEmpty()) {
            wake()
        }
        repository.nextRunAfterByType(AsyncTaskType.external_favorite_sync.name, now)
            ?.let { wakeAfter((it - now).coerceAtLeast(1L)) }
    }

    fun cancelSync(source: External_favorite_source) {
        val sourceId = source.id
        FavoriteSyncMode.entries.forEach { mode ->
            asyncTaskRepo
                ?.cancelLatestByUniqueKey(externalFavoriteSyncUniqueKey(sourceId, mode.name))
                ?.let { taskId ->
                    ExternalFavoriteTaskCancellationRegistry.cancel(taskId)
                    asyncTaskScheduler?.cancel(taskId)
                }
            WorkManager.getInstance(context).cancelUniqueWork(externalFavoriteSyncWorkName(sourceId, mode.name))
        }
        cancelPeriodic(sourceId)
        enqueuePeriodic(source)
    }

    fun observeSync(sourceId: Long): Flow<WorkInfo?> = callbackFlow {
        val liveData = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(externalFavoriteSyncWorkName(sourceId, FavoriteSyncMode.sync.name))
        val observer = Observer<List<WorkInfo>> { infos ->
            trySend(infos.firstOrNull())
        }
        runOnMainThread {
            liveData.observeForever(observer)
        }
        awaitClose {
            runOnMainThread {
                liveData.removeObserver(observer)
            }
        }
    }

    fun enqueuePeriodic(sourceId: Long, intervalMinutes: Long) {
        val request = buildExternalFavoritePeriodicSyncWorkRequest(sourceId, intervalMinutes)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            externalFavoriteSyncWorkName(sourceId, "periodic"),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueuePeriodic(source: External_favorite_source) {
        if (externalFavoriteShouldSchedulePeriodic(source.enabled, source.sync_interval_minutes)) {
            enqueuePeriodic(source.id, source.sync_interval_minutes)
        }
    }

    fun enqueuePeriodic(sources: List<External_favorite_source>) {
        sources.forEach(::enqueuePeriodic)
    }

    fun cancelPeriodic(sourceId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(externalFavoriteSyncWorkName(sourceId, "periodic"))
    }
}

private fun runOnMainThread(block: () -> Unit) {
    val mainLooper = Looper.getMainLooper()
    if (Looper.myLooper() == mainLooper) {
        block()
    } else {
        Handler(mainLooper).post(block)
    }
}

class ExternalFavoriteSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo =
        createExternalFavoriteForegroundInfo(applicationContext)

    override suspend fun doWork(): Result {
        val sourceId = inputData.getLong(KEY_SOURCE_ID, -1L)
        val mode = externalFavoriteSyncMode(inputData.getString(KEY_MODE)) ?: return Result.failure()
        if (sourceId <= 0L) return Result.failure()

        setForeground(getForegroundInfo())
        setProgress(externalFavoriteSyncProgressData("queued", 0, DEFAULT_X_BOOKMARK_SYNC_MAX_PAGES, 0, false))
        GlobalContext.get().get<ExternalFavoriteSyncScheduler>().enqueue(sourceId, mode.name)
        return Result.success()
    }

    companion object {
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_MODE = "mode"
        const val PROGRESS_PHASE = "phase"
        const val PROGRESS_PAGES_SEEN = "pages_seen"
        const val PROGRESS_MAX_PAGES = "max_pages"
        const val PROGRESS_ITEMS_SEEN = "items_seen"
        const val PROGRESS_HISTORY_COMPLETE = "history_complete"
    }
}

class ExternalFavoriteQueueWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo =
        createExternalFavoriteForegroundInfo(applicationContext)

    override suspend fun doWork(): Result {
        val koin = GlobalContext.get()
        val repository = koin.get<AsyncTaskRepository>()
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        repository.markExpiredRunningForRetry(now)
        val task = repository.runnableTasksByType(AsyncTaskType.external_favorite_sync.name, now, 1).firstOrNull()
        if (task == null) {
            scheduleNextRetry(repository, now)
            return Result.success()
        }

        setForeground(getForegroundInfo())
        val runner = AsyncTaskRunner(
            repository = repository,
            registry = koin.get<AsyncTaskHandlerRegistry>(),
            logger = runCatching { koin.get<AsyncTaskLogStore>() }.getOrNull()
                ?: com.dailysatori.service.asynctask.NoopAsyncTaskLogger,
        )
        val outcome = try {
            coroutineScope {
                val taskJob = async { runner.run(task.id) }
                ExternalFavoriteTaskCancellationRegistry.register(task.id, taskJob)
                try {
                    taskJob.await()
                } catch (error: CancellationException) {
                    if (repository.getById(task.id)?.status == AsyncTaskStatus.cancelled.name) {
                        AsyncTaskRunOutcome.Skipped
                    } else {
                        throw error
                    }
                } finally {
                    ExternalFavoriteTaskCancellationRegistry.unregister(task.id, taskJob)
                }
            }
        } catch (error: CancellationException) {
            scheduleNextRetry(repository, kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
            throw error
        }
        val afterRun = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val scheduler = koin.get<ExternalFavoriteSyncScheduler>()
        val current = repository.getById(task.id)
        val currentRunAfter = current?.run_after_ms
        val claimWasBusy = outcome == AsyncTaskRunOutcome.RetryScheduled &&
            current?.status in setOf("queued", "retrying") &&
            (currentRunAfter == null || currentRunAfter <= afterRun)
        if (claimWasBusy) {
            scheduler.wakeAfter(SERIAL_CLAIM_RETRY_DELAY_MS)
        } else if (repository.runnableTasksByType(AsyncTaskType.external_favorite_sync.name, afterRun, 1).isNotEmpty()) {
            scheduler.wake()
        }
        scheduleNextRetry(repository, afterRun)
        return Result.success()
    }

    private fun scheduleNextRetry(repository: AsyncTaskRepository, now: Long) {
        val next = repository.nextRunAfterByType(AsyncTaskType.external_favorite_sync.name, now) ?: return
        GlobalContext.get().get<ExternalFavoriteSyncScheduler>().wakeAfter((next - now).coerceAtLeast(1L))
    }
}

class ExternalFavoriteQueueDelayWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        GlobalContext.get().get<ExternalFavoriteSyncScheduler>().wake()
        return Result.success()
    }
}

internal fun externalFavoriteSyncProgressData(progress: FavoriteSyncProgress) =
    externalFavoriteSyncProgressData(
        phase = progress.phase,
        pagesSeen = progress.pagesSeen,
        maxPages = progress.maxPages,
        itemsSeen = progress.itemsSeen,
        historyComplete = progress.historyComplete,
    )

internal fun externalFavoriteSyncProgressData(
    phase: String,
    pagesSeen: Int,
    maxPages: Int,
    itemsSeen: Int,
    historyComplete: Boolean,
) = workDataOf(
    ExternalFavoriteSyncWorker.PROGRESS_PHASE to phase,
    ExternalFavoriteSyncWorker.PROGRESS_PAGES_SEEN to pagesSeen,
    ExternalFavoriteSyncWorker.PROGRESS_MAX_PAGES to maxPages,
    ExternalFavoriteSyncWorker.PROGRESS_ITEMS_SEEN to itemsSeen,
    ExternalFavoriteSyncWorker.PROGRESS_HISTORY_COMPLETE to historyComplete,
)

internal fun externalFavoriteSyncWorkName(sourceId: Long, mode: String): String =
    "external-favorite-sync-$sourceId-$mode"

internal fun externalFavoriteSyncUniqueKey(sourceId: Long, mode: String): String =
    "external_favorite_sync:$sourceId:$mode"

internal fun externalFavoriteSyncUniqueKeyPrefix(sourceId: Long): String =
    "external_favorite_sync:$sourceId:"

internal fun buildExternalFavoriteQueueWorkRequest(): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<ExternalFavoriteQueueWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .build()

internal fun buildExternalFavoriteQueueDelayWorkRequest(delayMs: Long): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<ExternalFavoriteQueueDelayWorker>()
        .setInitialDelay(delayMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
        .build()

internal fun buildExternalFavoriteSyncWorkRequest(
    sourceId: Long,
    mode: String = FavoriteSyncMode.sync.name,
): OneTimeWorkRequest =
    OneTimeWorkRequestBuilder<ExternalFavoriteSyncWorker>()
        .setInputData(
            workDataOf(
                ExternalFavoriteSyncWorker.KEY_SOURCE_ID to sourceId,
                ExternalFavoriteSyncWorker.KEY_MODE to mode,
            ),
        )
        .build()

internal fun buildExternalFavoritePeriodicSyncWorkRequest(
    sourceId: Long,
    intervalMinutes: Long,
): PeriodicWorkRequest {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    return PeriodicWorkRequestBuilder<ExternalFavoriteSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
        .setInputData(
            workDataOf(
                ExternalFavoriteSyncWorker.KEY_SOURCE_ID to sourceId,
                ExternalFavoriteSyncWorker.KEY_MODE to FavoriteSyncMode.sync.name,
            ),
        )
        .setConstraints(constraints)
        .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
        .build()
}

internal fun externalFavoriteSyncMode(value: String?): FavoriteSyncMode? =
    FavoriteSyncMode.entries.firstOrNull { it.name == value }

internal fun externalFavoriteShouldSchedulePeriodic(enabled: Long, intervalMinutes: Long): Boolean =
    enabled == 1L && intervalMinutes > 0L

internal const val DEFAULT_X_BOOKMARK_SYNC_MAX_PAGES = 250

private fun createExternalFavoriteForegroundInfo(context: Context): ForegroundInfo {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                EXTERNAL_FAVORITE_CHANNEL_ID,
                "外部收藏同步",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
    val notification = NotificationCompat.Builder(context, EXTERNAL_FAVORITE_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Daily Satori")
        .setContentText("正在同步外部收藏…")
        .setOngoing(true)
        .setSilent(true)
        .build()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(
            EXTERNAL_FAVORITE_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    } else {
        ForegroundInfo(EXTERNAL_FAVORITE_NOTIFICATION_ID, notification)
    }
}

private const val EXTERNAL_FAVORITE_CHANNEL_ID = "external_favorite_sync"
private const val EXTERNAL_FAVORITE_NOTIFICATION_ID = 3101
private const val EXTERNAL_FAVORITE_QUEUE_WORK_NAME = "external-favorite-sync-queue"
private const val SERIAL_CLAIM_RETRY_DELAY_MS = 5_000L

internal fun externalFavoriteSyncFailureResult(error: Exception): ListenableWorker.Result = when (error) {
    is FavoriteAuthException -> ListenableWorker.Result.failure()
    is FavoriteRateLimitException -> ListenableWorker.Result.failure()
    else -> ListenableWorker.Result.retry()
}
