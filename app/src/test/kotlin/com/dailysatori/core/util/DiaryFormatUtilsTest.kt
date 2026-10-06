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

    @Test
    fun compactMetadataUsesOneDateLabelAndKeepsTheTime() {
        val now = localMillis(2026, 9, 30)
        assertEquals("今天 · 10:30", diaryCardDateTime(now, now))
        assertEquals("昨天 · 10:30", diaryCardDateTime(localMillis(2026, 9, 29), now))
        assertEquals("9月28日 · 10:30", diaryCardDateTime(localMillis(2026, 9, 28), now))
        assertEquals("2025年12月28日 · 10:30", diaryCardDateTime(localMillis(2025, 12, 28), now))
    }

    @Test
    fun yesterdayWorksAcrossTheYearBoundaryAndMidnightUpdatesTheLabel() {
        val newYear = localMillis(2026, 1, 1)
        val lastDay = localMillis(2025, 12, 31)
        assertEquals("昨天 · 10:30", diaryCardDateTime(lastDay, newYear))
        assertEquals("今天 · 10:30", diaryCardDateTime(lastDay, lastDay))
        assertEquals("Yesterday · 10:30", diaryCardDateTime(lastDay, newYear, "Today", "Yesterday", Locale.US))
    }

    @Test
    fun compactPreviewRemovesMarkdownDecorationWithoutLosingWords() {
        assertEquals("标题 记录一个想法 查看原文", diaryPreviewText("# 标题\n\n**记录一个想法**\n[查看原文](https://example.com)\n#日常"))
        assertEquals("", diaryPreviewText("\n#日常\n"))
    }

    @Test
    fun cardPreviewSeparatesTheFirstLevelOneHeadingFromTheBody() {
        val preview = diaryCollapsedPreview("# 慢下来\n\n**记录一个想法**\n[查看原文](https://example.com)\n#日常")

        assertEquals(DiaryCollapsedPreview("慢下来", "记录一个想法 查看原文"), preview)
    }

    @Test
    fun titleOnlyDiaryHasNoBodyPreview() {
        assertEquals(DiaryCollapsedPreview("今天也要好好生活", ""), diaryCollapsedPreview("# 今天也要好好生活"))
        assertEquals(DiaryCollapsedPreview("今天也要好好生活", ""),
            diaryCollapsedPreview("# 今天也要好好生活\n\n#生活"))
    }

    @Test
    fun cardPreviewOnlyTreatsAFirstLineLevelOneHeadingAsATitle() {
        val ordinaryContents = listOf("普通首行\n# 后面的标题", "## 二级标题\n正文", "#标签\n正文",
            "\n# 前面有空行\n正文", "    # 缩进代码\n正文", "# ", "")

        ordinaryContents.forEach { content ->
            assertEquals(null, diaryCollapsedPreview(content).title, content)
            assertEquals(diaryPreviewText(content), diaryCollapsedPreview(content).body, content)
        }
    }

    @Test
    fun cardTitleHandlesMarkdownWhitespaceAndInlineFormatting() {
        assertEquals(DiaryCollapsedPreview("今天 读书", "正文"),
            diaryCollapsedPreview("  #\t**今天** [读书](https://example.com) ###\r\n\r\n正文"))
        assertEquals(DiaryCollapsedPreview("C#", "正文"), diaryCollapsedPreview("# C#\n正文"))
    }

    @Test
    fun cardPreviewKeepsQuotedWordsAfterExtractingATitle() {
        assertEquals(DiaryCollapsedPreview("记住这句话", "慢一点"), diaryCollapsedPreview("# 记住这句话\n\n> 慢一点"))
        assertEquals(DiaryCollapsedPreview(null, "慢一点"), diaryCollapsedPreview("> 慢一点"))
    }

    @Test
    fun cardTitleCanContainAHashtagWithoutBeingStrippedAsATagLine() {
        assertEquals("# #生活", stripDiaryInlineTags("# #生活\n#日常"))
        assertEquals(DiaryCollapsedPreview("#生活", ""), diaryCollapsedPreview("# #生活\n#日常"))
    }

    @Test
    fun feedDateKeepsTheCalendarDateAlongsideRelativeLabels() {
        val now = localMillis(2026, 10, 5)
        assertEquals("10月5日 · 今天 · 10:30", diaryCardDateTime(now, now, includeDateForRelativeDays = true))
        assertEquals("10月4日 · 昨天 · 10:30", diaryCardDateTime(localMillis(2026, 10, 4), now,
            includeDateForRelativeDays = true))
        assertEquals("Oct 5 · Today · 10:30", diaryCardDateTime(now, now, "Today", "Yesterday", Locale.US,
            includeDateForRelativeDays = true))
    }

    @Test
    fun feedDateKeepsTheYearWhenYesterdayFallsInThePreviousYear() {
        assertEquals("2025年12月31日 · 昨天 · 10:30", diaryCardDateTime(localMillis(2025, 12, 31),
            localMillis(2026, 1, 1), includeDateForRelativeDays = true))
    }

    private fun localMillis(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance(Locale.CHINA).apply {
            set(year, month - 1, day, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
