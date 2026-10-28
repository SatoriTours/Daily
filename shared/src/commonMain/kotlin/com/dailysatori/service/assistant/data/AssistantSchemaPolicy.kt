package com.dailysatori.service.assistant.data

import com.dailysatori.service.migration.DatabaseSchemaInventory

/** Host-only exhaustive schema inventory. Never included in model prompts or catalog output. */
enum class AssistantFieldPolicy { READABLE, REDACTED, LOCAL_ONLY, DENIED }

internal object AssistantSchemaPolicy {
    // Explicit snapshot: a schema addition fails the coverage test until it is reviewed here.
    val columns: Map<String, Set<String>> = DatabaseSchemaInventory.columns.filterKeys { it!="diary_record" }
    private val forbiddenTables = setOf("session", "sms_reminder_source", "phone_message", "bookkeeping_entry")
    private val credentials = setOf("api_token", "api_key", "auth_json", "encrypted_payload", "encrypted_source", "config_json", "tool_schema_json", "profile_json")
    fun classify(table: String, field: String): AssistantFieldPolicy {
        if (field !in columns[table].orEmpty() || table in forbiddenTables || field in credentials) return AssistantFieldPolicy.DENIED
        val source = assistantSources.values.firstOrNull { it.table == table } ?: return AssistantFieldPolicy.LOCAL_ONLY
        val projected = field in source.fields || source.expressions.values.any { Regex("\\br\\.$field\\b").containsMatchIn(it) }
        if (!projected) return AssistantFieldPolicy.LOCAL_ONLY
        return if (field in source.numeric) AssistantFieldPolicy.READABLE else AssistantFieldPolicy.REDACTED
    }
}
