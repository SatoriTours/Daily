package com.dailysatori.service.diary

data class DiaryTagIndexEntry(val id: Long, val tags: String?, val hasContent: Boolean)

data class DiaryTagEditorSnapshot(val diaryId: Long, val tags: String?, val state: DiaryTagState)

data class DiaryTagCatalog(
    val vocabulary: DiaryTagVocabulary,
    val counts: Map<String, Int>,
    val missingIds: List<Long>,
    val enabled: Boolean,
    val canUndo: Boolean,
)

/** Counts change only for inserted, changed or deleted tag rows. Alias changes reset the caller's baseline. */
internal fun updateDiaryTagCounts(
    previous: Map<Long, DiaryTagIndexEntry>, current: Map<Long, DiaryTagIndexEntry>,
    counts: Map<String, Int>, vocabulary: DiaryTagVocabulary,
): Map<String, Int> {
    val changed = (previous.keys + current.keys).filter { previous[it] != current[it] }
    if (changed.isEmpty()) return counts
    val updated = counts.toMutableMap()
    fun apply(tags: String?, delta: Int) {
        parseDiaryTags(tags).map(vocabulary::canonical).distinct().forEach { tag ->
            val count = (updated[tag] ?: 0) + delta
            if (count <= 0) updated.remove(tag) else updated[tag] = count
        }
    }
    changed.forEach { id ->
        apply(previous[id]?.tags, -1)
        apply(current[id]?.tags, 1)
    }
    return updated.toMap()
}
