package com.dailysatori.service.ideatopic

import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.shared.db.Diary

/**
 * Pure adapters from the two existing capture entry points. They build snapshots only:
 * no AI call, no repository access and nothing fabricated for steps that never happened.
 */

/** Diary capture. Tags are never treated as AI analysis, and the diary must already be saved. */
fun diaryIdeaCaptureInput(diary: Diary): IdeaCaptureInput {
    require(diary.id > 0) { "Diary must be saved before it can be captured as an idea topic" }
    val content = diary.content.trim()
    return IdeaCaptureInput(
        source = IdeaSourceSnapshot(
            key = IdeaSourceKey(IdeaSourceTypes.Diary, diary.id.toString()),
            originalTitle = diaryInitialTitle(content),
            originalContent = diary.content,
            originalCreatedAt = diary.created_at,
            originalRecordId = diary.id.toString(),
            originalUrl = null,
        ),
        content = IdeaTopicContent(
            title = diaryInitialTitle(content),
            description = "",
        ),
    )
}

/** News opportunity capture: raw article snapshot and the existing AI analysis stay separate. */
fun opportunityIdeaCaptureInput(opportunity: NewsOpportunity): IdeaCaptureInput {
    val article = opportunity.article
    return IdeaCaptureInput(
        source = IdeaSourceSnapshot(
            key = IdeaSourceKey(IdeaSourceTypes.NewsOpportunity, opportunity.id),
            originalTitle = article.title,
            originalContent = article.content,
            originalCreatedAt = opportunity.createdAt,
            originalRecordId = article.localArticleId?.toString(),
            originalUrl = article.url,
            analysisId = opportunity.id,
            analysisContent = opportunityAnalysisText(opportunity),
            analysisCreatedAt = opportunity.createdAt,
            analysisVersion = null,
        ),
        content = IdeaTopicContent(
            title = opportunity.title,
            description = opportunity.fact,
            provenanceSummary = opportunity.quote,
            conclusions = opportunity.relevance,
            nextAction = opportunity.action,
        ),
    )
}

private fun diaryInitialTitle(content: String): String =
    content.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }?.let {
        if (it.length <= DiaryTitleLimit) it else it.take(DiaryTitleLimit)
    }.orEmpty()

private const val DiaryTitleLimit = 60

private fun opportunityAnalysisText(opportunity: NewsOpportunity): String? {
    val sections = listOf(
        "事实" to opportunity.fact,
        "相关性" to opportunity.relevance,
        "建议行动" to opportunity.action,
        "风险与注意" to opportunity.caveat,
        "原文引用" to opportunity.quote,
    ).filter { (_, value) -> value.isNotBlank() }
    if (sections.isEmpty()) return null
    return sections.joinToString("\n") { (label, value) -> "$label：$value" }
}
