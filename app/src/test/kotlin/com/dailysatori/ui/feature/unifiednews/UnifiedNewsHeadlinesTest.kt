package com.dailysatori.ui.feature.unifiednews

import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.opportunity.ReadNewsArticle
import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import kotlin.test.*

class UnifiedNewsHeadlinesTest {
    private val summary = Unified_news_summary(1, "2026-09-29", "daily", 0, 1, "title", "已有要闻", "success", null, null, 1, 1, 1)
    private val source = Unified_news_source(1, 1, "R1", "remote_article", 7, null, "https://example.com/news#quote", "标题", "摘要", null)
    private val opportunity = NewsOpportunity("a", ReadNewsArticle("key", "title", "body", "https://example.com/news", "source", readAt = 0, localArticleId = 7),
        "产品", "分类", "事实", "关联", "行动", "限制", "body", 1)

    @Test fun retainedHeadlinesStayReadableAfterFailedOrEmptyRefresh() {
        for (status in listOf("failed", "empty", "pending")) {
            val retained = summary.copy(status = status)
            assertEquals(retained, latestHeadlinesSummary(listOf(retained, summary.copy(id = 2))))
        }
        assertEquals(summary, latestHeadlinesSummary(listOf(summary.copy(content = " "), summary)))
        assertNull(latestHeadlinesSummary(emptyList()))
    }

    @Test fun lastSuccessfulHeadlinesRemainVisibleWhileLatestDataIsUnavailable() {
        assertEquals(summary, latestHeadlinesSummary(emptyList(), summary))
        assertEquals(summary, latestHeadlinesSummary(listOf(summary.copy(content = " ", status = "pending")), summary))
        val newer = summary.copy(id = 2, content = "新的要闻")
        assertEquals(newer, latestHeadlinesSummary(listOf(newer), summary))
        assertNull(latestHeadlinesSummary(emptyList(), summary.copy(content = " ")))
    }

    @Test fun relatedOpportunitiesMatchCanonicalUrlAndRespectLocalIdentity() {
        assertEquals(listOf(opportunity), relatedNewsOpportunities(source, listOf(opportunity)))
        assertTrue(relatedNewsOpportunities(source.copy(source_url = null), listOf(opportunity)).isEmpty())
        assertEquals(listOf(opportunity), relatedNewsOpportunities(source.copy(source_type = "local_favorite", source_url = null), listOf(opportunity)))
        assertTrue(relatedNewsOpportunities(source, listOf(opportunity.copy(ignored = true))).isEmpty())
        assertTrue(relatedNewsOpportunities(source.copy(source_url = "https://example.com/other"), listOf(opportunity)).isEmpty())
    }
}
