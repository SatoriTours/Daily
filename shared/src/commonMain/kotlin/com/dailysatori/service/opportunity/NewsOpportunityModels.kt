package com.dailysatori.service.opportunity

import kotlinx.serialization.Serializable

@Serializable
data class ReadNewsArticle(
    val key: String,
    val title: String,
    val content: String,
    val url: String? = null,
    val source: String,
    val publishedAt: String? = null,
    val readAt: Long,
    val localArticleId: Long? = null,
)

@Serializable
data class NewsOpportunity(
    val id: String,
    val article: ReadNewsArticle,
    val title: String,
    val category: String,
    val fact: String,
    val relevance: String,
    val action: String,
    val caveat: String,
    val quote: String,
    val createdAt: Long,
    val saved: Boolean = false,
    val ignored: Boolean = false,
    val reminderId: String? = null,
)

@Serializable
data class OpportunityState(
    val items: List<NewsOpportunity> = emptyList(),
    val focus: String = "",
    val readCount: Int = 0,
    val pendingCount: Int = 0,
    val isUpdating: Boolean = false,
    val progress: String = "",
    val error: String? = null,
    val hasAnalysisContext: Boolean = false,
)

@Serializable
data class OpportunityDraft(
    val title: String,
    val category: String,
    val fact: String,
    val relevance: String,
    val action: String,
    val caveat: String,
    val quote: String,
)

data class OpportunityAnalysisInput(
    val article: ReadNewsArticle,
    val focus: String,
    val thoughtContext: String?,
)

fun interface OpportunityAnalyzer {
    suspend fun analyze(input: OpportunityAnalysisInput): OpportunityDraft?
}

interface NewsOpportunityContext {
    val enabled: Boolean
    fun verifiedContext(): String?
}

class NewsOpportunityAnalysisException : IllegalStateException("分析失败，请稍后重试")
