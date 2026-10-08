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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
                                topicId = detail?.topic?.id ?: it.topicId,
                                mergedInto = detail?.topic?.id?.takeIf { target -> target != requestedTopicId },
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
                            state.copy(mergeCandidates = summaries.filter { it.id != state.topicId })
                        }
                    }
            }
        }
    }

    init {
        viewModelScope.launch {
            service.observeBusy(requestedTopicId).collect { busy -> _state.update { it.copy(busy = busy) } }
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
        val detail = service.getDetailSync(finalTarget)
        _state.update { it.copy(topicId = finalTarget, mergedInto = finalTarget, detail = detail, deleted = detail == null) }
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
            } catch (cancelled: CancellationException) {
                throw cancelled
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
    val summaryPartial: Boolean = false,
    val busy: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val error: IdeaTopicError? = null,
) {
    val canSummarize: Boolean get() = !busy && messages.any { it.status == com.dailysatori.service.ideatopic.IdeaMessageStatus.Complete }
}

/** Accepts either a legacy session id or a topic id for its ongoing discussion. */
@OptIn(ExperimentalCoroutinesApi::class)
class IdeaTopicSessionViewModel(
    sessionId: String,
    private val service: IdeaTopicService,
    private val workflow: IdeaTopicAiWorkflow,
) : ViewModel() {
    private val _state = MutableStateFlow(IdeaTopicSessionState(sessionId = sessionId))
    val state: StateFlow<IdeaTopicSessionState> = _state.asStateFlow()

    private val olderMessages = MutableStateFlow<List<IdeaTopicMessage>>(emptyList())
    private val activeSession = MutableStateFlow<String?>(null)

    init {
        val legacySession = runCatching { service.sessionOrThrow(sessionId) }.getOrNull()
        val mainTopicId = service.resolveTopicId(legacySession?.topicId ?: sessionId)
        val session = legacySession ?: mainTopicId?.let(service::discussionSessionSync)
        activeSession.value = session?.id
        _state.update {
            it.copy(
                sessionId = session?.id.orEmpty(),
                topicId = mainTopicId,
                title = session?.title.orEmpty(),
                summary = session?.summary.orEmpty(),
                summaryStatus = session?.summaryStatus ?: IdeaSessionSummaryStatus.None,
                busy = mainTopicId?.let(service::isRequestActive) ?: false,
                error = if (mainTopicId == null) IdeaTopicError.NotFound else null,
            )
        }
        if (mainTopicId != null) observeDiscussion(mainTopicId)
    }

    private fun observeDiscussion(topicId: String) {
        // Opening a discussion neither creates a session nor starts an AI request.
        viewModelScope.launch {
            activeSession.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else {
                    combine(service.observeMessages(id), olderMessages) { latest, older ->
                        (older + latest).distinctBy { it.id }.sortedWith(compareBy({ it.createdAt }, { it.id }))
                    }
                }
            }.catch { failure -> _state.update { it.copy(error = failure.toIdeaTopicError()) } }
                .collect { messages -> _state.update { it.copy(messages = messages) } }
        }
        viewModelScope.launch {
            service.observeBusy(topicId).collect { busy -> _state.update { it.copy(busy = busy) } }
        }
        viewModelScope.launch {
            service.observeDetail(topicId).collect { detail ->
                if (detail == null) _state.update { it.copy(error = IdeaTopicError.NotFound) }
                else {
                    if (activeSession.value == null) activeSession.value = service.discussionSessionSync(detail.topic.id)?.id
                    refreshSession(detail.topic.id)
                }
            }
        }
    }

    fun send(text: String, onSaved: () -> Unit = {}) {
        if (text.isBlank()) return
        if (text.length > IdeaAiMaxUserPromptCharacters) {
            _state.update { it.copy(error = IdeaTopicError.InputTooLong) }
            return
        }
        runRequest { topicId ->
            val id = activeSession.value ?: service.getOrCreateDiscussionSession(topicId).also {
                activeSession.value = it
                refreshSession(topicId)
            }
            workflow.send(id, text, onSaved)
        }
    }

    fun summarize() {
        val id = activeSession.value ?: return
        runRequest { workflow.summarize(id) }
    }

    private fun runRequest(block: suspend (String) -> Unit) {
        val topicId = _state.value.topicId ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block(topicId)
                _state.update { it.copy(error = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
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
        val sessionId = activeSession.value ?: return
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
        val sessionId = activeSession.value ?: return
        val target = _state.value.messages.lastOrNull { message ->
            (failedMessageId == null || message.id == failedMessageId) &&
                message.role == com.dailysatori.service.ideatopic.IdeaMessageRoles.Assistant &&
                message.status in listOf(com.dailysatori.service.ideatopic.IdeaMessageStatus.Failed,
                    com.dailysatori.service.ideatopic.IdeaMessageStatus.Interrupted)
        } ?: return
        runRequest { workflow.retry(sessionId, target.id) }
    }

    private fun refreshSession(topicId: String) {
        val session = activeSession.value?.let { runCatching { service.sessionOrThrow(it) }.getOrNull() }
        _state.update {
            it.copy(
                sessionId = session?.id.orEmpty(),
                topicId = service.resolveTopicId(topicId),
                title = session?.title ?: it.title,
                summary = session?.summary ?: it.summary,
                summaryStatus = session?.summaryStatus ?: it.summaryStatus,
                summaryPartial = session?.let { s -> s.summary.isNotBlank() && s.summaryCoveredMessageIds.size < service.messagesSync(s.id).count { message -> message.status == com.dailysatori.service.ideatopic.IdeaMessageStatus.Complete } } ?: false,
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
                .catch { _state.update { it.copy(error = IdeaTopicError.StorageFailure) } }
                .collect { summaries ->
                    _state.update { it.copy(existingTopics = summaries) }
                }
        }
    }

    fun lookupExisting(key: IdeaSourceKey) {
        val existing = service.findBySourceSync(key)
        _state.update { it.copy(sourceKey = key, existingTopicId = existing, capturedTopicId = null, busy = false, error = null) }
    }

    fun consumeCapturedTopicId(): String? {
        val topicId = _state.value.capturedTopicId ?: return null
        _state.update { it.copy(capturedTopicId = null) }
        return topicId
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
