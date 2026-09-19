package com.dailysatori.ui.feature.diary

import com.dailysatori.service.diary.DiaryThought

internal data class DiaryThoughtSection(val category: String, val title: String, val thoughts: List<DiaryThought>)

internal data class DiaryThoughtPresentation(
    val highlights: List<DiaryThought>,
    val sections: List<DiaryThoughtSection>,
)

internal fun diaryThoughtPresentation(thoughts: List<DiaryThought>): DiaryThoughtPresentation {
    val titles = linkedMapOf(
        "价值观" to "我看重什么", "做事准则" to "做事准则",
        "思维方式" to "思考方式", "变化与探索" to "变化与探索",
    )
    val ranked = thoughts.sortedByDescending { thought -> thought.evidence.map { it.diaryId }.distinct().size }
    val representatives = ranked.distinctBy { it.category }.take(3)
    val highlights = (representatives + ranked.filterNot { it in representatives }).take(3)
    val sections = thoughts.groupBy { it.category }.entries
        .sortedBy { titles.keys.indexOf(it.key).takeIf { index -> index >= 0 } ?: titles.size }
        .map { (category, entries) -> DiaryThoughtSection(category, titles[category] ?: category, entries) }
    return DiaryThoughtPresentation(highlights, sections)
}
