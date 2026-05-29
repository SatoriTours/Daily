package com.dailysatori.service.diary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryMonthSummaryServiceTest {
    @Test
    fun recentDiaryMonthKeysIncludesCurrentAndPreviousTwoMonths() {
        val keys = recentDiaryMonthKeys(nowMs = 1_716_936_000_000L)

        assertEquals(listOf("2024-05", "2024-04", "2024-03"), keys)
    }

    @Test
    fun currentMonthRefreshesWhenFingerprintChanges() {
        assertTrue(diaryMonthSummaryNeedsRefresh(null, 1, 100, isCurrentMonth = true))
        assertFalse(diaryMonthSummaryNeedsRefresh(MonthSummaryFingerprint(1, 100), 1, 100, isCurrentMonth = true))
        assertTrue(diaryMonthSummaryNeedsRefresh(MonthSummaryFingerprint(1, 100), 2, 100, isCurrentMonth = true))
        assertTrue(diaryMonthSummaryNeedsRefresh(MonthSummaryFingerprint(1, 100), 1, 200, isCurrentMonth = true))
    }

    @Test
    fun previousMonthsOnlyRefreshWhenCacheMissing() {
        assertTrue(diaryMonthSummaryNeedsRefresh(null, 1, 100, isCurrentMonth = false))
        assertFalse(diaryMonthSummaryNeedsRefresh(MonthSummaryFingerprint(1, 100), 2, 200, isCurrentMonth = false))
    }

    @Test
    fun failedCurrentMonthSummaryRetriesOnlyOnANewDay() {
        val may29 = 1_716_936_000_000L
        val may29Later = may29 + 60_000L
        val may30 = may29 + 24 * 60 * 60 * 1000L

        assertFalse(diaryMonthFailedSummaryCanRetry(may29, may29Later))
        assertTrue(diaryMonthFailedSummaryCanRetry(may29, may30))
    }

    @Test
    fun promptIsOneSentenceRequestAndIsTruncated() {
        val prompt = buildDiaryMonthSummaryPrompt(
            monthKey = "2024-05",
            diaryTexts = listOf("日记".repeat(3000)),
            tags = listOf("生活", "散步"),
            moods = listOf("平静"),
        )

        assertTrue(prompt.contains("请只输出一句中文自然语言总结"))
        assertTrue(prompt.contains("2024-05"))
        assertTrue(prompt.contains("生活、散步"))
        assertTrue(prompt.length <= 4_500)
    }
}
