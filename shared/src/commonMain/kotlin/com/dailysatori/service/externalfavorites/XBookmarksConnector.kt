package com.dailysatori.service.externalfavorites

import co.touchlab.kermit.Logger
import com.dailysatori.shared.db.External_favorite_source
import com.dailysatori.shared.isDevelopmentBuild
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class XBookmarksConnector(
    private val client: HttpClient? = null,
    private val apiBaseUrl: String = "https://api.x.com",
    @Suppress("unused") private val developmentMode: Boolean = isDevelopmentBuild(),
) : FavoriteConnector {
    private val log = Logger.withTag("XBookmarksConnector")

    override val provider: String = ExternalFavoriteProvider.X.id

    override val capabilities: FavoriteConnectorCapabilities = FavoriteConnectorCapabilities(
        maxPageSize = X_BOOKMARKS_MAX_PAGE_SIZE,
        defaultBackoffMinutes = 15,
        maxPagesPerRun = X_BOOKMARKS_DEFAULT_MAX_ITEMS_PER_RUN / X_BOOKMARKS_MAX_PAGE_SIZE,
        maxItemsPerRun = X_BOOKMARKS_DEFAULT_MAX_ITEMS_PER_RUN,
        supportsFolders = false,
        supportsFavoritedAt = false,
        supportsWriteBack = false,
        supportsRefreshToken = true,
    )

    override suspend fun refreshAuth(source: External_favorite_source): External_favorite_source {
        val now = Clock.System.now().toEpochMilliseconds()
        if (!xAuthShouldRefresh(source.auth_json, now)) return source
        val auth = parseXAuth(source.auth_json) ?: return source
        if (auth.clientId.isBlank() || auth.refreshToken.isBlank()) return source
        val httpClient = client ?: error("XBookmarksConnector requires an HttpClient to refresh OAuth tokens")
        val response = httpClient.submitForm(
            url = "$apiBaseUrl/2/oauth2/token",
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", auth.refreshToken)
                append("client_id", auth.clientId)
            },
        )
        val body = response.bodyAsText()
        if (response.status.value == 401 || response.status.value == 403) {
            throw XFavoriteAuthException(response.status.value, xProviderErrorDetail(body))
        }
        if (response.status.value !in 200..299) {
            throw XFavoriteProviderException(
                statusCode = response.status.value,
                message = "X OAuth token refresh failed with HTTP ${response.status.value}",
            )
        }
        val token = parseXRefreshTokenResponse(body)
        return source.copy(
            auth_json = xRefreshedAuthJson(
                existingAuthJson = source.auth_json,
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                expiresInSeconds = token.expiresInSeconds,
                nowMillis = now,
                scope = token.scope,
                tokenType = token.tokenType,
            ),
        )
    }

    override suspend fun fetchPage(
        source: External_favorite_source,
        cursor: String?,
        pageSize: Int,
        httpLogger: FavoriteSyncHttpLogger,
        taskId: Long?,
        shouldFetchDetail: FavoriteFetchDetailPolicy,
        sinceExternalId: String?,
    ): FavoriteFetchPage {
        val httpClient = client ?: error("XBookmarksConnector requires an HttpClient to fetch remote bookmarks")
        val token = extractXAccessToken(source.auth_json)
            ?: error("X bookmarks auth_json must contain access_token, bearer_token, or token")
        val requestPath = xBookmarksEndpointPath(source.account_id)
        val requestParameters = xBookmarksRequestParameters(
            pageSize = pageSize.coerceIn(1, capabilities.maxPageSize),
            cursor = cursor,
        )
        httpLogger.logRequest(
            taskId = taskId,
            label = "bookmarks",
            method = "GET",
            url = "$apiBaseUrl$requestPath",
            parameters = requestParameters,
        )
        val response = httpClient.get("$apiBaseUrl$requestPath") {
            bearerAuth(token)
            requestParameters.forEach { (key, value) -> parameter(key, value) }
        }
        val body = response.bodyAsText()
        httpLogger.logResponse(
            taskId = taskId,
            label = "bookmarks",
            statusCode = response.status.value,
            headers = xDiagnosticHeaders(response.headers[X_RATE_LIMIT_RESET_HEADER]),
            body = body,
        )
        logXApiResponseBody(
            label = "bookmarks",
            statusCode = response.status.value,
            metadata = "account=${source.account_id}, cursor=${cursor.orEmpty()}, pageSize=${pageSize.coerceIn(1, capabilities.maxPageSize)}, sinceExternalId=${sinceExternalId.orEmpty()}",
            body = body,
        )
        val page = parseXBookmarksHttpResponse(
            statusCode = response.status.value,
            body = body,
            headers = mapOf(
                X_RATE_LIMIT_RESET_HEADER to response.headers[X_RATE_LIMIT_RESET_HEADER].orEmpty(),
            ),
        )
        return enrichPageWithFetchedReferencedPosts(source, page, httpLogger, taskId, shouldFetchDetail)
    }

    suspend fun fetchPostById(
        source: External_favorite_source,
        postId: String,
        httpLogger: FavoriteSyncHttpLogger = NoopFavoriteSyncHttpLogger,
        taskId: Long? = null,
    ): ExternalFavoriteItemDraft? {
        val httpClient = client ?: error("XBookmarksConnector requires an HttpClient to fetch posts")
        val token = extractXAccessToken(source.auth_json)
            ?: error("X auth_json must contain access_token, bearer_token, or token")
        val requestUrl = "$apiBaseUrl/2/tweets/${postId.trim()}"
        val requestParameters = xPostLookupRequestParameters()
        httpLogger.logRequest(
            taskId = taskId,
            label = "post_lookup",
            method = "GET",
            url = requestUrl,
            parameters = requestParameters,
        )
        val response = httpClient.get(requestUrl) {
            bearerAuth(token)
            requestParameters.forEach { (key, value) -> parameter(key, value) }
        }
        val body = response.bodyAsText()
        httpLogger.logResponse(
            taskId = taskId,
            label = "post_lookup",
            statusCode = response.status.value,
            headers = xDiagnosticHeaders(response.headers[X_RATE_LIMIT_RESET_HEADER]),
            body = body,
        )
        logXApiResponseBody(
            label = "post_lookup",
            statusCode = response.status.value,
            metadata = "account=${source.account_id}, postId=${postId.trim()}",
            body = body,
        )
        return parseXPostLookupHttpResponse(
            statusCode = response.status.value,
            body = body,
            headers = mapOf(
                X_RATE_LIMIT_RESET_HEADER to response.headers[X_RATE_LIMIT_RESET_HEADER].orEmpty(),
            ),
        )
    }

    private suspend fun enrichPageWithFetchedReferencedPosts(
        source: External_favorite_source,
        page: FavoriteFetchPage,
        httpLogger: FavoriteSyncHttpLogger,
        taskId: Long?,
        shouldFetchDetail: FavoriteFetchDetailPolicy,
    ): FavoriteFetchPage {
        val referencedIdsByExternalId = page.items.associate { item ->
            item.externalId to xReferencedPostIdsFromDraft(item)
        }
        val fetchedPosts = page.items
            .filter(shouldFetchDetail)
            .map { item -> referencedIdsByExternalId[item.externalId].orEmpty() }
            .flatten()
            .distinct()
            .associateWith { postId ->
                runCatching { fetchPostById(source, postId, httpLogger, taskId) }.getOrNull()
            }
        val referencedEnrichedItems = page.items.map { item ->
            if (!shouldFetchDetail(item)) return@map item
            referencedIdsByExternalId[item.externalId]
                .orEmpty()
                .firstNotNullOfOrNull { postId -> xBookmarkItemWithFetchedReferencedPost(item, fetchedPosts[postId]) }
                ?: item
        }
        val articleEnrichedItems = referencedEnrichedItems.map { item ->
            if (!shouldFetchDetail(item)) return@map item
            val articleId = xArticleIdFromUrl(item.canonicalUrl.orEmpty()) ?: return@map item
            val articleDraft = runCatching { fetchArticleById(source, articleId, httpLogger, taskId) }.getOrNull()
            xBookmarkItemWithFetchedReferencedPost(item, articleDraft) ?: item
        }
        return page.copy(items = articleEnrichedItems)
    }

    suspend fun fetchArticleById(
        source: External_favorite_source,
        articleId: String,
        httpLogger: FavoriteSyncHttpLogger,
        taskId: Long?,
    ): ExternalFavoriteItemDraft? {
        val httpClient = client ?: error("XBookmarksConnector requires an HttpClient to fetch X articles")
        val token = extractXAccessToken(source.auth_json)
            ?: error("X auth_json must contain access_token, bearer_token, or token")
        val requestUrl = "$apiBaseUrl/2/posts/${articleId.trim()}"
        val requestParameters = xPostLookupRequestParameters()
        httpLogger.logRequest(
            taskId = taskId,
            label = "article_lookup",
            method = "GET",
            url = requestUrl,
            parameters = requestParameters,
        )
        val response = httpClient.get(requestUrl) {
            bearerAuth(token)
            requestParameters.forEach { (key, value) -> parameter(key, value) }
        }
        val body = response.bodyAsText()
        httpLogger.logResponse(
            taskId = taskId,
            label = "article_lookup",
            statusCode = response.status.value,
            headers = xDiagnosticHeaders(response.headers[X_RATE_LIMIT_RESET_HEADER]),
            body = body,
        )
        logXApiResponseBody(
            label = "article_lookup",
            statusCode = response.status.value,
            metadata = "account=${source.account_id}, articleId=${articleId.trim()}",
            body = body,
        )
        return parseXPostLookupHttpResponse(
            statusCode = response.status.value,
            body = body,
            headers = mapOf(
                X_RATE_LIMIT_RESET_HEADER to response.headers[X_RATE_LIMIT_RESET_HEADER].orEmpty(),
            ),
        )
    }

    private fun logXApiResponseBody(
        label: String,
        statusCode: Int,
        metadata: String,
        body: String,
    ) {
        val chunks = body.chunked(X_API_LOG_CHUNK_SIZE).ifEmpty { listOf("") }
        chunks.forEachIndexed { index, chunk ->
            log.i {
                "X API response [$label] status=$statusCode $metadata chunk=${index + 1}/${chunks.size}: $chunk"
            }
        }
    }
}

