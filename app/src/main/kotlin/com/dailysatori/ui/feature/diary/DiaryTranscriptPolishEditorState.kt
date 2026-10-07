package com.dailysatori.ui.feature.diary

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.dailysatori.service.diary.DiaryPolishedTranscript

/** Only whole, uniquely identifiable recording blocks may be located automatically. */
internal fun diaryTranscriptPolishSnapshot(
    current: TextFieldValue,
    original: String,
    applied: DiaryPolishedTranscript?,
): DiaryAssistantSelectionSnapshot {
    val candidates = listOfNotNull(applied?.takeIf { it.original == original }?.content, original.trim())
        .filter(String::isNotBlank).distinct()
    val matches = candidates.flatMap { candidate ->
        val ranges = mutableListOf<TextRange>()
        var start = current.text.indexOf(candidate)
        while (start >= 0) {
            val end = start + candidate.length
            if ((start == 0 || current.text[start - 1] == '\n') &&
                (end == current.text.length || current.text[end] == '\n')) {
                ranges += TextRange(start, end)
            }
            start = current.text.indexOf(candidate, start + 1)
        }
        ranges
    }.distinct()
    // A user selection is explicit consent to target a manually edited or ambiguous block.
    val selection = if (!current.selection.collapsed) current.selection
        else matches.singleOrNull() ?: TextRange.Zero
    val start = selection.min.coerceIn(0, current.text.length)
    val end = selection.max.coerceIn(start, current.text.length)
    return DiaryAssistantSelectionSnapshot(current.text, TextRange(start, end), current.text.substring(start, end))
}

/** Body undo restores the locator, not the user's later feedback or adopted history. */
internal fun restoreDiaryPolishHistory(
    current: Map<Long, DiaryPolishedTranscript>,
    restored: Map<Long, DiaryPolishedTranscript>,
): Map<Long, DiaryPolishedTranscript> = (current.keys + restored.keys).associateWith { id ->
    val latest = current[id]
    val prior = restored[id]
    when {
        latest == null -> requireNotNull(prior)
        prior == null -> latest.copy(content = latest.original)
        latest.original != prior.original -> prior
        else -> prior.copy(history = (prior.adoptedVersions() + latest.adoptedVersions())
            .associateBy { it.id }.values.sortedBy { it.id })
    }
}
