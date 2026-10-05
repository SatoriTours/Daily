package com.dailysatori.service.bookkeeping

import com.dailysatori.bookkeeping.*
import com.dailysatori.data.repository.BookkeepingRepository
import com.dailysatori.data.repository.SettingRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class BookkeepingSettings(val enabled: Boolean = false, val sources: Set<String> = emptySet())

class BookkeepingService(private val repository: BookkeepingRepository, private val settings: SettingRepository) {
    private val mutex = Mutex()
    private val parser = TransactionParser()

    fun preferences(): BookkeepingSettings = BookkeepingSettings(
        settings.get(ENABLED_KEY) == "true",
        settings.get(SOURCES_KEY)?.let { Json.decodeFromString<Set<String>>(it) }.orEmpty(),
    )

    suspend fun setEnabled(enabled: Boolean) = mutex.withLock { settings.upsert(ENABLED_KEY, enabled.toString()) }

    suspend fun selectSource(source: String, selected: Boolean) = mutex.withLock {
        require(source.isNotBlank() && source.length <= 200)
        val sources = preferences().sources.let { if (selected) it + source else it - source }
        settings.upsert(SOURCES_KEY, Json.encodeToString(sources))
    }

    suspend fun accept(source: String, eventKey: String, text: String, receivedAt: Long): Boolean = mutex.withLock {
        val preferences = preferences()
        if (!preferences.enabled || source !in preferences.sources) return@withLock false
        if (parser.parse(text) == null) return@withLock false
        repository.ingestChanged(source, eventKey, text, receivedAt)
    }

    suspend fun edit(id: String, amount: String, currency: String, kind: LedgerKind, merchant: String) = mutex.withLock {
        repository.edit(id, amount, currency, kind, merchant)
    }

    suspend fun dismiss(id: String, status: LedgerStatus) = mutex.withLock { repository.dismiss(id, status) }

    companion object {
        const val ENABLED_KEY = "bookkeeping.enabled"
        const val SOURCES_KEY = "bookkeeping.sources"
    }
}
