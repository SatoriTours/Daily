package com.dailysatori.ui.feature.diary

import com.dailysatori.shared.db.Diary
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiaryMonthReviewContentTest {
    @Test
    fun reviewKeepsActualSummaryAndSkipsEntriesWithOnlyTagsWhenChoosingAnExcerpt() {
        val review = diaryMonthReviewContent(listOf(
            diary(2, "\n#生活\n", "#生活, null, 生活"),
            diary(1, "**晚风刚刚好。**\n\n#散步", "散步, #生活, 读书, 工作"),
        ), "  我给自己留了一点时间。  ")

        assertEquals("我给自己留了一点时间。", review.summary)
        assertEquals(listOf("生活", "散步", "读书"), review.tags)
        assertEquals("晚风刚刚好。", review.excerpt)
    }

    @Test
    fun unavailableSummaryAndAttachmentOnlyDiaryDoNotInventContent() {
        val review = diaryMonthReviewContent(listOf(diary(1, "", "null, #, ,")), " \n ")

        assertNull(review.summary)
        assertNull(review.excerpt)
        assertEquals(emptyList(), review.tags)
    }

    @Test
    fun emptyMonthDoesNotDisplayAStaleSummary() {
        val review = diaryMonthReviewContent(emptyList(), "旧的回顾")

        assertNull(review.summary)
        assertNull(review.excerpt)
        assertEquals(emptyList(), review.tags)
    }

    @Test
    fun monthHeadingIncludesYearAndMatchesTheSelectedLocale() {
        assertEquals("2026年10月", diaryReviewMonthLabel("2026-10", Locale.CHINA))
        assertEquals("October 2026", diaryReviewMonthLabel("2026-10", Locale.US))
    }

    private fun diary(id: Long, content: String, tags: String?) = Diary(id, content, tags, null, null, 0, 0, null)
}
