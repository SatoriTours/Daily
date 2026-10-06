package com.dailysatori.core.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dailysatori.service.backup.BackupService
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class BackupScheduler(private val context: Context) {
    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private companion object {
        const val WorkName = "daily-backup"
    }
}

class BackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runBackupWork { GlobalContext.get().get<BackupService>().backupNow() }
}

internal suspend fun runBackupWork(attempt: suspend () -> Boolean): androidx.work.ListenableWorker.Result = try {
    if (attempt()) androidx.work.ListenableWorker.Result.success() else androidx.work.ListenableWorker.Result.retry()
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (_: Exception) {
    androidx.work.ListenableWorker.Result.retry()
}
