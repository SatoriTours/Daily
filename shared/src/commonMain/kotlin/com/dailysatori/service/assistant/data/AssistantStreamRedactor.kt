package com.dailysatori.service.assistant.data

/**
 * Releases only a stable, sanitized prefix. A configured secret may cross any delta boundary;
 * URL user-info/query material is held until the complete URL token is known.
 */
class AssistantStreamRedactor internal constructor(
    private val scrub: (String) -> String,
    longestSecret: Int,
) {
    private val guard = maxOf(longestSecret, 32)
    private val pendingUrl = Regex("https?://[^\\s<>\"']*$", RegexOption.IGNORE_CASE)

    fun prefix(raw: String, complete: Boolean = false): String {
        require(raw.length <= 200_000) { "Assistant output exceeds the host budget" }
        val safe = scrub(raw)
        if (complete) return safe
        var end = (safe.length - guard).coerceAtLeast(0)
        pendingUrl.find(raw)?.let { url -> end = minOf(end, scrub(raw.take(url.range.first)).length) }
        // Never expose half of a UTF-16 surrogate pair.
        if (end > 0 && safe[end - 1].isHighSurrogate()) end--
        return safe.take(end)
    }
}
