package com.dailysatori.service.weekly

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeeklySummaryContentTest {
    @Test
    fun failedRemoteArticleUsesReadableOriginalInsteadOfBlockingTheWeek() {
        assertEquals(
            "Readable remote original",
            weeklyArticleContent(
                aiContent = "AI processing failed: timeout",
                aiMarkdownContent = null,
                originalMarkdownContent = "Readable remote original",
                title = "Fallback title",
            ),
        )
    }

    @Test
    fun weeklyInputAcceptsPartialSourceCategories() {
        val input = weeklySummaryInput(
            articles = emptyList(),
            diaries = listOf(WeeklyMaterial(1, "日记", "本周完成了一项重要工作。")),
            viewpoints = emptyList(),
        )

        assertFalse(input.contains("收藏文章"))
        assertTrue(input.contains("本周完成了一项重要工作"))
        assertTrue(weeklySummaryPrompt().contains("个别文章处理失败时"))
    }
}
