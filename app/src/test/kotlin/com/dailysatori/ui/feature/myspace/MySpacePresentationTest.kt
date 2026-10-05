package com.dailysatori.ui.feature.myspace

import com.dailysatori.service.diary.DiaryThought
import com.dailysatori.service.diary.DiaryThoughtEvidence
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.opportunity.ReadNewsArticle
import com.dailysatori.service.reminder.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.*
import kotlin.random.Random

class MySpacePresentationTest {
    private val today = LocalDate(2026, 10, 4)
    private fun reminder(id: String, date: LocalDate = today, status: ReminderStatus = ReminderStatus.ACTIVE) =
        Reminder(id, "提醒 $id", date, date, LocalTime(9, 0), ReminderActiveDayRule.Daily,
            ReminderProfileSnapshot.standard(), status, TimeZone.UTC, 1)

    @Test fun reminderPreviewKeepsTodayPendingAfterNotificationAndDismissalUntilCompleted() {
        val pending = setOf(ReminderStatus.ACTIVE, ReminderStatus.NOTIFIED, ReminderStatus.DISMISSED)
        ReminderStatus.entries.forEach { status ->
            assertEquals(if (status in pending) listOf("today") else emptyList(),
                myUpcomingReminders(listOf(reminder("today", status = status)), today).map { it.id },
                "status=$status")
        }
    }

    @Test fun reminderPreviewPrioritizesTodayPendingOverFutureAndLimitsToTwo() {
        val entries = listOf(reminder("future", LocalDate(2027, 1, 3)),
            reminder("tomorrow", LocalDate(2026, 10, 5)),
            reminder("today", status = ReminderStatus.NOTIFIED),
            reminder("done", status = ReminderStatus.COMPLETED))
        val preview = myUpcomingReminders(entries, today)
        assertEquals(listOf("today", "tomorrow"), preview.map { it.id })
        assertEquals(listOf(0, 1), preview.map { it.daysUntil })
        assertEquals(listOf("tomorrow", "future"),
            myUpcomingReminders(entries.map { if (it.id == "today") it.copy(status = ReminderStatus.COMPLETED) else it }, today).map { it.id })
    }

    @Test fun reminderPreviewKeepsAnOngoingReminderDueTodayAfterItsStartDate() {
        val ongoing = reminder("phone-top-up", LocalDate(2026, 10, 3), ReminderStatus.NOTIFIED)
            .copy(endDate = today, firstReminderTime = LocalTime(10, 0),
                activeDayRule = ReminderActiveDayRule.ConsecutiveDateRange)
        val future = reminder("future", LocalDate(2027, 1, 3))

        val preview = myUpcomingReminders(listOf(future, ongoing), today)

        assertEquals(listOf("phone-top-up", "future"), preview.map { it.id })
        assertEquals(today, preview.first().occurrenceDate)
        assertEquals(0, preview.first().daysUntil)
    }

    private val source = ReadNewsArticle("key", "title", "body", source = "site", readAt = 1)
    private fun item(id: String, saved: Boolean = false, ignored: Boolean = false, reminder: String? = null) =
        NewsOpportunity(id, source, "title", "category", "fact", "inference", "step", "caveat", "body", 1, saved, ignored, reminder)

    @Test fun thoughtPreviewsShowFourDistinctSupportedIdeas() {
        val thoughts = (1..8).map { index ->
            DiaryThought("做事准则", "想法 $index", "明确表达", listOf(DiaryThoughtEvidence(index.toLong(), "原文 $index")))
        }
        val unsupported = thoughts.first().copy(statement = "无依据的观点", evidence = emptyList())
        val previews = myThoughtPreviews(thoughts + thoughts.first() + unsupported, Random(23))

        assertEquals(4, previews.size)
        assertEquals(4, previews.distinctBy { it.statement }.size)
        assertTrue(previews.all { it in thoughts })
    }

    @Test fun thoughtPreviewsGiveEverySupportedIdeaAChanceToAppear() {
        val thoughts = (1..6).map { index ->
            DiaryThought("做事准则", "想法 $index", "明确表达", listOf(DiaryThoughtEvidence(index.toLong(), "原文 $index")))
        }
        val displayed = (1..64).flatMap { myThoughtPreviews(thoughts, Random(it)) }.map { it.statement }.toSet()

        assertEquals(setOf("想法 1", "想法 2", "想法 3", "想法 4", "想法 5", "想法 6"), displayed)
    }

    @Test fun thoughtPreviewsDoNotRepeatTheSameStatementAcrossCategories() {
        val thought = DiaryThought("价值观", "先完成重要的事", "明确表达", listOf(DiaryThoughtEvidence(1, "重要的事")))
        val previews = myThoughtPreviews(listOf(thought, thought.copy(category = "做事准则")))

        assertEquals(listOf("先完成重要的事"), previews.map { it.statement })
    }

    @Test fun thoughtPreviewsKeepFullStatementsAndEvidenceStableForTheSameVisit() {
        val statement = "当工作上的事情挤在一起时，你想先完成最重要的一件，再处理琐事，而不是为了清空待办清单把精力分散掉。"
        val explicit = DiaryThought("做事准则", statement, "明确表达", listOf(
            DiaryThoughtEvidence(1, "先完成重要的事"), DiaryThoughtEvidence(2, "不把精力用在琐事上"),
        ))
        val inferred = DiaryThought("价值观", "你可能更看重有意义的进展，而不是完成任务的数量。", "AI归纳", listOf(DiaryThoughtEvidence(3, "忙了一天，却没推进重要的事情")))
        val third = DiaryThought("思维方式", "先试一小步", "明确表达", listOf(DiaryThoughtEvidence(4, "今天决定先试一下")))

        val thoughts = listOf(explicit, inferred, third)
        val previews = myThoughtPreviews(thoughts, Random(23))

        assertEquals(3, previews.size)
        assertEquals(3, previews.distinctBy { it.statement }.size)
        assertTrue(previews.all { it in thoughts })
        assertEquals(previews, myThoughtPreviews(thoughts, Random(23)))
        assertEquals(setOf(explicit, inferred), myThoughtPreviews(listOf(explicit, inferred), Random(23)).toSet())
    }

    @Test fun thoughtPreviewsExcludeBlankOrUnsupportedIdeasAndKeepExistingEvidence() {
        val unsupported = DiaryThought("价值观", "没有依据的想法", "明确表达", emptyList())
        val legacy = unsupported.copy(statement = "旧版想法", basis = "旧版标签", evidence = listOf(
            DiaryThoughtEvidence(1, " "), DiaryThoughtEvidence(2, "真实原文，不要改写。"),
        ))
        assertTrue(myThoughtPreviews(listOf(unsupported)).isEmpty())
        val preview = myThoughtPreviews(listOf(unsupported, legacy)).single()
        assertEquals(legacy, preview)
        assertTrue(myThoughtPreviews(listOf(legacy.copy(statement = " "))).isEmpty())
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
