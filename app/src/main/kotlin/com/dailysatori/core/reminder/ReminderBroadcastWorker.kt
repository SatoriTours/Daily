package com.dailysatori.core.reminder

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.Data
import com.dailysatori.core.startup.StartupWorker
import org.koin.core.context.GlobalContext

class ReminderBroadcastWorker(context: Context, params: WorkerParameters) : StartupWorker(context, params) {
    override suspend fun doReadyWork(): Result {
        GlobalContext.get().get<ReminderCoordinator>().handleBroadcast(
            inputData.getString(KEY_ACTION),
            inputData.getString(ReminderReceiver.EXTRA_REMINDER_ID),
            if (inputData.keyValueMap.containsKey(ReminderReceiver.EXTRA_EXPECTED_VERSION))
                inputData.getLong(ReminderReceiver.EXTRA_EXPECTED_VERSION, -1) else null,
        )
        return Result.success()
    }

    companion object { const val KEY_ACTION = "broadcast_action" }
}

internal fun ReminderCoordinator.handleBroadcast(action: String?, id: String?, version: Long?) {
    if (isReminderRestoreAction(action)) {
        recomputeAll()
        return
    }
    if (id == null || version == null) return
    when (action) {
        ReminderReceiver.ACTION_DELIVER -> deliver(id, version)
        ReminderReceiver.ACTION_CUTOFF -> cutoff(id, version)
        ReminderReceiver.ACTION_DISMISS -> dismiss(id, version)
        ReminderReceiver.ACTION_COMPLETE -> complete(id, version)
    }
}

internal fun reminderBroadcastData(action: String?, id: String?, version: Long?): Data = Data.Builder()
    .putString(ReminderBroadcastWorker.KEY_ACTION, action)
    .putString(ReminderReceiver.EXTRA_REMINDER_ID, id)
    .apply { if (version != null) putLong(ReminderReceiver.EXTRA_EXPECTED_VERSION, version) }
    .build()
