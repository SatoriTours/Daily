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
    fun overviewKeepsAllGeneratedParagraphsWithoutCitationsOrPoints() {
        val digest = dailyHeadlineDigest(
            summary(
                listOf(
                    "## 今日要闻",
                    "今天主线是工具接入。[R1]",
                    "同一段补充变化。",
                    "",
                    "第二段说明影响。",
                    "",
                    "第三段同样保留在总览中。",
                    "",
                    "## 今日要点",
                    "- 新闻一：要点正文。[R1]",
                ).joinToString("\n"),
            ),
            sources = emptyList(),
        )

        assertEquals(3, digest.overview.size)
        assertEquals("今天主线是工具接入。同一段补充变化。", digest.overview[0])
        assertEquals("第二段说明影响。", digest.overview[1])
        assertEquals("第三段同样保留在总览中。", digest.overview[2])
        assertTrue(digest.overview.none { it.contains("[R1]") || it.contains("新闻一") })
        assertEquals(listOf("新闻一"), digest.stories.map { it.title })
    }

    @Test
    fun explicitThreeParagraphOverviewAndHeadlessMultiParagraphOverviewKeepAllParagraphs() {
        val explicitContent = listOf(
            "## 综述",
            "端侧模型相继更新。",
            "",
            "The U.S. market reacted.",
            "",
            "第三段：隐私工具也有新进展。",
            "## 今日要点",
            "- 新闻：要点说明。[R1]",
        ).joinToString("\n")
        val explicitOverview = dailyHeadlineOverview(explicitContent)
        val explicitLayout = headlineOverviewLayout(explicitOverview)!!

        assertEquals(
            listOf("端侧模型相继更新。", "The U.S. market reacted.", "第三段：隐私工具也有新进展。"),
            explicitOverview,
        )
        assertEquals("端侧模型相继更新。", explicitLayout.lead)
        assertEquals(
            listOf("The U.S. market reacted.", "第三段：隐私工具也有新进展。"),
            explicitLayout.paragraphs,
        )
        assertTrue(explicitOverview.none { it.contains("新闻") })

        val headlessContent = listOf(
            "# 今日统一新闻总结",
            "",
            "第一段主线说明。[R1]",
            "",
            "第二段补充变化。",
            "",
            "第三段：无标题导读也不应丢失。",
            "",
            "- 旧新闻一：正文一。[R1]",
        ).joinToString("\n")
        val headlessDigest = dailyHeadlineDigest(summary(headlessContent), sources = emptyList())
        val headlessLayout = headlineOverviewLayout(headlessDigest.overview)!!

        assertEquals(
            listOf("第一段主线说明。", "第二段补充变化。", "第三段：无标题导读也不应丢失。"),
            headlessDigest.overview,
        )
        assertEquals("第一段主线说明。", headlessLayout.lead)
        assertEquals(listOf("第二段补充变化。", "第三段：无标题导读也不应丢失。"), headlessLayout.paragraphs)
        assertTrue(headlessDigest.overview.none { it.contains("旧新闻一") })
        assertEquals(listOf("旧新闻一"), headlessDigest.stories.map { it.title })
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

    @Test
    fun legacyListStyleOverviewIsExtractedWithoutDroppingStoriesOrRemaining() {
        val content = listOf(
            "## 🗞️ 今日要闻：",
            "- 开源办公与 AI 代理成为今日主线，foo_bar_baz 模块同步更新。[R1]",
            "- 本地文件协作工具持续演进，响应延迟显著下降。[R2]",
            "",
            "## 今日要点",
            "- 开源办公：提供表格与文档编辑能力。[R1]",
            "- 代理协议：以事件流连接前端与模型。[R2]",
            "",
            "## 重要变化",
            "- 端侧协议趋向统一 [R2]",
        ).joinToString("\n")
        val digest = dailyHeadlineDigest(summary(content), sources = emptyList())

        assertEquals(
            listOf(
                "开源办公与 AI 代理成为今日主线，foo_bar_baz 模块同步更新。",
                "本地文件协作工具持续演进，响应延迟显著下降。",
            ),
            digest.overview,
        )
        assertEquals(listOf("开源办公", "代理协议"), digest.stories.map { it.title })
        assertTrue(digest.remaining.contains("## 重要变化"))
    }

    @Test
    fun overviewLayoutSplitsLeadSentenceAndNaturalParagraphsWithoutDuplication() {
        val singleParagraph = "主线聚焦工具链与端侧协议。" +
            "第一组项目开放核心编辑引擎，支持表格与文档协作；协议层通过事件流打通前端与代理。" +
            "社区同时探索本地文件操作方案。" +
            "开发者分享完整部署实践；实测指出新款屏幕调光频率约 240 Hz，部分用户反馈不适。"
        val layout = headlineOverviewLayout(listOf(singleParagraph))!!

        assertEquals("主线聚焦工具链与端侧协议。", layout.lead)
        assertEquals(2, layout.paragraphs.size)
        assertFalse(layout.paragraphs.any { it.contains(layout.lead!!) })
        assertEquals(singleParagraph, layout.lead + layout.paragraphs.joinToString(""))
    }

    @Test
    fun overviewLayoutPreservesShortTextAndMixedPunctuationWithoutFalseBreaks() {
        val shortLayout = headlineOverviewLayout(listOf("多项服务发布面向普通用户的更新。"))!!
        assertEquals("多项服务发布面向普通用户的更新。", shortLayout.lead)
        assertTrue(shortLayout.paragraphs.isEmpty())

        val mixed = "OpenAI shipped v2.5 with 3.14x faster runs. Dr. Lin noted example.com usage rose steadily. Local tools followed."
        val mixedLayout = headlineOverviewLayout(listOf(mixed))!!
        assertEquals("OpenAI shipped v2.5 with 3.14x faster runs.", mixedLayout.lead)
        assertTrue(mixedLayout.paragraphs.joinToString(" ").contains("Dr. Lin noted example.com usage rose steadily."))
        assertTrue(mixedLayout.paragraphs.joinToString(" ").contains("Local tools followed."))
    }

    @Test
    fun overviewLayoutPreservesLongTextAcrossNaturalParagraphsWithoutCutting() {
        val sentence = "多个项目今天发布新版本，主要变化涉及部署方式和团队协作。"
        val longOverview = sentence.repeat(10)
        val layout = headlineOverviewLayout(listOf(longOverview))!!

        assertEquals(sentence, layout.lead)
        assertTrue(layout.paragraphs.size >= 2)
        assertEquals(longOverview, layout.lead + layout.paragraphs.joinToString(""))
    }
}
