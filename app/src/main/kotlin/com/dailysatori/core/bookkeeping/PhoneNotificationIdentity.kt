package com.dailysatori.core.bookkeeping

import com.dailysatori.data.repository.SettingRepository
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class PhoneNotificationIdentity(private val settings: SettingRepository) {
    private val storageKey = "phone_assistant.notification_sessions"
    private val stored = settings.get(storageKey)
    private val sessions = stored?.let { Json.decodeFromString<Map<String, String>>(it) }.orEmpty().toMutableMap()
    private var initialized = stored != null

    @Synchronized fun key(notificationKey: String, eventTime: Long): String {
        val slot = hash(notificationKey)
        sessions[slot]?.let { return it }
        val id = hash("$notificationKey\n$eventTime\n${UUID.randomUUID()}")
        save(sessions + (slot to id))
        return id
    }

    @Synchronized fun removed(notificationKey: String) { save(sessions - hash(notificationKey)) }

    @Synchronized fun reconcile(activeEvents: Map<String, Long>) {
        val active = activeEvents.keys.map(::hash).toSet()
        // Adopt the previous fingerprint once for notifications already visible at upgrade.
        val next = if (!initialized) activeEvents.entries.associate { hash(it.key) to hash("${it.key}\n${it.value}") }
            else sessions.filterKeys { it in active }
        save(next)
    }

    private fun save(next: Map<String, String>) {
        if (initialized && next == sessions) return
        settings.upsert(storageKey, Json.encodeToString(next))
        initialized = true
        sessions.clear(); sessions.putAll(next)
    }

    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
