package com.dailysatori.ui.feature.ideatopic

import com.dailysatori.service.ideatopic.*
import kotlinx.serialization.json.Json

data class IdeaEventPresentation(
    val title: String,
    val description: String,
    val originalAttribution: String? = null,
    val timeMillis: Long,
)

fun ideaTopicStatusLabelKey(status: IdeaTopicStatus): String = when (status) {
    IdeaTopicStatus.PendingResearch -> "idea_topic.status.to_research"
    IdeaTopicStatus.Researching -> "idea_topic.status.researching"
    IdeaTopicStatus.Advancing -> "idea_topic.status.in_progress"
    IdeaTopicStatus.Completed -> "idea_topic.status.completed"
}

fun ideaTopicErrorLabelKey(error: IdeaTopicError): String = "idea_topic.error." + when (error) {
    IdeaTopicError.NotFound -> "not_found"
    IdeaTopicError.InvalidInput -> "invalid_input"
    IdeaTopicError.AlreadyMerged -> "already_merged"
    IdeaTopicError.MergeCycle -> "merge_cycle"
    IdeaTopicError.Busy -> "busy"
    IdeaTopicError.StaleDraft -> "stale_draft"
    IdeaTopicError.InvalidAiResponse -> "invalid_ai_response"
    IdeaTopicError.AiNotConfigured -> "ai_not_configured"
    IdeaTopicError.InputTooLong -> "input_too_long"
    IdeaTopicError.StorageFailure -> "storage_failure"
}

private val presentationJson = Json { ignoreUnknownKeys = true }

/** One label key per capture source type; unknown future types fall back to the opportunity wording. */
fun ideaSourceTypeLabelKey(type: String): String = when (type) {
    IdeaSourceTypes.Diary -> "source_type_diary"
    IdeaSourceTypes.Article -> "source_type_article"
    else -> "source_type_opportunity"
}

fun formatIdeaTopicEvent(
    event: IdeaTopicEvent,
    currentTopicId: String? = null,
    translate: (String) -> String,
): IdeaEventPresentation {
    val text = IdeaEventText(translate)
    return IdeaEventPresentation(
        title = if (event.kind == IdeaEventKinds.Captured) {
            val type = decode<IdeaCapturedEventPayload>(event)?.snapshot?.key?.type
            text.get("event_capture_title", text.get(ideaSourceTypeLabelKey(type.orEmpty())))
        } else text.get("event_${event.kind}"),
        description = describeEvent(event, text),
        originalAttribution = event.originalTopicId.takeIf { currentTopicId != null && it != currentTopicId }
            ?.let { text.get("event_original_attribution", it) },
        timeMillis = event.createdAt,
    )
}

private class IdeaEventText(private val translate: (String) -> String) {
    fun get(key: String, vararg args: Any?): String =
        String.format(translate("idea_topic.$key"), *args)
}

private fun describeEvent(event: IdeaTopicEvent, text: IdeaEventText): String = when (event.kind) {
    IdeaEventKinds.Captured -> decode<IdeaCapturedEventPayload>(event)?.let {
        val source = text.get(ideaSourceTypeLabelKey(it.snapshot.key.type))
        text.get("event_capture_description", source, it.snapshot.originalTitle.ifBlank { it.initialContent.title })
    } ?: text.get("event_captured")
    IdeaEventKinds.Progress -> decode<IdeaProgressEventPayload>(event)?.text.orEmpty()
    IdeaEventKinds.Merged -> decode<IdeaMergedEventPayload>(event)?.let {
        text.get("event_merge_description", it.fromTitle.ifBlank { it.fromTopicId }, it.intoTitle.ifBlank { it.intoTopicId })
    } ?: text.get("event_merged")
    IdeaEventKinds.ContentUpdated -> decode<IdeaContentUpdatedEventPayload>(event)?.let {
        val fields = changedFields(it).map { key -> text.get(key) }
        if (fields.isEmpty()) text.get("event_content_updated") else text.get("event_changed_fields", fields.joinToString(", "))
    } ?: text.get("event_content_updated")
    IdeaEventKinds.StatusChanged -> decode<IdeaStatusChangedEventPayload>(event)?.let {
        text.get("event_status_description", text.get(ideaTopicStatusLabelKey(IdeaTopicStatus.fromStorage(it.from)).removePrefix("idea_topic.")),
            text.get(ideaTopicStatusLabelKey(IdeaTopicStatus.fromStorage(it.to)).removePrefix("idea_topic.")))
    } ?: text.get("event_status_changed")
    IdeaEventKinds.DraftProposed -> decode<IdeaDraftEventPayload>(event)?.let { text.get("event_suggested_title", it.content.title) }
        ?: text.get("event_draft_proposed")
    IdeaEventKinds.DraftApplied -> decode<IdeaDraftResolvedEventPayload>(event)?.after?.let { text.get("event_new_title", it.title) }
        ?: text.get("event_draft_applied")
    IdeaEventKinds.DraftDiscarded -> text.get("event_draft_discarded")
    IdeaEventKinds.AiRequestFailed -> decode<IdeaAiRequestFailedEventPayload>(event)?.reason?.let { reason ->
        IdeaTopicError.entries.firstOrNull { it.name == reason }?.let { text.get(ideaTopicErrorLabelKey(it).removePrefix("idea_topic.")) }
    } ?: text.get("event_ai_request_failed")
    else -> event.kind
}

private fun changedFields(payload: IdeaContentUpdatedEventPayload): List<String> = buildList {
    if (payload.before.title != payload.after.title) add("overview_title")
    if (payload.before.description != payload.after.description) add("overview_description")
    if (payload.before.provenanceSummary != payload.after.provenanceSummary) add("overview_provenance_summary")
    if (payload.before.conclusions != payload.after.conclusions) add("overview_conclusions")
    if (payload.before.nextAction != payload.after.nextAction) add("overview_next_action")
}

private inline fun <reified T> decode(event: IdeaTopicEvent): T? =
    runCatching { presentationJson.decodeFromString<T>(event.payload) }.getOrNull()
