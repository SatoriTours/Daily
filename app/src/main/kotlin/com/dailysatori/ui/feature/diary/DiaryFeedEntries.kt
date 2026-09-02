package com.dailysatori.ui.feature.diary

import com.dailysatori.core.util.diaryDayKey
import com.dailysatori.core.util.diaryMonthKey
import com.dailysatori.shared.db.Diary

internal data class DiaryFeedEntry(
    val diary: Diary,
    val monthKey: String,
    val showMonthHeader: Boolean,
    val showDateHeader: Boolean,
    val dayDiaryCount: Int,
    val monthDiaries: List<Diary>?,
)

internal fun buildDiaryFeedEntries(diaries: List<Diary>): List<DiaryFeedEntry> {
    if (diaries.isEmpty()) return emptyList()
    val monthKeys = diaries.map(::diaryMonthKey)
    val dayKeys = diaries.map(::diaryDayKey)
    val dayCounts = mutableMapOf<String, Int>()
    val diariesByMonth = mutableMapOf<String, MutableList<Diary>>()
    diaries.forEachIndexed { index, diary ->
        dayCounts[dayKeys[index]] = dayCounts.getOrDefault(dayKeys[index], 0) + 1
        diariesByMonth.getOrPut(monthKeys[index], ::mutableListOf).add(diary)
    }

    return diaries.mapIndexed { index, diary ->
        val monthKey = monthKeys[index]
        val showMonthHeader = index == 0 || monthKeys[index - 1] != monthKey
        DiaryFeedEntry(
            diary = diary,
            monthKey = monthKey,
            showMonthHeader = showMonthHeader,
            showDateHeader = index == 0 || dayKeys[index - 1] != dayKeys[index],
            dayDiaryCount = dayCounts.getValue(dayKeys[index]),
            monthDiaries = diariesByMonth.getValue(monthKey).takeIf { showMonthHeader },
        )
    }
}
