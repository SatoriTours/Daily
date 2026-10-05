package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.service.phone.PhoneMessage
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class PhoneMessageRepository(private val db: DailySatoriDatabase, private val cipher: SecretValueCipher) {
    private val q get() = db.dailySatoriQueries
    private val json = Json { encodeDefaults = true }
    fun get(id: String): PhoneMessage? = q.selectPhoneMessage(id).executeAsOneOrNull()?.let { decode(it.id, it.encrypted_payload) }
    fun all(): List<PhoneMessage> = q.selectPhoneMessages().executeAsList().map { decode(it.id, it.encrypted_payload) }
    fun observe() = q.selectPhoneMessages().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.map { decode(it.id, it.encrypted_payload) } }
    fun history(limit: Int = 50, offset: Int = 0): List<PhoneMessage> {
        require(limit in 1..1000 && offset >= 0)
        return q.selectPhoneHistory(limit.toLong(), offset.toLong()).executeAsList().map { decode(it.id, it.encrypted_payload) }
    }
    fun observeHistory(limit: Int) = q.selectPhoneHistory(limit.toLong(), 0).asFlow().mapToList(Dispatchers.IO)
        .map { rows -> rows.map { decode(it.id, it.encrypted_payload) } }
    fun save(message: PhoneMessage) = q.upsertPhoneMessage(message.id, cipher.encrypt(json.encodeToString(message)), message.event.receivedAt)
    fun <T> transaction(block: () -> T): T = q.transactionWithResult { block() }

    fun enqueue(message: PhoneMessage): Long = AsyncTaskRepository(db).enqueue("phone_assistant_process", buildJsonObject {
        put("messageId", message.id)
    }.toString(), "phone:${message.id}:${message.revision}:${message.generation}", maxAttempts = 3)

    private fun decode(id: String, payload: String): PhoneMessage {
        val record = json.decodeFromString<PhoneMessage>(cipher.decrypt(payload))
        require(record.id == id && record.revision > 0 && record.generation >= 0)
        return record
    }
}
