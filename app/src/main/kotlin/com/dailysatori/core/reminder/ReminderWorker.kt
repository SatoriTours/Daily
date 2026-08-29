package com.dailysatori.core.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_REMINDER_ID) ?: return Result.failure()
        if (!inputData.keyValueMap.containsKey(KEY_EXPECTED_VERSION)) return Result.failure()
        val version = inputData.getLong(KEY_EXPECTED_VERSION, -1)
        GlobalContext.get().get<ReminderCoordinator>().deliver(id, version)
        return Result.success()
    }

    companion object {
        const val KEY_REMINDER_ID = "reminder_id"
        const val KEY_EXPECTED_VERSION = "expected_version"
    }
}
