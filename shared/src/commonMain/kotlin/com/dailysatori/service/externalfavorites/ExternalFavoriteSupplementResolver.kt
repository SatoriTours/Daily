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
    val articleContentComplete: Boolean = false,
    val articleModifiedAt: String? = null,
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
    private val fetchXStatusSupplement: suspend (String, Long, FavoriteSyncHttpLogger, Long?) -> ExternalFavoriteSupplement?,
    private val fetchXArticleSupplement: suspend (String, String, Long, FavoriteSyncHttpLogger, Long?) -> ExternalFavoriteSupplement?,
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
        fetchXStatusSupplement = { url, sourceId, httpLogger, taskId ->
            val postId = xPostIdFromStatusLikeUrl(url)
            postId?.let {
                xBookmarksConnector.fetchPostById(null, it, httpLogger, taskId) {
                    refreshedXSupplementSource(sourceRepo, xBookmarksConnector, sourceId)
                }?.toSupplement(url, "x_status")
            }
        },
        fetchXArticleSupplement = { url, postId, sourceId, httpLogger, taskId ->
            xBookmarksConnector.fetchPostById(null, postId, httpLogger, taskId, expectedArticle = true) {
                refreshedXSupplementSource(sourceRepo, xBookmarksConnector, sourceId)
            }?.toSupplement(url, "x_article")
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
            isXArticleUrl(url) || xArticleNeedsBody(item.canonical_url, item.normalized_json) || favoriteHasArticleCache(item) -> xArticleOwningPostId(item)
                ?.let { postId -> fetchXArticleSupplement(url, postId, item.source_id, httpLogger, taskId) }
            isXStatusLikeUrl(url) -> fetchXStatusSupplement(url, item.source_id, httpLogger, taskId)
            else -> fetchWebSupplement(url, httpLogger, taskId)
        }?.takeIf { it.text.isNotBlank() }
    }
}

private suspend fun refreshedXSupplementSource(
    sourceRepo: ExternalFavoriteSourceRepository,
    connector: XBookmarksConnector,
    sourceId: Long,
): com.dailysatori.shared.db.External_favorite_source? {
    val source = sourceRepo.getById(sourceId)?.takeIf { it.provider == ExternalFavoriteProvider.X.id && it.enabled == 1L }
        ?: throw IllegalStateException("该收藏所属 X 来源已删除或停用，无法使用官方授权")
    return connector.refreshAuth(source).also { refreshed ->
        if (refreshed.auth_json != source.auth_json) sourceRepo.updateAuthJson(source.id, refreshed.auth_json)
    }
}

internal fun xArticleOwningPostId(item: External_favorite_item): String? {
    val root = runCatching { supplementJson.parseToJsonElement(item.normalized_json).jsonObject }.getOrNull()
    return root?.stringValue("canonical_tweet_url")
        ?.let(::xPostIdFromStatusLikeUrl)
        ?: item.external_id.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
}

internal fun externalFavoriteSupplementUrl(item: External_favorite_item, input: ExternalFavoriteAiInput): String? {
    val root = runCatching { supplementJson.parseToJsonElement(item.normalized_json).jsonObject }.getOrNull()
    val urls = listOf(root?.stringValue("primary_url"), input.canonicalUrl, previousCachedArticleUrl(item))
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
    urls.firstOrNull(::isXArticleUrl)?.let { return it }
    if (root?.textValue("is_article") == "true") {
        return root.stringValue("canonical_tweet_url")?.takeIf(::isXStatusLikeUrl)
            ?: input.canonicalUrl.takeIf(::isXStatusLikeUrl)
    }
    return urls.firstOrNull { url -> !isShortUrl(url) }
}

internal fun xPostIdFromStatusLikeUrl(url: String): String? =
    Regex("""^https?://(?:mobile\.)?(?:twitter\.com|x\.com)/[^/]+/status/(\d+)(?:[/?#].*)?$""", RegexOption.IGNORE_CASE)
        .matchEntire(url.trim())
        ?.groupValues
        ?.getOrNull(1)

private fun ExternalFavoriteItemDraft.toSupplement(url: String, sourceType: String): ExternalFavoriteSupplement =
    ExternalFavoriteSupplement(
        url = canonicalUrl?.takeIf(::isXArticleUrl) ?: url,
        title = title.takeIf { it.isNotBlank() },
        text = text,
        sourceType = sourceType,
        articleContentComplete = favoriteMetadata(normalizedJson).textValue("article_content_complete") == "true",
        articleModifiedAt = favoriteMetadata(normalizedJson).textValue("article_modified_at"),
    )

private fun isShortUrl(url: String): Boolean =
    Regex("""^https?://t\.co/\S+$""", RegexOption.IGNORE_CASE).matches(url.trim())

private fun kotlinx.serialization.json.JsonObject.stringValue(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

private val supplementJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
