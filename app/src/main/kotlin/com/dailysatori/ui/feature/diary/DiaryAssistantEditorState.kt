package com.dailysatori.ui.feature.diary

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.dailysatori.service.diary.DiaryAssistantRequest
import com.dailysatori.service.diary.DiaryAssistantResult
import com.dailysatori.service.diary.boundedDiaryAssistantContext
import com.dailysatori.service.diary.canonicalDiaryAssistantUrl
import com.dailysatori.service.diary.detectDiaryAssistantUrls
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
    val existing = detectDiaryAssistantUrls(before)
        .groupingBy { it }
        .eachCount()
    val afterUrls = detectDiaryAssistantUrls(after)
    val afterCounts = afterUrls.groupingBy { it }.eachCount()
    return afterUrls.firstOrNull { (afterCounts[it] ?: 0) > (existing[it] ?: 0) }
}

fun canReplaceDiaryAssistantSelection(current: TextFieldValue, snapshot: DiaryAssistantSelectionSnapshot): Boolean {
    if (current.text != snapshot.text) return false
    val range = snapshot.normalizedSelection
    if (range.collapsed || snapshot.selectedText.isBlank()) return false
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
        TextRange(current.selection.end.coerceIn(0, current.text.length))
    }
    val cleanResult = result.trim()
    if (cleanResult.isEmpty()) return current
    val before = current.text.substring(0, range.end)
    val after = current.text.substring(range.end)
    val separator = "\n\n"
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
        val key = cacheKey(url) ?: return
        entries.remove(key)
        entries[key] = result
        while (entries.size > maxEntries) entries.entries.iterator().apply { next(); remove() }
    }

    fun clear() = entries.clear()

    operator fun get(url: String): DiaryAssistantResult? = cacheKey(url)?.let(entries::get)
    operator fun set(url: String, result: DiaryAssistantResult) = put(url, result)

    fun resultFor(url: String?, forceRefresh: Boolean): DiaryAssistantResult? =
        if (forceRefresh) null else url?.let(::get)

    private fun cacheKey(url: String): String? = canonicalDiaryAssistantUrl(url)
}

/** Invalidates late assistant completions independently of coroutine cancellation cooperation. */
internal class DiaryAssistantRequestGate {
    private var generation = 0L

    fun begin(): Long {
        generation += 1
        return generation
    }

    fun invalidate() {
        generation += 1
    }

    fun isCurrent(requestGeneration: Long): Boolean = requestGeneration == generation
}
