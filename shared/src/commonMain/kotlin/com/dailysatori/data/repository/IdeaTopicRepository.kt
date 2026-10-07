package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.dailysatori.service.ideatopic.IdeaCapturedEventPayload
import com.dailysatori.service.ideatopic.IdeaContentUpdatedEventPayload
import com.dailysatori.service.ideatopic.IdeaDraftEventPayload
import com.dailysatori.service.ideatopic.IdeaDraftResolvedEventPayload
import com.dailysatori.service.ideatopic.IdeaDraftState
import com.dailysatori.service.ideatopic.IdeaEventKinds
import com.dailysatori.service.ideatopic.IdeaMergedEventPayload
import com.dailysatori.service.ideatopic.IdeaProgressEventPayload
import com.dailysatori.service.ideatopic.IdeaSourceKey
import com.dailysatori.service.ideatopic.IdeaSourceSnapshot
import com.dailysatori.service.ideatopic.IdeaStatusChangedEventPayload
import com.dailysatori.service.ideatopic.IdeaTopicContent
import com.dailysatori.service.ideatopic.IdeaTopicDetail
import com.dailysatori.service.ideatopic.IdeaTopicDraft
import com.dailysatori.service.ideatopic.IdeaTopicError
import com.dailysatori.service.ideatopic.IdeaTopicEvent
import com.dailysatori.service.ideatopic.IdeaTopicException
import com.dailysatori.service.ideatopic.IdeaTopicMessage
import com.dailysatori.service.ideatopic.IdeaTopicSession
import com.dailysatori.service.ideatopic.IdeaTopicSource
import com.dailysatori.service.ideatopic.IdeaTopicStatus
import com.dailysatori.service.ideatopic.IdeaTopicSummary
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Idea_topic
import com.dailysatori.shared.db.Idea_topic_event
import com.dailysatori.shared.db.Idea_topic_message
import com.dailysatori.shared.db.Idea_topic_session
import com.dailysatori.shared.db.Idea_topic_source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Storage for idea topics. All structural writes happen in a single transaction here; the
 * owning service serializes callers, so a captured snapshot is never half-written.
 */
class IdeaTopicRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val stringListSerializer = ListSerializer(String.serializer())

    // ---------- Reads ----------

    fun observeSummaries(): Flow<List<IdeaTopicSummary>> =
        q.selectMainIdeaTopics().asFlow().mapToList(Dispatchers.IO).map { rows ->
            rows.map { row ->
                IdeaTopicSummary(
                    id = row.id,
                    content = IdeaTopicContent(
                        title = row.title,
                        description = row.description,
                        provenanceSummary = row.provenance_summary,
                        conclusions = row.conclusions,
                        nextAction = row.next_action,
                    ),
                    status = IdeaTopicStatus.fromStorage(row.status),
                    latestProgress = decodeProgressText(row.latest_progress),
                    sourceCount = row.source_count,
                    updatedAt = row.updated_at,
                )
            }
        }

    fun observeDetail(topicId: String): Flow<IdeaTopicDetail?> = combine(
        q.selectIdeaTopicById(topicId).asFlow().mapToOneOrNull(Dispatchers.IO),
        q.selectIdeaTopicSourcesByTopic(topicId).asFlow().mapToList(Dispatchers.IO),
        q.selectIdeaTopicEventsByTopic(topicId).asFlow().mapToList(Dispatchers.IO),
        q.selectIdeaTopicSessionsByTopic(topicId).asFlow().mapToList(Dispatchers.IO),
    ) { topic, sources, events, sessions ->
        topic?.let { toDetail(it, sources, events, sessions) }
    }

    fun getDetailSync(topicId: String): IdeaTopicDetail? = q.transactionWithResult {
        val topic = q.selectIdeaTopicById(topicId).executeAsOneOrNull() ?: return@transactionWithResult null
        toDetail(
            topic = topic,
            sources = q.selectIdeaTopicSourcesByTopic(topicId).executeAsList(),
            events = q.selectIdeaTopicEventsByTopic(topicId).executeAsList(),
            sessions = q.selectIdeaTopicSessionsByTopic(topicId).executeAsList(),
        )
    }

    fun getTopicRowSync(topicId: String): Idea_topic? = q.selectIdeaTopicById(topicId).executeAsOneOrNull()

    fun getSessionRowSync(sessionId: String): Idea_topic_session? =
        q.selectIdeaTopicSessionById(sessionId).executeAsOneOrNull()

    /** Owner topic currently holding the source key, without resolving the merge chain. */
    fun findBySourceSync(key: IdeaSourceKey): String? =
        q.selectIdeaTopicSourceByKey(key.type, key.recordId).executeAsOneOrNull()?.topic_id

    /** Follows `merged_into` pointers to the final main topic; null when the topic is gone. */
    fun resolveMainTopicIdSync(topicId: String): String? {
        var current = topicId
        var guard = 0
        while (guard++ < 64) {
            val row = q.selectIdeaTopicById(current).executeAsOneOrNull() ?: return null
            val next = row.merged_into_topic_id ?: return current
            if (next == current) return current
            current = next
        }
        throw IdeaTopicException(IdeaTopicError.StorageFailure)
    }

    /** All topic ids in the merge component owned by [mainTopicId], including itself. */
    fun componentTopicIdsSync(mainTopicId: String): List<String> {
        val collected = mutableListOf(mainTopicId)
        var index = 0
        while (index < collected.size) {
            val children = q.selectIdeaTopicIdsMergedInto(collected[index]).executeAsList()
            children.forEach { child -> if (child !in collected) collected += child }
            index++
        }
        return collected
    }

    fun getMessagesSync(sessionId: String): List<IdeaTopicMessage> =
        q.selectIdeaTopicMessagesBySession(sessionId).executeAsList().map(::toMessage)

    fun getMessagesBeforeSync(
        sessionId: String,
        beforeCreatedAt: Long,
        beforeId: String,
        limit: Int,
    ): List<IdeaTopicMessage> =
        q.selectIdeaTopicMessagesBefore(sessionId, beforeCreatedAt, beforeCreatedAt, beforeId, limit.toLong())
            .executeAsList()
            .sortedWith(messageOrder)
            .map(::toMessage)

    fun observeMessages(sessionId: String, limit: Int = 30): Flow<List<IdeaTopicMessage>> =
        q.selectRecentIdeaTopicMessages(sessionId, limit.toLong())
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.sortedWith(messageOrder).map(::toMessage) }

    fun pendingMessagesSync(): List<Idea_topic_message> =
        q.selectIdeaTopicMessagesByStatus(com.dailysatori.service.ideatopic.IdeaMessageStatus.Pending).executeAsList()

    fun eventsByTopicSync(topicId: String): List<IdeaTopicEvent> =
        q.selectIdeaTopicEventsByTopic(topicId).executeAsList().map(::toEvent)

    fun sessionsByTopicSync(topicId: String): List<IdeaTopicSession> =
        q.selectIdeaTopicSessionsByTopic(topicId).executeAsList().map(::toSession)

    fun draftsByTopicSync(topicId: String): List<IdeaTopicDraft> {
        val events = q.selectIdeaTopicEventsByTopic(topicId).executeAsList()
        val proposals = events.filter { it.kind == IdeaEventKinds.DraftProposed }
        if (proposals.isEmpty()) return emptyList()
        val resolutions = events.filter {
            it.kind == IdeaEventKinds.DraftApplied || it.kind == IdeaEventKinds.DraftDiscarded
        }.mapNotNull { event ->
            decodeOrNull<IdeaDraftResolvedEventPayload>(event.payload_json)?.let { it.draftId to event }
        }.toMap()
        val topicRevision = q.selectIdeaTopicById(topicId).executeAsOneOrNull()?.context_revision ?: 0L
        return proposals.mapNotNull { event ->
            val payload = decodeOrNull<IdeaDraftEventPayload>(event.payload_json) ?: return@mapNotNull null
            val resolution = resolutions[event.id]
            val state = when {
                resolution?.kind == IdeaEventKinds.DraftApplied -> IdeaDraftState.Applied
                resolution?.kind == IdeaEventKinds.DraftDiscarded -> IdeaDraftState.Discarded
                payload.baseRevision != topicRevision -> IdeaDraftState.Stale
                else -> IdeaDraftState.Pending
            }
            IdeaTopicDraft(
                id = event.id,
                topicId = topicId,
                baseRevision = payload.baseRevision,
                proposal = com.dailysatori.service.ideatopic.IdeaDraftContent(payload.content, payload.referenceIds),
                state = state,
                createdAt = event.created_at,
                resolvedAt = resolution?.created_at,
            )
        }.sortedWith(compareByDescending<IdeaTopicDraft> { it.createdAt }.thenByDescending { it.id })
    }

    // ---------- Capture ----------

    fun capture(
        source: IdeaSourceSnapshot,
        content: IdeaTopicContent,
        resolvedTargetTopicId: String?,
        newTopicId: String,
        newSourceId: String,
        newEventId: String,
        now: Long,
    ): com.dailysatori.service.ideatopic.IdeaCaptureResult = q.transactionWithResult {
        val existing = q.selectIdeaTopicSourceByKey(source.key.type, source.key.recordId).executeAsOneOrNull()
        if (existing != null) {
            val main = resolveMainTopicIdSync(existing.topic_id)
                ?: throw IdeaTopicException(IdeaTopicError.StorageFailure)
            return@transactionWithResult com.dailysatori.service.ideatopic.IdeaCaptureResult(main, alreadyCaptured = true)
        }

        val topicId: String
        val originalTopicId: String
        if (resolvedTargetTopicId == null) {
            topicId = newTopicId
            originalTopicId = newTopicId
            q.insertIdeaTopic(
                id = newTopicId,
                title = content.title,
                description = content.description,
                provenance_summary = content.provenanceSummary,
                conclusions = content.conclusions,
                next_action = content.nextAction,
                status = IdeaTopicStatus.PendingResearch.storageValue,
                merged_into_topic_id = null,
                context_revision = 0L,
                created_at = now,
                updated_at = now,
            )
        } else {
            val target = q.selectIdeaTopicById(resolvedTargetTopicId).executeAsOneOrNull()
                ?: throw IdeaTopicException(IdeaTopicError.NotFound)
            if (target.merged_into_topic_id != null) throw IdeaTopicException(IdeaTopicError.AlreadyMerged)
            topicId = resolvedTargetTopicId
            originalTopicId = resolvedTargetTopicId
            bumpRevisionInternal(resolvedTargetTopicId, now)
        }

        q.insertIdeaTopicSource(
            id = newSourceId,
            topic_id = topicId,
            original_topic_id = originalTopicId,
            source_type = source.key.type,
            source_record_id = source.key.recordId,
            snapshot_json = json.encodeToString(IdeaSourceSnapshot.serializer(), source),
            captured_at = now,
            created_at = now,
            updated_at = now,
        )
        q.insertIdeaTopicEvent(
            id = newEventId,
            topic_id = topicId,
            original_topic_id = originalTopicId,
            kind = IdeaEventKinds.Captured,
            payload_json = json.encodeToString(
                IdeaCapturedEventPayload.serializer(),
                IdeaCapturedEventPayload(source, content),
            ),
            created_at = now,
        )
        com.dailysatori.service.ideatopic.IdeaCaptureResult(topicId, alreadyCaptured = false)
    }

    // ---------- Lifecycle ----------

    fun updateContent(topicId: String, content: IdeaTopicContent, eventId: String, now: Long) = q.transactionWithResult {
        val row = q.selectIdeaTopicById(topicId).executeAsOneOrNull()
            ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        val before = row.toContent()
        q.updateIdeaTopicContent(
            title = content.title,
            description = content.description,
            provenance_summary = content.provenanceSummary,
            conclusions = content.conclusions,
            next_action = content.nextAction,
            context_revision = row.context_revision + 1,
            updated_at = now,
            id = topicId,
        )
        q.insertIdeaTopicEvent(
            id = eventId,
            topic_id = topicId,
            original_topic_id = row.originalOwnerId(),
            kind = IdeaEventKinds.ContentUpdated,
            payload_json = json.encodeToString(
                IdeaContentUpdatedEventPayload.serializer(),
                IdeaContentUpdatedEventPayload(before, content),
            ),
            created_at = now,
        )
    }

    /** Returns false when the status already equals [status]; no event and no revision bump then. */
    fun changeStatus(topicId: String, status: IdeaTopicStatus, eventId: String, now: Long): Boolean =
        q.transactionWithResult {
            val row = q.selectIdeaTopicById(topicId).executeAsOneOrNull()
                ?: throw IdeaTopicException(IdeaTopicError.NotFound)
            if (row.status == status.storageValue) return@transactionWithResult false
            q.updateIdeaTopicStatus(
                status = status.storageValue,
                context_revision = row.context_revision + 1,
                updated_at = now,
                id = topicId,
            )
            q.insertIdeaTopicEvent(
                id = eventId,
                topic_id = topicId,
                original_topic_id = row.originalOwnerId(),
                kind = IdeaEventKinds.StatusChanged,
                payload_json = json.encodeToString(
                    IdeaStatusChangedEventPayload.serializer(),
                    IdeaStatusChangedEventPayload(row.status, status.storageValue),
                ),
                created_at = now,
            )
            true
        }

    fun appendProgress(topicId: String, text: String, eventId: String, now: Long) = q.transactionWithResult {
        val row = q.selectIdeaTopicById(topicId).executeAsOneOrNull()
            ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        q.insertIdeaTopicEvent(
            id = eventId,
            topic_id = topicId,
            original_topic_id = row.originalOwnerId(),
            kind = IdeaEventKinds.Progress,
            payload_json = json.encodeToString(IdeaProgressEventPayload.serializer(), IdeaProgressEventPayload(text)),
            created_at = now,
        )
        bumpRevisionInternal(topicId, now)
    }

    fun merge(fromTopicId: String, intoTopicId: String, eventId: String, now: Long) = q.transactionWithResult {
        val from = q.selectIdeaTopicById(fromTopicId).executeAsOneOrNull()
            ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        val into = q.selectIdeaTopicById(intoTopicId).executeAsOneOrNull()
            ?: throw IdeaTopicException(IdeaTopicError.NotFound)

        val duplicateSnapshots = mutableListOf<IdeaSourceSnapshot>()
        q.selectIdeaTopicSourcesByTopic(fromTopicId).executeAsList().forEach { source ->
            val conflict = q.selectIdeaTopicSourceByKey(source.source_type, source.source_record_id)
                .executeAsOneOrNull()
            if (conflict != null && conflict.topic_id != fromTopicId) {
                decodeOrNull<IdeaSourceSnapshot>(source.snapshot_json)?.let { duplicateSnapshots += it }
                q.deleteIdeaTopicSource(source.id)
            } else {
                q.moveIdeaTopicSource(topic_id = intoTopicId, updated_at = now, id = source.id)
            }
        }
        q.selectIdeaTopicEventsByTopic(fromTopicId).executeAsList().forEach { event ->
            q.moveIdeaTopicEvent(topic_id = intoTopicId, id = event.id)
        }
        q.selectIdeaTopicSessionsByTopic(fromTopicId).executeAsList().forEach { session ->
            q.moveIdeaTopicSession(topic_id = intoTopicId, updated_at = now, id = session.id)
        }
        q.insertIdeaTopicEvent(
            id = eventId,
            topic_id = intoTopicId,
            original_topic_id = into.originalOwnerId(),
            kind = IdeaEventKinds.Merged,
            payload_json = json.encodeToString(
                IdeaMergedEventPayload.serializer(),
                IdeaMergedEventPayload(
                    fromTopicId = fromTopicId,
                    fromTitle = from.title,
                    fromContent = from.toContent(),
                    intoTopicId = intoTopicId,
                    intoTitle = into.title,
                    intoContent = into.toContent(),
                    duplicateSourceSnapshots = duplicateSnapshots,
                ),
            ),
            created_at = now,
        )
        q.markIdeaTopicMergedInto(merged_into_topic_id = intoTopicId, updated_at = now, id = fromTopicId)
        bumpRevisionInternal(intoTopicId, now)
    }

    fun deleteComponent(mainTopicId: String) = q.transactionWithResult {
        componentTopicIdsSync(mainTopicId).forEach { topicId ->
            q.selectIdeaTopicSessionsByTopic(topicId).executeAsList().forEach { session ->
                q.selectIdeaTopicMessagesBySession(session.id).executeAsList().forEach { message ->
                    q.deleteIdeaTopicMessage(message.id)
                }
                q.deleteIdeaTopicSession(session.id)
            }
            q.selectIdeaTopicSourcesByTopic(topicId).executeAsList().forEach { q.deleteIdeaTopicSource(it.id) }
            q.selectIdeaTopicEventsByTopic(topicId).executeAsList().forEach { q.deleteIdeaTopicEvent(it.id) }
            q.deleteIdeaTopic(topicId)
        }
    }

    // ---------- Sessions and messages ----------

    fun createSession(
        sessionId: String,
        topicId: String,
        originalTopicId: String,
        title: String,
        now: Long,
    ) = q.transactionWithResult {
        q.insertIdeaTopicSession(
            id = sessionId,
            topic_id = topicId,
            original_topic_id = originalTopicId,
            title = title,
            summary = "",
            summary_through_message_id = null,
            summary_covered_message_ids = "[]",
            summary_status = com.dailysatori.service.ideatopic.IdeaSessionSummaryStatus.None,
            created_at = now,
            updated_at = now,
        )
        bumpRevisionInternal(topicId, now)
    }

    fun insertMessage(
        messageId: String,
        sessionId: String,
        role: String,
        content: String,
        status: String,
        error: String?,
        now: Long,
        bumpRevision: Boolean = true,
    ) = q.transactionWithResult {
        q.insertIdeaTopicMessage(messageId, sessionId, role, content, status, error, now)
        if (bumpRevision) {
            q.selectIdeaTopicSessionById(sessionId).executeAsOneOrNull()?.let { session ->
                bumpRevisionInternal(session.topic_id, now)
            }
        }
    }

    fun updateMessage(messageId: String, content: String, status: String, error: String?) {
        q.updateIdeaTopicMessage(content = content, status = status, error = error, id = messageId)
    }

    fun updateMessageStatus(messageId: String, status: String, error: String?) {
        q.updateIdeaTopicMessageStatus(status = status, error = error, id = messageId)
    }

    fun updateSessionSummary(
        sessionId: String,
        summary: String,
        throughMessageId: String?,
        coveredMessageIds: List<String>,
        status: String,
        now: Long,
    ) {
        q.updateIdeaTopicSessionSummary(
            summary = summary,
            summary_through_message_id = throughMessageId,
            summary_covered_message_ids = json.encodeToString(
                stringListSerializer,
                coveredMessageIds,
            ),
            summary_status = status,
            updated_at = now,
            id = sessionId,
        )
    }

    fun updateSessionSummaryStatus(sessionId: String, status: String, now: Long) {
        q.updateIdeaTopicSessionSummaryStatus(summary_status = status, updated_at = now, id = sessionId)
    }

    fun updateSessionTitle(sessionId: String, title: String, now: Long) {
        q.updateIdeaTopicSessionTitle(title = title, updated_at = now, id = sessionId)
    }

    // ---------- Drafts (append-only events) ----------

    fun insertDraft(
        draftId: String,
        topicId: String,
        originalTopicId: String,
        baseRevision: Long,
        content: IdeaTopicContent,
        referenceIds: List<String>,
        originSessionId: String?,
        now: Long,
    ) {
        q.insertIdeaTopicEvent(
            id = draftId,
            topic_id = topicId,
            original_topic_id = originalTopicId,
            kind = IdeaEventKinds.DraftProposed,
            payload_json = json.encodeToString(
                IdeaDraftEventPayload.serializer(),
                IdeaDraftEventPayload(draftId, baseRevision, content, referenceIds, originSessionId),
            ),
            created_at = now,
        )
    }

    /** Returns the pre-apply content, or null when the draft was already applied (idempotent). */
    fun applyDraft(
        draftId: String,
        mainTopicId: String,
        editedContent: IdeaTopicContent,
        eventId: String,
        now: Long,
    ): IdeaTopicContent? = q.transactionWithResult {
        val draftEvent = q.selectIdeaTopicEventById(draftId).executeAsOneOrNull()
            ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        if (draftEvent.kind != IdeaEventKinds.DraftProposed) throw IdeaTopicException(IdeaTopicError.StaleDraft)
        val owner = resolveMainTopicIdSync(draftEvent.topic_id) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        if (owner != mainTopicId) throw IdeaTopicException(IdeaTopicError.StaleDraft)
        val topic = q.selectIdeaTopicById(owner).executeAsOneOrNull()
            ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        when (draftResolutionState(draftId, owner)) {
            IdeaDraftState.Applied -> return@transactionWithResult null
            IdeaDraftState.Discarded -> throw IdeaTopicException(IdeaTopicError.StaleDraft)
            else -> Unit
        }
        val payload = decodeOrNull<IdeaDraftEventPayload>(draftEvent.payload_json)
            ?: throw IdeaTopicException(IdeaTopicError.StorageFailure)
        if (payload.baseRevision != topic.context_revision) throw IdeaTopicException(IdeaTopicError.StaleDraft)
        val before = topic.toContent()
        q.updateIdeaTopicContent(
            title = editedContent.title,
            description = editedContent.description,
            provenance_summary = editedContent.provenanceSummary,
            conclusions = editedContent.conclusions,
            next_action = editedContent.nextAction,
            context_revision = topic.context_revision + 1,
            updated_at = now,
            id = owner,
        )
        q.insertIdeaTopicEvent(
            id = eventId,
            topic_id = owner,
            original_topic_id = topic.originalOwnerId(),
            kind = IdeaEventKinds.DraftApplied,
            payload_json = json.encodeToString(
                IdeaDraftResolvedEventPayload.serializer(),
                IdeaDraftResolvedEventPayload(draftId, before, editedContent),
            ),
            created_at = now,
        )
        before
    }

    /** Returns true when this call discarded the draft, false when it was already resolved. */
    fun discardDraft(draftId: String, eventId: String, now: Long): Boolean = q.transactionWithResult {
        val draftEvent = q.selectIdeaTopicEventById(draftId).executeAsOneOrNull()
            ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        if (draftEvent.kind != IdeaEventKinds.DraftProposed) throw IdeaTopicException(IdeaTopicError.InvalidInput)
        val owner = resolveMainTopicIdSync(draftEvent.topic_id) ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        if (draftResolutionState(draftId, owner) != null) return@transactionWithResult false
        val topic = q.selectIdeaTopicById(owner).executeAsOneOrNull()
            ?: throw IdeaTopicException(IdeaTopicError.NotFound)
        q.insertIdeaTopicEvent(
            id = eventId,
            topic_id = owner,
            original_topic_id = topic.originalOwnerId(),
            kind = IdeaEventKinds.DraftDiscarded,
            payload_json = json.encodeToString(
                IdeaDraftResolvedEventPayload.serializer(),
                IdeaDraftResolvedEventPayload(draftId),
            ),
            created_at = now,
        )
        true
    }

    fun findDraftSync(draftId: String): IdeaTopicDraft? {
        val draftEvent = q.selectIdeaTopicEventById(draftId).executeAsOneOrNull() ?: return null
        if (draftEvent.kind != IdeaEventKinds.DraftProposed) return null
        val owner = resolveMainTopicIdSync(draftEvent.topic_id) ?: return null
        return draftsByTopicSync(owner).firstOrNull { it.id == draftId }
    }

    private fun draftResolutionState(draftId: String, ownerTopicId: String): String? {
        val events = q.selectIdeaTopicEventsByTopic(ownerTopicId).executeAsList()
        val resolution = events.firstOrNull { event ->
            (event.kind == IdeaEventKinds.DraftApplied || event.kind == IdeaEventKinds.DraftDiscarded) &&
                decodeOrNull<IdeaDraftResolvedEventPayload>(event.payload_json)?.draftId == draftId
        } ?: return null
        return if (resolution.kind == IdeaEventKinds.DraftApplied) IdeaDraftState.Applied else IdeaDraftState.Discarded
    }

    /** Appends an AI failure marker to the topic timeline. */
    fun recordEvent(
        eventId: String,
        topicId: String,
        kind: String,
        payloadJson: String,
        now: Long,
    ) {
        val row = q.selectIdeaTopicById(topicId).executeAsOneOrNull() ?: return
        q.insertIdeaTopicEvent(
            id = eventId,
            topic_id = topicId,
            original_topic_id = row.originalOwnerId(),
            kind = kind,
            payload_json = payloadJson,
            created_at = now,
        )
    }

    // ---------- Internals ----------

    private fun bumpRevisionInternal(topicId: String, now: Long) {
        val row = q.selectIdeaTopicById(topicId).executeAsOneOrNull() ?: return
        q.bumpIdeaTopicRevision(context_revision = row.context_revision + 1, updated_at = now, id = topicId)
    }

    private fun toDetail(
        topic: Idea_topic,
        sources: List<Idea_topic_source>,
        events: List<Idea_topic_event>,
        sessions: List<Idea_topic_session>,
    ): IdeaTopicDetail {
        val latestProgress = events
            .filter { it.kind == IdeaEventKinds.Progress }
            .maxWithOrNull(compareBy<Idea_topic_event>({ it.created_at }, { it.id }))
            ?.let { decodeProgressText(it.payload_json) }
        return IdeaTopicDetail(
            topic = IdeaTopicSummary(
                id = topic.id,
                content = topic.toContent(),
                status = IdeaTopicStatus.fromStorage(topic.status),
                latestProgress = latestProgress,
                sourceCount = sources.size.toLong(),
                updatedAt = topic.updated_at,
            ),
            contextRevision = topic.context_revision,
            sources = sources.map(::toSource),
            events = events.map(::toEvent),
            sessions = sessions.map(::toSession),
        )
    }

    private fun Idea_topic.toContent() = IdeaTopicContent(
        title = title,
        description = description,
        provenanceSummary = provenance_summary,
        conclusions = conclusions,
        nextAction = next_action,
    )

    private fun Idea_topic.originalOwnerId(): String = id

    private fun toSource(row: Idea_topic_source) = IdeaTopicSource(
        id = row.id,
        topicId = row.topic_id,
        originalTopicId = row.original_topic_id,
        snapshot = decodeOrNull<IdeaSourceSnapshot>(row.snapshot_json) ?: emptySnapshot(row),
        capturedAt = row.captured_at,
    )

    private fun emptySnapshot(row: Idea_topic_source) = IdeaSourceSnapshot(
        key = IdeaSourceKey(row.source_type, row.source_record_id),
        originalTitle = "",
        originalContent = "",
    )

    private fun toEvent(row: Idea_topic_event) = IdeaTopicEvent(
        id = row.id,
        topicId = row.topic_id,
        originalTopicId = row.original_topic_id,
        kind = row.kind,
        payload = row.payload_json,
        createdAt = row.created_at,
    )

    private fun toSession(row: Idea_topic_session) = IdeaTopicSession(
        id = row.id,
        topicId = row.topic_id,
        originalTopicId = row.original_topic_id,
        title = row.title,
        summary = row.summary,
        summaryThroughMessageId = row.summary_through_message_id,
        summaryCoveredMessageIds = decodeStringList(row.summary_covered_message_ids),
        summaryStatus = row.summary_status,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )

    private fun toMessage(row: Idea_topic_message) = IdeaTopicMessage(
        id = row.id,
        sessionId = row.session_id,
        role = row.role,
        content = row.content,
        status = row.status,
        error = row.error,
        createdAt = row.created_at,
    )

    private fun decodeProgressText(payloadJson: String): String? =
        decodeOrNull<IdeaProgressEventPayload>(payloadJson)?.text?.takeIf { it.isNotBlank() }

    private fun decodeStringList(raw: String): List<String> = try {
        json.decodeFromString(stringListSerializer, raw)
    } catch (_: Exception) {
        emptyList()
    }

    private inline fun <reified T> decodeOrNull(raw: String): T? = try {
        json.decodeFromString(raw)
    } catch (_: Exception) {
        null
    }

    private val messageOrder = compareBy<Idea_topic_message>({ it.created_at }, { it.id })
}
