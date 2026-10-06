package com.dailysatori.ui.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.DiaryTagRepository
import com.dailysatori.service.asynctask.AsyncTaskFilter
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.diary.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DiaryTagUiState(
    val vocabulary: DiaryTagVocabulary = DiaryTagVocabulary(),
    val counts: Map<String, Int> = emptyMap(),
    val provenance: Map<Long, DiaryTagState> = emptyMap(),
    val editorTags: Map<Long, List<String>> = emptyMap(),
    val enabled: Boolean = true,
    val canUndo: Boolean = false,
    val activeCount: Int = 0,
    val failedCount: Int = 0,
    val missingCount: Int = 0,
    val taskStatuses: Map<Long, String> = emptyMap(),
    val busy: Boolean = false,
    val messageKey: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryTagViewModel(
    private val tags: DiaryTagRepository,
    private val generator: DiaryTagGenerator,
    private val coordinator: DiaryTagCoordinator,
    private val tasks: AsyncTaskRepository,
    private val scheduler: AsyncTaskScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(DiaryTagUiState())
    val state = _state.asStateFlow()
    private val editingId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            tags.observeCatalog().collect { catalog ->
                _state.update { it.copy(vocabulary = catalog.vocabulary, counts = catalog.counts,
                    enabled = catalog.enabled, canUndo = catalog.canUndo, missingCount = catalog.missingIds.size) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            editingId.flatMapLatest { id -> if (id == null) flowOf<DiaryTagEditorSnapshot?>(null) else tags.observeEditor(id) }
                .collect { editor ->
                    _state.update { it.copy(provenance = editor?.let { mapOf(it.diaryId to it.state) }.orEmpty(),
                        editorTags = editor?.let { mapOf(it.diaryId to parseDiaryTags(it.tags)) }.orEmpty()) }
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            tasks.observeTaskCenter(AsyncTaskFilter(types = setOf(AsyncTaskType.diary_tag_generate.name)), 500).collect { page ->
                val latest = page.tasks.sortedByDescending { it.id }.distinctBy { diaryTagTaskDiaryId(it.payloadJson) }
                _state.update { it.copy(activeCount = page.tasks.count { task -> task.status in ACTIVE_STATUSES },
                    failedCount = latest.count { task -> task.status == "failed" },
                    taskStatuses = latest.mapNotNull { task -> diaryTagTaskDiaryId(task.payloadJson)?.let { it to task.status } }.toMap()) }
            }
        }
    }

    fun setEnabled(enabled: Boolean) = perform { tags.setEnabled(enabled) }

    fun observeEditor(id: Long?) { editingId.value = id }

    suspend fun generate(content: String): DiaryTagResult = withContext(Dispatchers.IO) {
        val vocabulary = tags.vocabulary()
        generator.generate(content, vocabulary.names, vocabulary.aliases)
    }

    fun fillMissing() = perform {
        tags.currentCatalog().missingIds.forEach { id ->
            coordinator.enqueue(id, force = true, missingOnly = true)?.let(scheduler::enqueue)
        }
        _state.update { it.copy(messageKey = "diary_tags.batch_queued") }
    }

    fun retryFailed() = perform {
        _state.value.taskStatuses.filterValues { it == "failed" }.keys.forEach { id ->
            coordinator.enqueue(id, force = true)?.let(scheduler::enqueue)
        }
    }

    fun suggestMerges() = perform {
        val result = generator.suggestMerges(tags.vocabulary().names)
        tags.suggest(result)
        if (result.isEmpty()) _state.update { it.copy(messageKey = "diary_tags.no_suggestions") }
    }

    fun mergeImpact(from: String): Int = _state.value.counts[_state.value.vocabulary.canonical(from)] ?: 0

    fun merge(from: String, to: String) = perform { tags.merge(from, to) }

    fun dismissSuggestion(merge: DiaryTagMerge) = perform { tags.dismissSuggestion(merge) }

    fun approvePending(name: String) = perform { tags.approvePending(name) }

    fun dismissPending(name: String) = perform { tags.dismissPending(name) }

    fun undoMerge() = perform {
        if (!tags.undoMerge()) _state.update { it.copy(messageKey = "diary_tags.undo_conflict") }
    }

    fun clearMessage() = _state.update { it.copy(messageKey = null) }

    private fun perform(action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, messageKey = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(messageKey = "diary_tags.operation_failed") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        val ACTIVE_STATUSES = setOf("queued", "running", "retrying")
    }
}
