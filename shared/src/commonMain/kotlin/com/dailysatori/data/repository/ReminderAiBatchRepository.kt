package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.combine
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

    fun updateDraftUiState(batchId:String,sourceIndex:Int,overrideJson:String,selected:Boolean,discarded:Boolean,
        expectedRevision:Long?=null):Boolean=q.transactionWithResult {
        val row=q.selectReminderAiDraft(batchId,sourceIndex.toLong()).executeAsOneOrNull() ?: return@transactionWithResult false
        q.updateReminderAiDraftUiState(overrideJson,if(selected) 1 else 0,if(discarded) "DISCARDED" else "PENDING",
            Clock.System.now().toEpochMilliseconds(),batchId,sourceIndex.toLong(),expectedRevision ?: row.revision).value==1L
    }

    fun markDraftSchedulingFailed(batchId:String,sourceIndex:Int,reminderId:String) {
        val row=q.selectReminderAiDraft(batchId,sourceIndex.toLong()).executeAsOneOrNull() ?: return
        if(row.reminder_id==reminderId) q.markReminderAiDraftSchedulingFailed(Clock.System.now().toEpochMilliseconds(),reminderId)
    }

    fun markDraftConfirmed(batchId:String,sourceIndex:Int,reminderId:String):Boolean {
        val row=q.selectReminderAiDraft(batchId,sourceIndex.toLong()).executeAsOneOrNull() ?: return false
        if(row.reminder_id!=reminderId) return false
        val version=q.selectReminderById(reminderId).executeAsOneOrNull()?.version ?: return false
        return finishSchedule(reminderId,version,true)
    }

    /** Version, policy, identity and intent are one commit, before platform side effects. */
    fun confirmDraft(batchId:String,sourceIndex:Int,expectedRevision:Long,
        draft:com.dailysatori.service.reminder.ReminderDraft,
        profile:com.dailysatori.service.reminder.ReminderProfileSnapshot):Long?=q.transactionWithResult {
        val row=q.selectReminderAiDraft(batchId,sourceIndex.toLong()).executeAsOneOrNull() ?: return@transactionWithResult null
        val reminders=ReminderRepository(db)
        if(row.state=="DISCARDED" || row.state=="CONFIRMED") return@transactionWithResult null
        if(row.state=="PENDING" && (row.revision!=expectedRevision || row.selected==0L ||
            getBatch(batchId)?.status!=ReminderAiBatchStatus.READY_FOR_CONFIRMATION)) return@transactionWithResult null
        val id=row.reminder_id ?: "$batchId:$sourceIndex"
        val reminder=if(row.reminder_id==null) {
            check(reminders.get(id)==null) { "Reminder confirmation identity conflict" }
            reminders.createConfirmed(draft.copy(id=id),profile)
        } else checkNotNull(reminders.get(id))
        if(row.state!="SCHEDULE_PENDING") check(q.scheduleReminderAiDraft(id,Clock.System.now().toEpochMilliseconds(),
            batchId,sourceIndex.toLong(),row.revision).value==1L)
        com.dailysatori.service.reminder.ReminderScheduleOutbox(db).enqueue(id,reminder.version)
    }

    /** Retry the stored reminder definition, never the retired AI suggestion or UI override. */
    fun retryDraftSchedule(batchId:String,sourceIndex:Int):Long?=q.transactionWithResult {
        val row=q.selectReminderAiDraft(batchId,sourceIndex.toLong()).executeAsOneOrNull()
            ?: return@transactionWithResult null
        if(row.state !in setOf("SCHEDULING_FAILED","SCHEDULE_PENDING")) return@transactionWithResult null
        val id=row.reminder_id ?: return@transactionWithResult null
        val reminder=q.selectReminderById(id).executeAsOneOrNull() ?: return@transactionWithResult null
        if(row.state=="SCHEDULING_FAILED") check(q.scheduleReminderAiDraft(id,Clock.System.now().toEpochMilliseconds(),
            batchId,sourceIndex.toLong(),row.revision).value==1L)
        com.dailysatori.service.reminder.ReminderScheduleOutbox(db).enqueue(id,reminder.version)
    }

    /** Old interrupted confirmations have durable pending state but predate the outbox. */
    fun reconcileScheduleIntents():List<Long> = q.transactionWithResult {
        q.selectPendingReminderSchedules().executeAsList().mapNotNull { row ->
            val key="reminder_schedule:${row.reminder_id}:${row.version}"
            if(q.selectAsyncTaskByUniqueKey(key).executeAsOneOrNull()!=null) null
            else com.dailysatori.service.reminder.ReminderScheduleOutbox(db).enqueue(row.reminder_id,row.version)
        }
    }

    fun beginSchedule(id:String,version:Long):Boolean=q.transactionWithResult {
        if(!currentScheduleVersion(id,version)) return@transactionWithResult false
        q.beginReminderAiDraftSchedule(Clock.System.now().toEpochMilliseconds(),id)
        true
    }

    fun finishSchedule(id:String,version:Long,successful:Boolean):Boolean=q.transactionWithResult {
        if(!currentScheduleVersion(id,version)) return@transactionWithResult false
        val row=q.selectReminderAiDraftByReminder(id).executeAsOneOrNull()
        val now=Clock.System.now().toEpochMilliseconds()
        if(successful) {
            q.markReminderAiDraftConfirmed(now,now,id)
            if(row!=null && q.selectReminderAiDraftsByBatchId(row.batch_id).executeAsList().none {
                it.selected!=0L && it.state !in setOf("CONFIRMED","DISCARDED")
            }) q.markReminderAiBatchConfirmed(now,row.batch_id)
        } else q.markReminderAiDraftSchedulingFailed(now,id)
        true
    }

    private fun currentScheduleVersion(id:String,version:Long):Boolean {
        val current=q.selectReminderById(id).executeAsOneOrNull() ?: return false
        if(current.version==version) return true
        com.dailysatori.service.reminder.ReminderScheduleOutbox(db).enqueue(id,current.version)
        return false
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

    fun discardReady(batchId:String):Boolean=q.transactionWithResult {
        val now=Clock.System.now().toEpochMilliseconds()
        if(q.discardReadyReminderAiBatch(now,batchId).value!=1L) return@transactionWithResult false
        q.discardPendingReminderAiDrafts(now,batchId)
        true
    }

    fun observeBatch(batchId:String):Flow<ReminderAiBatch?> = combine(
        q.selectReminderAiBatchById(batchId).asFlow().mapToOneOrNull(Dispatchers.IO),
        q.selectReminderAiDraftsByBatchId(batchId).asFlow().mapToList(Dispatchers.IO),
    ) { _,_ -> getBatch(batchId) }

    fun getBatch(batchId:String):ReminderAiBatch?=q.transactionWithResult {
        q.selectReminderAiBatchById(batchId).executeAsOneOrNull()?.let(::toBatch)
    }

    fun markConfirmed(batchId: String, sourceIndexes: Set<Int>): Boolean = q.transactionWithResult {
        val batch = q.selectReminderAiBatchById(batchId).executeAsOneOrNull() ?: return@transactionWithResult false
        if (batch.status != ReminderAiBatchStatus.READY_FOR_CONFIRMATION.name) return@transactionWithResult false
        val rows=q.selectReminderAiDraftsByBatchId(batchId).executeAsList().filter { it.source_index.toInt() in sourceIndexes }
        if(rows.size!=sourceIndexes.size || rows.any { it.state!="CONFIRMED" }) return@transactionWithResult false
        q.markReminderAiBatchConfirmed(Clock.System.now().toEpochMilliseconds(),batchId).value==1L
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
                it.selected.asBoolean(), it.state=="DISCARDED", it.state, it.reminder_id,
                it.state=="CONFIRMED", it.confirmed_at?.let(Instant::fromEpochMilliseconds),it.revision,
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
