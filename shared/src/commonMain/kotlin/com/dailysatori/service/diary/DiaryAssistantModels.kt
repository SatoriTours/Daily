package com.dailysatori.service.diary

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
    '.', ',', '!', '?', ';', ':',
    '。', '，', '！', '？', '；', '：', '、',
    '）', ')', '］', ']', '】', '}', '〉', '》', '」', '』', '”', '’',
)

/** Finds the first HTTP(S) URL in text and removes sentence punctuation appended to it. */
fun detectDiaryAssistantUrl(text: String): String? =
    diaryAssistantUrlPattern.find(text)?.value?.let(::normalizeDiaryAssistantUrl)

/** Removes punctuation that belongs to the sentence around a URL, not its path. */
fun normalizeDiaryAssistantUrl(url: String): String =
    url.trimEnd { it in diaryAssistantSentenceEnding }

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
    val host = url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
        .substringBefore(':').lowercase()
    return if (host == "douyin.com" || host.endsWith(".douyin.com") ||
        host == "iesdouyin.com" || host.endsWith(".iesdouyin.com")
    ) DiaryAssistantTarget.DOUYIN else DiaryAssistantTarget.WEBPAGE
}
