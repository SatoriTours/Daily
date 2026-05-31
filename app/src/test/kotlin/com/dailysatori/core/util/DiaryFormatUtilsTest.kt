package com.dailysatori.core.util

import java.util.Calendar
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class DiaryFormatUtilsTest {
    @Test
    fun parsesDiaryTagsAndImagePathsWithExistingNullRules() {
        assertEquals(emptyList(), diaryTags(null))
        assertEquals(emptyList(), diaryTags(""))
        assertEquals(emptyList(), diaryTags("   "))
        assertEquals(emptyList(), diaryTags(" null "))
        assertEquals(listOf("生活", "工作"), diaryTags(" 生活, null, ,工作 "))

        assertEquals(emptyList(), diaryImagePaths(null))
        assertEquals(emptyList(), diaryImagePaths(""))
        assertEquals(emptyList(), diaryImagePaths("   "))
        assertEquals(emptyList(), diaryImagePaths(" null "))
        assertEquals(listOf("a.jpg", "b.png"), diaryImagePaths("a.jpg,, null, b.png"))
    }

    @Test
    fun stripsTrailingInlineTagLinesOnly() {
        val content = "# 标题\n今天很好\n\n#生活 #记录\n#daily"

        assertEquals("# 标题\n今天很好", stripDiaryInlineTags(content))
    }

    @Test
    fun formatsDiaryDateLabelsDeterministically() {
        val time = localMillis(year = 2026, month = 1, day = 15)

        assertEquals("2026-01", diaryMonthKey(time))
        assertEquals("2026-01-15", diaryDayKey(time))
        assertEquals("一月", diaryDateMonthLabel(time))
        assertEquals("15", diaryDateDayNumber(time))
        assertEquals("周四", diaryDateWeekLabel(time))
        assertEquals("1 月 15 日", diaryMonthDayLabel(time))
    }

    @Test
    fun labelsRelativeDaysFromSuppliedNow() {
        val now = localMillis(year = 2026, month = 1, day = 15)
        val yesterday = localMillis(year = 2026, month = 1, day = 14)
        val beforeYesterday = localMillis(year = 2026, month = 1, day = 13)
        val older = localMillis(year = 2026, month = 1, day = 12)

        assertEquals("今天", diaryRelativeDayLabel(now, now))
        assertEquals("昨天", diaryRelativeDayLabel(yesterday, now))
        assertEquals("前天", diaryRelativeDayLabel(beforeYesterday, now))
        assertEquals("", diaryRelativeDayLabel(older, now))
    }

    @Test
    fun formatsDiaryDateCountLabelWithRelativePrefix() {
        val now = localMillis(year = 2026, month = 1, day = 15)
        val today = localMillis(year = 2026, month = 1, day = 15)
        val older = localMillis(year = 2026, month = 1, day = 12)

        assertEquals("今天 · 2 篇", diaryDateCountLabel(today, dayDiaryCount = 2, nowMillis = now))
        assertEquals("3 篇", diaryDateCountLabel(older, dayDiaryCount = 3, nowMillis = now))
    }

    @Test
    fun convertsChineseNumbersWithExistingRules() {
        assertEquals("一", toChineseNumber(1))
        assertEquals("十", toChineseNumber(10))
        assertEquals("十一", toChineseNumber(11))
        assertEquals("二十", toChineseNumber(20))
        assertEquals("二十一", toChineseNumber(21))
    }

    private fun localMillis(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance(Locale.CHINA).apply {
            set(year, month - 1, day, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
