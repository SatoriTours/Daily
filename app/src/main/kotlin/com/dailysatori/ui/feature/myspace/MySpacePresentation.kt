package com.dailysatori.ui.feature.myspace

import com.dailysatori.service.diary.DiaryThought
import com.dailysatori.ui.feature.diary.diaryThoughtPresentation
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.opportunity.ReadNewsArticle
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderStatus
import com.dailysatori.ui.feature.reminder.*
import kotlinx.datetime.LocalDate

internal data class MyThoughtPreview(val statement: String, val quote: String, val isInference: Boolean)

internal fun myThoughtPreviews(thoughts: List<DiaryThought>): List<MyThoughtPreview> {
    val supported = thoughts.filter { it.statement.isNotBlank() && it.evidence.any { evidence -> evidence.quote.isNotBlank() } }
    return diaryThoughtPresentation(supported).highlights.take(2).map { thought ->
        MyThoughtPreview(thought.statement, thought.evidence.first { it.quote.isNotBlank() }.quote, thought.basis.trim() != "明确表达")
    }
}

enum class OpportunityFilter { PENDING, SAVED, ACTED, IGNORED }

fun opportunityItems(items: List<NewsOpportunity>, filter: OpportunityFilter): List<NewsOpportunity> =
    items.filter { item -> when (filter) {
        OpportunityFilter.PENDING -> !item.ignored && item.reminderId == null
        OpportunityFilter.SAVED -> item.saved && !item.ignored
        OpportunityFilter.ACTED -> item.reminderId != null && !item.ignored
        OpportunityFilter.IGNORED -> item.ignored
    } }.sortedByDescending { it.createdAt }

fun recommendedArticles(items: List<NewsOpportunity>): List<NewsOpportunity> =
    items.filterNot { it.ignored }.sortedByDescending { it.createdAt }.take(3)

fun myUpcomingReminders(reminders: List<Reminder>, today: LocalDate): List<ReminderListItemUi> =
    buildReminderListState(reminders.filter { it.status == ReminderStatus.ACTIVE }, today, ReminderListMode.RECENT, ReminderListFilter())
        .sections.flatMap { it.items }.take(2)

fun opportunityReminderId(id: String): String = "news-opportunity:$id"

/** Same original URL has one reading identity across the local and remote readers. */
fun readNewsKey(url: String?, fallback: String): String = com.dailysatori.service.opportunity.newsArticleKey(url, fallback)

fun ReadNewsArticle.hasReadableBody(): Boolean = title.isNotBlank() && content.isNotBlank()
