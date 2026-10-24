package com.dailysatori.service.assistant.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlinx.serialization.json.*

/** Only trusted host code reads credential values, solely to irreversibly remove them from model data. */
internal class AssistantCredentialRedactor(private val driver: SqlDriver) {
    fun snapshot(): (String) -> String = scrubber(credentials())

    fun stream(): AssistantStreamRedactor {
        val secrets = credentials()
        return AssistantStreamRedactor(scrubber(secrets), secrets.maxOfOrNull(String::length) ?: 0)
    }

    private fun credentials(): List<String> {
        val secrets = linkedSetOf<String>()
        listOf("ai_config" to "api_token", "mcp_server" to "api_key", "skill_config" to "api_token",
            "remote_news_source" to "api_token").forEach { (table, column) ->
            strings("SELECT $column FROM $table").filter(String::isNotBlank).forEach { secrets += it }
        }
        strings("SELECT auth_json FROM external_favorite_source").forEach { collectJson(it, secrets, false) }
        strings("SELECT config_json FROM mcp_server").forEach { collectJson(it, secrets, false) }
        driver.executeQuery(null, "SELECT key, value FROM setting", { cursor ->
            while (cursor.next().value) {
                val key = cursor.getString(0).orEmpty()
                val value = cursor.getString(1).orEmpty()
                if (sensitive(key)) {
                    if (value.startsWith("{") || value.startsWith("[")) collectJson(value, secrets, false)
                    else if (value.isNotBlank()) secrets += value
                } else if (key.contains("speech", true)) collectJson(value, secrets, false)
            }
            QueryResult.Value(Unit)
        }, 0).value
        return secrets.filter(String::isNotEmpty).sortedByDescending(String::length)
    }

    private fun scrubber(ordered: List<String>): (String) -> String = { text ->
        val withoutKnown = ordered.fold(text) { result, secret -> result.replace(secret, "[已隐藏凭据]") }
        redactPatterns(withoutKnown)
    }

    private fun strings(sql: String): List<String> = driver.executeQuery(null, sql, { cursor ->
        val rows = mutableListOf<String>()
        while (cursor.next().value) cursor.getString(0)?.let(rows::add)
        QueryResult.Value(rows)
    }, 0).value

    private fun collectJson(value: String, secrets: MutableSet<String>, inherited: Boolean) {
        val parsed = runCatching { Json.parseToJsonElement(value) }.getOrNull() ?: return
        fun visit(node: JsonElement, secret: Boolean) {
            when (node) {
                is JsonObject -> node.forEach { (key, child) -> visit(child, secret || sensitive(key)) }
                is JsonArray -> node.forEach { visit(it, secret) }
                is JsonPrimitive -> if (secret && node.isString && node.content.isNotBlank()) secrets += node.content
            }
        }
        visit(parsed, inherited)
    }

    private fun sensitive(key: String): Boolean {
        val normalized = key.lowercase().filter(Char::isLetterOrDigit)
        return listOf("token", "secret", "password", "passwd", "apikey", "authorization", "cookie", "credential", "privatekey")
            .any(normalized::contains) || normalized in setOf("auth", "key", "bearer", "sessionid")
    }

    private fun redactPatterns(text: String): String {
        var result = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE).replace(text) { match ->
            // Opaque query strings/fragments are not safe projections, even for unknown parameter names.
            match.value.substringBefore('?').substringBefore('#')
                .replace(Regex("(?<=://)[^/]*@"), "[已隐藏凭据]@")
        }
        result = Regex("(?i)\\b(?:bearer|authorization|cookie|api[_-]?key|access[_-]?token|refresh[_-]?token|password|secret)\\s*[:=]?\\s*[^\\s,;]+")
            .replace(result, "[已隐藏凭据]")
        return result
    }
}
