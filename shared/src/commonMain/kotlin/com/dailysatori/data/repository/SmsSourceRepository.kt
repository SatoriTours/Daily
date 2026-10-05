package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.service.sms.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SmsSourceRepository(private val db: DailySatoriDatabase, private val cipher: SecretValueCipher) {
    private val q get() = db.dailySatoriQueries
    private val tasks = AsyncTaskRepository(db)

    fun accept(id: String, source: SmsSource, received: Instant, zone: TimeZone, cloudEligible: Boolean): Long? = q.transactionWithResult {
        if (get(id) == null) q.insertSmsSource(id, cipher.encrypt(Json.encodeToString(source)), received.toEpochMilliseconds(), zone.id,
            if (cloudEligible) SmsSourceStatus.QUEUED.name else SmsSourceStatus.LOCAL_ONLY.name)
        if (get(id)?.status != SmsSourceStatus.QUEUED) return@transactionWithResult null
        enqueue(id)
    }

    fun enqueue(id: String): Long = tasks.enqueue("sms_reminder_parse", kotlinx.serialization.json.buildJsonObject {
        put("sourceId", kotlinx.serialization.json.JsonPrimitive(id))
    }.toString(), "sms_reminder:$id", maxAttempts = 3)
    fun get(id: String): SmsSourceRecord? = q.selectSmsSource(id).executeAsOneOrNull()?.toRecord()
    fun forReminder(id: String): SmsSourceRecord? = q.selectSmsSourceByReminder(id).executeAsOneOrNull()?.toRecord()
    fun all(): List<SmsSourceRecord> = q.selectSmsSources().executeAsList().mapNotNull { runCatching { it.toRecord() }.getOrNull() }
    fun observe() = q.selectSmsSources().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.mapNotNull { runCatching { it.toRecord() }.getOrNull() } }

    fun pendingCreatedCount(): Int = SettingRepository(db).get(SmsReminderService.CREATED_COUNT_KEY)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    fun observeCreatedCount() = q.selectSettingByKey(SmsReminderService.CREATED_COUNT_KEY).asFlow().mapToOneOrNull(Dispatchers.IO)
        .map { it?.value_?.toIntOrNull()?.coerceAtLeast(0) ?: 0 }
    fun takeCreatedCount(): Int = transaction {
        pendingCreatedCount().also { if (it > 0) SettingRepository(db).upsert(SmsReminderService.CREATED_COUNT_KEY, "0") }
    }

    fun update(id: String, status: SmsSourceStatus, draft: SmsReminderDraft? = get(id)?.draft, reminderId: String? = get(id)?.reminderId) {
        q.updateSmsSource(status.name, draft?.let { Json.encodeToString(it) }.orEmpty(), reminderId, id)
    }

    fun claimNotification(id: String): Boolean = q.claimSmsSourceNotification(id).value == 1L
    fun resetNotification(id: String) = q.resetSmsSourceNotification(id)
    fun <T> transaction(block: () -> T): T = q.transactionWithResult { block() }
    fun blockedSenders(): Set<String> = SettingRepository(db).get(SmsReminderService.BLOCKED_KEY)?.let {
        runCatching { Json.decodeFromString<Set<String>>(cipher.decrypt(it)) }.getOrNull()
    }.orEmpty()
    fun saveBlockedSenders(senders: Set<String>) = SettingRepository(db).upsert(SmsReminderService.BLOCKED_KEY, cipher.encrypt(Json.encodeToString(senders)))

    private fun com.dailysatori.shared.db.Sms_reminder_source.toRecord(): SmsSourceRecord {
        val savedDraft = draft_json.takeIf { it.isNotBlank() }?.let { Json.decodeFromString<SmsReminderDraft>(it) }
        val reminder = reminder_id?.let { q.selectReminderById(it).executeAsOneOrNull() }
        val displayedDraft = if (savedDraft != null && reminder != null) savedDraft.copy(
            title = reminder.content, reason = "", deadlineMs = reminder.deadline_at,
            estimated = savedDraft.estimated && savedDraft.deadlineMs == reminder.deadline_at,
        ) else savedDraft
        return SmsSourceRecord(
            id, Json.decodeFromString<SmsSource>(cipher.decrypt(encrypted_source)), Instant.fromEpochMilliseconds(received_at),
            TimeZone.of(time_zone_id), SmsSourceStatus.valueOf(status),
            displayedDraft, reminder_id,
        )
    }
}
