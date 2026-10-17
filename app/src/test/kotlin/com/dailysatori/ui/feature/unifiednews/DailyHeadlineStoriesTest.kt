package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyHeadlineStoriesTest {
    private val summary = Unified_news_summary(7, "2026-10-17", "daily", 0, 1, "title",
        "## 今日要点\n- 本地语音工具 [H1]\n- 智能体逆向工程 [H2]\n- 内核代码审查 [R1]\n- 其他新闻 [R2]",
        "success", null, null, 1, 1, 1)
    private val source = Unified_news_source(11, 7, "H1", "hot_news", null, "github:voice",
        "https://github.com/example/voice", "VoiceStudio", "本地运行，支持语音创作。", null)

    @Test fun previewShowsThreeEditorialPointsInBriefingOrder() {
        val stories = dailyHeadlineStories(summary, listOf(source.copy(id = 12, ref_key = "H2"), source))
        assertEquals(listOf("本地语音工具", "智能体逆向工程", "内核代码审查"), stories.map { it.title })
        assertEquals(listOf(11L, 12L, null), stories.map { it.source?.id })
    }

    @Test fun sourceLinksNeverResolveAgainstAnotherSummaryOrSimilarCitation() {
        val stories = dailyHeadlineStories(summary, listOf(source.copy(id = 99, summary_id = 8),
            source.copy(id = 100, ref_key = "H10"), source))
        assertEquals(11L, stories.first().source?.id)
        assertNull(stories[1].source)
        assertNull(stories[1].description)
    }

    @Test fun processedArticleFactsSupplyTheStoryDescription() {
        val stories = dailyHeadlineStories(summary, listOf(source),
            mapOf(11L to "**核心内容：** 支持离线配音。\n\n**为什么重要（分析）：** 方便内容创作。"))
        assertEquals("支持离线配音。", stories.first().description)
    }

    @Test fun duplicateHeadlineAndNavigationNoiseDoNotBecomeDescriptions() {
        assertNull(dailyHeadlineStories(summary, listOf(source.copy(summary = "本地语音工具"))).first().description)
        val noise = "main39 BranchesGo to fileCodeOpen more actions menuLatest commit"
        assertNull(dailyHeadlineStories(summary, listOf(source.copy(summary = noise))).first().description)
    }

    @Test fun sourcesWithoutEditorialPointsDoNotCreateASecondHotNewsFeed() {
        assertEquals(emptyList(), dailyHeadlineStories(summary.copy(content = "今天的概览。"), listOf(source)))
    }

    @Test fun searchCanReachSourcesBeyondTheThreeStoryPreview() {
        val fourth = source.copy(id = 14, ref_key = "R2", title = "其他新闻", summary = "目标内容及其细节。")
        val uncited = source.copy(id = 19, ref_key = "R9", title = "未列入要点的报道", summary = "目标内容的补充来源。")
        val foreign = source.copy(id = 20, summary_id = 8, title = "目标内容")
        val stories = dailyHeadlineStories(summary, listOf(source, fourth, uncited, foreign), query = "目标内容")
        assertEquals(listOf(14L, 19L), stories.map { it.source?.id })
    }

    @Test fun searchDoesNotDuplicateAReportThatAlreadyHasAnEditorialPoint() {
        val stories = dailyHeadlineStories(summary, listOf(source), query = "voicestudio")
        assertEquals(listOf("本地语音工具"), stories.map { it.title })
        assertEquals(11L, stories.single().source?.id)
    }
}