internal fun xBookmarksEndpointPath(userId: String): String =
    "/2/users/${userId.trim()}/bookmarks"

open class XFavoriteProviderException(
    val statusCode: Int,
    message: String,
) : RuntimeException(message)

class XFavoriteAuthException(
    statusCode: Int,
    providerDetail: String? = null,
) : XFavoriteProviderException(
    statusCode = statusCode,
    message = buildString {
        append("X bookmarks authorization failed with HTTP ")
        append(statusCode)
        providerDetail?.takeIf { it.isNotBlank() }?.let {
            append(": ")
            append(it)
        }
    },
)

class XFavoriteRateLimitException(
    statusCode: Int,
    val rateLimitResetAt: Long?,
) : XFavoriteProviderException(
    statusCode = statusCode,
    message = "X bookmarks rate limited with HTTP $statusCode",
)

private const val X_RATE_LIMIT_RESET_HEADER = "x-rate-limit-reset"
private const val X_REFRESH_BUFFER_MS = 60_000L
private const val X_BOOKMARKS_MAX_PAGE_SIZE = 100
private const val X_BOOKMARKS_DEFAULT_MAX_ITEMS_PER_RUN = 5_000
private const val X_BOOKMARKS_TWEET_FIELDS =
    "created_at,author_id,attachments,entities,note_tweet,referenced_tweets,conversation_id,lang,public_metrics"
