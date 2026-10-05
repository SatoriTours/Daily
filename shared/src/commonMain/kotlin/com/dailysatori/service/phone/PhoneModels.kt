package com.dailysatori.service.phone

import kotlinx.serialization.Serializable
import kotlinx.datetime.TimeZone

@Serializable enum class PhoneChannel { SMS, NOTIFICATION }
@Serializable enum class PhoneResultState { QUEUED, PENDING, DONE, IGNORED, FAILED, SKIPPED }

@Serializable data class PhoneOptions(
    val enabled: Boolean = false, val todos: Boolean = true, val ledger: Boolean = true,
    val cloud: Boolean = false, val generation: Long = 0,
)

data class PhonePreferences(val sms: PhoneOptions, val notification: PhoneOptions, val sources: Set<String>) {
    fun forChannel(channel: PhoneChannel) = if (channel == PhoneChannel.SMS) sms else notification
}

@Serializable data class PhoneEvent(
    val channel: PhoneChannel, val origin: String, val key: String, val title: String,
    val body: String, val receivedAt: Long, val zone: String, val mirrorsSms: Boolean = false,
) {
    val id get() = "${channel.name.lowercase()}:$key"
    val text get() = listOf(title.trim(), body.trim()).filter(String::isNotEmpty).joinToString("\n")
    init {
        require(origin.isNotBlank() && origin.length <= 200 && key.matches(Regex("[A-Za-z0-9_-]{1,180}")))
        require(receivedAt > 0 && title.length + body.length + 1 <= 8000)
        TimeZone.of(zone)
    }
}

@Serializable data class PhoneTodo(
    val id: String, val text: String, val state: PhoneResultState = PhoneResultState.QUEUED,
    val reason: String = "", val reminderId: String = "",
) {
    init { require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && text.length <= 8000 && reason.length <= 80) }
}

@Serializable data class PhoneMessage(
    val event: PhoneEvent, val revision: Long = 1, val generation: Long,
    val todos: List<PhoneTodo> = emptyList(), val ledgerState: PhoneResultState = PhoneResultState.SKIPPED,
    val ledgerId: String = "", val ledgerReason: String = "", val textErased: Boolean = false,
) {
    val id get() = event.id
    init { require(revision > 0 && generation >= 0 && todos.map { it.id }.distinct().size == todos.size) }
}
