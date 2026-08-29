package com.dailysatori.core.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

internal fun isReminderRestoreAction(action: String?): Boolean = action in REMINDER_RESTORE_ACTIONS

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val coordinator = GlobalContext.get().get<ReminderCoordinator>()
                when (intent.action) {
                    ACTION_DELIVER -> withIdentity(intent) { id, version -> coordinator.deliver(id, version) }
                    ACTION_DISMISS -> withIdentity(intent) { id, version -> coordinator.dismiss(id, version) }
                    ACTION_COMPLETE -> withIdentity(intent) { id, version -> coordinator.complete(id, version) }
                    in REMINDER_RESTORE_ACTIONS -> coordinator.recomputeAll()
                    else -> Unit
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun withIdentity(intent: Intent, block: (String, Long) -> Unit) {
        val id = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        if (!intent.hasExtra(EXTRA_EXPECTED_VERSION)) return
        block(id, intent.getLongExtra(EXTRA_EXPECTED_VERSION, -1))
    }

    companion object {
        const val ACTION_DELIVER = "com.dailysatori.reminder.DELIVER"
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
