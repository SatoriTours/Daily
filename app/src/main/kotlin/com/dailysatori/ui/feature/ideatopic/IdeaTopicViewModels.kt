package com.dailysatori.ui.feature.ideatopic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.service.ideatopic.IdeaAiMaxUserPromptCharacters
import com.dailysatori.service.ideatopic.IdeaCaptureInput
import com.dailysatori.service.ideatopic.IdeaDraftContent
import com.dailysatori.service.ideatopic.IdeaDraftState
import com.dailysatori.service.ideatopic.IdeaSessionSummaryStatus
import com.dailysatori.service.ideatopic.IdeaSourceKey
import com.dailysatori.service.ideatopic.IdeaTopicAiWorkflow
import com.dailysatori.service.ideatopic.IdeaTopicContent
import com.dailysatori.service.ideatopic.IdeaTopicDetail
import com.dailysatori.service.ideatopic.IdeaTopicDraft
import com.dailysatori.service.ideatopic.IdeaTopicError
import com.dailysatori.service.ideatopic.IdeaTopicException
import com.dailysatori.service.ideatopic.IdeaTopicMessage
import com.dailysatori.service.ideatopic.IdeaTopicService
import com.dailysatori.service.ideatopic.IdeaTopicStatus
import com.dailysatori.service.ideatopic.IdeaTopicSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun Throwable.toIdeaTopicError(): IdeaTopicError =
    (this as? IdeaTopicException)?.code ?: IdeaTopicError.StorageFailure

// ---------- List ----------

data class IdeaTopicListState(
    val topics: List<IdeaTopicSummary> = emptyList(),
    val query: String = "",
    val statusFilter: IdeaTopicStatus? = null,
    val isLoading: Boolean = true,
    val error: IdeaTopicError? = null,
)

class IdeaTopicListViewModel(private val service: IdeaTopicService) : ViewModel() {
    private val topics = MutableStateFlow<List<IdeaTopicSummary>>(emptyList())
    private val query = MutableStateFlow("")
    private val statusFilter = MutableStateFlow<IdeaTopicStatus?>(null)
    private val isLoading = MutableStateFlow(true)
    private val error = MutableStateFlow<IdeaTopicError?>(null)

