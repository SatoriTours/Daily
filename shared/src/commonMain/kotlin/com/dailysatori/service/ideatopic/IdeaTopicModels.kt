package com.dailysatori.service.ideatopic

import kotlinx.serialization.Serializable

/** Business status of a topic. Merging and deletion are lifecycle operations, not statuses. */
enum class IdeaTopicStatus(val storageValue: String) {
    PendingResearch("pending_research"),
    Researching("researching"),
    Advancing("advancing"),
    Completed("completed");

    companion object {
        fun fromStorage(value: String): IdeaTopicStatus =
            entries.firstOrNull { it.storageValue == value } ?: PendingResearch
    }
}

object IdeaSourceTypes {
    const val Diary = "diary"
    const val NewsOpportunity = "news_opportunity"
}

object IdeaEventKinds {
    const val Captured = "captured"
    const val ContentUpdated = "content_updated"
    const val StatusChanged = "status_changed"
    const val Progress = "progress"
    const val Merged = "merged"
    const val DraftProposed = "draft_proposed"
    const val DraftApplied = "draft_applied"
    const val DraftDiscarded = "draft_discarded"
    const val AiRequestFailed = "ai_request_failed"
}

object IdeaMessageRoles {
    const val User = "user"
    const val Assistant = "assistant"
}

object IdeaMessageStatus {
    const val Pending = "pending"
    const val Complete = "complete"
    const val Failed = "failed"
    const val Interrupted = "interrupted"
}

object IdeaDraftState {
    const val Pending = "pending"
    const val Applied = "applied"
    const val Discarded = "discarded"
    const val Stale = "stale"
}

object IdeaSessionSummaryStatus {
    const val None = "none"
    const val Ready = "ready"
    const val Pending = "pending"
    const val Failed = "failed"
    const val NeedsUpdate = "needs_update"
}

/** Stable business key of an original entry. Diary IDs and opportunity IDs never collide across types. */
@Serializable
data class IdeaSourceKey(
    val type: String,
    val recordId: String,
)

/**
 * Snapshot of one captured entry taken at capture time. Later edits, deletions or
 * re-analysis of the original record never change it.
 */
@Serializable
data class IdeaSourceSnapshot(
    val key: IdeaSourceKey,
    val originalTitle: String,
    val originalContent: String,
    val originalCreatedAt: Long? = null,
    val originalRecordId: String? = null,
    val originalUrl: String? = null,
    val analysisId: String? = null,
    val analysisContent: String? = null,
    val analysisCreatedAt: Long? = null,
    val analysisVersion: String? = null,
)

@Serializable
data class IdeaTopicContent(
    val title: String = "",
    val description: String = "",
    val provenanceSummary: String = "",
    val conclusions: String = "",
    val nextAction: String = "",
)

data class IdeaCaptureInput(
    val source: IdeaSourceSnapshot,
    val content: IdeaTopicContent,
    val targetTopicId: String? = null,
)

data class IdeaCaptureResult(
    val topicId: String,
    val alreadyCaptured: Boolean,
)

data class IdeaTopicSummary(
    val id: String,
    val content: IdeaTopicContent,
    val status: IdeaTopicStatus,
    val latestProgress: String?,
    val sourceCount: Long,
    val updatedAt: Long,
)

data class IdeaTopicSource(
    val id: String,
    val topicId: String,
    val originalTopicId: String,
    val snapshot: IdeaSourceSnapshot,
    val capturedAt: Long,
)

data class IdeaTopicEvent(
    val id: String,
    val topicId: String,
    val originalTopicId: String,
    val kind: String,
    val payload: String,
    val createdAt: Long,
)

data class IdeaTopicSession(
    val id: String,
    val topicId: String,
    val originalTopicId: String,
    val title: String,
    val summary: String,
    val summaryThroughMessageId: String?,
    val summaryCoveredMessageIds: List<String>,
    val summaryStatus: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class IdeaTopicMessage(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val status: String,
    val error: String?,
    val createdAt: Long,
)

data class IdeaTopicDetail(
    val topic: IdeaTopicSummary,
    val contextRevision: Long,
    val sources: List<IdeaTopicSource>,
    val events: List<IdeaTopicEvent>,
    val sessions: List<IdeaTopicSession>,
)

@Serializable
data class IdeaDraftContent(
    val content: IdeaTopicContent,
    val referenceIds: List<String> = emptyList(),
)

data class IdeaTopicDraft(
    val id: String,
    val topicId: String,
    val baseRevision: Long,
    val proposal: IdeaDraftContent,
    val state: String,
    val createdAt: Long,
    val resolvedAt: Long? = null,
)

/** Context handed to the AI port. Sources and history are data, never instructions. */
data class IdeaAiContext(
    val topicId: String,
    val sessionId: String?,
    val revision: Long,
    val systemPrompt: String,
    val messages: List<IdeaTopicMessage>,
    val userPrompt: String,
    val allowedReferenceIds: Set<String>,
)

enum class IdeaTopicError {
    NotFound,
    InvalidInput,
    AlreadyMerged,
    MergeCycle,
    Busy,
    StaleDraft,
    InvalidAiResponse,
    AiNotConfigured,
    InputTooLong,
    StorageFailure,
}

class IdeaTopicException(val code: IdeaTopicError) : Exception(code.name)
