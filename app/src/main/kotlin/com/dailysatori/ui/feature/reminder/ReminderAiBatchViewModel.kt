package com.dailysatori.ui.feature.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.core.reminder.ReminderCoordinator
import com.dailysatori.core.task.ReminderAiParseTaskHandler
import com.dailysatori.core.task.reminderAiParseTaskPayloadJson
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.core.worker.wakeReminderAiTask
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ReminderAiBatchRepository
import com.dailysatori.data.repository.ReminderRepository
import com.dailysatori.service.reminder.ReminderAiBatch
import com.dailysatori.service.reminder.ReminderAiBatchStatus
import com.dailysatori.service.reminder.ReminderDraftCodec
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReminderAiBatchScreenState(
    val batch: ReminderAiBatch? = null,
    val preview: ReminderBatchUiState? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
)

class ReminderAiBatchViewModel(
    private val batchId: String,
    private val batchRepository: ReminderAiBatchRepository,
    private val asyncTaskRepository: AsyncTaskRepository,
    private val taskScheduler: AsyncTaskScheduler,
    private val reminderRepository: ReminderRepository,
    private val coordinator: ReminderCoordinator,
    private val draftCodec: ReminderDraftCodec,
) : ViewModel() {
    private val _state = MutableStateFlow(ReminderAiBatchScreenState())
    val state: StateFlow<ReminderAiBatchScreenState> = _state
    val profiles = reminderRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            batchRepository.observeBatch(batchId).collect { batch ->
                val current = _state.value
                val refreshPreview = batch?.status == ReminderAiBatchStatus.READY_FOR_CONFIRMATION &&
                    (current.preview == null || current.batch?.updatedAt != batch.updatedAt)
                _state.value = current.copy(
                    batch = batch,
                    preview = if (refreshPreview) batch.toPreview() else current.preview,
                    isSaving = current.isSaving && batch?.status == ReminderAiBatchStatus.READY_FOR_CONFIRMATION,
                )
            }
        }
    }

    fun toggleItem(id: String) = updatePreview { it.toggleItem(id) }
    fun removeItem(id: String) = updatePreview { it.removeItem(id) }
    fun confirmItem(id: String) = updatePreview { it.confirmItem(id) }
    fun updateItem(id: String, transform: (ReminderBatchUiItem) -> ReminderBatchUiItem) = updatePreview { it.updateItem(id, transform) }

    fun confirmSelected() {
        val preview = _state.value.preview ?: return
        val selection = preview.selectedIds
        if (selection.isEmpty() || _state.value.isSaving) return
        _state.value = _state.value.copy(isSaving = true, error = null, preview = preview.markSaving(selection))
        viewModelScope.launch(Dispatchers.IO) {
            var failed = false
            selection.forEach { id ->
                val item = preview.items.getValue(id)
                val payload = item.draft.confirmationPayload() ?: run { failed = true; return@forEach }
                try {
                    val reminder = reminderRepository.get(id)
                        ?: reminderRepository.createConfirmed(payload.draft, payload.profileSnapshot)
                    try {
                        coordinator.recompute(reminder.id)
                        batchRepository.markDraftConfirmed(batchId, id.sourceIndex(), reminder.id)
                        updatePreview { it.markSaved(id, reminder.id) }
                    } catch (_: Exception) {
                        failed = true
                        batchRepository.markDraftSchedulingFailed(batchId, id.sourceIndex(), reminder.id)
                        updatePreview {
                            it.markSchedulingFailed(
                                id,
                                reminder.id,
                                ReminderBatchErrorCode.SCHEDULING_FAILED,
                            )
                        }
                    }
                } catch (_: Exception) {
                    failed = true
                    updatePreview { it.markFailed(id, ReminderBatchErrorCode.SAVE_FAILED) }
                }
            }
            _state.value = _state.value.copy(isSaving = false)
        }
    }

    fun retryBatch(): String? {
        if (_state.value.isSaving) return null
        val submission = batchRepository.createRetrySuccessorWithTask(
            batchId = batchId,
            taskType = ReminderAiParseTaskHandler.TYPE,
            payloadForBatch = ::reminderAiParseTaskPayloadJson,
            uniqueKeyForBatch = { "reminder_ai_parse:$it" },
        )
            ?: return null
        viewModelScope.launch(Dispatchers.IO) {
            wakeReminderAiTask(submission.taskId, taskScheduler::enqueue)
        }
        return submission.batch.id
    }

    fun discardBatch() {
        if (!_state.value.isSaving) batchRepository.discardReady(batchId)
    }

    private fun updatePreview(transform: (ReminderBatchUiState) -> ReminderBatchUiState) {
        val current = _state.value
        current.preview?.let { original ->
            val updated = transform(original)
            _state.value = current.copy(preview = updated)
            updated.items.forEach { (id, item) ->
                val old = original.items[id]
                if (old != item) batchRepository.updateDraftUiState(
                    batchId, id.sourceIndex(), encodeReminderBatchItem(item), item.selected, id !in updated.items,
                )
            }
            (original.items.keys - updated.items.keys).forEach { id ->
                val old = original.items.getValue(id)
                batchRepository.updateDraftUiState(batchId, id.sourceIndex(), encodeReminderBatchItem(old), false, true)
            }
        }
    }

    private fun ReminderAiBatch.toPreview(): ReminderBatchUiState = ReminderBatchUiState(
        batchId = id,
        items = drafts.associateTo(linkedMapOf()) { record ->
            val reminderId = "$id:${record.sourceIndex}"
            val decoded = draftCodec.decodeInterpretationResponse(record.draftJson, timeZone).copy(id = reminderId)
            val persisted = record.overrideJson.takeIf { it.isNotBlank() }?.let(::decodeReminderBatchItem)
            reminderId to (persisted ?: ReminderBatchUiItem(
                id = reminderId,
                sourceText = record.sourceText,
                draft = ReminderDraftUiState.from(decoded, ReminderProfileSnapshot.standard()),
                selected = record.selected && !record.confirmed && !record.discarded && decoded.validationErrors.isEmpty(),
                saveStatus = if (record.confirmed) BatchSaveStatus.SAVED else if (record.confirmationState == "SCHEDULING_FAILED") BatchSaveStatus.FAILED else BatchSaveStatus.PENDING,
                createdReminderId = record.reminderId,
                saveError = if (record.confirmationState == "SCHEDULING_FAILED") ReminderBatchErrorCode.SCHEDULING_FAILED else null,
            )).copy(selected = record.selected && !record.discarded && !record.confirmed)
        },
    )

    private fun String.sourceIndex(): Int = substringAfterLast(':').toInt()
}