private const val X_BOOKMARKS_USER_FIELDS = "username,name,profile_image_url,verified"
private const val X_BOOKMARKS_EXPANSIONS = "author_id,attachments.media_keys,referenced_tweets.id,referenced_tweets.id.author_id"
private const val X_BOOKMARKS_MEDIA_FIELDS = "media_key,type,url,preview_image_url,alt_text,width,height"
private const val X_API_LOG_CHUNK_SIZE = 3_000

private fun xBookmarksRequestParameters(pageSize: Int, cursor: String?): Map<String, String> = buildMap {
    put("max_results", pageSize.toString())
    put("tweet.fields", X_BOOKMARKS_TWEET_FIELDS)
    put("user.fields", X_BOOKMARKS_USER_FIELDS)
    put("expansions", X_BOOKMARKS_EXPANSIONS)
    put("media.fields", X_BOOKMARKS_MEDIA_FIELDS)
    cursor?.takeIf { it.isNotBlank() }?.let { put("pagination_token", it) }
}

private fun xPostLookupRequestParameters(): Map<String, String> = mapOf(
    "tweet.fields" to X_BOOKMARKS_TWEET_FIELDS,
    "user.fields" to X_BOOKMARKS_USER_FIELDS,
    "expansions" to X_BOOKMARKS_EXPANSIONS,
    "media.fields" to X_BOOKMARKS_MEDIA_FIELDS,
)

private fun xDiagnosticHeaders(rateLimitReset: String?): Map<String, String> =
    rateLimitReset?.takeIf { it.isNotBlank() }?.let { mapOf(X_RATE_LIMIT_RESET_HEADER to it) }.orEmpty()

fun parseXBookmarksHttpResponse(
    statusCode: Int,
    body: String,
    headers: Map<String, String> = emptyMap(),
): FavoriteFetchPage {
    if (statusCode in 200..299) return XBookmarksResponseParser.parse(body)
    if (statusCode == 401 || statusCode == 403) throw XFavoriteAuthException(statusCode, xProviderErrorDetail(body))
    if (statusCode == 429) {
        throw XFavoriteRateLimitException(
            statusCode = statusCode,
            rateLimitResetAt = headers.headerValue(X_RATE_LIMIT_RESET_HEADER)?.toRateLimitResetMillis(),
        )
    }
    throw XFavoriteProviderException(
        statusCode = statusCode,
        message = buildString {
            append("X bookmarks provider request failed with HTTP ")
            append(statusCode)
            xProviderErrorDetail(body)?.let {
                append(": ")
                append(it)
            }
        },
    )
}

