package com.dailysatori.service.diagnostics

import io.ktor.http.Url

/** Allow-list projection, not a best-effort replacement of secrets in arbitrary text. */
object DiagnosticRedactor {
    private val numeric = setOf("status", "durationMs", "bytes", "count", "attempt", "reason", "exitTimestampMs", "sdk", "versionCode", "firstChunkMs", "inputTokens", "outputTokens")
    private val methods = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
    private val providers = setOf("openai", "anthropic", "gemini")
    private val publicModels = setOf("gpt-4o", "gpt-4o-mini", "gpt-5", "gpt-5-mini", "gpt-5.1", "gpt-5.2",
        "claude-sonnet-4-5", "claude-opus-4-5", "gemini-2.5-pro", "gemini-2.5-flash")
    private val tools = setOf("get_latest_diary", "get_diary_by_date", "search_diary_by_content", "get_diary_by_tag",
        "get_diary_count", "get_latest_articles", "search_articles", "get_favorite_articles", "get_article_count",
        "get_latest_books", "search_books", "search_book_notes", "get_book_viewpoints", "get_book_count", "get_statistics",
        "query_local_database", "search_web_with_mcp", "search_memory", "get_memory_source", "create_reminder_draft")
    private val routes = listOf("/chat/completions", "/messages", "/models", "/responses", "/graphql")
    private val hosts = setOf("api.github.com", "api.x.com", "api.twitter.com", "api.openai.com", "api.anthropic.com", "generativelanguage.googleapis.com")

    fun fields(input: Map<String, String>): Map<String, String> = buildMap {
        input.forEach { (key, value) ->
            when {
                key in numeric -> value.toLongOrNull()?.takeIf { it >= 0 }?.let { put(key, it.toString()) }
                key == "method" && value in methods -> put(key, value)
                key == "provider" && value.lowercase() in providers -> put(key, value.lowercase())
                key == "url" -> putAll(endpoint(value))
                key == "model" -> put(key, value.takeIf { it in publicModels } ?: "custom-${DiagnosticLog.fingerprint(value)}")
                key == "tool" -> put(key, value.takeIf { it in tools } ?: "custom-${DiagnosticLog.fingerprint(value)}")
                key in setOf("foreground", "permission.microphone", "permission.notifications") && value in setOf("true", "false") -> put(key, value)
                key == "screen" && value in setOf("MainActivity", "ShareReceiverActivity") -> put(key, value)
                key == "network" && value in setOf("wifi", "cellular", "ethernet", "other", "none") -> put(key, value)
            }
        }
    }

    fun endpoint(raw: String): Map<String, String> = runCatching {
        val url = Url(raw)
        val host = url.host.lowercase()
        val service = if (host in hosts) host else "custom-${DiagnosticLog.fingerprint(host + ":" + url.port)}"
        val route = routes.firstOrNull { url.encodedPath.endsWith(it) } ?: "[dynamic-path-omitted]"
        mapOf("service" to service, "route" to route)
    }.getOrDefault(mapOf("service" to "unknown", "route" to "[omitted]"))
}
