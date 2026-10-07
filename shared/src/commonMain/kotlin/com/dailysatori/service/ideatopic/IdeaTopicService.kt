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
 */
class IdeaTopicService(
    private val repository: IdeaTopicRepository,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val newId: () -> String = { DiagnosticLog.newId() },
) {
    private val writeLock = Mutex()

    private val activeRequestTopics = mutableSetOf<String>()

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

    fun draftsSync(topicId: String): List<IdeaTopicDraft> {
        val main = resolveTopicId(topicId) ?: return emptyList()
        return repository.draftsByTopicSync(main)
    }

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
        if (topicId in activeRequestTopics) throw IdeaTopicException(IdeaTopicError.Busy)
        repository.componentTopicIdsSync(topicId).forEach { componentId ->
            if (componentId in activeRequestTopics) throw IdeaTopicException(IdeaTopicError.Busy)
        }
    }
}
