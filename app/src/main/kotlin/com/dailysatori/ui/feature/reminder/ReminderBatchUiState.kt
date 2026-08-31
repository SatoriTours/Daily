package com.dailysatori.ui.feature.reminder

import com.dailysatori.service.reminder.ReminderBatchInterpretation
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

enum class BatchSaveStatus { PENDING, SAVING, SAVED, FAILED }

object ReminderBatchErrorCode {
    const val PARSE_FAILED = "reminder_batch_error_parse"
    const val SCHEDULING_FAILED = "reminder_batch_error_scheduling"
    const val SAVE_FAILED = "reminder_batch_error_save"
}

data class ReminderBatchUiItem(
    val id: String,
    val sourceText: String,
    val draft: ReminderDraftUiState,
    val parseError: String? = null,
    val requiresConfirmation: Boolean = false,
    val selected: Boolean = false,
    val saveStatus: BatchSaveStatus = BatchSaveStatus.PENDING,
    val createdReminderId: String? = null,
    val saveError: String? = null,
) {
    val canSave: Boolean get() = selected && !requiresConfirmation && saveStatus in setOf(BatchSaveStatus.PENDING, BatchSaveStatus.FAILED) && draft.confirmationPayload() != null
    val isSaving: Boolean get() = saveStatus == BatchSaveStatus.SAVING
}

data class ReminderBatchUiState(
    val batchId: String,
    val items: Map<String, ReminderBatchUiItem>,
    val failure: String? = null,
) {
    val selectedIds: Set<String> get() = items.values.filter { it.canSave }.mapTo(linkedSetOf()) { it.id }
    val selectedCount: Int get() = selectedIds.size
    val isComplete: Boolean get() = items.isNotEmpty() && items.values.all { it.saveStatus == BatchSaveStatus.SAVED }

    fun toggleItem(id: String): ReminderBatchUiState = updateItem(id) { item ->
        if (item.saveStatus !in setOf(BatchSaveStatus.PENDING, BatchSaveStatus.FAILED) || item.parseError != null || item.requiresConfirmation) item else item.copy(selected = !item.selected)
    }

    fun confirmItem(id: String): ReminderBatchUiState = updateItem(id) { item ->
        if (item.parseError != null || item.draft.confirmationPayload() == null) item else item.copy(requiresConfirmation = false, selected = true)
    }

    fun removeItem(id: String): ReminderBatchUiState {
        val item = items[id] ?: return this
        return if (item.isSaving) this else copy(items = items - id)
    }

    fun updateItem(id: String, transform: (ReminderBatchUiItem) -> ReminderBatchUiItem): ReminderBatchUiState {
        val item = items[id] ?: return this
        if (item.isSaving) return this
        val updated = transform(item)
        val recovered = if (item.parseError != null && updated.draft.confirmationPayload() != null) {
            updated.copy(parseError = null, requiresConfirmation = false, selected = true)
        } else updated
        return copy(items = items + (id to recovered))
    }

    fun markSaving(ids: Set<String>): ReminderBatchUiState = copy(items = items.mapValues { (id, item) ->
        if (id in ids && item.canSave) item.copy(saveStatus = BatchSaveStatus.SAVING, saveError = null) else item
    })

    fun claimSelectedItems(): ReminderBatchSaveClaim {
        val claimed = selectedIds.associateWith { items.getValue(it) }
        return ReminderBatchSaveClaim(markSaving(claimed.keys), claimed)
    }

    fun markSaved(id: String, createdReminderId: String): ReminderBatchUiState = replaceItem(id) {
        it.copy(selected = false, saveStatus = BatchSaveStatus.SAVED, createdReminderId = createdReminderId, saveError = null)
    }

    fun markFailed(id: String, message: String): ReminderBatchUiState = replaceItem(id) {
        it.copy(selected = true, saveStatus = BatchSaveStatus.FAILED, saveError = message)
    }

    fun markSchedulingFailed(id: String, createdReminderId: String, message: String): ReminderBatchUiState = replaceItem(id) {
        it.copy(selected = true, saveStatus = BatchSaveStatus.FAILED, createdReminderId = createdReminderId, saveError = message)
    }

    private fun replaceItem(id: String, transform: (ReminderBatchUiItem) -> ReminderBatchUiItem): ReminderBatchUiState {
        val item = items[id] ?: return this
        return copy(items = items + (id to transform(item)))
    }

    companion object {
        fun from(
            interpretation: ReminderBatchInterpretation,
            fallbackProfile: ReminderProfileSnapshot = ReminderProfileSnapshot.standard(),
            fallbackProfileId: String = "builtin-standard",
        ): ReminderBatchUiState = ReminderBatchUiState(
            batchId = interpretation.batchId,
            failure = interpretation.failure,
            items = interpretation.items.associateTo(linkedMapOf()) { parsed ->
                val parseError = parsed.interpretation.failure
                val draft = ReminderDraftUiState.from(parsed.interpretation.draft, fallbackProfile, fallbackProfileId)
                    .copy(id = parsed.id)
                parsed.id to ReminderBatchUiItem(
                    id = parsed.id,
                    sourceText = parsed.sourceText,
                    draft = draft,
                    parseError = parseError,
                    requiresConfirmation = parsed.interpretation.requiresConfirmation,
                    selected = parseError == null && !parsed.interpretation.requiresConfirmation && draft.confirmationPayload() != null,
                )
            },
        )
    }
}

