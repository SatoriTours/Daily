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
import com.dailysatori.service.externalfavorites.FavoriteSyncMode
import com.dailysatori.service.externalfavorites.FavoriteSyncProgress
import com.dailysatori.service.externalfavorites.FavoriteSyncService
import com.dailysatori.service.externalfavorites.FavoriteAuthException
import com.dailysatori.service.externalfavorites.FavoriteRateLimitException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class ExternalFavoriteSyncScheduler(
    private val context: Context,
    private val asyncTaskRepo: AsyncTaskRepository? = null,
    private val asyncTaskScheduler: AsyncTaskScheduler? = null,
) {
    fun enqueue(sourceId: Long, mode: String = FavoriteSyncMode.sync.name): Long? {
        if (asyncTaskRepo != null && asyncTaskScheduler != null) {
            val taskId = asyncTaskRepo.enqueue(
                type = AsyncTaskType.external_favorite_sync.name,
                payloadJson = externalFavoriteSyncTaskPayloadJson(sourceId, mode),
                uniqueKey = externalFavoriteSyncUniqueKey(sourceId, mode),
            )
            asyncTaskScheduler.enqueue(taskId)
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

    fun cancelSync(source: External_favorite_source) {
        val sourceId = source.id
        FavoriteSyncMode.entries.forEach { mode ->
            asyncTaskRepo
                ?.cancelLatestByUniqueKey(externalFavoriteSyncUniqueKey(sourceId, mode.name))
                ?.let { taskId -> asyncTaskScheduler?.cancel(taskId) }
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

        return try {
            setForeground(getForegroundInfo())
            setProgress(externalFavoriteSyncProgressData("queued", 0, DEFAULT_X_BOOKMARK_SYNC_MAX_PAGES, 0, false))
            GlobalContext.get().get<FavoriteSyncService>().syncSource(sourceId, mode) { progress ->
                setProgress(externalFavoriteSyncProgressData(progress))
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: IllegalArgumentException) {
            Result.failure()
        } catch (error: Exception) {
            externalFavoriteSyncFailureResult(error)
        }
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

internal fun externalFavoriteSyncFailureResult(error: Exception): ListenableWorker.Result = when (error) {
    is FavoriteAuthException -> ListenableWorker.Result.failure()
    is FavoriteRateLimitException -> ListenableWorker.Result.failure()
    else -> ListenableWorker.Result.retry()
}