    val state: StateFlow<IdeaTopicListState> = combine(
        topics,
        query,
        statusFilter,
        isLoading,
        error,
    ) { all, queryValue, filter, loading, failure ->
        IdeaTopicListState(
            topics = all.filter { topic ->
                (filter == null || topic.status == filter) &&
                    (queryValue.isBlank() || topic.content.title.contains(queryValue.trim(), ignoreCase = true))
            },
            query = queryValue,
            statusFilter = filter,
            isLoading = loading,
            error = failure,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, IdeaTopicListState())

    init {
        viewModelScope.launch {
            service.observeSummaries()
                .catch { failure ->
                    error.value = failure.toIdeaTopicError()
                    isLoading.value = false
                }
                .collect { summaries ->
                    topics.value = summaries
                    error.value = null
                    isLoading.value = false
                }
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setStatusFilter(status: IdeaTopicStatus?) {
        statusFilter.value = status
    }
}

// ---------- Detail ----------

data class IdeaDraftChange(val field: String, val before: String, val after: String)

data class IdeaDraftPreview(
    val draftId: String,
    val state: String,
    val changes: List<IdeaDraftChange>,
    val proposal: IdeaDraftContent? = null,
)

data class IdeaTopicDetailState(
    val requestedTopicId: String,
    val topicId: String? = null,
    val detail: IdeaTopicDetail? = null,
    val draftPreviews: List<IdeaDraftPreview> = emptyList(),
    val busy: Boolean = false,
    val error: IdeaTopicError? = null,
    val mergedInto: String? = null,
    val deleted: Boolean = false,
    val mergeCandidates: List<IdeaTopicSummary> = emptyList(),
) {
    val canMergeOrDelete: Boolean get() = detail != null && !busy && !deleted
}

class IdeaTopicDetailViewModel(
    private val requestedTopicId: String,
    private val service: IdeaTopicService,
    private val workflow: IdeaTopicAiWorkflow,
) : ViewModel() {
    private val _state = MutableStateFlow(IdeaTopicDetailState(requestedTopicId = requestedTopicId))
    val state: StateFlow<IdeaTopicDetailState> = _state.asStateFlow()

    private var observedTopicId: String? = null
    private var currentDrafts: List<IdeaTopicDraft> = emptyList()

    init {
        val mainTopicId = service.resolveTopicId(requestedTopicId)
        if (mainTopicId == null) {
            _state.update { it.copy(deleted = true, error = IdeaTopicError.NotFound) }
        } else {
            observedTopicId = mainTopicId
            _state.update {
                it.copy(
                    topicId = mainTopicId,
                    mergedInto = mainTopicId.takeIf { main -> main != requestedTopicId },
                    busy = service.isRequestActive(mainTopicId),
                )
            }
            viewModelScope.launch {
                service.observeDetail(mainTopicId)
                    .catch { failure -> _state.update { it.copy(error = failure.toIdeaTopicError()) } }
                    .collect { detail ->
                        _state.update {
                            it.copy(
                                detail = detail,
                                deleted = detail == null,
                                error = if (detail == null) IdeaTopicError.NotFound else it.error,
                                draftPreviews = draftPreviews(currentDrafts, detail?.topic?.content ?: IdeaTopicContent()),
                            )
                        }
                    }
            }
            viewModelScope.launch {
                service.observeDrafts(mainTopicId)
                    .catch { failure -> _state.update { it.copy(error = failure.toIdeaTopicError()) } }
                    .collect { drafts ->
                        currentDrafts = drafts
                        _state.update {
                            it.copy(draftPreviews = draftPreviews(drafts, it.detail?.topic?.content ?: IdeaTopicContent()))
                        }
                    }
            }
            viewModelScope.launch {
                service.observeSummaries()
                    .catch { failure -> _state.update { it.copy(error = failure.toIdeaTopicError()) } }
                    .collect { summaries ->
                        _state.update { state ->
                            state.copy(mergeCandidates = summaries.filter { it.id != mainTopicId })
                        }
                    }
            }
        }
    }

    fun createSession(title: String = "", onCreated: (String) -> Unit = {}) = runAction {
        val sessionId = service.createSession(requireTopicId(), title)
        onCreated(sessionId)
    }

    fun updateContent(content: IdeaTopicContent) = runAction { service.updateContent(requireTopicId(), content) }

    fun setStatus(status: IdeaTopicStatus) = runAction { service.setStatus(requireTopicId(), status) }

    fun appendProgress(text: String) = runAction { service.appendProgress(requireTopicId(), text) }

    fun merge(intoTopicId: String) = runAction {
        val finalTarget = service.merge(requireTopicId(), intoTopicId)
        _state.update { it.copy(mergedInto = finalTarget) }
    }

    fun delete() = runAction {
        service.delete(requireTopicId())
        _state.update { it.copy(deleted = true, detail = null, error = IdeaTopicError.NotFound) }
    }

    fun propose() = runAction(busy = true) { workflow.propose(requireTopicId()) }

    fun applyDraft(draftId: String, content: IdeaTopicContent) = runAction { service.applyDraft(draftId, content) }

    fun discardDraft(draftId: String) = runAction { service.discardDraft(draftId) }

    fun cancel() {
        val topicId = _state.value.topicId ?: return
        viewModelScope.launch {
            workflow.cancel(topicId)
            _state.update { it.copy(busy = service.isRequestActive(topicId)) }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun requireTopicId(): String =
        _state.value.topicId ?: throw IdeaTopicException(IdeaTopicError.NotFound)

    private fun runAction(busy: Boolean = false, block: suspend () -> Unit) {
        val topicId = _state.value.topicId
        viewModelScope.launch {
            if (busy) _state.update { it.copy(busy = true) }
            try {
                block()
                _state.update { it.copy(error = null) }
            } catch (failure: Exception) {
                _state.update { it.copy(error = failure.toIdeaTopicError()) }
            } finally {
                _state.update { it.copy(busy = topicId?.let { service.isRequestActive(it) } ?: false) }
            }
        }
    }

    private fun draftPreviews(drafts: List<IdeaTopicDraft>, before: IdeaTopicContent): List<IdeaDraftPreview> =
        drafts.map { draft ->
            IdeaDraftPreview(
                draftId = draft.id,
                state = draft.state,
                changes = if (draft.state == IdeaDraftState.Pending) draftChanges(before, draft.proposal.content) else emptyList(),
                proposal = draft.proposal,
            )
        }

    private fun draftChanges(before: IdeaTopicContent, after: IdeaTopicContent): List<IdeaDraftChange> = buildList {
        if (before.title != after.title) add(IdeaDraftChange("title", before.title, after.title))
        if (before.description != after.description) add(IdeaDraftChange("description", before.description, after.description))
        if (before.provenanceSummary != after.provenanceSummary) {
            add(IdeaDraftChange("provenanceSummary", before.provenanceSummary, after.provenanceSummary))
        }
        if (before.conclusions != after.conclusions) add(IdeaDraftChange("conclusions", before.conclusions, after.conclusions))
        if (before.nextAction != after.nextAction) add(IdeaDraftChange("nextAction", before.nextAction, after.nextAction))
    }
}

// ---------- Session ----------

data class IdeaTopicSessionState(
    val sessionId: String,
    val topicId: String? = null,
    val title: String = "",
    val messages: List<IdeaTopicMessage> = emptyList(),
    val summary: String = "",
    val summaryStatus: String = IdeaSessionSummaryStatus.None,
    val busy: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val error: IdeaTopicError? = null,
)

class IdeaTopicSessionViewModel(
    private val sessionId: String,
    private val service: IdeaTopicService,
    private val workflow: IdeaTopicAiWorkflow,
) : ViewModel() {
    private val _state = MutableStateFlow(IdeaTopicSessionState(sessionId = sessionId))
    val state: StateFlow<IdeaTopicSessionState> = _state.asStateFlow()

    private val olderMessages = MutableStateFlow<List<IdeaTopicMessage>>(emptyList())

    init {
        val session = runCatching { service.sessionOrThrow(sessionId) }.getOrNull()
        val mainTopicId = session?.let { service.resolveTopicId(it.topicId) }
        _state.update {
            it.copy(
                topicId = mainTopicId,
                title = session?.title.orEmpty(),
                summary = session?.summary.orEmpty(),
                summaryStatus = session?.summaryStatus ?: IdeaSessionSummaryStatus.None,
                busy = mainTopicId?.let(service::isRequestActive) ?: false,
                error = if (session == null) IdeaTopicError.NotFound else null,
            )
        }
        if (mainTopicId != null) {
            // Loading a screen never triggers an AI request.
            viewModelScope.launch {
                combine(service.observeMessages(sessionId), olderMessages) { latest, older ->
                    (older + latest).distinctBy { it.id }
                        .sortedWith(compareBy({ it.createdAt }, { it.id }))
                }
                    .catch { failure -> _state.update { it.copy(error = failure.toIdeaTopicError()) } }
                    .collect { messages -> _state.update { it.copy(messages = messages) } }
            }
        }
    }

    fun send(text: String) {
        val topicId = _state.value.topicId ?: return
        if (text.length > IdeaAiMaxUserPromptCharacters) {
            _state.update { it.copy(error = IdeaTopicError.InputTooLong) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                workflow.send(sessionId, text)
                _state.update { it.copy(error = null) }
            } catch (failure: Exception) {
                _state.update { it.copy(error = failure.toIdeaTopicError()) }
            } finally {
                refreshSession(topicId)
            }
        }
    }

    fun summarize() {
        val topicId = _state.value.topicId ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                workflow.summarize(sessionId)
                _state.update { it.copy(error = null) }
            } catch (failure: Exception) {
                _state.update { it.copy(error = failure.toIdeaTopicError()) }
            } finally {
                refreshSession(topicId)
            }
        }
    }

    fun cancel() {
        val topicId = _state.value.topicId ?: return
        viewModelScope.launch {
            workflow.cancel(topicId)
            _state.update { it.copy(busy = service.isRequestActive(topicId)) }
        }
    }

    fun loadOlder() {
        val oldest = _state.value.messages.firstOrNull() ?: return
        if (_state.value.isLoadingOlder) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingOlder = true) }
            try {
                val page = service.getMessagesBeforeSync(sessionId, oldest.createdAt, oldest.id)
                olderMessages.update { previous ->
                    (previous + page).distinctBy { it.id }
                }
            } catch (failure: Exception) {
                _state.update { it.copy(error = failure.toIdeaTopicError()) }
            } finally {
                _state.update { it.copy(isLoadingOlder = false) }
            }
        }
    }

