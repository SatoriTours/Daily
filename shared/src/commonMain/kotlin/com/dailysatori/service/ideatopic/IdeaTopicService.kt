package com.dailysatori.service.ideatopic

import com.dailysatori.data.repository.IdeaTopicRepository
import com.dailysatori.service.diagnostics.DiagnosticLog
import kotlinx.coroutines.flow.Flow
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
    private val writeLock = Mutex()
    private val activeRequests = mutableMapOf<String, String>()

    // ---------- Reads ----------

    fun observeSummaries(): Flow<List<IdeaTopicSummary>> = repository.observeSummaries()

    fun observeDetail(topicId: String): Flow<IdeaTopicDetail?> =
        repository.observeDetail(resolveTopicId(topicId) ?: topicId)

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
    suspend fun recoverInterruptedRequests() = writeLock.withLock {
        repository.pendingMessagesSync().forEach { message ->
            val session = repository.getSessionRowSync(message.session_id) ?: return@forEach
            val main = repository.resolveMainTopicIdSync(session.topic_id) ?: return@forEach
            if (main in activeRequests) return@forEach
            repository.updateMessageStatus(message.id, IdeaMessageStatus.Interrupted, null)
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

    internal suspend fun savePendingReply(sessionId: String, messageId: String) {
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

    internal fun updateReplyContent(messageId: String, content: String) {
        repository.updateMessage(messageId, content, IdeaMessageStatus.Pending, null)
    }

    /** Writes the final reply only while this request still owns the topic and the topic still exists. */
    internal suspend fun completeReply(mainTopicId: String, token: String, messageId: String, content: String) =
        writeLock.withLock {
            if (!ownsRequest(mainTopicId, token)) return@withLock
            if (repository.resolveMainTopicIdSync(mainTopicId) != mainTopicId) return@withLock
            repository.updateMessage(messageId, content, IdeaMessageStatus.Complete, null)
            activeRequests.remove(mainTopicId)
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
        activeRequests.remove(mainTopicId)
    }

    internal suspend fun interruptReply(mainTopicId: String, token: String, messageId: String) = writeLock.withLock {
        if (!ownsRequest(mainTopicId, token)) return@withLock
        repository.updateMessageStatus(messageId, IdeaMessageStatus.Interrupted, null)
        activeRequests.remove(mainTopicId)
    }

    internal suspend fun beginRequest(mainTopicId: String, token: String): Long = writeLock.withLock {
        if (activeRequests.containsKey(mainTopicId)) throw IdeaTopicException(IdeaTopicError.Busy)
        val revision = repository.getTopicRowSync(mainTopicId)?.context_revision
            ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        activeRequests[mainTopicId] = token
        revision
    }

    internal suspend fun releaseRequest(mainTopicId: String, token: String) = writeLock.withLock {
        if (ownsRequest(mainTopicId, token)) activeRequests.remove(mainTopicId)
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
        activeRequests.remove(mainTopicId)
    }

    internal suspend fun failSessionSummary(mainTopicId: String, token: String, sessionId: String) =
        writeLock.withLock {
            if (!ownsRequest(mainTopicId, token)) return@withLock
            repository.updateSessionSummaryStatus(sessionId, IdeaSessionSummaryStatus.Failed, now())
            activeRequests.remove(mainTopicId)
        }

    internal suspend fun markSessionSummaryPending(sessionId: String) = writeLock.withLock {
        repository.updateSessionSummaryStatus(sessionId, IdeaSessionSummaryStatus.Pending, now())
    }

    /** Releases a request without a token (explicit user cancel) and marks partial output interrupted. */
    internal suspend fun forceCancelRequest(mainTopicId: String) = writeLock.withLock {
        if (activeRequests.remove(mainTopicId) == null) return@withLock
        repository.sessionsByTopicSync(mainTopicId).forEach { session ->
            repository.getMessagesSync(session.id)
                .filter { it.status == IdeaMessageStatus.Pending }
                .forEach { repository.updateMessageStatus(it.id, IdeaMessageStatus.Interrupted, null) }
        }
    }

    private fun ownsRequest(mainTopicId: String, token: String): Boolean =
        activeRequests[mainTopicId] == token
}
