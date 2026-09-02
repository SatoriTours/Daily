package com.dailysatori.ui.feature.diary

import com.dailysatori.core.util.diaryDayKey
import com.dailysatori.core.util.diaryMonthKey
import com.dailysatori.shared.db.Diary
import java.util.Calendar
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class DiaryFeedEntriesTest {
    @Test
    fun entriesPreserveOrderAndLegacyHeaderAndCountBehavior() = withDefaultTimeZone("Asia/Hong_Kong") {
        val diaries = listOf(
            diary(5, 2026, Calendar.AUGUST, 15, 20),
            diary(4, 2026, Calendar.AUGUST, 15, 8),
            diary(3, 2026, Calendar.AUGUST, 14, 12),
            diary(2, 2026, Calendar.JULY, 31, 23),
            diary(1, 2026, Calendar.JULY, 1, 9),
        )

        val entries = buildDiaryFeedEntries(diaries)

        assertEquals(diaries.map { it.id }, entries.map { it.diary.id })
        assertEquals(listOf(true, false, false, true, false), entries.map { it.showMonthHeader })
        assertEquals(listOf(true, false, true, true, true), entries.map { it.showDateHeader })
        assertEquals(listOf(2, 2, 1, 1, 1), entries.map { it.dayDiaryCount })
        assertEquals(listOf(5L, 4L, 3L), entries[0].monthDiaries?.map { it.id })
        assertEquals(null, entries[1].monthDiaries)
        assertEquals(listOf(2L, 1L), entries[3].monthDiaries?.map { it.id })
    }

    @Test
    fun entriesMatchTheCurrentAlgorithmWithoutReorderingInput() = withDefaultTimeZone("UTC") {
        val diaries = listOf(
            diary(2, 2026, Calendar.JANUARY, 1, 0),
            diary(1, 2025, Calendar.DECEMBER, 31, 23),
            diary(3, 2026, Calendar.JANUARY, 1, 0),
        )

        val entries = buildDiaryFeedEntries(diaries)

        entries.forEachIndexed { index, entry ->
            val current = diaries[index]
            val monthKey = diaryMonthKey(current)
            val dayKey = diaryDayKey(current)
            assertEquals(current.id, entry.diary.id)
            assertEquals(index == 0 || diaryMonthKey(diaries[index - 1]) != monthKey, entry.showMonthHeader)
            assertEquals(index == 0 || diaryDayKey(diaries[index - 1]) != dayKey, entry.showDateHeader)
            assertEquals(diaries.count { diaryDayKey(it) == dayKey }, entry.dayDiaryCount)
            assertEquals(
                diaries.filter { diaryMonthKey(it) == monthKey }.takeIf { entry.showMonthHeader },
                entry.monthDiaries,
            )
        }
    }

    @Test
    fun emptyInputProducesEmptyEntries() {
        assertEquals(emptyList(), buildDiaryFeedEntries(emptyList()))
    }

    private fun diary(id: Long, year: Int, month: Int, day: Int, hour: Int): Diary {
        val timestamp = Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, 0, 0)
        }.timeInMillis
        return Diary(id, "diary $id", null, null, null, timestamp, timestamp)
    }

    private inline fun withDefaultTimeZone(id: String, block: () -> Unit) {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(id))
            block()
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
