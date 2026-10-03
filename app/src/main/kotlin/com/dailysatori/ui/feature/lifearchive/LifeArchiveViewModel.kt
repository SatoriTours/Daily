package com.dailysatori.ui.feature.lifearchive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.service.lifearchive.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.EmptyCoroutineContext

data class LifeArchiveUiState(
    val records: List<LifeArchiveRecord> = emptyList(),
    val categories: List<LifeArchiveCategory> = defaultLifeArchiveCategories(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val saving: Boolean = false,
    val searchVisible: Boolean = false,
    val query: String = "",
    val selectedCategoryId: String? = null,
    val draft: LifeArchiveRecord? = null,
    val original: LifeArchiveRecord? = null,
    val beforeOptimization: LifeArchiveRecord? = null,
    val pendingDrafts: List<LifeArchiveRecord> = emptyList(),
    val input: String = "",
    val instruction: String = "",
    val manualEditing: Boolean = false,
    val showLocalNotice: Boolean = false,
    val error: LifeArchiveError? = null,
    val sessionId: Long = 0,
    val importDone: Int = 0,
    val importTotal: Int = 0,
    val sourceDescriptions: Map<String, String> = emptyMap(),
) {
    val visibleRecords: List<LifeArchiveRecord> get() = records.filter { record ->
        (selectedCategoryId == null || selectedCategoryId == record.categoryId) &&
            (query.isBlank() || listOf(record.title, record.body, categories.find { it.id == record.categoryId }?.name.orEmpty(),
                record.fields.joinToString { "${it.name} ${it.value}" }).any { it.contains(query.trim(), ignoreCase = true) })
    }
}

class LifeArchiveViewModel(
    private val repository: LifeArchiveRepository,
    private val ai: LifeArchiveAi,
    private val sources: LifeArchiveReminderSource,
    private val importer: LifeArchiveImporter,
    private val testScope: CoroutineScope? = null,
) : ViewModel() {
    private val mutable = MutableStateFlow(LifeArchiveUiState())
    val state = mutable.asStateFlow()
    private var work: Job? = null

    init { reload() }

    fun reload() = request { token ->
        val records = repository.records()
        val categories = repository.categories()
        ifCurrent(token) { it.copy(records = records, categories = categories, loading = false,
            selectedCategoryId = it.selectedCategoryId?.takeIf { id -> categories.any { category -> category.id == id } }) }
    }

    fun toggleSearch() = mutable.update { it.copy(searchVisible = !it.searchVisible, query = "") }
    fun setQuery(query: String) = mutable.update { it.copy(query = query) }
    fun selectCategory(id: String?) = mutable.update { it.copy(selectedCategoryId = id) }
    fun dismissError() = mutable.update { if (it.error == LifeArchiveError.STORAGE) it else it.copy(error = null) }
    fun dismissLocalNotice() = mutable.update { it.copy(showLocalNotice = false) }
    fun toggleManualEditing() = mutable.update { it.copy(manualEditing = !it.manualEditing) }

    fun beginNew() {
        invalidate()
        mutable.update { it.copy(draft = LifeArchiveRecord(newLifeArchiveId(), "", it.selectedCategoryId ?: it.categories.first().id),
            original = null, beforeOptimization = null, input = "", instruction = "", manualEditing = false) }
    }

    fun open(record: LifeArchiveRecord) {
        invalidate()
        mutable.update { it.copy(draft = record, original = it.records.find { saved -> saved.id == record.id },
            input = "", instruction = "", beforeOptimization = null, manualEditing = false) }
    }

    fun openPending(id: String) { state.value.pendingDrafts.find { it.id == id }?.let(::open) }

    fun closeEditor() {
        invalidate()
        mutable.update { it.copy(draft = null, original = null, beforeOptimization = null, input = "", instruction = "") }
    }

    fun setInput(text: String) {
        invalidate()
        mutable.update { current -> current.copy(input = text,
            draft = current.draft?.copy(title = "", body = "", fields = emptyList()),
            pendingDrafts = current.pendingDrafts.filter { it.sourceReminderId != null }) }
    }

    fun setInstruction(text: String) { invalidate(); mutable.update { it.copy(instruction = text) } }
    fun setTitle(title: String) = changeDraft { it.copy(title = title) }
    fun setBody(body: String) = changeDraft { it.copy(body = body) }
    fun setDraftCategory(id: String) = changeDraft { it.copy(categoryId = id) }
    fun addField() = changeDraft { it.copy(fields = it.fields + LifeArchiveField("", "")) }
    fun removeField(index: Int) = changeDraft { it.copy(fields = it.fields.filterIndexed { position, _ -> position != index }) }
    fun setField(index: Int, name: String, value: String) = changeDraft { record ->
        record.copy(fields = record.fields.mapIndexed { position, field -> if (index == position) LifeArchiveField(name, value) else field })
    }

    fun organize() {
        val current = state.value
        request { token ->
            val drafts = ai.organize(current.input, current.categories, current.records.flatMap { it.fields }.map { it.name })
            ifCurrent(token) { it.copy(draft = drafts.first(), original = null, pendingDrafts = it.pendingDrafts + drafts) }
        }
    }

    fun optimize() {
        val current = state.value
        val old = current.draft ?: return
        request { token ->
            val optimized = ai.optimize(old, current.instruction, current.categories)
            ifCurrent(token) { it.copy(draft = optimized, beforeOptimization = old,
                pendingDrafts = it.pendingDrafts.map { pending -> if (pending.id == old.id) optimized else pending }) }
        }
    }

    fun discardOptimization() {
        invalidate()
        mutable.update { current ->
            val previous = current.beforeOptimization ?: return@update current
            current.copy(draft = previous, beforeOptimization = null,
                pendingDrafts = current.pendingDrafts.map { if (it.id == previous.id) previous else it })
        }
    }

    fun cancelRequest() { if (!state.value.saving) invalidate() }

    fun save(confirmLocalOnly: Boolean = false) {
        val current = state.value
        val draft = current.draft?.let { it.copy(title = it.title.trim(), fields = it.fields.filterNot { field -> field.name.isBlank() && field.value.isBlank() }) } ?: return
        if (current.busy) return
        try { validateLifeArchiveRecord(draft, current.categories) } catch (_: IllegalArgumentException) {
            mutable.update { it.copy(error = LifeArchiveError.INPUT) }; return
        }
        if (current.records.isEmpty() && !confirmLocalOnly) { mutable.update { it.copy(showLocalNotice = true) }; return }
        mutable.update { it.copy(saving = true, showLocalNotice = false) }
        request { token ->
            val sourceId = draft.sourceReminderId
            if (sourceId != null && current.pendingDrafts.any { it.id == draft.id }) {
                if (sources.get(sourceId)?.version != draft.sourceReminderVersion) throw LifeArchiveConflict()
            }
            repository.save(draft, current.original?.updatedAt)
            val records = repository.records()
            ifCurrent(token) {
                val pending = it.pendingDrafts.filterNot { candidate -> candidate.id == draft.id }
                it.copy(records = records, draft = pending.firstOrNull(), original = records.find { saved -> saved.id == pending.firstOrNull()?.id },
                    pendingDrafts = pending, input = "", instruction = "", beforeOptimization = null)
            }
        }
    }

    fun deleteRecord(id: String) = request { token ->
        repository.delete(id)
        val records = repository.records()
        ifCurrent(token) { it.copy(records = records, draft = null, original = null) }
    }

    fun removePending(id: String) = mutable.update { it.copy(pendingDrafts = it.pendingDrafts.filterNot { draft -> draft.id == id }) }

    fun addCategory(name: String) = categoryWork { repository.addCategory(name) }
    fun renameCategory(id: String, name: String) = categoryWork { repository.renameCategory(id, name) }
    fun deleteCategory(id: String, replacementId: String) = categoryWork { repository.deleteCategory(id, replacementId) }

    private fun categoryWork(action: suspend () -> Unit) = request { token ->
        action()
        val records = repository.records()
        val categories = repository.categories()
        ifCurrent(token) { it.copy(records = records, categories = categories,
            selectedCategoryId = it.selectedCategoryId?.takeIf { id -> categories.any { category -> category.id == id } }) }
    }

    fun importReminders() {
        val current = state.value
        request { token ->
            val pendingSources = current.pendingDrafts.mapNotNull { it.sourceReminderId }.toSet()
            val reminders = sources.all().filterNot { it.id in pendingSources }
            ifCurrent(token) { it.copy(sourceDescriptions = it.sourceDescriptions + reminders.associate { reminder -> reminder.id to reminder.content },
                importDone = 0, importTotal = 0) }
            val records = repository.records()
            val categories = repository.categories()
            val drafts = importer.analyze(reminders, records, categories) { batch, done, total ->
                ifCurrent(token) { it.copy(pendingDrafts = (it.pendingDrafts + batch).distinctBy { draft -> draft.id }, importDone = done, importTotal = total) }
            }
            if (drafts.isEmpty() && state.value.pendingDrafts.isEmpty()) ifCurrent(token) { it.copy(error = LifeArchiveError.IMPORT_EMPTY) }
        }
    }

    private fun changeDraft(transform: (LifeArchiveRecord) -> LifeArchiveRecord) {
        if (state.value.saving) return
        invalidate()
        mutable.update { current ->
            val draft = current.draft?.let(transform) ?: return@update current
            current.copy(draft = draft, pendingDrafts = current.pendingDrafts.map { if (it.id == draft.id) draft else it })
        }
    }

    private fun invalidate() {
        work?.cancel()
        mutable.update { it.copy(sessionId = it.sessionId + 1, busy = false, error = null) }
    }

    private fun request(block: suspend (Long) -> Unit) {
        if (state.value.busy) return
        val token = state.value.sessionId + 1
        mutable.update { it.copy(sessionId = token, busy = true, error = null) }
        work = (testScope ?: viewModelScope).launch(if (testScope == null) Dispatchers.IO else EmptyCoroutineContext) {
            try { block(token) } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ifCurrent(token) { it.copy(error = errorKind(error)) }
            } finally {
                ifCurrent(token) { it.copy(busy = false, saving = false, loading = false) }
            }
        }
    }

    private fun ifCurrent(token: Long, transform: (LifeArchiveUiState) -> LifeArchiveUiState) = mutable.update {
        if (it.sessionId == token) transform(it) else it
    }

    private fun errorKind(error: Exception): LifeArchiveError = when (error) {
        is LifeArchiveAiNotConfigured -> LifeArchiveError.AI_CONFIG
        is LifeArchiveInvalidResponse -> LifeArchiveError.AI_RESPONSE
        is LifeArchiveConflict -> LifeArchiveError.CONFLICT
        is LifeArchiveStorageFailure -> LifeArchiveError.STORAGE
        is IllegalArgumentException -> LifeArchiveError.INPUT
        else -> LifeArchiveError.AI_REQUEST
    }
}