fun parseXPostLookupHttpResponse(
    statusCode: Int,
    body: String,
    headers: Map<String, String> = emptyMap(),
): ExternalFavoriteItemDraft? {
    if (statusCode in 200..299) return XBookmarksResponseParser.parsePostLookup(body)
    if (statusCode == 401 || statusCode == 403) throw XFavoriteAuthException(statusCode, xProviderErrorDetail(body))
    if (statusCode == 429) {
        throw XFavoriteRateLimitException(
            statusCode = statusCode,
            rateLimitResetAt = headers.headerValue(X_RATE_LIMIT_RESET_HEADER)?.toRateLimitResetMillis(),
        )
    }
    throw XFavoriteProviderException(
        statusCode = statusCode,
        message = buildString {
            append("X post lookup request failed with HTTP ")
            append(statusCode)
            xProviderErrorDetail(body)?.let {
                append(": ")
                append(it)
            }
        },
    )
}

internal fun xReferencedPostIdsFromDraft(item: ExternalFavoriteItemDraft): List<String> = runCatching {
    Json.parseToJsonElement(item.normalizedJson)
        .jsonObject
        .get("referenced_tweets")
        ?.jsonArrayOrNull()
        .orEmpty()
        .mapNotNull { it.jsonObjectOrNull()?.string("id")?.takeIf(String::isNotBlank) }
        .distinct()
}.getOrDefault(emptyList())

internal fun xArticleIdFromUrl(url: String): String? =
    Regex("""^https?://(?:mobile\.)?(?:twitter\.com|x\.com)/i/article/(\d+)(?:[/?#].*)?$""", RegexOption.IGNORE_CASE)
        .matchEntire(url.trim())
        ?.groupValues
        ?.getOrNull(1)

internal fun xBookmarkItemWithFetchedReferencedPost(
    item: ExternalFavoriteItemDraft,
    fetchedPost: ExternalFavoriteItemDraft?,
): ExternalFavoriteItemDraft? {
    if (fetchedPost == null) return null
    val canonicalUrl = item.canonicalUrl?.takeIf(::isXArticleUrl) ?: fetchedPost.canonicalUrl ?: item.canonicalUrl
    val bookmarkedArticleTitle = item.canonicalUrl
        ?.takeIf(::isXArticleUrl)
        ?.let { xUrlTitleFromNormalizedTweetJson(item.normalizedJson) }
    val title = bookmarkedArticleTitle ?: fetchedPost.title.takeIf { it.isNotBlank() } ?: item.title
    val text = fetchedPost.text.takeIf { it.isNotBlank() } ?: item.text
    val authorName = fetchedPost.authorName.takeIf { it.isNotBlank() } ?: item.authorName
    val normalizedJson = fetchedPost.normalizedJson
    val sourceCreatedAt = fetchedPost.sourceCreatedAt ?: item.sourceCreatedAt
    val mediaUrls = xMediaUrlsFromNormalizedTweetJson(normalizedJson)
    return item.copy(
        canonicalUrl = canonicalUrl,
        title = title,
        text = text,
        authorName = authorName,
        sourceCreatedAt = sourceCreatedAt,
        normalizedJson = normalizedJson,
        contentHash = sha256Hex(listOf(item.externalId, text, authorName, normalizedJson).joinToString("\n")),
        aiInputHash = sha256Hex(aiInputHashText(item.externalId, canonicalUrl, text, authorName, sourceCreatedAt, mediaUrls)),
    )
}

private fun xMediaUrlsFromNormalizedTweetJson(normalizedJson: String): List<String> = runCatching {
    val root = Json.parseToJsonElement(normalizedJson).jsonObject
    root["media"]
        ?.jsonArrayOrNull()
        ?.mapNotNull { media ->
            val obj = media.jsonObjectOrNull() ?: return@mapNotNull null
            obj.string("url") ?: obj.string("preview_image_url")
        }
        .orEmpty()
}.getOrDefault(emptyList())

private fun xUrlTitleFromNormalizedTweetJson(normalizedJson: String): String? = runCatching {
    Json.parseToJsonElement(normalizedJson)
        .jsonObject["url_title"]
        ?.jsonPrimitiveOrNull()
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
}.getOrNull()

object XBookmarksResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun parse(rawJson: String): FavoriteFetchPage {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val usersById = root["includes"]
            ?.jsonObjectOrNull()
            ?.get("users")
            ?.jsonArrayOrNull()
            ?.mapNotNull { user ->
                val obj = user.jsonObjectOrNull() ?: return@mapNotNull null
                val id = obj.string("id") ?: return@mapNotNull null
                id to XUser(
                    username = obj.string("username"),
                    name = obj.string("name"),
                    profileImageUrl = obj.string("profile_image_url"),
                    verified = obj.boolean("verified"),
                )
            }
            ?.toMap()
            .orEmpty()
        val mediaByKey = root["includes"]
            ?.jsonObjectOrNull()
            ?.get("media")
            ?.jsonArrayOrNull()
            ?.mapNotNull { media ->
                val obj = media.jsonObjectOrNull() ?: return@mapNotNull null
                val key = obj.string("media_key") ?: return@mapNotNull null
                key to obj
            }
            ?.toMap()
            .orEmpty()
        val tweetsById = root["includes"]
            ?.jsonObjectOrNull()
            ?.get("tweets")
            ?.jsonArrayOrNull()
            ?.mapNotNull { tweet ->
                val obj = tweet.jsonObjectOrNull() ?: return@mapNotNull null
                val id = obj.string("id") ?: return@mapNotNull null
                id to obj
            }
            ?.toMap()
            .orEmpty()

