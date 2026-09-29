package com.dailysatori.ui.feature.myspace

import com.dailysatori.service.diary.DiaryThought
import com.dailysatori.service.diary.DiaryThoughtEvidence
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.opportunity.ReadNewsArticle
import kotlin.test.*

class MySpacePresentationTest {
    private val source = ReadNewsArticle("key", "title", "body", source = "site", readAt = 1)
    private fun item(id: String, saved: Boolean = false, ignored: Boolean = false, reminder: String? = null) =
        NewsOpportunity(id, source, "title", "category", "fact", "inference", "step", "caveat", "body", 1, saved, ignored, reminder)

    @Test fun thoughtPreviewsKeepTwoDistinctIdeasWithFullStatementsAndTheirOwnQuotes() {
        val statement = "当工作上的事情挤在一起时，你想先完成最重要的一件，再处理琐事，而不是为了清空待办清单把精力分散掉。"
        val explicit = DiaryThought("做事准则", statement, "明确表达", listOf(
            DiaryThoughtEvidence(1, "先完成重要的事"), DiaryThoughtEvidence(2, "不把精力用在琐事上"),
        ))
        val inferred = DiaryThought("价值观", "你可能更看重有意义的进展，而不是完成任务的数量。", "AI归纳", listOf(DiaryThoughtEvidence(3, "忙了一天，却没推进重要的事情")))
        val third = DiaryThought("思维方式", "先试一小步", "明确表达", listOf(DiaryThoughtEvidence(4, "今天决定先试一下")))

        val previews = myThoughtPreviews(listOf(explicit, inferred, third))

        assertEquals(2, previews.size)
        assertEquals(statement, previews.first().statement)
        assertEquals("先完成重要的事", previews.first().quote)
        assertFalse(previews.first().isInference)
        assertEquals("忙了一天，却没推进重要的事情", previews.last().quote)
        assertTrue(previews.last().isInference)
    }

    @Test fun thoughtPreviewsDoNotInventEvidenceAndUnknownBasisRemainsAnInference() {
        val unsupported = DiaryThought("价值观", "没有依据的想法", "明确表达", emptyList())
        val legacy = unsupported.copy(statement = "旧版想法", basis = "旧版标签", evidence = listOf(
            DiaryThoughtEvidence(1, " "), DiaryThoughtEvidence(2, "真实原文，不要改写。"),
        ))
        assertTrue(myThoughtPreviews(listOf(unsupported)).isEmpty())
        val preview = myThoughtPreviews(listOf(unsupported, legacy)).single()
        assertEquals("真实原文，不要改写。", preview.quote)
        assertTrue(preview.isInference)
        assertTrue(myThoughtPreviews(emptyList()).isEmpty())
    }

    @Test fun completedAndIgnoredItemsDoNotCrowdThePendingList() {
        val items = listOf(item("pending"), item("saved", saved = true), item("acted", reminder = "r"), item("hidden", saved = true, ignored = true))
        assertEquals(listOf("saved", "pending"), opportunityItems(items, OpportunityFilter.PENDING).map { it.id })
        assertEquals(listOf("saved"), opportunityItems(items, OpportunityFilter.SAVED).map { it.id })
        assertEquals(listOf("acted"), opportunityItems(items, OpportunityFilter.ACTED).map { it.id })
        assertEquals(listOf("hidden"), opportunityItems(items, OpportunityFilter.IGNORED).map { it.id })
    }

    @Test fun recommendationsKeepFiveAcrossDatesPrioritizeSavedAndExcludeActed() {
        val entries = (1..6).map { item("news-$it").copy(createdAt = it.toLong()) } +
            item("saved", saved = true).copy(createdAt = 0) +
            item("acted", saved = true, reminder = "r") + item("hidden", ignored = true)
        assertEquals(listOf("saved", "news-6", "news-5", "news-4", "news-3"), recommendedArticles(entries).map { it.id })
        assertEquals("news-6", recommendedArticles(entries.filterNot { it.saved }).first().id)
        assertEquals(listOf("saved", "news-6", "news-5", "news-4", "news-3"),
            opportunityItems(entries, OpportunityFilter.PENDING).take(5).map { it.id })
    }

    @Test fun localAndRemoteReadersShareIdentityForTheSameOriginalArticle() {
        assertEquals(readNewsKey("https://site.test/a#section", "local:1"), readNewsKey("https://site.test/a", "remote:2"))
        assertNotEquals(readNewsKey(null, "remote:1:8"), readNewsKey(null, "remote:2:8"))
    }

    @Test fun matchingAndActionableOpportunitiesOutrankMerelyRecentOnesButSavedWins() {
        val oldUseful = item("useful").copy(createdAt = 1, relevanceScore = 90, actionabilityScore = 90)
        val recentWeak = item("recent").copy(createdAt = 100, relevanceScore = 20, actionabilityScore = 20)
        val saved = item("saved", saved = true).copy(savedAt = 2, relevanceScore = 0)
        assertEquals(listOf("saved", "useful", "recent"), rankedOpportunities(listOf(recentWeak, saved, oldUseful), 101).map { it.id })
        val newlySaved = item("saved-later", saved = true).copy(savedAt = 20)
        assertEquals(listOf("saved-later", "saved"), rankedOpportunities(listOf(saved, newlySaved), 101).map { it.id })
        assertEquals("useful", rankedOpportunities(listOf(recentWeak, saved.copy(saved = false, savedAt = null), oldUseful), 101).first().id)
    }

    @Test fun blankSummariesCannotBeMarkedAsReadBodies() {
        assertFalse(source.copy(content = " ").hasReadableBody())
        assertFalse(source.copy(title = "").hasReadableBody())
        assertTrue(source.hasReadableBody())
    }

    @Test fun reminderIdentitySurvivesRepeatedConversionAndThoughtKeysDistinguishCategories() {
        assertEquals(opportunityReminderId("a"), opportunityReminderId("a"))
        assertNotEquals(opportunityReminderId("a"), opportunityReminderId("b"))
        val thought = DiaryThought("价值观", "做重要的事", "依据", emptyList())
        assertNotEquals(thoughtChatKey(thought), thoughtChatKey(thought.copy(category = "思维方式")))
        assertEquals(thoughtChatKey(thought), thoughtChatKey(thought.copy(basis = "更新依据")))
    }
}
