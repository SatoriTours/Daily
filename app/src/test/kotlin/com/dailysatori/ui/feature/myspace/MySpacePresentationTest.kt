package com.dailysatori.ui.feature.myspace

import com.dailysatori.service.diary.DiaryThought
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.opportunity.ReadNewsArticle
import kotlin.test.*

class MySpacePresentationTest {
    private val source = ReadNewsArticle("key", "title", "body", source = "site", readAt = 1)
    private fun item(id: String, saved: Boolean = false, ignored: Boolean = false, reminder: String? = null) =
        NewsOpportunity(id, source, "title", "category", "fact", "inference", "step", "caveat", "body", 1, saved, ignored, reminder)

    @Test fun completedAndIgnoredItemsDoNotCrowdThePendingList() {
        val items = listOf(item("pending"), item("saved", saved = true), item("acted", reminder = "r"), item("hidden", saved = true, ignored = true))
        assertEquals(listOf("pending", "saved"), opportunityItems(items, OpportunityFilter.PENDING).map { it.id })
        assertEquals(listOf("saved"), opportunityItems(items, OpportunityFilter.SAVED).map { it.id })
        assertEquals(listOf("acted"), opportunityItems(items, OpportunityFilter.ACTED).map { it.id })
        assertEquals(listOf("hidden"), opportunityItems(items, OpportunityFilter.IGNORED).map { it.id })
    }

    @Test fun localAndRemoteReadersShareIdentityForTheSameOriginalArticle() {
        assertEquals(readNewsKey("https://site.test/a#section", "local:1"), readNewsKey("https://site.test/a", "remote:2"))
        assertNotEquals(readNewsKey(null, "remote:1:8"), readNewsKey(null, "remote:2:8"))
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