        val items = root.tweetDataElements()
            .mapNotNull { tweet -> tweet.toDraft(usersById, mediaByKey, tweetsById) }
        val nextCursor = root["meta"]
            ?.jsonObjectOrNull()
            ?.string("next_token")
            ?.takeIf { it.isNotBlank() }

        return FavoriteFetchPage(
            items = items,
            nextCursor = nextCursor,
            rateLimitResetAt = null,
        )
    }

    fun parsePostLookup(rawJson: String): ExternalFavoriteItemDraft? =
        parse(rawJson).items.firstOrNull()

    private fun JsonObject.tweetDataElements(): List<JsonElement> {
        val data = get("data") ?: return emptyList()
        data.jsonArrayOrNull()?.let { return it }
        data.jsonObjectOrNull()?.let { return listOf(it) }
        return emptyList()
    }

    private fun JsonElement.toDraft(
        usersById: Map<String, XUser>,
        mediaByKey: Map<String, JsonObject>,
        tweetsById: Map<String, JsonObject>,
    ): ExternalFavoriteItemDraft? {
        val tweet = jsonObjectOrNull() ?: return null
        val id = tweet.string("id") ?: return null
        val noteText = tweet["note_tweet"]?.jsonObjectOrNull()?.string("text")
        val articleObject = tweet["article"]?.jsonObjectOrNull()
        val articleText = articleObject?.articleContentText()
        val text = noteText?.takeIf { it.isNotBlank() }
            ?: articleText?.takeIf { it.isNotBlank() }
            ?: tweet.string("content")?.takeIf { it.isNotBlank() }
            ?: tweet.string("text").orEmpty()
        val author = usersById[tweet.string("author_id")]
        val referencedTweets = tweet["referenced_tweets"]?.jsonArrayOrNull().orEmpty()
        val referencedTweetObjects = referencedTweets
            .mapNotNull { it.jsonObjectOrNull()?.string("id") }
            .mapNotNull { tweetsById[it] }
        val urls = tweet.xUrlEntities() + referencedTweetObjects.flatMap { it.xUrlEntities() }
        val media = tweet["attachments"]
            ?.jsonObjectOrNull()
            ?.get("media_keys")
            ?.jsonArrayOrNull()
            ?.mapNotNull { key -> key.jsonPrimitiveOrNull()?.contentOrNull }
            ?.mapNotNull { mediaByKey[it] }
            .orEmpty()
        val createdAt = tweet.string("created_at")?.let(::parseInstantMillis)
        val primaryUrl = urls.firstNotNullOfOrNull { it.primaryUrl }
        val rawTextForArticleLinkDetection = noteText?.takeIf { it.isNotBlank() } ?: tweet.string("text").orEmpty()
        val articleTitle = articleObject?.string("title")?.takeIf { it.isNotBlank() }
        val canonicalArticleUrl = xArticleUrlFromArticleCard(articleTitle, urls)
            ?: xArticleUrlWhenTextOnlyContainsArticleLink(rawTextForArticleLinkDetection, urls)
        val cardTitle = urls.firstNotNullOfOrNull { it.title } ?: articleTitle
        val cardDescription = urls.firstNotNullOfOrNull { it.description }
        val urlImages = urls.flatMap { it.images }
        val tweetUrl = xStatusUrl(id, author?.username)
        val normalizedJson = normalizedTweetJson(
            id = id,
            text = tweet.string("text").orEmpty(),
            noteText = noteText,
            author = author,
            createdAt = tweet.string("created_at"),
            canonicalTweetUrl = tweetUrl,
            primaryUrl = primaryUrl,
            urlTitle = cardTitle,
            urlDescription = cardDescription,
            urlImages = urlImages,
            media = media,
            referencedTweets = referencedTweets,
            publicMetrics = tweet["public_metrics"]?.jsonObjectOrNull(),
            lang = tweet.string("lang"),
        )
        val authorName = author?.name ?: author?.username.orEmpty()
        val hashInput = listOf(id, text, authorName, normalizedJson).joinToString("\n")
        val canonicalUrl = canonicalArticleUrl ?: tweetUrl
        val mediaUrls = media.mapNotNull { it.string("url") ?: it.string("preview_image_url") }.sorted()

        return ExternalFavoriteItemDraft(
            provider = ExternalFavoriteProvider.X.id,
            externalId = id,
            canonicalUrl = canonicalUrl,
            title = cardTitle ?: text,
            text = text,
            authorName = authorName,
            sourceCreatedAt = createdAt,
            favoritedAt = null,
            normalizedJson = normalizedJson,
            debugJson = "",
            contentHash = sha256Hex(hashInput),
            aiInputHash = sha256Hex(aiInputHashText(id, canonicalUrl, text, authorName, createdAt, mediaUrls)),
        )
    }

    private fun normalizedTweetJson(
        id: String,
        text: String,
        noteText: String?,
        author: XUser?,
        createdAt: String?,
        canonicalTweetUrl: String,
        primaryUrl: String?,
        urlTitle: String?,
        urlDescription: String?,
        urlImages: List<String>,
        media: List<JsonObject>,
        referencedTweets: List<JsonElement>,
        publicMetrics: JsonObject?,
        lang: String?,
    ): String = json.encodeToString(
        buildJsonObject {
            put("id", id)
            put("text", text)
            noteText?.let { put("note_text", it) }
            if (author != null) {
                put("author", buildJsonObject {
                    author.username?.let { put("username", it) }
                    author.name?.let { put("name", it) }
                    author.profileImageUrl?.let { put("profile_image_url", it) }
                    author.verified?.let { put("verified", it) }
                })
            }
            createdAt?.let { put("created_at", it) }
            put("canonical_tweet_url", canonicalTweetUrl)
            primaryUrl?.let { put("primary_url", it) }
            urlTitle?.let { put("url_title", it) }
            urlDescription?.let { put("url_description", it) }
            if (urlImages.isNotEmpty()) {
                putJsonArray("url_images") {
                    urlImages.forEach { add(JsonPrimitive(it)) }
                }
            }
            if (media.isNotEmpty()) {
                putJsonArray("media") {
                    media.forEach { item ->
                        add(
                            buildJsonObject {
                                item.string("media_key")?.let { put("media_key", it) }
                                item.string("type")?.let { put("type", it) }
                                item.string("url")?.let { put("url", it) }
                                item.string("preview_image_url")?.let { put("preview_image_url", it) }
                                item.string("alt_text")?.let { put("alt_text", it) }
                                item.long("width")?.let { put("width", it) }
                                item.long("height")?.let { put("height", it) }
                            },
                        )
                    }
                }
            }
            if (referencedTweets.isNotEmpty()) {
                putJsonArray("referenced_tweets") {
                    referencedTweets.forEach { add(it) }
                }
            }
            publicMetrics?.let { put("public_metrics", it) }
            lang?.let { put("lang", it) }
        },
    )
}

