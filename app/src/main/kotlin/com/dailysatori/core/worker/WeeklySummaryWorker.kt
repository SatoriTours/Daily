package com.dailysatori.core.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dailysatori.service.weekly.WeeklySummaryService
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class WeeklySummaryScheduler(private val context: Context) {
    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(nextWeeklySummaryDelayMs(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private companion object {
        const val WORK_NAME = "weekly-summary"
    }
}

class WeeklySummaryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val service = GlobalContext.get().get<WeeklySummaryService>()
        val range = service.getLastCompletedWeekRange() ?: return Result.success()
        return if (service.generateWeeklySummary(range.first, range.second)) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}

internal fun nextWeeklySummaryDelayMs(
    now: kotlinx.datetime.Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Long {
    val localNow = now.toLocalDateTime(timeZone)
    val daysUntilMonday = (8 - localNow.date.dayOfWeek.value) % 7
    var dueDate = localNow.date.plus(daysUntilMonday, DateTimeUnit.DAY)
    val dueTime = LocalTime(3, 0)
    if (daysUntilMonday == 0 && localNow.time >= dueTime) {
        dueDate = dueDate.plus(7, DateTimeUnit.DAY)
    }
    val due = LocalDateTime(dueDate, dueTime).toInstant(timeZone)
    return (due.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0L)
}
