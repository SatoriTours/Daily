package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.opportunity.OpportunityPreview
import com.dailysatori.service.opportunity.ReadNewsArticle
import kotlin.test.*

class TodayNewsOpportunityPreviewTest {
    private val item = NewsOpportunity("id",
        ReadNewsArticle("key", "原新闻标题", "原文", source = "来源", readAt = 0),
        "分析标题", "工具", "新闻事实", "已有关注分析", "已有行动建议", "风险", "引用", 0)

    @Test fun legacyAnalysisIsReadableWithoutRegeneration() {
        val preview = todayNewsOpportunityPreview(item)
        assertEquals("原新闻标题", preview.title)
        assertEquals("新闻事实", preview.reason)
        assertEquals("已有行动建议", preview.opportunity)
        assertNull(item.preview)
    }

    @Test fun generatedTitleAndOpportunityKeepPriorityButOverviewExplainsTheNews() {
        val preview = OpportunityPreview("精简标题", "精简关注", "精简机会")
        assertEquals(OpportunityPreview("精简标题", "新闻事实", "精简机会"),
            todayNewsOpportunityPreview(item.copy(preview = preview)))
    }

    @Test fun missingFactsUseExistingPreviewThenLegacyAnalysis() {
        val preview = OpportunityPreview("标题", "具体痛点", "机会")
        assertEquals("具体痛点", todayNewsOpportunityPreview(item.copy(fact = " ", preview = preview)).reason)
        assertEquals("已有关注分析", todayNewsOpportunityPreview(item.copy(fact = " ")).reason)
    }

    @Test fun missingLegacyFieldsFallBackToAvailableContent() {
        val preview = todayNewsOpportunityPreview(item.copy(
            article = item.article.copy(title = " "), relevance = " ", action = ""))
        assertEquals("分析标题", preview.title)
        assertEquals("新闻事实", preview.reason)
        assertEquals("", preview.opportunity)
    }

    @Test fun overviewUsesTwoCompleteSentencesAndOpportunityUsesOne() {
        val legacy = item.copy(fact = "**开发者**缺少诊断依据。\n多个来源难以核对！还有更详细的分析。",
            action = "可做并排对照工具。后续再增加评分。")
        val preview = todayNewsOpportunityPreview(legacy)
        assertEquals("开发者缺少诊断依据。 多个来源难以核对！", preview.reason)
        assertEquals("可做并排对照工具。", preview.opportunity)
        assertEquals("可做并排对照工具。后续再增加评分。", legacy.action)
    }

    @Test fun numberedLegacyStepsDoNotTurnIntoAnEmptyNumberPreview() {
        val preview = todayNewsOpportunityPreview(item.copy(
            action = "1. 可做请求头审计工具。\n2. 再增加自动提醒。"))
        assertEquals("可做请求头审计工具。", preview.opportunity)
    }

    @Test fun longUnbrokenSentenceIsNotCutInTheMiddle() {
        val fact = "详细说明".repeat(50) + "。"
        assertEquals(fact, todayNewsOpportunityPreview(item.copy(fact = fact)).reason)
    }

    @Test fun englishSentencesKeepDecimalVersionsAndLinksIntact() {
        val fact = "Tool v1.2 is available at https://example.com. It explains IP scores. More detail."
        assertEquals("Tool v1.2 is available at https://example.com. It explains IP scores.",
            todayNewsOpportunityPreview(item.copy(fact = fact)).reason)
    }
}