private data class XUser(
    val username: String?,
    val name: String?,
    val profileImageUrl: String?,
    val verified: Boolean?,
)

private data class XUrlEntity(
    val primaryUrl: String?,
    val shortUrl: String?,
    val title: String?,
    val description: String?,
    val images: List<String>,
)

private fun xArticleUrlWhenTextOnlyContainsArticleLink(text: String, urls: List<XUrlEntity>): String? {
    val articleUrl = urls.mapNotNull { it.primaryUrl?.trim()?.takeIf(::isXArticleUrl) }
        .distinct()
        .singleOrNull()
        ?: text.onlyLinkValue()?.takeIf(::isXArticleUrl)
        ?: return null
    if (urls.size > 1) return null
    val link = text.onlyLinkValue() ?: return null
    val matchesEntityLink = urls.isEmpty() || urls.any { entity ->
        listOfNotNull(entity.shortUrl, entity.primaryUrl).any { sameUrlIgnoringTrailingSlash(it, link) }
    }
    return if (matchesEntityLink) articleUrl else null
}

private fun xArticleUrlFromArticleCard(articleTitle: String?, urls: List<XUrlEntity>): String? {
    if (articleTitle.isNullOrBlank()) return null
    return urls.mapNotNull { it.primaryUrl?.trim()?.takeIf(::isXArticleUrl) }
        .distinct()
        .singleOrNull()
}

private fun String.onlyLinkValue(): String? {
    val link = trim()
        .removePrefix("链接：")
        .removePrefix("链接:")
        .removePrefix("Link:")
        .removePrefix("link:")
        .trim()
    return link.takeIf {
        Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE).matches(it)
    }
}

private fun sameUrlIgnoringTrailingSlash(left: String, right: String): Boolean =
    left.trim().trimEnd('/') == right.trim().trimEnd('/')

private fun JsonObject.xUrlEntities(): List<XUrlEntity> {
    val noteUrls = this["note_tweet"]
        ?.jsonObjectOrNull()
        ?.get("entities")
        ?.jsonObjectOrNull()
        ?.get("urls")
        ?.jsonArrayOrNull()
        .orEmpty()
    val tweetUrls = this["entities"]
        ?.jsonObjectOrNull()
        ?.get("urls")
        ?.jsonArrayOrNull()
        .orEmpty()
    return (noteUrls + tweetUrls)
        .mapNotNull { it.jsonObjectOrNull()?.toXUrlEntity() }
}

