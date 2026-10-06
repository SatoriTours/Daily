package com.dailysatori.ui.feature.myspace

import com.dailysatori.service.diary.DiaryThought
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.opportunity.ReadNewsArticle
import com.dailysatori.service.opportunity.OpportunityState
import com.dailysatori.service.opportunity.isOpportunityCancellationMessage
import com.dailysatori.shared.db.Async_task
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderStatus
import com.dailysatori.ui.feature.reminder.*
import kotlinx.datetime.LocalDate
import kotlin.random.Random

internal fun myThoughtPreviews(thoughts: List<DiaryThought>, random: Random = Random.Default): List<DiaryThought> =
    thoughts.filter { it.statement.isNotBlank() && it.evidence.any { evidence -> evidence.quote.isNotBlank() } }
        .distinctBy { it.statement }.shuffled(random).take(4)

internal enum class RecommendationAction { WAIT, SET_UP_CONTEXT, UPDATE }

internal fun recommendationError(state: OpportunityState, task: Async_task?, fallback: String): String? {
    if (state.isUpdating || task?.status in listOf("queued", "running", "retrying")) return null
    state.error?.takeUnless(::isOpportunityCancellationMessage)?.let { return it }
    if (task == null || task.status != "failed" || task.id == state.dismissedErrorTaskId) return null
    if (task.last_error_code == "interrupted" || isOpportunityCancellationMessage(task.last_error_message)) return null
    return fallback
}

internal fun recommendationAction(hasAnalysisContext: Boolean, isUpdating: Boolean, taskStatus: String?): RecommendationAction = when {
    isUpdating || taskStatus in listOf("queued", "running", "retrying") -> RecommendationAction.WAIT
    !hasAnalysisContext -> RecommendationAction.SET_UP_CONTEXT
    else -> RecommendationAction.UPDATE
}

enum class OpportunityFilter { PENDING, SAVED, ACTED, IGNORED }

fun opportunityItems(items: List<NewsOpportunity>, filter: OpportunityFilter): List<NewsOpportunity> =
    items.filter { item -> when (filter) {
        OpportunityFilter.PENDING -> !item.ignored && item.reminderId == null
        OpportunityFilter.SAVED -> item.saved && !item.ignored
        OpportunityFilter.ACTED -> item.reminderId != null && !item.ignored
        OpportunityFilter.IGNORED -> item.ignored
    } }.let { filtered ->
        if (filter == OpportunityFilter.PENDING || filter == OpportunityFilter.SAVED) rankedOpportunities(filtered)
        else filtered.sortedByDescending { it.createdAt }
    }

fun recommendedArticles(items: List<NewsOpportunity>): List<NewsOpportunity> =
    opportunityItems(items, OpportunityFilter.PENDING).take(5)

internal fun rankedOpportunities(
    items: List<NewsOpportunity>,
    now: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
): List<NewsOpportunity> = items.sortedWith(
    compareByDescending<NewsOpportunity> { it.saved }
        .thenByDescending { if (it.saved) it.savedAt ?: it.createdAt else 0L }
        .thenByDescending { item ->
            val published = runCatching { kotlinx.datetime.Instant.parse(item.article.publishedAt.orEmpty()).toEpochMilliseconds() }
                .getOrDefault(item.createdAt)
            val ageDays = ((now - published).coerceAtLeast(0) / 86_400_000.0).coerceAtMost(30.0)
            item.relevanceScore.coerceIn(0, 100) * 0.6 + item.actionabilityScore.coerceIn(0, 100) * 0.3 +
                (1 - ageDays / 30) * 10
        }.thenByDescending { it.createdAt }.thenBy { it.id },
)

fun myUpcomingReminders(reminders: List<Reminder>, today: LocalDate): List<ReminderListItemUi> =
    buildReminderListState(reminders, today, ReminderListMode.RECENT,
        ReminderListFilter(statuses = setOf(ReminderStatus.ACTIVE, ReminderStatus.NOTIFIED, ReminderStatus.DISMISSED)))
        .sections.flatMap { it.items }.take(2)

fun opportunityReminderId(id: String): String = "news-opportunity:$id"

/** Same original URL has one reading identity across the local and remote readers. */
fun readNewsKey(url: String?, fallback: String): String = com.dailysatori.service.opportunity.newsArticleKey(url, fallback)

fun ReadNewsArticle.hasReadableBody(): Boolean = title.isNotBlank() && content.isNotBlank()