    fun retry(failedMessageId: String? = null) {
        val list = _state.value.messages
        val target = if (failedMessageId != null) {
            val msg = list.firstOrNull { it.id == failedMessageId }
            if (msg?.role == com.dailysatori.service.ideatopic.IdeaMessageRoles.User) msg
            else {
                val idx = list.indexOfFirst { it.id == failedMessageId }
                if (idx > 0) list.subList(0, idx).lastOrNull { it.role == com.dailysatori.service.ideatopic.IdeaMessageRoles.User } else null
            }
        } else {
            list.lastOrNull { it.role == com.dailysatori.service.ideatopic.IdeaMessageRoles.User }
        }
        target?.let { send(it.content) }
    }

    private fun refreshSession(topicId: String) {
        val session = runCatching { service.sessionOrThrow(sessionId) }.getOrNull()
        _state.update {
            it.copy(
                title = session?.title ?: it.title,
                summary = session?.summary ?: it.summary,
                summaryStatus = session?.summaryStatus ?: it.summaryStatus,
                busy = service.isRequestActive(topicId),
            )
        }
    }
}

// ---------- Capture ----------

data class IdeaTopicCaptureState(
    val sourceKey: IdeaSourceKey? = null,
    val existingTopicId: String? = null,
    val capturedTopicId: String? = null,
    val busy: Boolean = false,
    val error: IdeaTopicError? = null,
    val existingTopics: List<IdeaTopicSummary> = emptyList(),
)

class IdeaTopicCaptureViewModel(private val service: IdeaTopicService) : ViewModel() {
    private val _state = MutableStateFlow(IdeaTopicCaptureState())
    val state: StateFlow<IdeaTopicCaptureState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            service.observeSummaries()
                .catch { /* ignore */ }
                .collect { summaries ->
                    _state.update { it.copy(existingTopics = summaries) }
                }
        }
    }

    fun lookupExisting(key: IdeaSourceKey) {
        val existing = service.findBySourceSync(key)
        _state.update { it.copy(sourceKey = key, existingTopicId = existing) }
    }

    fun submit(input: IdeaCaptureInput) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val result = service.capture(input)
                _state.update {
                    it.copy(
                        busy = false,
                        capturedTopicId = result.topicId,
                        existingTopicId = result.topicId,
                    )
                }
            } catch (failure: Exception) {
                _state.update { it.copy(busy = false, error = failure.toIdeaTopicError()) }
            }
        }
    }
}
