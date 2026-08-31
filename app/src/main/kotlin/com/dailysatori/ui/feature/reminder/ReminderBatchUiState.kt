package com.dailysatori.ui.feature.reminder

import com.dailysatori.service.reminder.ReminderBatchInterpretation
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

enum class BatchSaveStatus { PENDING, SAVING, SAVED, FAILED }

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
    val canSave: Boolean get() = selected && saveStatus in setOf(BatchSaveStatus.PENDING, BatchSaveStatus.FAILED) && draft.confirmationPayload() != null
}

data class ReminderBatchUiState(
    val batchId: String,
    val items: Map<String, ReminderBatchUiItem>,
    val failure: String? = null,
) {
    val selectedIds: Set<String> get() = items.values.filter { it.canSave }.mapTo(linkedSetOf()) { it.id }
    val selectedCount: Int get() = selectedIds.size

    fun toggleItem(id: String): ReminderBatchUiState = updateItem(id) { item ->
        if (item.saveStatus == BatchSaveStatus.SAVED || item.parseError != null) item else item.copy(selected = !item.selected)
    }

    fun removeItem(id: String): ReminderBatchUiState = copy(items = items - id)

    fun updateItem(id: String, transform: (ReminderBatchUiItem) -> ReminderBatchUiItem): ReminderBatchUiState {
        val item = items[id] ?: return this
        return copy(items = items + (id to transform(item)))
    }

    fun markSaving(ids: Set<String>): ReminderBatchUiState = copy(items = items.mapValues { (id, item) ->
        if (id in ids && item.canSave) item.copy(saveStatus = BatchSaveStatus.SAVING, saveError = null) else item
    })

    fun claimSelectedItems(): ReminderBatchSaveClaim {
        val claimed = selectedIds.associateWith { items.getValue(it) }
        return ReminderBatchSaveClaim(markSaving(claimed.keys), claimed)
    }

    fun markSaved(id: String, createdReminderId: String): ReminderBatchUiState = updateItem(id) {
        it.copy(selected = false, saveStatus = BatchSaveStatus.SAVED, createdReminderId = createdReminderId, saveError = null)
    }

    fun markFailed(id: String, message: String): ReminderBatchUiState = updateItem(id) {
        it.copy(selected = true, saveStatus = BatchSaveStatus.FAILED, saveError = message)
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
                parsed.id to ReminderBatchUiItem(
                    id = parsed.id,
                    sourceText = parsed.sourceText,
                    draft = draft,
                    parseError = parseError,
                    requiresConfirmation = parsed.interpretation.requiresConfirmation,
                    selected = parseError == null && draft.confirmationPayload() != null,
                )
            },
        )
    }
}

data class ReminderBatchSaveClaim(
    val state: ReminderBatchUiState,
    val items: Map<String, ReminderBatchUiItem>,
)

data class ReminderBatchSaveOperation(
    val claim: ReminderBatchSaveClaim,
    val batchId: String,
    val generation: Long,
)

fun claimSelectedBatch(state: MutableStateFlow<ReminderUiState>): ReminderBatchSaveOperation? {
    while (true) {
        val current = state.value
        val batch = current.aiParse.batch ?: return null
        val claim = batch.claimSelectedItems()
        if (claim.items.isEmpty()) return null
        val next = current.copy(aiParse = current.aiParse.copy(batch = claim.state))
        if (state.compareAndSet(current, next)) {
            return ReminderBatchSaveOperation(claim, batch.batchId, current.aiParse.batchGeneration)
        }
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
            current = current.markFailed(id, error.message ?: "Save failed")
        }
    }
    return current
}
