package com.dailysatori.service.ideatopic

import com.dailysatori.data.repository.IdeaTopicRepository
import com.dailysatori.service.diagnostics.DiagnosticLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * The single write owner for idea topic state. Every structural change is serialized here and
 * committed through one repository transaction, so callers never observe a partial capture.
 * AI request ownership is registered here as well, so merge/delete can refuse while a request runs.
 */
class IdeaTopicService(
    private val repository: IdeaTopicRepository,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val newId: () -> String = { DiagnosticLog.newId() },
) {
    private data class ActiveRequest(val token: String, val job: Job?, val cancelling: Boolean = false)

    private val writeLock = Mutex()
    private val activeRequests = mutableMapOf<String, ActiveRequest>()
    private val requestTopics = MutableStateFlow<Set<String>>(emptySet())

    init { recoverPendingWork() }

    // ---------- Reads ----------

    fun observeSummaries(): Flow<List<IdeaTopicSummary>> = repository.observeSummaries()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDetail(topicId: String): Flow<IdeaTopicDetail?> =
        observeMainTopicId(topicId).flatMapLatest { main ->
            if (main == null) flowOf(null) else repository.observeDetail(main)
        }

    private fun observeMainTopicId(topicId: String): Flow<String?> =
        repository.observeSummaries().map { resolveTopicId(topicId) }.distinctUntilChanged()

    fun getDetailSync(topicId: String): IdeaTopicDetail? {
        val main = resolveTopicId(topicId) ?: return null
        return repository.getDetailSync(main)
    }

    /** Final main topic id holding the given source key, or null when it is not captured. */
    fun findBySourceSync(key: IdeaSourceKey): String? =
        repository.findBySourceSync(key)?.let(repository::resolveMainTopicIdSync)

    /** Resolves a possibly merged-away topic id to the final main topic; null when it no longer exists. */
    fun resolveTopicId(topicId: String): String? = repository.resolveMainTopicIdSync(topicId)

    fun sessionsSync(topicId: String): List<IdeaTopicSession> {
        val main = resolveTopicId(topicId) ?: return emptyList()
        return repository.sessionsByTopicSync(main)
    }

    fun messagesSync(sessionId: String): List<IdeaTopicMessage> = repository.getMessagesSync(sessionId)

    fun observeMessages(sessionId: String, limit: Int = 30): Flow<List<IdeaTopicMessage>> =
        repository.observeMessages(sessionId, limit)

    fun getMessagesBeforeSync(
        sessionId: String,
        beforeCreatedAt: Long,
        beforeId: String,
        limit: Int = 30,
    ): List<IdeaTopicMessage> = repository.getMessagesBeforeSync(sessionId, beforeCreatedAt, beforeId, limit)

    fun draftsSync(topicId: String): List<IdeaTopicDraft> {
        val main = resolveTopicId(topicId) ?: return emptyList()
        return repository.draftsByTopicSync(main)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDrafts(topicId: String): Flow<List<IdeaTopicDraft>> =
        observeMainTopicId(topicId).flatMapLatest { main ->
            if (main == null) flowOf(emptyList()) else repository.observeDrafts(main)
        }

    fun observeBusy(topicId: String): Flow<Boolean> =
        combine(requestTopics, observeMainTopicId(topicId)) { active, main -> main in active }
            .distinctUntilChanged()

    /** True until this topic's network job and cancellation cleanup have finished. */
    fun isRequestActive(topicId: String): Boolean =
        (resolveTopicId(topicId) ?: topicId) in requestTopics.value

    fun sessionOrThrow(sessionId: String): IdeaTopicSession =
        repository.getSessionSync(sessionId) ?: throw IdeaTopicException(IdeaTopicError.NotFound)

    // ---------- Capture ----------

    suspend fun capture(input: IdeaCaptureInput): IdeaCaptureResult = writeLock.withLock {
        val content = input.content.copy(title = input.content.title.trim())
        if (content.title.isBlank()) throw IdeaTopicException(IdeaTopicError.InvalidInput)
        validateSnapshot(input.source)
        val target = input.targetTopicId?.let { requested ->
            repository.resolveMainTopicIdSync(requested) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        }
        repository.capture(
            source = input.source,
            content = content,
            resolvedTargetTopicId = target,
            newTopicId = newId(),
            newSourceId = newId(),
            newEventId = newId(),
            now = now(),
        )
    }

    private fun validateSnapshot(snapshot: IdeaSourceSnapshot) {
        if (snapshot.key.type.isBlank() || snapshot.key.recordId.isBlank()) {
            throw IdeaTopicException(IdeaTopicError.InvalidInput)
        }
    }

    // ---------- Lifecycle ----------

    suspend fun updateContent(topicId: String, content: IdeaTopicContent) = writeLock.withLock {
        val main = repository.resolveMainTopicIdSync(topicId) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        val trimmed = content.copy(title = content.title.trim())
        if (trimmed.title.isBlank()) throw IdeaTopicException(IdeaTopicError.InvalidInput)
        assertNotBusy(main)
        repository.updateContent(main, trimmed, newId(), now())
    }

    suspend fun setStatus(topicId: String, status: IdeaTopicStatus) = writeLock.withLock {
        val main = repository.resolveMainTopicIdSync(topicId) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        assertNotBusy(main)
        repository.changeStatus(main, status, newId(), now())
    }

    suspend fun appendProgress(topicId: String, text: String) = writeLock.withLock {
        val main = repository.resolveMainTopicIdSync(topicId) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        val trimmed = text.trim()
        if (trimmed.isBlank()) throw IdeaTopicException(IdeaTopicError.InvalidInput)
        assertNotBusy(main)
        repository.appendProgress(main, trimmed, newId(), now())
    }

    /** Merges [fromTopicId] into the main topic behind [intoTopicId] and returns the final target id. */
    suspend fun merge(fromTopicId: String, intoTopicId: String): String = writeLock.withLock {
        val fromRow = repository.getTopicRowSync(fromTopicId) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        val intoMain = repository.resolveMainTopicIdSync(intoTopicId) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        if (fromRow.merged_into_topic_id != null) throw IdeaTopicException(IdeaTopicError.AlreadyMerged)
        if (fromTopicId == intoMain) {
            if (fromTopicId == intoTopicId) throw IdeaTopicException(IdeaTopicError.InvalidInput)
            throw IdeaTopicException(IdeaTopicError.MergeCycle)
        }
        assertNotBusy(fromTopicId)
        assertNotBusy(intoMain)
        repository.merge(fromTopicId, intoMain, newId(), now())
        intoMain
    }

    /** Deletes the main topic together with everything merged into it. Original entries stay untouched. */
    suspend fun delete(topicId: String) = writeLock.withLock {
        val main = repository.resolveMainTopicIdSync(topicId) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        assertNotBusy(main)
        repository.deleteComponent(main)
    }

    private fun assertNotBusy(topicId: String) {
        if (topicId in activeRequests) throw IdeaTopicException(IdeaTopicError.Busy)
        repository.componentTopicIdsSync(topicId).forEach { componentId ->
            if (componentId in activeRequests) throw IdeaTopicException(IdeaTopicError.Busy)
        }
    }

    // ---------- Conversations ----------

    suspend fun createSession(topicId: String, title: String): String = writeLock.withLock {
        val main = repository.resolveMainTopicIdSync(topicId) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        val sessionId = newId()
        repository.createSession(
            sessionId = sessionId,
            topicId = main,
            originalTopicId = main,
            title = title.trim().ifBlank { "新的沟通" },
            now = now(),
        )
        sessionId
    }

    /** Marks leftover in-flight replies from a previous process as interrupted without retrying them. */
    suspend fun recoverInterruptedRequests() = writeLock.withLock { recoverPendingWork() }

    private fun recoverPendingWork() {
        repository.pendingMessagesSync().forEach { message ->
            val session = repository.getSessionRowSync(message.session_id) ?: return@forEach
            val main = repository.resolveMainTopicIdSync(session.topic_id) ?: return@forEach
            if (main !in activeRequests) repository.updateMessageStatus(message.id, IdeaMessageStatus.Interrupted, null)
        }
        repository.pendingSessionsSync().forEach { session ->
            val main = resolveTopicId(session.topicId) ?: return@forEach
            if (main !in activeRequests) repository.updateSessionSummaryStatus(session.id, IdeaSessionSummaryStatus.Failed, now())
        }
    }

    internal suspend fun saveUserMessage(sessionId: String, messageId: String, content: String) = writeLock.withLock {
        repository.insertMessage(
            messageId = messageId,
            sessionId = sessionId,
            role = IdeaMessageRoles.User,
            content = content,
            status = IdeaMessageStatus.Complete,
            error = null,
            now = now(),
        )
        repository.markSummaryNeedsUpdateIfReady(sessionId, now())
    }

    internal suspend fun savePendingReply(sessionId: String, messageId: String) = writeLock.withLock {
        repository.insertMessage(
            messageId = messageId,
            sessionId = sessionId,
            role = IdeaMessageRoles.Assistant,
            content = "",
            status = IdeaMessageStatus.Pending,
            error = null,
            now = now(),
        )
    }

    internal suspend fun updateReplyContent(mainTopicId: String, token: String, messageId: String, content: String) =
        writeLock.withLock {
            if (ownsRequest(mainTopicId, token)) repository.updateMessage(messageId, content, IdeaMessageStatus.Pending, null)
        }

    /** Writes the final reply only while this request still owns the topic and the topic still exists. */
    internal suspend fun completeReply(mainTopicId: String, token: String, messageId: String, content: String) =
        writeLock.withLock {
            if (!ownsRequest(mainTopicId, token)) return@withLock
            if (repository.resolveMainTopicIdSync(mainTopicId) != mainTopicId) return@withLock
            repository.updateMessage(messageId, content, IdeaMessageStatus.Complete, null)
        }

    internal suspend fun failReply(
        mainTopicId: String,
        token: String,
        messageId: String,
        errorCode: String,
    ) = writeLock.withLock {
        if (!ownsRequest(mainTopicId, token)) return@withLock
        repository.updateMessageStatus(messageId, IdeaMessageStatus.Failed, errorCode)
        repository.recordEvent(
            eventId = newId(),
            topicId = mainTopicId,
            kind = IdeaEventKinds.AiRequestFailed,
            payloadJson = "{\"reason\":\"$errorCode\"}",
            now = now(),
        )
    }

    internal suspend fun interruptReply(mainTopicId: String, token: String, messageId: String) = writeLock.withLock {
        if (activeRequests[mainTopicId]?.token == token) {
            repository.updateMessageStatus(messageId, IdeaMessageStatus.Interrupted, null)
        }
    }

    internal suspend fun beginRequest(mainTopicId: String, token: String): Long = writeLock.withLock {
        if (activeRequests.containsKey(mainTopicId)) throw IdeaTopicException(IdeaTopicError.Busy)
        val revision = repository.getTopicRowSync(mainTopicId)?.context_revision
            ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        activeRequests[mainTopicId] = ActiveRequest(token, currentCoroutineContext()[Job])
        requestTopics.value = activeRequests.keys.toSet()
        revision
    }

    internal suspend fun releaseRequest(mainTopicId: String, token: String) = writeLock.withLock {
        if (activeRequests[mainTopicId]?.token == token) {
            activeRequests.remove(mainTopicId)
            requestTopics.value = activeRequests.keys.toSet()
        }
    }

    internal suspend fun saveSessionSummary(
        sessionId: String,
        token: String,
        mainTopicId: String,
        summary: String,
        coveredMessageIds: List<String>,
        throughMessageId: String?,
    ) = writeLock.withLock {
        if (!ownsRequest(mainTopicId, token)) return@withLock
        if (repository.resolveMainTopicIdSync(mainTopicId) != mainTopicId) return@withLock
        repository.updateSessionSummary(
            sessionId = sessionId,
            summary = summary,
            throughMessageId = throughMessageId,
            coveredMessageIds = coveredMessageIds,
            status = IdeaSessionSummaryStatus.Ready,
            now = now(),
        )
        repository.bumpRevision(mainTopicId, now())
    }

    internal suspend fun failSessionSummary(mainTopicId: String, token: String, sessionId: String) =
        writeLock.withLock {
            if (activeRequests[mainTopicId]?.token != token) return@withLock
            repository.updateSessionSummaryStatus(sessionId, IdeaSessionSummaryStatus.Failed, now())
        }

    internal suspend fun markSessionSummaryPending(sessionId: String) = writeLock.withLock {
        repository.updateSessionSummaryStatus(sessionId, IdeaSessionSummaryStatus.Pending, now())
    }

    /** Request ownership remains busy while cancellation waits for its network job to finish. */
    internal suspend fun forceCancelRequest(mainTopicId: String) {
        val request = writeLock.withLock {
            activeRequests[mainTopicId]?.also { activeRequests[mainTopicId] = it.copy(cancelling = true) }
        } ?: return
        request.job?.cancelAndJoin()
        releaseRequest(mainTopicId, request.token)
    }

    internal suspend fun saveDraft(
        mainTopicId: String,
        token: String,
        draftId: String,
        baseRevision: Long,
        proposal: IdeaDraftContent,
    ): IdeaTopicDraft = writeLock.withLock {
        if (!ownsRequest(mainTopicId, token)) throw IdeaTopicException(IdeaTopicError.Busy)
        if (repository.resolveMainTopicIdSync(mainTopicId) != mainTopicId) {
            throw IdeaTopicException(IdeaTopicError.NotFound)
        }
        repository.insertDraft(
            draftId = draftId,
            topicId = mainTopicId,
            originalTopicId = mainTopicId,
            baseRevision = baseRevision,
            content = proposal.content,
            referenceIds = proposal.referenceIds,
            originSessionId = null,
            now = now(),
        )
        repository.findDraftSync(draftId) ?: throw IdeaTopicException(IdeaTopicError.StorageFailure)
    }

    // ---------- Draft confirmation ----------

    /** Applies a user-confirmed draft. Stale, discarded or already-applied drafts never overwrite content. */
    suspend fun applyDraft(draftId: String, editedContent: IdeaTopicContent) = writeLock.withLock {
        val trimmed = editedContent.copy(title = editedContent.title.trim())
        if (trimmed.title.isBlank()) throw IdeaTopicException(IdeaTopicError.InvalidInput)
        val draft = repository.findDraftSync(draftId) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        assertNotBusy(draft.topicId)
        repository.applyDraft(draftId, draft.topicId, trimmed, newId(), newId(), now())
    }

    suspend fun discardDraft(draftId: String) = writeLock.withLock {
        repository.discardDraft(draftId, newId(), now())
    }

    private fun ownsRequest(mainTopicId: String, token: String): Boolean =
        activeRequests[mainTopicId]?.let { it.token == token && !it.cancelling } == true
}
