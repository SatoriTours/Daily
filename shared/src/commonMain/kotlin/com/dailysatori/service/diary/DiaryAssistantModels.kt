package com.dailysatori.service.diary

import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom

data class DiaryAssistantRequest(
    val selectedText: String,
    val contextBefore: String = "",
    val contextAfter: String = "",
    val url: String? = null,
    val allowModelKnowledgeFallback: Boolean = false,
)

data class DiaryAssistantSource(val title: String, val url: String)

enum class DiaryAssistantVerification { WEB_VERIFIED, MODEL_ONLY, PAGE_EXTRACTED }

enum class DiaryAssistantExtraction { FULL_TEXT, PUBLIC_METADATA, NO_SUBTITLES }

enum class DiaryAssistantTarget { KNOWLEDGE, WEBPAGE, DOUYIN }

data class DiaryAssistantResult(
    val content: String,
    val sources: List<DiaryAssistantSource>,
    val verification: DiaryAssistantVerification,
    val extraction: DiaryAssistantExtraction? = null,
    val warnings: List<String> = emptyList(),
)

private val diaryAssistantUrlPattern = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
private val diaryAssistantSentenceEnding = setOf(
    '。', '，', '！', '？', '；', '：', '、',
)

internal data class DiaryAssistantHttpUrl(
    val value: String,
    val host: String,
)

/** Finds the first HTTP(S) URL in text and removes sentence punctuation appended to it. */
fun detectDiaryAssistantUrl(text: String): String? =
    detectDiaryAssistantUrls(text).firstOrNull()

fun detectDiaryAssistantUrls(text: String): List<String> = diaryAssistantUrlPattern.findAll(text)
    .mapNotNull { canonicalDiaryAssistantUrl(it.value) }
    .toList()

fun canonicalDiaryAssistantUrl(url: String): String? = parseDiaryAssistantHttpUrl(url)?.value

/** Removes Chinese sentence punctuation, while preserving ASCII URL path/delimiter characters. */
fun normalizeDiaryAssistantUrl(url: String): String =
    url.trimEnd { it in diaryAssistantSentenceEnding }

internal fun parseDiaryAssistantHttpUrl(rawUrl: String): DiaryAssistantHttpUrl? {
    if (rawUrl.isEmpty() || '\\' in rawUrl || rawUrl.any { it.isWhitespace() || it.isISOControl() }) return null
    val normalized = normalizeDiaryAssistantUrl(rawUrl)
    val authority = normalized.substringAfter("://", missingDelimiterValue = "")
        .substringBefore('/').substringBefore('?').substringBefore('#')
    if (authority.isBlank() || '@' in authority) return null
    val parsed = runCatching { URLBuilder().takeFrom(normalized).build() }.getOrNull() ?: return null
    if (parsed.protocol.name !in setOf("http", "https") || parsed.port !in 1..65535) return null
    if (!parsed.host.isValidDiaryAssistantHost()) return null
    val encoded = parsed.toString().replace("<", "%3C").replace(">", "%3E")
    return DiaryAssistantHttpUrl(encoded, parsed.host.lowercase())
}

private fun String.isValidDiaryAssistantHost(): Boolean {
    if (isBlank() || startsWith('.') || endsWith('.') || contains(',')) return false
    if (contains(':')) return all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' }
    return split('.').all { label ->
        label.isNotBlank() &&
            !label.startsWith('-') &&
            !label.endsWith('-') &&
            label.all { it.isLetterOrDigit() || it == '-' }
    }
}

/** Returns at most 240 characters immediately before and after the selected range. */
fun boundedDiaryAssistantContext(text: String, selection: IntRange): Pair<String, String> {
    val start = selection.first.coerceIn(0, text.length)
    val end = (selection.last + 1).coerceIn(start, text.length)
    val beforeStart = (start - 240).coerceAtLeast(0)
    val afterEnd = (end + 240).coerceAtMost(text.length)
    return text.substring(beforeStart, start) to text.substring(end, afterEnd)
}

fun diaryAssistantTarget(url: String?): DiaryAssistantTarget {
    if (url == null) return DiaryAssistantTarget.KNOWLEDGE
    val host = parseDiaryAssistantHttpUrl(url)?.host ?: return DiaryAssistantTarget.WEBPAGE
    return if (host == "douyin.com" || host.endsWith(".douyin.com") ||
        host == "iesdouyin.com" || host.endsWith(".iesdouyin.com")
    ) DiaryAssistantTarget.DOUYIN else DiaryAssistantTarget.WEBPAGE
}
