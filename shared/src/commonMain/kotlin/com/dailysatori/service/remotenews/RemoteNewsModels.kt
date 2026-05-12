package com.dailysatori.service.remotenews

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteNewsPagination(
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 20,
    val total: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 0,
    val next: Int? = null,
)

@Serializable
data class RemoteArticle(
    val id: Long,
    val title: String? = null,
    val url: String? = null,
    val summary: String? = null,
    val viewpoints: List<String> = emptyList(),
    val status: String? = null,
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("feed_id") val feedId: Long? = null,
    @SerialName("feed_name") val feedName: String? = null,
    val domain: String? = null,
    @SerialName("importance_score") val importanceScore: Double? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("processed_at") val processedAt: String? = null,
    val content: String? = null,
)

@Serializable
data class RemoteDigest(
    val id: Long,
    val date: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val highlights: List<String> = emptyList(),
    val sections: List<RemoteDigestSection> = emptyList(),
    @SerialName("article_count") val articleCount: Int = 0,
    @SerialName("manual_count") val manualCount: Int = 0,
    @SerialName("feed_count") val feedCount: Int = 0,
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    val articles: List<RemoteArticle> = emptyList(),
)

@Serializable
data class RemoteDigestSection(
    val topic: String? = null,
    val title: String? = null,
    val highlights: List<String> = emptyList(),
    val summary: String? = null,
)

@Serializable
data class RemoteFeed(
    val id: Long,
    val name: String? = null,
    val url: String? = null,
    @SerialName("feed_type") val feedType: String? = null,
    val category: String? = null,
    val status: String? = null,
    @SerialName("is_enabled") val isEnabled: Boolean = false,
    @SerialName("is_global") val isGlobal: Boolean = false,
    @SerialName("refresh_interval") val refreshInterval: Int? = null,
    @SerialName("last_fetched_at") val lastFetchedAt: String? = null,
    @SerialName("next_fetch_at") val nextFetchAt: String? = null,
    @SerialName("health_score") val healthScore: Double? = null,
)

@Serializable
data class RemoteArticlesResponse(
    val articles: List<RemoteArticle> = emptyList(),
    val pagination: RemoteNewsPagination = RemoteNewsPagination(),
)

@Serializable
data class RemoteArticleResponse(val article: RemoteArticle)

@Serializable
data class RemoteDigestsResponse(
    val digests: List<RemoteDigest> = emptyList(),
    val pagination: RemoteNewsPagination = RemoteNewsPagination(),
)

@Serializable
data class RemoteDigestResponse(val digest: RemoteDigest)

@Serializable
data class RemoteFeedsResponse(
    val feeds: List<RemoteFeed> = emptyList(),
    val pagination: RemoteNewsPagination = RemoteNewsPagination(),
)

data class RemoteNewsConfigValues(
    val baseUrl: String,
    val token: String,
)

sealed class RemoteNewsResult<out T> {
    data class Success<T>(val value: T) : RemoteNewsResult<T>()
    data class Failure(val message: String) : RemoteNewsResult<Nothing>()
}
