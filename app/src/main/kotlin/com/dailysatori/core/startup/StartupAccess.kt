package com.dailysatori.core.startup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dailysatori.DailySatoriApplication

val Context.appStartup: StartupCoordinator
    get() = (applicationContext as DailySatoriApplication).startup

/** WorkManager may start the process before any activity. Suspend, never block its main thread. */
abstract class StartupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    final override suspend fun doWork(): Result =
        if (applicationContext.appStartup.awaitReady()) doReadyWork() else Result.retry()

    abstract suspend fun doReadyWork(): Result
}
