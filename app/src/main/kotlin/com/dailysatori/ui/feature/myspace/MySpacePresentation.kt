package com.dailysatori.ui.feature.myspace

import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.opportunity.ReadNewsArticle
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderStatus
import com.dailysatori.ui.feature.reminder.*
import kotlinx.datetime.LocalDate

enum class OpportunityFilter { PENDING, SAVED, ACTED, IGNORED }

fun opportunityItems(items: List<NewsOpportunity>, filter: OpportunityFilter): List<NewsOpportunity> =
    items.filter { item -> when (filter) {
        OpportunityFilter.PENDING -> !item.ignored && item.reminderId == null
        OpportunityFilter.SAVED -> item.saved && !item.ignored
        OpportunityFilter.ACTED -> item.reminderId != null && !item.ignored
        OpportunityFilter.IGNORED -> item.ignored
    } }.sortedByDescending { it.createdAt }

fun myUpcomingReminders(reminders: List<Reminder>, today: LocalDate): List<ReminderListItemUi> =
    buildReminderListState(reminders.filter { it.status == ReminderStatus.ACTIVE }, today, ReminderListMode.RECENT, ReminderListFilter())
        .sections.flatMap { it.items }.take(2)

fun opportunityReminderId(id: String): String = "news-opportunity:$id"

/** Same original URL has one reading identity across the local and remote readers. */
fun readNewsKey(url: String?, fallback: String): String = url?.trim()?.substringBefore('#')
    ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { "url:$it" } ?: fallback

fun ReadNewsArticle.hasReadableBody(): Boolean = title.isNotBlank() && content.isNotBlank()