private fun JsonObject.articleContentText(): String? =
    string("body")
        ?: string("content")
        ?: string("text")
        ?: string("description")

private fun JsonObject.toXUrlEntity(): XUrlEntity? {
    val primaryUrl = string("unwound_url")
        ?: string("expanded_url")
        ?: string("url")
    if (primaryUrl.isNullOrBlank() && string("title").isNullOrBlank() && string("description").isNullOrBlank()) return null
    return XUrlEntity(
        primaryUrl = primaryUrl,
        shortUrl = string("url")?.takeIf { it.isNotBlank() },
        title = string("title")?.takeIf { it.isNotBlank() },
        description = string("description")?.takeIf { it.isNotBlank() },
        images = get("images")
            ?.jsonArrayOrNull()
            ?.mapNotNull { image -> image.jsonObjectOrNull()?.string("url")?.takeIf { it.isNotBlank() } }
            .orEmpty(),
    )
}

private data class XAuth(
    val clientId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long?,
)

private data class XRefreshTokenPayload(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long?,
    val scope: String,
    val tokenType: String,
)

internal fun xAuthShouldRefresh(authJson: String, nowMillis: Long): Boolean {
    val auth = parseXAuth(authJson) ?: return false
    val expiresAt = auth.expiresAt ?: return false
    return auth.refreshToken.isNotBlank() && expiresAt <= nowMillis + X_REFRESH_BUFFER_MS
}

internal fun xRefreshedAuthJson(
    existingAuthJson: String,
    accessToken: String,
    refreshToken: String,
    expiresInSeconds: Long?,
    nowMillis: Long,
    scope: String,
    tokenType: String,
): String {
    val existing = parseXAuthJson(existingAuthJson)
    val nextRefreshToken = refreshToken.ifBlank {
        existing?.string("refresh_token").orEmpty()
    }
    val clientId = existing?.string("client_id").orEmpty()
    return Json.encodeToString(
        buildJsonObject {
            if (clientId.isNotBlank()) put("client_id", clientId)
            put("access_token", accessToken)
            if (nextRefreshToken.isNotBlank()) put("refresh_token", nextRefreshToken)
            expiresInSeconds?.let {
                put("expires_in", it)
                put("expires_at", nowMillis + it * 1_000L)
            }
            put("issued_at", nowMillis)
            val nextScope = scope.ifBlank { existing?.string("scope").orEmpty() }
            if (nextScope.isNotBlank()) put("scope", nextScope)
            val nextTokenType = tokenType.ifBlank { existing?.string("token_type").orEmpty() }
            if (nextTokenType.isNotBlank()) put("token_type", nextTokenType)
        },
    )
}

private fun parseXAuth(authJson: String): XAuth? = parseXAuthJson(authJson)?.let { auth ->
    XAuth(
        clientId = auth.string("client_id").orEmpty(),
        accessToken = auth.string("access_token") ?: auth.string("bearer_token") ?: auth.string("token").orEmpty(),
        refreshToken = auth.string("refresh_token").orEmpty(),
        expiresAt = auth.long("expires_at"),
    )
}

private fun parseXAuthJson(authJson: String): JsonObject? = runCatching {
    Json.parseToJsonElement(authJson).jsonObject
}.getOrNull()

private fun parseXRefreshTokenResponse(body: String): XRefreshTokenPayload {
    val root = Json.parseToJsonElement(body).jsonObject
    return XRefreshTokenPayload(
        accessToken = root.string("access_token").orEmpty(),
        refreshToken = root.string("refresh_token").orEmpty(),
        expiresInSeconds = root.long("expires_in"),
        scope = root.string("scope").orEmpty(),
        tokenType = root.string("token_type").orEmpty(),
    ).also {
        if (it.accessToken.isBlank()) error("X OAuth refresh response missing access_token")
    }
}

private fun xProviderErrorDetail(body: String): String? = runCatching {
    val root = Json.parseToJsonElement(body).jsonObject
    val title = root.string("title")
    val detail = root.string("detail")
    val errors = root["errors"]
        ?.jsonArrayOrNull()
        ?.mapNotNull { error ->
            error.jsonObjectOrNull()?.let { obj ->
                obj.string("message") ?: obj.string("detail") ?: obj.string("title")
            }
        }
        .orEmpty()
    (listOfNotNull(title, detail) + errors)
        .joinToString(" - ")
        .replace(Regex("\\s+"), " ")
        .take(240)
        .takeIf { it.isNotBlank() }
}.getOrNull()

fun extractXAccessToken(authJson: String): String? = runCatching {
    Json.parseToJsonElement(authJson).jsonObject.let { auth ->
        auth.string("access_token")
            ?: auth.string("bearer_token")
            ?: auth.string("token")
    }
}.getOrNull()?.takeIf { it.isNotBlank() }

private fun parseInstantMillis(value: String): Long? = runCatching {
    Instant.parse(value).toEpochMilliseconds()
}.getOrNull()

