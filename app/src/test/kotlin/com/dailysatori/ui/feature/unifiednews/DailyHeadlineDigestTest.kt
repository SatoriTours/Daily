package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DailyHeadlineDigestTest {
    private fun summary(content: String) = Unified_news_summary(
        7, "2026-10-17", "daily", 0, 1, "title", content, "success", null, null, 1, 1, 1,
    )

    @Test
    fun overviewKeepsUpToTwoGeneratedParagraphsWithoutCitations() {
        val digest = dailyHeadlineDigest(
            summary(
                listOf(
                    "## 今日要闻",
                    "今天主线是工具接入。[R1]",
                    "同一段补充变化。",
                    "",
                    "第二段说明影响。",
                    "",
                    "第三段不应进入总览。",
                    "",
                    "## 今日要点",
                    "- 新闻一 [R1]",
                ).joinToString("\n"),
            ),
            sources = emptyList(),
        )

        assertEquals(2, digest.overview.size)
        assertTrue(digest.overview[0].contains("今天主线是工具接入"))
        assertTrue(digest.overview[1].contains("第二段说明影响"))
        assertTrue(digest.overview.none { it.contains("[R1]") })
    }

    @Test
    fun everyEditorialPointIsKeptInsteadOfOnlyThreeOrFive() {
        val content = "## 今日要点\n" + (1..7).joinToString("\n") { "- 新闻 $it：第 $it 条正文。[R$it]" }
        val digest = dailyHeadlineDigest(summary(content), sources = emptyList())

        assertEquals((1..7).map { "新闻 $it" }, digest.stories.map { it.title })
        assertEquals(7, digest.stories.size)
    }

    @Test
    fun remainingSectionsAreKeptOutsideTheStoryList() {
        val content = listOf(
            "## 今日要点",
            "- 要闻：要闻正文。[R1]",
            "",
            "## 重要变化",
            "- 变化正文 [R1]",
            "",
            "## 值得关注",
            "- 关注正文 [R1]",
        ).joinToString("\n")
        val digest = dailyHeadlineDigest(summary(content), sources = emptyList())

        assertEquals(listOf("要闻"), digest.stories.map { it.title })
        assertTrue(digest.remaining.contains("## 重要变化"))
        assertTrue(digest.remaining.contains("## 值得关注"))
        assertFalse(digest.stories.any { it.title.contains("变化") || it.title.contains("关注") })
    }

    @Test
    fun storyReadingTextKeepsFullGeneratedFactsAndAnalysis() {
        val source = Unified_news_source(
            11, 7, "R1", "remote_article", null, "remote_news_source:3",
            "https://example.com/a", "标题", "来源摘要", null,
        )
        val paragraphs = unifiedNewsReadingParagraphs(
            source,
            "**新闻事实：** 原文支持的事实内容。\n\n**为什么重要（分析）：** 这段分析说明影响。",
        )

        assertEquals(listOf("原文支持的事实内容。", "这段分析说明影响。"), paragraphs)
    }

    @Test
    fun sourceMetaUsesDomainAndTypeInsteadOfInventedCopy() {
        val source = Unified_news_source(
            11, 7, "R1", "remote_article", null, "remote_news_source:3",
            "https://example.com/a", "标题", "来源摘要", null,
        )

        assertEquals("example.com · 远程新闻", headlineSourceMeta(source))
        assertFalse(headlineSourceMeta(source).contains("开源工具"))
    }
}
