package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyHeadlineStoriesTest {
    private fun summary(content: String) = Unified_news_summary(
        7, "2026-10-17", "daily", 0, 1, "title", content, "success", null, null, 1, 1, 1,
    )

    private fun source(
        id: Long,
        ref: String,
        title: String = "来源 $ref",
        summary: String = "来源摘要 $ref",
        type: String = "remote_article",
    ) = Unified_news_source(
        id, 7, ref, type, null, "remote_news_source:1",
        "https://example.com/$ref", title, summary, null,
    )

    private fun stories(
        content: String,
        sources: List<Unified_news_source> = emptyList(),
        articleSummaries: Map<Long, String> = emptyMap(),
        query: String = "",
    ) = dailyHeadlineStories(summary(content), sources, articleSummaries, query)

    @Test
    fun onlyDailyPointsBecomeStoriesAndOtherSectionsStaySeparate() {
        val content = listOf(
            "## 今日要闻",
            "今天总览第一段。[R1]",
            "",
            "## 今日要点",
            "- 语音工具：支持离线配音，适合内容创作。[H1][R1]",
            "- 内核审查：新增自动检查流程，减少人工复核。[R1]",
            "",
            "## 重要变化",
            "- 重要变化条目 [R1]",
            "",
            "## 值得关注",
            "- 值得关注条目 [R2]",
        ).joinToString("\n")
        val sources = listOf(
            source(11, "H1", "VoiceStudio", "本地运行，支持语音创作。"),
            source(12, "R1", "内核项目", "内核审查新增流程。"),
            source(13, "R2", "关注来源", "关注来源正文。"),
        )
        val digest = dailyHeadlineDigest(summary(content), sources)

        assertEquals(listOf("语音工具", "内核审查"), digest.stories.map { it.title })
        assertEquals(listOf(11L, 12L), digest.stories.map { it.source?.id })
        assertEquals(listOf("H1", "R1"), digest.stories.first().citations)
        assertEquals(listOf("R1"), digest.stories.last().citations)
        assertTrue(digest.remaining.contains("## 重要变化"))
        assertTrue(digest.remaining.contains("## 值得关注"))
        assertTrue(digest.remaining.contains("重要变化条目"))
        assertFalse(digest.stories.any { it.title.contains("重要变化") || it.title.contains("值得关注") })
    }

    @Test
    fun sameSourceWithDifferentIndependentEventsIsNotDropped() {
        val content = "## 今日要点\n- 事件甲：甲的正文。[R1]\n- 事件乙：乙的正文。[R1]"
        val source = source(12, "R1")

        assertEquals(listOf("事件甲", "事件乙"), stories(content, listOf(source)).map { it.title })
    }

    @Test
    fun multiCitationItemKeepsAllMatchedSources() {
        val content = "## 今日要点\n- 联合事件：两个来源支持同一变化。[H1][R1]"
        val sources = listOf(source(11, "H1"), source(12, "R1"))
        val story = stories(content, sources).single()

        assertEquals(listOf(11L, 12L), story.sources.map { it.id })
        assertEquals("H1", story.source?.ref_key)
        assertEquals(listOf("H1", "R1"), story.citations)
        assertTrue(headlineStorySourceMeta(story).contains("example.com"))
    }

    @Test
    fun englishSourceTextIsKeptInsteadOfBeingSilentlyCleared() {
        val source = source(12, "R1", "AG-UI", "Open source agent protocol adds event streaming and state updates.")
        val story = stories("## 今日要点\n- AG-UI 事件协议 [R1]", listOf(source)).single()

        assertTrue(story.paragraphs.single().contains("Open source agent protocol"))
        assertEquals("AG-UI 事件协议", story.title)
    }

    @Test
    fun unknownCitationIsExplicitAndNeverInventsASource() {
        val story = stories("## 今日要点\n- 未知来源事件：正文仍然来自生成总结。[R9]").single()

        assertNull(story.source)
        assertEquals(listOf("R9"), story.unmatchedCitations)
        assertTrue(headlineStorySourceMeta(story).contains("未匹配引用 R9"))
        assertEquals("未知来源事件", story.title)
        assertTrue(story.paragraphs.single().contains("正文仍然来自生成总结"))
    }

    @Test
    fun missingBodyDoesNotCreateAnEmptyTitleOnlyStory() {
        val source = source(12, "R2", "纯标题", "纯标题")

        assertEquals(emptyList(), stories("## 今日要点\n- 纯标题 [R2]", listOf(source)))
        assertEquals(emptyList(), stories("## 今日要点\n- 无正文无来源 [R9]"))
    }

    @Test
    fun oldBriefingWithoutPointsHeadingStillReadsListItems() {
        val content = listOf(
            "# 今日统一新闻总结",
            "今天概览。",
            "",
            "- 旧新闻一：旧正文一。[R1]",
            "- 旧新闻二：旧正文二。[R2]",
        ).joinToString("\n")
        val sources = listOf(source(11, "R1", "旧来源一"), source(12, "R2", "旧来源二"))
        val digest = dailyHeadlineDigest(summary(content), sources)

        assertEquals(listOf("旧新闻一", "旧新闻二"), digest.stories.map { it.title })
        assertEquals(listOf("今天概览。"), digest.overview)
    }

    @Test
    fun moreThanFiveRealPointsAreKept() {
        val content = "## 今日要点\n" + (1..7).joinToString("\n") { "- 事件 $it：第 $it 条完整正文。[R$it]" }
        val sourceItems = (1..7).map { source(it.toLong(), "R$it") }

        assertEquals((1..7).map { "事件 $it" }, stories(content, sourceItems).map { it.title })
    }

    @Test
    fun longGeneratedBodyIsNotSilentlyCut() {
        val longBody = "长内容".repeat(3_500)
        val story = stories("## 今日要点\n- 长文事件：$longBody [R9]").single()

        assertEquals(3_500 * 3, story.paragraphs.single().length)
    }

    @Test
    fun processedArticleFactsFillAStoryWithoutGeneratedBody() {
        val source = source(11, "H1", "VoiceStudio", "本地运行。")
        val story = stories(
            "## 今日要点\n- 本地语音工具 [H1]",
            listOf(source),
            mapOf(11L to "**核心内容：** 支持离线配音。\n\n**为什么重要（分析）：** 方便内容创作。"),
        ).single()

        assertEquals("本地语音工具", story.title)
        assertEquals("支持离线配音。", story.description)
    }

    @Test
    fun searchKeepsSameSourceDifferentIndependentEvents() {
        val content = "## 今日要点\n- 事件甲：甲的正文。[R1]\n- 事件乙：乙的正文。[R1]"
        val source = source(12, "R1")

        assertEquals(listOf("事件甲", "事件乙"), stories(content, listOf(source), query = "事件").map { it.title })
    }

    @Test
    fun searchCanReachSourcesBeyondThePoints() {
        val source = source(19, "R9", "未列入要点的报道", "目标内容及其细节。")
        val result = stories("## 今日要点\n- 其他事件：正文。[R1]", listOf(source), query = "目标内容")

        assertEquals(listOf("未列入要点的报道"), result.map { it.title })
        assertEquals(19L, result.single().source?.id)
    }
}
