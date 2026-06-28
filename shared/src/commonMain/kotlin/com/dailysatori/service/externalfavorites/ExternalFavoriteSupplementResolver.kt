package com.dailysatori.service.externalfavorites

import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.shared.db.External_favorite_item
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ExternalFavoriteSupplement(
    val url: String,
    val title: String?,
    val text: String,
    val sourceType: String,
)

interface ExternalFavoriteSupplementResolver {
    suspend fun resolve(
        item: External_favorite_item,
        input: ExternalFavoriteAiInput,
        httpLogger: FavoriteSyncHttpLogger = NoopFavoriteSyncHttpLogger,
        taskId: Long? = null,
    ): ExternalFavoriteSupplement?
}

class DefaultExternalFavoriteSupplementResolver(
    private val fetchWebSupplement: suspend (String, FavoriteSyncHttpLogger, Long?) -> ExternalFavoriteSupplement?,
    private val fetchXStatusSupplement: suspend (String, FavoriteSyncHttpLogger, Long?) -> ExternalFavoriteSupplement?,
    private val fetchXArticleSupplement: suspend (String, FavoriteSyncHttpLogger, Long?) -> ExternalFavoriteSupplement?,
) : ExternalFavoriteSupplementResolver {
    constructor(
        sourceRepo: ExternalFavoriteSourceRepository,
        xBookmarksConnector: XBookmarksConnector,
        webpageParserService: WebpageParserService,
    ) : this(
        fetchWebSupplement = { url, httpLogger, taskId ->
            httpLogger.logRequest(
                taskId = taskId,
                label = "external_favorite_supplement",
                method = "GET",
                url = url,
                parameters = mapOf("source" to "web"),
            )
            webpageParserService.extractContent(url).let { extracted ->
                val supplement = ExternalFavoriteSupplement(
                    url = url,
                    title = extracted.title,
                    text = extracted.content.orEmpty(),
                    sourceType = "web",
                )
                httpLogger.logResponse(
                    taskId = taskId,
                    label = "external_favorite_supplement",
                    statusCode = 200,
                    headers = mapOf("source" to "web", "title" to supplement.title.orEmpty()),
                    body = supplement.text,
                )
                supplement
            }
        },
        fetchXStatusSupplement = { url, httpLogger, taskId ->
            val postId = xPostIdFromStatusLikeUrl(url)
            val source = sourceRepo.getEnabled().firstOrNull { it.provider == ExternalFavoriteProvider.X.id }
            if (postId == null || source == null) {
                null
            } else {
                val refreshed = xBookmarksConnector.refreshAuth(source)
                if (refreshed.auth_json != source.auth_json) {
                    sourceRepo.updateAuthJson(source.id, refreshed.auth_json)
                }
                xBookmarksConnector.fetchPostById(refreshed, postId, httpLogger, taskId)?.toSupplement(url, "x_status")
            }
        },
        fetchXArticleSupplement = { url, httpLogger, taskId ->
            val articleId = xArticleIdFromUrl(url)
            val source = sourceRepo.getEnabled().firstOrNull { it.provider == ExternalFavoriteProvider.X.id }
            if (articleId == null || source == null) {
                null
            } else {
                val refreshed = xBookmarksConnector.refreshAuth(source)
                if (refreshed.auth_json != source.auth_json) {
                    sourceRepo.updateAuthJson(source.id, refreshed.auth_json)
                }
                xBookmarksConnector.fetchArticleById(refreshed, articleId, httpLogger, taskId)?.toSupplement(url, "x_article")
            }
        },
    )

    override suspend fun resolve(
        item: External_favorite_item,
        input: ExternalFavoriteAiInput,
        httpLogger: FavoriteSyncHttpLogger,
        taskId: Long?,
    ): ExternalFavoriteSupplement? {
        val url = externalFavoriteSupplementUrl(item, input) ?: return null
        return when {
            isXArticleUrl(url) -> fetchXArticleSupplement(url, httpLogger, taskId)
            isXStatusLikeUrl(url) -> fetchXStatusSupplement(url, httpLogger, taskId)
            else -> fetchWebSupplement(url, httpLogger, taskId)
        }?.takeIf { it.text.isNotBlank() }
    }
}

internal fun externalFavoriteSupplementUrl(item: External_favorite_item, input: ExternalFavoriteAiInput): String? {
    val root = runCatching { supplementJson.parseToJsonElement(item.normalized_json).jsonObject }.getOrNull()
    return listOf(
        root?.stringValue("primary_url"),
        input.canonicalUrl,
    )
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .firstOrNull { url -> !isShortUrl(url) }
}

internal fun xPostIdFromStatusLikeUrl(url: String): String? =
    Regex("""^https?://(?:mobile\.)?(?:twitter\.com|x\.com)/[^/]+/status/(\d+)(?:[/?#].*)?$""", RegexOption.IGNORE_CASE)
        .matchEntire(url.trim())
        ?.groupValues
        ?.getOrNull(1)

private fun ExternalFavoriteItemDraft.toSupplement(url: String, sourceType: String): ExternalFavoriteSupplement =
    ExternalFavoriteSupplement(
        url = url,
        title = title.takeIf { it.isNotBlank() },
        text = text,
        sourceType = sourceType,
    )

private fun isShortUrl(url: String): Boolean =
    Regex("""^https?://t\.co/\S+$""", RegexOption.IGNORE_CASE).matches(url.trim())

private fun kotlinx.serialization.json.JsonObject.stringValue(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

private val supplementJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
