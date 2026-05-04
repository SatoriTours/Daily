package com.dailysatori.core.worker

import android.content.Context
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
        val request = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS).build()
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
    override suspend fun doWork(): Result {
        return try {
            GlobalContext.get().get<BackupService>().backupNow()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