private fun aiInputHashText(
    externalId: String,
    canonicalUrl: String?,
    text: String,
    authorName: String,
    sourceCreatedAt: Long?,
    mediaUrls: List<String>,
): String = buildString {
    appendLine(ExternalFavoriteProvider.X.id)
    appendLine(externalId)
    appendLine(canonicalUrl.orEmpty())
    appendLine(text)
    appendLine(authorName)
    appendLine(sourceCreatedAt?.toString().orEmpty())
    mediaUrls.forEach { appendLine(it) }
}

private fun Map<String, String>.headerValue(name: String): String? {
    val target = name.lowercase()
    return entries.firstOrNull { it.key.lowercase() == target }?.value?.takeIf { it.isNotBlank() }
}

private fun String.toRateLimitResetMillis(): Long? {
    val value = trim().toLongOrNull() ?: return null
    return if (value > 10_000_000_000L) value else value * 1_000L
}

private fun JsonObject.string(key: String): String? =
    get(key)?.jsonPrimitiveOrNull()?.contentOrNull

private fun JsonObject.long(key: String): Long? =
    string(key)?.toLongOrNull()

private fun JsonObject.boolean(key: String): Boolean? =
    get(key)?.jsonPrimitiveOrNull()?.contentOrNull?.toBooleanStrictOrNull()

private fun JsonElement.jsonObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()

private fun JsonElement.jsonArrayOrNull(): JsonArray? = runCatching { jsonArray }.getOrNull()

private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = runCatching { jsonPrimitive }.getOrNull()

private fun sha256Hex(input: String): String = Sha256.digest(input.encodeToByteArray()).joinToString("") {
    it.toUByte().toString(16).padStart(2, '0')
}

private object Sha256 {
    private val k = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b, 0x59f111f1,
        0x923f82a4.toInt(), 0xab1c5ed5.toInt(), 0xd807aa98.toInt(), 0x12835b01, 0x243185be,
        0x550c7dc3, 0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f,
        0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152.toInt(), 0xa831c66d.toInt(),
        0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(),
        0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(), 0xa2bfe8a1.toInt(),
        0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(),
        0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070, 0x19a4c116, 0x1e376c08,
        0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(),
        0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )

    fun digest(message: ByteArray): ByteArray {
        val padded = pad(message)
        val hash = intArrayOf(
            0x6a09e667,
            0xbb67ae85.toInt(),
            0x3c6ef372,
            0xa54ff53a.toInt(),
            0x510e527f,
            0x9b05688c.toInt(),
            0x1f83d9ab,
            0x5be0cd19,
        )
        val w = IntArray(64)

        for (offset in padded.indices step 64) {
            for (i in 0 until 16) {
                val index = offset + i * 4
                w[i] = ((padded[index].toInt() and 0xff) shl 24) or
                    ((padded[index + 1].toInt() and 0xff) shl 16) or
                    ((padded[index + 2].toInt() and 0xff) shl 8) or
                    (padded[index + 3].toInt() and 0xff)
            }
            for (i in 16 until 64) {
                val s0 = rotateRight(w[i - 15], 7) xor rotateRight(w[i - 15], 18) xor (w[i - 15] ushr 3)
                val s1 = rotateRight(w[i - 2], 17) xor rotateRight(w[i - 2], 19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = hash[0]
            var b = hash[1]
            var c = hash[2]
            var d = hash[3]
            var e = hash[4]
            var f = hash[5]
            var g = hash[6]
            var h = hash[7]

            for (i in 0 until 64) {
                val s1 = rotateRight(e, 6) xor rotateRight(e, 11) xor rotateRight(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + ch + k[i] + w[i]
                val s0 = rotateRight(a, 2) xor rotateRight(a, 13) xor rotateRight(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj
                h = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }

            hash[0] += a
            hash[1] += b
            hash[2] += c
            hash[3] += d
            hash[4] += e
            hash[5] += f
            hash[6] += g
            hash[7] += h
        }

        return ByteArray(32).also { output ->
            hash.forEachIndexed { i, value ->
                output[i * 4] = (value ushr 24).toByte()
                output[i * 4 + 1] = (value ushr 16).toByte()
                output[i * 4 + 2] = (value ushr 8).toByte()
                output[i * 4 + 3] = value.toByte()
            }
        }
    }

    private fun pad(message: ByteArray): ByteArray {
        val bitLength = message.size.toLong() * 8
        val paddingLength = ((56 - (message.size + 1) % 64) + 64) % 64
        return ByteArray(message.size + 1 + paddingLength + 8).also { output ->
            message.copyInto(output)
            output[message.size] = 0x80.toByte()
            for (i in 0 until 8) {
                output[output.lastIndex - i] = (bitLength ushr (8 * i)).toByte()
            }
        }
    }

    private fun rotateRight(value: Int, bits: Int): Int = (value ushr bits) or (value shl (32 - bits))
}