data class ReminderBatchSaveClaim(
    val state: ReminderBatchUiState,
    val items: Map<String, ReminderBatchUiItem>,
)

data class ReminderBatchOperationKey(
    val batchId: String,
    val generation: Long,
)

data class ReminderBatchSaveOperation(
    val claim: ReminderBatchSaveClaim,
    val key: ReminderBatchOperationKey,
)

class ReminderBatchSaveGate {
    private val lock = Any()
    private var active: ReminderBatchOperationKey? = null

    inner class LockedScope internal constructor() {
        fun activate(key: ReminderBatchOperationKey) {
            active = key
        }

        fun invalidate() {
            active = null
        }

        fun isCurrent(key: ReminderBatchOperationKey): Boolean = active == key

        fun <T> createIfCurrent(key: ReminderBatchOperationKey, create: () -> T): T? =
            if (isCurrent(key)) create() else null
    }

    fun <T> serialized(block: LockedScope.() -> T): T = synchronized(lock) { LockedScope().block() }

    fun <T> createIfCurrent(key: ReminderBatchOperationKey, create: () -> T): T? = synchronized(lock) {
        LockedScope().createIfCurrent(key, create)
    }
}

data class ReminderBatchInterpretationRequest(
    val prompt: String,
    val token: Long,
    val generation: Long,
)

