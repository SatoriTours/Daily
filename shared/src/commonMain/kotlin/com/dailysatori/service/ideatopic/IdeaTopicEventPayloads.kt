package com.dailysatori.service.ideatopic

import kotlinx.serialization.Serializable

/** Event payloads are append-only structured records; IDs are stable and never rewritten. */

@Serializable
data class IdeaCapturedEventPayload(
    val snapshot: IdeaSourceSnapshot,
    val initialContent: IdeaTopicContent,
)

@Serializable
data class IdeaContentUpdatedEventPayload(
    val before: IdeaTopicContent,
    val after: IdeaTopicContent,
)

@Serializable
data class IdeaStatusChangedEventPayload(
    val from: String,
    val to: String,
)

@Serializable
data class IdeaProgressEventPayload(
    val text: String,
)

@Serializable
data class IdeaMergedEventPayload(
    val fromTopicId: String,
    val fromTitle: String,
    val fromContent: IdeaTopicContent,
    val intoTopicId: String,
    val intoTitle: String,
    val intoContent: IdeaTopicContent,
    val duplicateSourceSnapshots: List<IdeaSourceSnapshot> = emptyList(),
)

@Serializable
data class IdeaDraftEventPayload(
    val draftId: String,
    val baseRevision: Long,
    val content: IdeaTopicContent,
    val referenceIds: List<String> = emptyList(),
    val originSessionId: String? = null,
)

@Serializable
data class IdeaDraftResolvedEventPayload(
    val draftId: String,
    val before: IdeaTopicContent? = null,
    val after: IdeaTopicContent? = null,
)

@Serializable
data class IdeaAiRequestFailedEventPayload(
    val sessionId: String? = null,
    val reason: String = "",
)
