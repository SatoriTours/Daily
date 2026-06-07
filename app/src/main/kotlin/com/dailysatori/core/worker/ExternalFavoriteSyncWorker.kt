package com.dailysatori.core.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dailysatori.service.externalfavorites.FavoriteSyncMode
import com.dailysatori.service.externalfavorites.FavoriteSyncService
import kotlinx.coroutines.CancellationException
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class ExternalFavoriteSyncScheduler(private val context: Context) {
    fun enqueue(sourceId: Long, mode: String = FavoriteSyncMode.recent.name) {
        val request = buildExternalFavoriteSyncWorkRequest(sourceId, mode)
        WorkManager.getInstance(context).enqueueUniqueWork(
            externalFavoriteSyncWorkName(sourceId, mode),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueuePeriodic(sourceId: Long, intervalMinutes: Long) {
        val request = buildExternalFavoritePeriodicSyncWorkRequest(sourceId, intervalMinutes)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            externalFavoriteSyncWorkName(sourceId, "periodic"),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
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
            GlobalContext.get().get<FavoriteSyncService>().syncSource(sourceId, mode)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: IllegalArgumentException) {
            Result.failure()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_MODE = "mode"
    }
}

internal fun externalFavoriteSyncWorkName(sourceId: Long, mode: String): String =
    "external-favorite-sync-$sourceId-$mode"

internal fun buildExternalFavoriteSyncWorkRequest(
    sourceId: Long,
    mode: String = FavoriteSyncMode.recent.name,
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
                ExternalFavoriteSyncWorker.KEY_MODE to FavoriteSyncMode.recent.name,
            ),
        )
        .setConstraints(constraints)
        .build()
}

internal fun externalFavoriteSyncMode(value: String?): FavoriteSyncMode? =
    FavoriteSyncMode.entries.firstOrNull { it.name == value }
