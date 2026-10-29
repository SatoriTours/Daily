package com.dailysatori.core.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dailysatori.core.startup.appStartup

internal fun isReminderRestoreAction(action: String?): Boolean = action in REMINDER_RESTORE_ACTIONS

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val id = intent.getStringExtra(EXTRA_REMINDER_ID)
                val version = if (intent.hasExtra(EXTRA_EXPECTED_VERSION)) intent.getLongExtra(EXTRA_EXPECTED_VERSION, -1) else null
                if (context.appStartup.isReady) {
                    GlobalContext.get().get<ReminderCoordinator>().handleBroadcast(intent.action, id, version)
                } else {
                    val work = OneTimeWorkRequestBuilder<ReminderBroadcastWorker>()
                        .setInputData(reminderBroadcastData(intent.action, id, version)).build()
                    WorkManager.getInstance(context).enqueue(work).result.get(5, java.util.concurrent.TimeUnit.SECONDS)
                }
            } catch (_: Exception) {
                android.util.Log.w("ReminderReceiver", "Reminder broadcast could not finish")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DELIVER = "com.dailysatori.reminder.DELIVER"
        const val ACTION_CUTOFF = "com.dailysatori.reminder.CUTOFF"
        const val ACTION_DISMISS = "com.dailysatori.reminder.DISMISS"
        const val ACTION_COMPLETE = "com.dailysatori.reminder.COMPLETE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_EXPECTED_VERSION = "expected_version"
    }
}

private val REMINDER_RESTORE_ACTIONS = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    AlarmManagerActions.EXACT_ALARM_PERMISSION_CHANGED,
)

private object AlarmManagerActions {
    const val EXACT_ALARM_PERMISSION_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
}
