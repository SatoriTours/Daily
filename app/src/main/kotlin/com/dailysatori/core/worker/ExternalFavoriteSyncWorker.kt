package com.dailysatori.core.worker

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
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
import com.dailysatori.shared.db.External_favorite_source
import com.dailysatori.service.externalfavorites.FavoriteSyncMode
import com.dailysatori.service.externalfavorites.FavoriteSyncProgress
import com.dailysatori.service.externalfavorites.FavoriteSyncService
import com.dailysatori.service.externalfavorites.XFavoriteAuthException
import com.dailysatori.service.externalfavorites.XFavoriteRateLimitException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class ExternalFavoriteSyncScheduler(private val context: Context) {
    fun enqueue(sourceId: Long, mode: String = FavoriteSyncMode.sync.name) {
        val request = buildExternalFavoriteSyncWorkRequest(sourceId, mode)
        WorkManager.getInstance(context).enqueueUniqueWork(
            externalFavoriteSyncWorkName(sourceId, mode),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelSync(sourceId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(externalFavoriteSyncWorkName(sourceId, FavoriteSyncMode.sync.name))
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
    override suspend fun doWork(): Result {
        val sourceId = inputData.getLong(KEY_SOURCE_ID, -1L)
        val mode = externalFavoriteSyncMode(inputData.getString(KEY_MODE)) ?: return Result.failure()
        if (sourceId <= 0L) return Result.failure()

        return try {
            setProgress(externalFavoriteSyncProgressData("queued", 0, 3, 0, false))
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
        .build()
}

internal fun externalFavoriteSyncMode(value: String?): FavoriteSyncMode? =
    FavoriteSyncMode.entries.firstOrNull { it.name == value }

internal fun externalFavoriteShouldSchedulePeriodic(enabled: Long, intervalMinutes: Long): Boolean =
    enabled == 1L && intervalMinutes > 0L

internal fun externalFavoriteSyncFailureResult(error: Exception): ListenableWorker.Result = when (error) {
    is XFavoriteAuthException -> ListenableWorker.Result.failure()
    is XFavoriteRateLimitException -> ListenableWorker.Result.failure()
    else -> ListenableWorker.Result.retry()
}
