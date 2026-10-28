package com.dailysatori.ui.feature.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

data class ReminderAiBatchScreenState(
    val batch: ReminderAiBatch? = null,
    val preview: ReminderBatchUiState? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val progress: ReminderAiProgressUi = ReminderAiProgressUi(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderAiBatchViewModel(
    private val batchId: String,
    private val batchRepository: ReminderAiBatchRepository,
    private val asyncTaskRepository: AsyncTaskRepository,
    private val taskScheduler: AsyncTaskScheduler,
    private val reminderRepository: ReminderRepository,
    private val draftCodec: ReminderDraftCodec,
) : ViewModel() {
    private val _state = MutableStateFlow(ReminderAiBatchScreenState())
    val state: StateFlow<ReminderAiBatchScreenState> = _state
    private val revisions=mutableMapOf<Int,Long>()
    val profiles = reminderRepository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            batchRepository.observeBatch(batchId).collect { batch ->
                val current = _state.value
                batch?.drafts?.forEach { revisions[it.sourceIndex]=maxOf(revisions[it.sourceIndex] ?: 0,it.revision) }
                val refreshPreview = batch?.status in setOf(ReminderAiBatchStatus.READY_FOR_CONFIRMATION,
                    ReminderAiBatchStatus.CONFIRMED,ReminderAiBatchStatus.DISCARDED) &&
                    (current.preview == null || current.batch != batch)
                _state.value = current.copy(
                    batch = batch,
                    preview = if (refreshPreview) batch?.toPreview() else current.preview,
                    isSaving = current.isSaving && batch?.status == ReminderAiBatchStatus.READY_FOR_CONFIRMATION,
                )
            }
        }
        viewModelScope.launch {
            batchRepository.observeBatch(batchId).map { it?.taskId }.distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) flowOf(null) else asyncTaskRepository.observeTaskById(id) }
                .collect { task ->
                    _state.update { current -> current.copy(progress = reminderAiProgress(
                        status = task?.status,
                        current = task?.progress_current ?: 0,
                        total = task?.progress_total ?: 0,
                        message = task?.progress_message.orEmpty(),
                        retryAtMillis = task?.run_after_ms,
                    )) }
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
        val expected=revisions.toMap()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                selection.forEach { id ->
                    val payload=preview.items.getValue(id).draft.confirmationPayload() ?: return@forEach
                    val task=batchRepository.confirmDraft(batchId,id.sourceIndex(),expected[id.sourceIndex()] ?: 0,
                        payload.draft,payload.profileSnapshot)
                    if(task!=null) taskScheduler.enqueue(task)
                    else _state.update { it.copy(error=ReminderBatchErrorCode.SAVE_FAILED) }
                }
            } catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
            catch(failure:Exception) {
                _state.update { it.copy(error=ReminderBatchErrorCode.SAVE_FAILED) }
            } finally { _state.update { it.copy(isSaving=false) } }
        }
    }

    fun retrySchedule(id:String) {
        if(_state.value.isSaving) return
        _state.update { it.copy(isSaving=true,error=null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val task=batchRepository.retryDraftSchedule(batchId,id.sourceIndex())
                if(task!=null) taskScheduler.enqueue(task)
                else _state.update { it.copy(error=ReminderBatchErrorCode.SCHEDULING_FAILED) }
            } catch(cancelled:kotlinx.coroutines.CancellationException) { throw cancelled }
            catch(failure:Exception) { _state.update { it.copy(error=ReminderBatchErrorCode.SCHEDULING_FAILED) } }
            finally { _state.update { it.copy(isSaving=false) } }
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
        if(current.isSaving) return
        current.preview?.let { original ->
            val updated = transform(original)
            var stale=false
            updated.items.forEach { (id, item) ->
                val old = original.items[id]
                if(old!=item) {
                    val index=id.sourceIndex()
                    if(batchRepository.updateDraftUiState(batchId,index,encodeReminderBatchItem(item),item.selected,false,
                        expectedRevision=revisions[index] ?: 0)) revisions[index]=(revisions[index] ?: 0)+1 else stale=true
                }
            }
            (original.items.keys - updated.items.keys).forEach { id ->
                val old = original.items.getValue(id)
                val index=id.sourceIndex()
                if(batchRepository.updateDraftUiState(batchId,index,encodeReminderBatchItem(old),false,true,
                    expectedRevision=revisions[index] ?: 0)) revisions[index]=(revisions[index] ?: 0)+1 else stale=true
            }
            if(stale) {
                val fresh=batchRepository.getBatch(batchId)
                fresh?.drafts?.forEach { revisions[it.sourceIndex]=it.revision }
                _state.value=current.copy(batch=fresh,preview=fresh?.toPreview(),error=ReminderBatchErrorCode.SAVE_FAILED)
            } else _state.value=current.copy(preview=updated)
        }
    }

    private fun ReminderAiBatch.toPreview()=toReminderBatchPreview(draftCodec)
    private fun String.sourceIndex():Int=substringAfterLast(':').toInt()
}

internal fun ReminderAiBatch.toReminderBatchPreview(draftCodec:ReminderDraftCodec):ReminderBatchUiState = ReminderBatchUiState(
        batchId = id,
        items = drafts.filterNot { it.discarded }.associateTo(linkedMapOf()) { record ->
            val reminderId = "$id:${record.sourceIndex}"
            val decoded = draftCodec.decodeInterpretationResponse(record.draftJson, timeZone).copy(id = reminderId)
            val persisted = record.overrideJson.takeIf { it.isNotBlank() }?.let(::decodeReminderBatchItem)
                ?.let { it.copy(id=reminderId,draft=it.draft.copy(id=reminderId)) }
            reminderId to (persisted ?: ReminderBatchUiItem(
                id = reminderId,
                sourceText = record.sourceText,
                draft = ReminderDraftUiState.from(decoded, ReminderProfileSnapshot.standard()),
                selected = record.selected && !record.confirmed && !record.discarded && decoded.validationErrors.isEmpty(),
                saveStatus = if (record.confirmed) BatchSaveStatus.SAVED else if (record.confirmationState == "SCHEDULING_FAILED") BatchSaveStatus.FAILED else BatchSaveStatus.PENDING,
                createdReminderId = record.reminderId,
                saveError = if (record.confirmationState == "SCHEDULING_FAILED") ReminderBatchErrorCode.SCHEDULING_FAILED else null,
            )).copy(selected=record.selected && !record.discarded && !record.confirmed,
                saveStatus=when(record.confirmationState) {
                    "CONFIRMED"->BatchSaveStatus.SAVED
                    "SCHEDULE_PENDING"->BatchSaveStatus.SAVING
                    "SCHEDULING_FAILED"->BatchSaveStatus.FAILED
                    else->BatchSaveStatus.PENDING
                },
                createdReminderId=record.reminderId,
                saveError=if(record.confirmationState=="SCHEDULING_FAILED") ReminderBatchErrorCode.SCHEDULING_FAILED else null)
        },
    )
