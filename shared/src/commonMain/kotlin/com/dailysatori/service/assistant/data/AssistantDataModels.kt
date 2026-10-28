package com.dailysatori.service.assistant.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class AssistantFilter(val field: String, val operator: String, val value: JsonPrimitive)

@Serializable
data class AssistantQuery(
    val source: String,
    val fields: List<String> = emptyList(),
    val filters: List<AssistantFilter> = emptyList(),
    val orderBy: String? = null,
    val descending: Boolean = false,
    val limit: Int = 20,
    val offset: Long = 0,
)

@Serializable
data class AssistantAggregate(
    val source: String,
    val filters: List<AssistantFilter> = emptyList(),
    val groupBy: List<String> = emptyList(),
    val measures: List<String> = listOf("count"),
)

/** This list, not SQLite metadata, is the permission boundary. New columns remain closed. */
internal data class AssistantSource(
    val name: String,
    val table: String = name,
    val text: String,
    val numbers: String,
    val search: String = "",
    val content: String? = null,
    val id: String = "id",
    val order: String = "created_at",
    val expressions: Map<String, String> = emptyMap(),
    val parent: Pair<String, String>? = null,
) {
    val numeric = numbers.split(' ').filter(String::isNotBlank).toSet()
    val fields = (text.split(' ') + numeric).filter(String::isNotBlank).toSet()
    val searchable = search.split(' ').filter(String::isNotBlank)
    fun expression(field: String): String {
        require(field in fields)
        return expressions[field] ?: "r.$field"
    }
}

internal val assistantSources = listOf(
    AssistantSource("article", text = "title ai_title content comment status source_type",
        numbers = "id is_favorite pub_date created_at updated_at", search = "title ai_title content comment", content = "content",
        expressions = mapOf("content" to "COALESCE(NULLIF(r.ai_markdown_content,''),NULLIF(r.original_markdown_content,''),r.ai_content,'')")),
    AssistantSource("diary", text = "content tags mood", numbers = "id parent_diary_id created_at updated_at",
        search = "content tags", content = "content",
        expressions = mapOf("tags" to "(SELECT p.tags FROM diary_active_tags p WHERE p.diary_id=r.id)")),
    AssistantSource("book", text = "title author category introduction", numbers = "id created_at updated_at",
        search = "title author introduction", content = "introduction"),
    AssistantSource("book_viewpoint", text = "title content example status source_notes", numbers = "id book_id created_at updated_at",
        search = "title content example", content = "content", parent = "book" to "book_id"),
    AssistantSource("tag", text = "name", numbers = "id created_at updated_at", search = "name", content = "name"),
    AssistantSource("chat", table = "chat_conversation", text = "session_id role content", numbers = "id created_at",
        search = "content", content = "content"),
    AssistantSource("memory_entry", text = "type source_type title content tags assistant_kind evidence", numbers = "id source_id created_at updated_at",
        search = "title content tags", content = "content"),
    AssistantSource("reminder", text = "id content notes status start_date end_date first_reminder_time time_zone_id",
        numbers = "version deadline_at next_occurrence_at created_at updated_at", search = "content notes", content = "content"),
    AssistantSource("ai_config", text = "provider model_name", numbers = "id is_default created_at updated_at"),
    AssistantSource("mcp_server", text = "name provider template_id template_type", numbers = "id enabled created_at updated_at"),
    AssistantSource("skill_config", text = "name description", numbers = "id enabled created_at updated_at"),
    AssistantSource("diary_attachment", text = "kind display_name mime_type transcript transcript_status knowledge_status",
        numbers = "id diary_id size_bytes duration_ms created_at updated_at", search = "transcript", content = "transcript", parent = "diary" to "diary_id"),
    AssistantSource("external_favorite_item", text = "title text author_name canonical_url import_status ai_status",
        numbers = "id article_id source_id source_created_at favorited_at created_at updated_at", search = "title text author_name", content = "text"),
    AssistantSource("external_favorite_source", text = "provider display_name status", numbers = "id enabled last_success_at created_at updated_at"),
    AssistantSource("remote_news_source", text = "name", numbers = "id enabled created_at updated_at"),
    AssistantSource("weekly_summary", text = "content status app_ideas week_start_date week_end_date", numbers = "id article_count diary_count viewpoint_count created_at updated_at",
        search = "content app_ideas", content = "content"),
    AssistantSource("diary_month_summary", text = "month_key summary status", numbers = "id diary_count created_at updated_at", search = "summary", content = "summary"),
    AssistantSource("async_task", text = "type status", numbers = "id progress_current progress_total attempt_count max_attempts created_at updated_at"),
    AssistantSource("idea_topic", text = "id title description provenance_summary conclusions next_action status content",
        numbers = "context_revision created_at updated_at", search = "title content", content = "content",
        expressions = mapOf("content" to "r.description || char(10) || r.provenance_summary || char(10) || r.conclusions || char(10) || r.next_action")) ,
    // Config JSON, URLs and auth material are intentionally not fields, including in safe configuration views.
).associateBy { it.name }
