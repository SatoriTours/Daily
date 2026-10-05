package com.dailysatori.service.phone

import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.sms.SmsReminderService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PhonePolicies(private val settings: SettingRepository) {
    fun preferences(): PhonePreferences = PhonePreferences(options(PhoneChannel.SMS), options(PhoneChannel.NOTIFICATION),
        settings.get("bookkeeping.sources")?.let { Json.decodeFromString<Set<String>>(it) }.orEmpty())

    private fun options(channel: PhoneChannel): PhoneOptions {
        settings.get(key(channel))?.let { return Json.decodeFromString(it) }
        return when (channel) {
            PhoneChannel.SMS -> PhoneOptions(enabled = settings.get(SmsReminderService.ENABLED_KEY) == "true",
                ledger = settings.get(SmsReminderService.ENABLED_KEY) == null,
                cloud = settings.get(SmsReminderService.CLOUD_KEY) == "true")
            PhoneChannel.NOTIFICATION -> PhoneOptions(enabled = settings.get("bookkeeping.enabled") == "true",
                todos = settings.get("bookkeeping.enabled") == null)
        }
    }

    fun configure(channel: PhoneChannel, options: PhoneOptions) {
        val generation = preferences().forChannel(channel).generation
        require(generation < Long.MAX_VALUE)
        settings.upsert(key(channel), Json.encodeToString(options.copy(generation = generation + 1)))
        if (channel == PhoneChannel.SMS) {
            settings.upsert(SmsReminderService.ENABLED_KEY, options.enabled.toString())
            settings.upsert(SmsReminderService.CLOUD_KEY, options.cloud.toString())
        } else settings.upsert("bookkeeping.enabled", options.enabled.toString())
    }

    fun selectSource(source: String, selected: Boolean) {
        require(source.isNotBlank() && source.length <= 200)
        val sources = preferences().sources.let { if (selected) it + source else it - source }
        settings.upsert("bookkeeping.sources", Json.encodeToString(sources))
        // Revoking an app invalidates already queued requests, including remove/re-add.
        if (!selected) configure(PhoneChannel.NOTIFICATION, preferences().notification)
    }

    fun optionsFor(event: PhoneEvent): PhoneOptions {
        val prefs = preferences()
        val option = prefs.forChannel(event.channel)
        if (event.channel != PhoneChannel.NOTIFICATION || !event.mirrorsSms || !prefs.sms.enabled) return option
        return option.copy(todos = option.todos && !prefs.sms.todos, ledger = option.ledger && !prefs.sms.ledger)
    }

    fun accepts(event: PhoneEvent): Boolean {
        val prefs = preferences()
        val option = optionsFor(event)
        return option.enabled && (option.todos || option.ledger) &&
            (event.channel == PhoneChannel.SMS || event.origin in prefs.sources)
    }

    private fun key(channel: PhoneChannel) = "phone_assistant.${channel.name.lowercase()}"
}
