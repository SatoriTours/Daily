package com.dailysatori.ui.feature.diary

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.dailysatori.service.diary.DiaryAssistantRequest
import com.dailysatori.service.diary.DiaryAssistantResult
import com.dailysatori.service.diary.boundedDiaryAssistantContext
import com.dailysatori.service.diary.normalizeDiaryAssistantUrl
import java.util.LinkedHashMap

/** The editor version and selection captured when an assistant request starts. */
data class DiaryAssistantSelectionSnapshot(
    val text: String,
    val selection: TextRange,
    val selectedText: String,
) {
    val normalizedSelection: TextRange
        get() = TextRange(selection.min, selection.max)

    fun toDiaryAssistantRequest(url: String? = null, allowModelKnowledgeFallback: Boolean = false): DiaryAssistantRequest {
        val range = normalizedSelection
        val safeStart = range.start.coerceIn(0, text.length)
        val safeEnd = range.end.coerceIn(safeStart, text.length)
        val actualSelection = text.substring(safeStart, safeEnd)
        val (before, after) = boundedDiaryAssistantContext(text, safeStart until safeEnd)
        return DiaryAssistantRequest(
            selectedText = actualSelection,
            contextBefore = before,
            contextAfter = after,
            url = url,
            allowModelKnowledgeFallback = allowModelKnowledgeFallback,
        )
    }
}

/** Returns a URL present in [after] but absent from [before], without invoking a loader. */
fun detectNewlyPastedDiaryUrl(before: String, after: String): String? {
    if (before == after) return null
    val urlPattern = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
    val existing = urlPattern.findAll(before)
        .map { normalizeDiaryAssistantUrl(it.value) }
        .toSet()
    return urlPattern.findAll(after)
        .map { normalizeDiaryAssistantUrl(it.value) }
        .firstOrNull { it !in existing }
}

fun canReplaceDiaryAssistantSelection(current: TextFieldValue, snapshot: DiaryAssistantSelectionSnapshot): Boolean {
    if (current.text != snapshot.text) return false
    val range = snapshot.normalizedSelection
    if (range.start < 0 || range.end > current.text.length || range.start > range.end) return false
    return current.text.substring(range.start, range.end) == snapshot.selectedText
}

fun insertDiaryAssistantResult(
    current: TextFieldValue,
    snapshot: DiaryAssistantSelectionSnapshot,
    result: String,
): TextFieldValue {
    val range = if (canReplaceDiaryAssistantSelection(current, snapshot)) {
        snapshot.normalizedSelection
    } else {
        TextRange(current.selection.max.coerceIn(0, current.text.length))
    }
    val cleanResult = result.trim()
    if (cleanResult.isEmpty()) return current
    val before = current.text.substring(0, range.end).trimEnd()
    val after = current.text.substring(range.end)
    val separator = if (before.isEmpty() || after.isEmpty()) "\n\n" else "\n\n"
    val text = before + separator + cleanResult + after
    val cursor = (before.length + separator.length + cleanResult.length).coerceIn(0, text.length)
    return TextFieldValue(text = text, selection = TextRange(cursor))
}

fun replaceDiaryAssistantSelection(
    current: TextFieldValue,
    snapshot: DiaryAssistantSelectionSnapshot,
    result: String,
): TextFieldValue {
    if (!canReplaceDiaryAssistantSelection(current, snapshot)) return current
    val range = snapshot.normalizedSelection
    val replacement = result.trim()
    val text = current.text.replaceRange(range.start, range.end, replacement)
    return TextFieldValue(text = text, selection = TextRange(range.start + replacement.length))
}

/** A sheet-scoped, bounded cache. Call [clear] when the sheet leaves composition. */
class DiaryAssistantSessionCache(private val maxEntries: Int = 10) {
    private val entries = LinkedHashMap<String, DiaryAssistantResult>()

    val size: Int get() = entries.size

    fun put(url: String, result: DiaryAssistantResult) {
        if (maxEntries <= 0) return
        val key = cacheKey(url)
        entries.remove(key)
        entries[key] = result
        while (entries.size > maxEntries) entries.entries.iterator().apply { next(); remove() }
    }

    fun clear() = entries.clear()

    operator fun get(url: String): DiaryAssistantResult? = entries[cacheKey(url)]
    operator fun set(url: String, result: DiaryAssistantResult) = put(url, result)

    private fun cacheKey(url: String): String = normalizeDiaryAssistantUrl(url.trim())
}
