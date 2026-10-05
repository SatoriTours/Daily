package com.dailysatori.data.repository

import com.dailysatori.bookkeeping.*
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BookkeepingRepository(private val db: DailySatoriDatabase, private val cipher: SecretValueCipher) {
    private val q get() = db.dailySatoriQueries
    private val engine = LedgerEngine()
    private val json = Json { encodeDefaults = true }

    fun snapshot(): LedgerState = rowsToState(q.selectBookkeepingEntries().executeAsList())

    fun observe(): Flow<LedgerState> = q.selectBookkeepingEntries().asFlow().mapToList(Dispatchers.IO).map(::rowsToState)

    fun ingest(source: String, eventKey: String, text: String, receivedAt: Long): LedgerState =
        update { engine.ingest(it, source, eventKey, text, receivedAt) }.state

    fun ingestChanged(source: String, eventKey: String, text: String, receivedAt: Long): Boolean =
        update { engine.ingest(it, source, eventKey, text, receivedAt) }.changed

    fun edit(id: String, amount: String, currency: String, kind: LedgerKind, merchant: String): LedgerState =
        update { engine.edit(it, id, amount, currency, kind, merchant) }.state

    fun dismiss(id: String, status: LedgerStatus): LedgerState = update { engine.dismiss(it, id, status) }.state

    fun clearText(id: String): LedgerState = update { state ->
        state.copy(entries = state.entries.map { if (it.id == id) it.copy(text = "") else it })
    }.state

    fun addManual(source: String, eventKey: String, text: String, receivedAt: Long,
        amount: String, currency: String, kind: LedgerKind, merchant: String): LedgerEntry {
        val minor = requireNotNull(LedgerMoney.parse(amount, currency))
        require(kind != LedgerKind.UNKNOWN)
        val entry = LedgerEntry("$source:$eventKey", listOf(eventKey), source, text, receivedAt,
            minor, currency, kind, merchant.trim(), status = LedgerStatus.POSTED, reason = "", userConfirmed = true)
        update { state -> state.copy(entries = state.entries.filterNot { it.id == entry.id } + entry) }
        return entry
    }

    private fun rowsToState(rows: List<com.dailysatori.shared.db.Bookkeeping_entry>): LedgerState =
        LedgerState(entries = rows.map { row ->
            val entry = json.decodeFromString<LedgerEntry>(cipher.decrypt(row.encrypted_payload))
            require(entry.id == row.id)
            entry
        })

    private data class Update(val state: LedgerState, val changed: Boolean)

    private fun update(transform: (LedgerState) -> LedgerState): Update = q.transactionWithResult {
        val before = snapshot()
        val after = transform(before)
        val old = before.entries.associateBy { it.id }
        val retained = after.entries.map { it.id }.toSet()
        before.entries.filterNot { it.id in retained }.forEach { q.deleteBookkeepingEntry(it.id) }
        val now = Clock.System.now().toEpochMilliseconds()
        after.entries.filter { it != old[it.id] }.forEach {
            q.upsertBookkeepingEntry(it.id, cipher.encrypt(json.encodeToString(it)), now, now)
        }
        Update(after, after != before)
    }
}
