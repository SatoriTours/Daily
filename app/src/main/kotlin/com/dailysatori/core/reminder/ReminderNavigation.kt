package com.dailysatori.core.reminder

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ReminderOpenRequestState {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    fun open(id: String) {
        if (id.isNotBlank()) _pending.value = id
    }

    fun consume(id: String) {
        _pending.compareAndSet(id, null)
    }
}

object ReminderOpenRequest {
    val state = ReminderOpenRequestState()
}

fun handleReminderViewIntent(intent: Intent?, target: ReminderOpenRequestState = ReminderOpenRequest.state) {
    reminderIdForViewIntent(
        action = intent?.action,
        reminderId = intent?.getStringExtra(ReminderCoordinator.EXTRA_REMINDER_ID),
    )?.let(target::open)
}

fun reminderIdForViewIntent(action: String?, reminderId: String?): String? =
    reminderId?.takeIf { action == ReminderCoordinator.ACTION_VIEW_REMINDER && it.isNotBlank() }
