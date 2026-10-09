package com.dailysatori.ui.feature.reminder

import com.dailysatori.service.phone.PhoneMessage

/** Reminder ids produced from SMS or notification intake, mapped to the channel label key. */
object ReminderSources {
    fun labels(messages: List<PhoneMessage>): Map<String, String> = messages
        .flatMap { row ->
            row.todos.filter { it.reminderId.isNotEmpty() }
                .map { it.reminderId to "phone.${row.event.channel.name.lowercase()}" }
        }
        .toMap()
}
