package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.dailysatori.service.reminder.ReminderAiBatch
import com.dailysatori.service.reminder.ReminderAiBatchDraft
import com.dailysatori.service.reminder.ReminderAiBatchStatus
import com.dailysatori.service.reminder.ReminderAiDraftRecord
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Reminder_ai_batch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.random.Random

data class ReminderAiBatchSubmission(val batch: ReminderAiBatch, val taskId: Long)

class ReminderAiBatchRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun enqueueOrReuse(input: String, timeZone: TimeZone, localDate: LocalDate): ReminderAiBatch = q.transactionWithResult {
        val normalized = input.normalize()
        require(normalized.isNotBlank()) { "Reminder text is empty" }
        q.selectActiveReminderAiBatch(normalized, timeZone.id, localDate.toString()).executeAsOneOrNull()
            ?.let(::toBatch) ?: createBatch(input, normalized, timeZone, localDate)
    }

    /** Persists the batch and its runnable task in the same database transaction. */
    fun submitOrReuseWithTask(
        input: String,
        timeZone: TimeZone,
        localDate: LocalDate,
        taskType: String,
        payloadForBatch: (String) -> String,
        uniqueKeyForBatch: (String) -> String,
    ): ReminderAiBatchSubmission = q.transactionWithResult {
        val normalized = input.normalize()
        require(normalized.isNotBlank()) { "Reminder text is empty" }
        val batch = q.selectActiveReminderAiBatch(normalized, timeZone.id, localDate.toString()).executeAsOneOrNull()
            ?.let(::toBatch) ?: createBatch(input, normalized, timeZone, localDate)
        val existingTask = batch.taskId?.let(q::selectAsyncTaskById)?.executeAsOneOrNull()
            ?: q.selectAsyncTaskByUniqueKey(uniqueKeyForBatch(batch.id)).executeAsOneOrNull()
        val taskId = if (existingTask != null && existingTask.status in setOf("queued", "running", "retrying")) {
            existingTask.id
        } else {
            insertAsyncTask(taskType, payloadForBatch(batch.id), uniqueKeyForBatch(batch.id), batch.maxAttempts)
        }
        q.attachReminderAiBatchTask(taskId, Clock.System.now().toEpochMilliseconds(), batch.id)
        ReminderAiBatchSubmission(requireNotNull(getBatch(batch.id)), taskId)
    }

    /** Repairs rows created by older builds before batch/task creation became atomic. */
    fun reconcileOrphanedActiveBatches(
        taskType: String,
        payloadForBatch: (String) -> String,
        uniqueKeyForBatch: (String) -> String,
    ): List<Long> = q.transactionWithResult {
        q.selectOrphanReminderAiBatches().executeAsList().map { row ->
            val taskId = insertAsyncTask(taskType, payloadForBatch(row.id), uniqueKeyForBatch(row.id), row.max_attempts)
            q.attachReminderAiBatchTask(taskId, Clock.System.now().toEpochMilliseconds(), row.id)
            taskId
        }
    }

    fun updateDraftUiState(batchId: String, sourceIndex: Int, overrideJson: String, selected: Boolean, discarded: Boolean) {
        q.updateReminderAiDraftUiState(overrideJson, if (selected) 1 else 0, if (discarded) 1 else 0, Clock.System.now().toEpochMilliseconds(), batchId, sourceIndex.toLong())
    }

    fun markDraftSchedulingFailed(batchId: String, sourceIndex: Int, reminderId: String) {
        q.markReminderAiDraftSchedulingFailed(reminderId, Clock.System.now().toEpochMilliseconds(), batchId, sourceIndex.toLong())
    }

    fun markDraftConfirmed(batchId: String, sourceIndex: Int, reminderId: String): Boolean = q.transactionWithResult {
        val now = Clock.System.now().toEpochMilliseconds()
        q.markReminderAiDraftConfirmed(reminderId, now, now, batchId, sourceIndex.toLong())
        val remaining = q.selectReminderAiDraftsByBatchId(batchId).executeAsList().any { !it.confirmed.asBoolean() && !it.discarded.asBoolean() && it.selected.asBoolean() }
        if (!remaining) q.markReminderAiBatchConfirmed(now, batchId)
        true
    }

    fun markRunning(batchId: String, taskId: Long, attemptCount: Long): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        return q.markReminderAiBatchRunning(taskId, attemptCount, now, now, batchId).value == 1L
    }

    fun markReady(batchId: String, drafts: List<ReminderAiBatchDraft>): Boolean = q.transactionWithResult {
        require(drafts.map { it.sourceIndex }.distinct().size == drafts.size) { "Draft source indexes must be unique" }
        val now = Clock.System.now().toEpochMilliseconds()
        if (q.markReminderAiBatchReady(now, batchId).value != 1L) return@transactionWithResult false
        q.deleteReminderAiDraftsByBatchId(batchId)
        drafts.forEach { q.insertReminderAiDraft(batchId, it.sourceIndex.toLong(), it.sourceText, it.draftJson, now, now) }
        true
    }

    fun markFailed(batchId: String, errorSummary: String): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        return q.markReminderAiBatchFailed(errorSummary.take(MAX_ERROR_LENGTH), now, batchId).value == 1L
    }

    /** Reopens only a terminal parse failure and clears its consumed notification marker. */
    fun restartFailed(batchId: String): ReminderAiBatch? {
        val now = Clock.System.now().toEpochMilliseconds()
        return if (q.restartFailedReminderAiBatch(now, batchId).value == 1L) getBatch(batchId) else null
    }

    /** Keeps diagnostic terminal history immutable; the retry is a new active request. */
    fun createRetrySuccessor(batchId: String): ReminderAiBatch? = q.transactionWithResult {
        val parent = q.selectReminderAiBatchById(batchId).executeAsOneOrNull() ?: return@transactionWithResult null
        if (parent.status != ReminderAiBatchStatus.PARSE_FAILED.name) return@transactionWithResult null
        q.selectActiveReminderAiBatch(parent.normalized_key, parent.time_zone_id, parent.local_date).executeAsOneOrNull()
            ?.let(::toBatch) ?: createBatch(parent.original_input, parent.normalized_key, TimeZone.of(parent.time_zone_id), LocalDate.parse(parent.local_date), parent.id)
    }

    fun createRetrySuccessorWithTask(
        batchId: String,
        taskType: String,
        payloadForBatch: (String) -> String,
        uniqueKeyForBatch: (String) -> String,
    ): ReminderAiBatchSubmission? = q.transactionWithResult {
        val parent = q.selectReminderAiBatchById(batchId).executeAsOneOrNull() ?: return@transactionWithResult null
        if (parent.status != ReminderAiBatchStatus.PARSE_FAILED.name) return@transactionWithResult null
        val successor = q.selectActiveReminderAiBatch(parent.normalized_key, parent.time_zone_id, parent.local_date).executeAsOneOrNull()
            ?.let(::toBatch) ?: createBatch(parent.original_input, parent.normalized_key, TimeZone.of(parent.time_zone_id), LocalDate.parse(parent.local_date), parent.id)
        val taskId = successor.taskId ?: insertAsyncTask(taskType, payloadForBatch(successor.id), uniqueKeyForBatch(successor.id), successor.maxAttempts)
        q.attachReminderAiBatchTask(taskId, Clock.System.now().toEpochMilliseconds(), successor.id)
        ReminderAiBatchSubmission(requireNotNull(getBatch(successor.id)), taskId)
    }

    fun discardReady(batchId: String): Boolean =
        q.discardReadyReminderAiBatch(Clock.System.now().toEpochMilliseconds(), batchId).value == 1L

    fun observeBatch(batchId: String): Flow<ReminderAiBatch?> = q.selectReminderAiBatchById(batchId)
        .asFlow().mapToOneOrNull(Dispatchers.IO).map { it?.let(::toBatch) }

    fun getBatch(batchId: String): ReminderAiBatch? = q.selectReminderAiBatchById(batchId).executeAsOneOrNull()?.let(::toBatch)

    fun markConfirmed(batchId: String, sourceIndexes: Set<Int>): Boolean = q.transactionWithResult {
        val batch = q.selectReminderAiBatchById(batchId).executeAsOneOrNull() ?: return@transactionWithResult false
        if (batch.status != ReminderAiBatchStatus.READY_FOR_CONFIRMATION.name) return@transactionWithResult false
        val now = Clock.System.now().toEpochMilliseconds()
        sourceIndexes.forEach { q.markReminderAiDraftConfirmed(null, now, now, batchId, it.toLong()) }
        q.markReminderAiBatchConfirmed(now, batchId)
        true
    }

    fun claimTerminalNotification(batchId: String): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        return q.claimReminderAiBatchTerminalNotification(now, now, batchId).value == 1L
    }

    private fun createBatch(input: String, normalized: String, timeZone: TimeZone, localDate: LocalDate, parentBatchId: String? = null): ReminderAiBatch {
        val now = Clock.System.now().toEpochMilliseconds()
        val id = "reminder_ai_${now}_${Random.nextLong().toString(16)}"
        q.insertReminderAiBatch(id, parentBatchId, input, normalized, timeZone.id, localDate.toString(), ReminderAiBatchStatus.PARSING.name, null, 0, 3, null, "", null, now, now)
        return requireNotNull(q.selectReminderAiBatchById(id).executeAsOneOrNull()).let(::toBatch)
    }

    private fun toBatch(row: Reminder_ai_batch): ReminderAiBatch = ReminderAiBatch(
        id = row.id, parentBatchId = row.parent_batch_id, originalInput = row.original_input, normalizedKey = row.normalized_key,
        timeZone = TimeZone.of(row.time_zone_id), localDate = LocalDate.parse(row.local_date),
        status = ReminderAiBatchStatus.valueOf(row.status), taskId = row.task_id,
        attemptCount = row.attempt_count, maxAttempts = row.max_attempts,
        lastAttemptAt = row.last_attempt_at?.let(Instant::fromEpochMilliseconds), errorSummary = row.error_summary,
        terminalNotificationAt = row.terminal_notification_at?.let(Instant::fromEpochMilliseconds),
        createdAt = Instant.fromEpochMilliseconds(row.created_at), updatedAt = Instant.fromEpochMilliseconds(row.updated_at),
        drafts = q.selectReminderAiDraftsByBatchId(row.id).executeAsList().map {
            ReminderAiDraftRecord(
                it.source_index.toInt(), it.source_text, it.draft_json, it.override_json,
                it.selected.asBoolean(), it.discarded.asBoolean(), it.confirmation_state, it.reminder_id,
                it.confirmed.asBoolean(), it.confirmed_at?.let(Instant::fromEpochMilliseconds),
            )
        },
    )

    private fun insertAsyncTask(type: String, payload: String, uniqueKey: String, maxAttempts: Long): Long {
        val existing = q.selectAsyncTaskByUniqueKey(uniqueKey).executeAsOneOrNull()
        if (existing != null && existing.status in setOf("queued", "running", "retrying")) return existing.id
        val now = Clock.System.now().toEpochMilliseconds()
        q.insertAsyncTask(type, "queued", payload, "", "", 0, 0, "", 0, maxAttempts, 0, uniqueKey, null, null, null, null, null, null, "", "", now, now)
        return q.selectLastInsertedAsyncTaskId().executeAsOne()
    }

    private fun Long.asBoolean() = this != 0L

    private fun String.normalize(): String = trim().replace(Regex("\\s+"), " ")

    private companion object { const val MAX_ERROR_LENGTH = 500 }
}