class ReminderBatchStateTransitions(
    private val state: MutableStateFlow<ReminderUiState>,
    private val gate: ReminderBatchSaveGate = ReminderBatchSaveGate(),
) {
    fun promptChanged(value: String) = gate.serialized {
        invalidate()
        updateState { current -> current.copy(aiParse = current.aiParse.onPromptChanged(value)) }
    }

    fun reset() = gate.serialized {
        invalidate()
        updateState { current -> current.copy(aiParse = current.aiParse.resetForEditor()) }
    }

    fun beginInterpretation(): ReminderBatchInterpretationRequest = gate.serialized {
        invalidate()
        val next = updateState { current -> current.copy(aiParse = current.aiParse.onExplicitSubmit()) }
        ReminderBatchInterpretationRequest(next.aiParse.prompt, next.aiParse.requestToken, next.aiParse.batchGeneration)
    }

    fun completeInterpretation(token: Long, batch: ReminderBatchUiState): Boolean = gate.serialized {
        var completed: Boolean
        while (true) {
            val current = state.value
            if (!current.aiParse.acceptsInterpretation(token)) {
                completed = false
                break
            }
            val next = current.copy(aiParse = current.aiParse.onInterpretationSucceeded(batch))
            if (state.compareAndSet(current, next)) {
                activate(ReminderBatchOperationKey(batch.batchId, next.aiParse.batchGeneration))
                completed = true
                break
            }
        }
        completed
    }

    fun failInterpretation(token: Long, message: String): Boolean = gate.serialized {
        var completed: Boolean
        while (true) {
            val current = state.value
            if (!current.aiParse.acceptsInterpretation(token)) {
                completed = false
                break
            }
            val next = current.copy(aiParse = current.aiParse.onInterpretationFinished(message))
            if (state.compareAndSet(current, next)) {
                invalidate()
                completed = true
                break
            }
        }
        completed
    }

    fun updateBatch(transform: (ReminderBatchUiState) -> ReminderBatchUiState) = gate.serialized {
        updateState { current ->
            val batch = current.aiParse.batch ?: return@updateState current
            current.copy(aiParse = current.aiParse.copy(batch = transform(batch)))
        }
    }

    fun claimSelectedBatch(): ReminderBatchSaveOperation? = gate.serialized {
        var operation: ReminderBatchSaveOperation? = null
        while (true) {
            val current = state.value
            val batch = current.aiParse.batch ?: break
            val key = ReminderBatchOperationKey(batch.batchId, current.aiParse.batchGeneration)
            if (!isCurrent(key)) break
            val claim = batch.claimSelectedItems()
            if (claim.items.isEmpty()) break
            val next = current.copy(aiParse = current.aiParse.copy(batch = claim.state))
            if (state.compareAndSet(current, next)) {
                operation = ReminderBatchSaveOperation(claim, key)
                break
            }
        }
        operation
    }

    fun <T> createIfCurrent(key: ReminderBatchOperationKey, create: () -> T): T? =
        gate.createIfCurrent(key, create)

    fun markSaved(
        key: ReminderBatchOperationKey,
        id: String,
        createdReminderId: String,
        schedulingError: String?,
    ): Boolean = publishIfCurrent(key) { batch ->
        batch.markSaved(id, createdReminderId).let { saved ->
            if (schedulingError == null) saved else saved.updateItem(id) { it.copy(saveError = schedulingError) }
        }
    }

    fun markFailed(key: ReminderBatchOperationKey, id: String, message: String): Boolean =
        publishIfCurrent(key) { it.markFailed(id, message) }

    fun markSchedulingFailed(key: ReminderBatchOperationKey, id: String, createdReminderId: String, message: String): Boolean =
        publishIfCurrent(key) { it.markSchedulingFailed(id, createdReminderId, message) }

    private fun publishIfCurrent(
        key: ReminderBatchOperationKey,
        transform: (ReminderBatchUiState) -> ReminderBatchUiState,
    ): Boolean = gate.serialized {
        var published = false
        while (isCurrent(key)) {
            val current = state.value
            val batch = current.aiParse.batch
            if (batch?.batchId != key.batchId || !current.aiParse.acceptsBatchGeneration(key.generation)) {
                break
            }
            val next = current.copy(aiParse = current.aiParse.copy(batch = transform(batch)))
            if (state.compareAndSet(current, next)) {
                published = true
                break
            }
        }
        published
    }

    private fun updateState(transform: (ReminderUiState) -> ReminderUiState): ReminderUiState {
        while (true) {
            val current = state.value
            val next = transform(current)
            if (next === current || state.compareAndSet(current, next)) return next
        }
    }
}

class ReminderBatchSaveLaunchController {
    private val lock = Any()
    private var activeJob: Job? = null

    fun <T> launchIfIdle(
        scope: CoroutineScope,
        context: CoroutineContext,
        claim: () -> T?,
        save: suspend (T) -> Unit,
    ): Job? = synchronized(lock) {
        if (activeJob?.isActive == true) return@synchronized null
        val operation = claim() ?: return@synchronized null
        lateinit var launched: Job
        launched = scope.launch(context, start = CoroutineStart.LAZY) {
            try {
                save(operation)
            } finally {
                synchronized(lock) {
                    if (activeJob === launched) activeJob = null
                }
            }
        }
        activeJob = launched
        launched.start()
        launched
    }

    fun cancelActive() = synchronized(lock) {
        activeJob?.cancel()
        activeJob = null
    }
}

suspend fun saveBatch(
    initial: ReminderBatchUiState,
    save: suspend (ReminderBatchUiItem) -> String,
): ReminderBatchUiState = saveClaim(initial.claimSelectedItems(), save)

suspend fun saveClaim(
    claim: ReminderBatchSaveClaim,
    save: suspend (ReminderBatchUiItem) -> String,
): ReminderBatchUiState {
    var current = claim.state
    claim.items.forEach { (id, item) ->
        try {
            current = current.markSaved(id, save(item))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            current = current.markFailed(id, error.message ?: ReminderBatchErrorCode.SAVE_FAILED)
        }
    }
    return current
}
