package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.shared.db.Unified_news_source
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedNewsTabSplitTest {
    @Test
    fun opportunitiesTabFollowsHeadlinesBeforeHotlistsAndRemoteSources() {
        val state = UnifiedNewsState(
            remoteSources = listOf(UnifiedNewsRemoteSourceOption(3, "远程新闻")),
            externalFavoriteSources = listOf(UnifiedNewsExternalFavoriteSourceOption(4, "爱马仕")),
        )

        assertEquals(
            listOf(
                UnifiedNewsSourceSelection.Summary,
                UnifiedNewsSourceSelection.Opportunities,
                UnifiedNewsSourceSelection.Hotlists,
                UnifiedNewsSourceSelection.RemoteSource(3, "远程新闻"),
                UnifiedNewsSourceSelection.ExternalFavoriteSource(4, "爱马仕"),
                UnifiedNewsSourceSelection.LocalArticles,
                UnifiedNewsSourceSelection.Favorites,
            ),
            unifiedNewsSourcePages(state),
        )
        assertEquals("news-opportunities", unifiedNewsSourcePageKey(UnifiedNewsSourceSelection.Opportunities))
    }

    @Test
    fun opportunityRefreshRequestOnlyReachesTheSelectedOpportunitiesPage() {
        val state = UnifiedNewsState(
            sourceSelection = UnifiedNewsSourceSelection.Opportunities,
            opportunityRefreshRequestKey = 3,
        )

        assertEquals(3, unifiedNewsSourcePageState(state, UnifiedNewsSourceSelection.Opportunities, 0, 0)
            .opportunityRefreshRequestKey)
        assertEquals(0, unifiedNewsSourcePageState(state, UnifiedNewsSourceSelection.Summary, 0, 0)
            .opportunityRefreshRequestKey)
    }

    @Test
    fun screenRoutesSummaryToHeadlinesAndOpportunitiesToTheOpportunityReadingSurface() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()

        assertTrue(screen.contains("UnifiedNewsSourceSelection.Summary -> UnifiedNewsHeadlinesContent(pageState, viewModel, onBriefing)"))
        assertTrue(screen.contains("UnifiedNewsSourceSelection.Opportunities -> UnifiedNewsOpportunitiesContent("))
        assertTrue(screen.contains("R.string.news_focus_opportunities_tab"))
    }

    @Test
    fun headlineReadingSurfaceShowsOverviewAndAllParagraphsWithoutLineClamp() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsHeadlinesContent.kt").readText()

        assertTrue(source.contains("dailyHeadlineDigest("))
        assertTrue(source.contains("headlineOverviewLayout("))
        assertTrue(source.contains("story.paragraphs.forEach"))
        assertTrue(source.contains("digest.remaining"))
        assertTrue(source.contains("R.string.news_focus_read_briefing"))
        assertTrue(source.contains("R.string.news_focus_view_source"))
        assertTrue(source.contains("R.string.news_focus_choose_source"))
        assertFalse(source.contains("HeadlineOverviewPreviewLength"), "总览不再固定字数切断")
        assertFalse(source.contains("HeadlineBriefingAction"), "总览不再渲染独立简报按钮卡片")
        assertFalse(source.contains("headlineStorySourceMeta(story)"), "新闻行不再渲染独立来源元信息行")
        assertFalse(source.contains("take(3)"), "要闻不再只显示三条")
        assertFalse(source.contains(".take("), "要闻不再截断生成内容")
        assertFalse(Regex("Text\\(\\s*paragraph,[^)]*maxLines").containsMatchIn(source), "正文段落不限制行数")
    }

    @Test
    fun opportunityReadingSurfaceUsesInferenceAndEvidenceButDropsValidationSteps() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsOpportunitiesContent.kt").readText()

        assertTrue(source.contains("R.string.news_focus_opportunity_point"))
        assertTrue(source.contains("R.string.news_focus_why_watch"))
        assertTrue(source.contains("R.string.news_focus_news_basis"))
        assertTrue(source.contains("item.relevance"))
        assertTrue(source.contains("item.fact"))
        assertTrue(source.contains("opportunityEvidenceText(item)"), "新闻事实单独作为依据展示")
        assertTrue(source.contains("R.string.my_space_fact"))
        assertFalse(source.contains("item.action"), "先验证/最小版本不入阅读面")
        assertFalse(source.contains("item.caveat"), "验证提示不混入为什么值得关注")
        assertFalse(source.contains("先验证"))
        assertFalse(source.contains("成功指标"))
        assertFalse(source.contains("如何切入"))
    }

    @Test
    fun opportunityFieldsFallBackToPreviewForOlderGeneratedItems() {
        val item = newsOpportunityWith(relevance = "", fact = "", preview = OpportunityPreviewFixture("标题", "原因", "机会"))
        assertEquals("机会", opportunityText(item))
        assertEquals("原因", opportunityWhyText(item))
    }

    @Test
    fun newOpportunityUsesInferenceForWhyAndFactOnlyForEvidence() {
        val item = newsOpportunityWith(
            relevance = "独立开发者：需要在设备之间临时传文字。\n与关注方向相关；推断：现有步骤多，轻量中转可减少操作。",
            fact = "Text Dropbox 通过浏览器同步纯文本。",
            preview = OpportunityPreviewFixture("临时文本中转", "值得尝试", "无需安装的临时中转"),
        )

        assertEquals("独立开发者：需要在设备之间临时传文字。\n\n无需安装的临时中转", opportunityText(item))
        assertEquals("与关注方向相关；推断：现有步骤多，轻量中转可减少操作。\n\n值得尝试", opportunityWhyText(item))
        assertEquals("Text Dropbox 通过浏览器同步纯文本。", opportunityEvidenceText(item))
        assertFalse(opportunityWhyText(item).contains(item.fact), "新闻事实不能冒充为什么值得关注")
    }

    @Test
    fun oldInlineInferenceIsSplitOutOfTheOpportunityPoint() {
        val item = newsOpportunityWith(
            relevance = "独立开发者：需要跨设备传文字；推断：现有步骤过多。",
            fact = "客观新闻事实。",
            preview = null,
        )

        assertEquals("独立开发者：需要跨设备传文字", opportunityText(item))
        assertEquals("现有步骤过多。", opportunityWhyText(item))
    }

    @Test
    fun oldOpportunityWithoutJudgmentLeavesWhyEmptyInsteadOfReusingFact() {
        val item = newsOpportunityWith(
            relevance = "独立开发者：需要在设备之间临时传文字。",
            fact = "客观新闻事实。",
            preview = null,
        )

        assertEquals("独立开发者：需要在设备之间临时传文字。", opportunityText(item))
        assertEquals("", opportunityWhyText(item))
        assertEquals("客观新闻事实。", opportunityEvidenceText(item))
    }

    private fun newsOpportunityWith(
        relevance: String,
        fact: String,
        preview: com.dailysatori.service.opportunity.OpportunityPreview?,
    ) = com.dailysatori.service.opportunity.NewsOpportunity(
        id = "1",
        article = com.dailysatori.service.opportunity.ReadNewsArticle(
            key = "k", title = "新闻标题", content = "正文", source = "来源", readAt = 1, localArticleId = null,
        ),
        title = "机会标题",
        category = "分类",
        fact = fact,
        relevance = relevance,
        action = "不应展示的验证步骤",
        caveat = "不应展示的验证提示",
        quote = "",
        createdAt = 1,
        preview = preview,
    )
}

private typealias OpportunityPreviewFixture = com.dailysatori.service.opportunity.OpportunityPreview
