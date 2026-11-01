package com.dailysatori.service.hotnews

import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.service.externalfavorites.ExternalFavoriteProvider
import com.dailysatori.service.externalfavorites.FavoriteProviderException
import com.dailysatori.service.externalfavorites.XBookmarksConnector
import com.dailysatori.service.externalfavorites.extractXAccessToken
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*

class XHotNewsAuthorization(val token: String, val userContext: Boolean = true) {
    override fun toString() = "XHotNewsAuthorization(userContext=$userContext)"
}

class XHotNewsException(val error: HotNewsError) : IllegalStateException("X official API: ${error.name}")

suspend fun xHotNewsAuthorization(
    sources: ExternalFavoriteSourceRepository,
    connector: XBookmarksConnector,
): XHotNewsAuthorization {
    val source = sources.getEnabled().firstOrNull { it.provider == ExternalFavoriteProvider.X.id }
        ?: throw XHotNewsException(HotNewsError.X_AUTH_REQUIRED)
    val refreshed = try { connector.refreshAuth(source) }
        catch (error: FavoriteProviderException) { throw XHotNewsException(xHotNewsHttpError(error.statusCode)) }
    val token = extractXAccessToken(refreshed.auth_json)
        ?: throw XHotNewsException(HotNewsError.X_AUTH_REQUIRED)
    val auth = Json.parseToJsonElement(refreshed.auth_json).jsonObject
    return XHotNewsAuthorization(token, userContext = "bearer_token" !in auth || "access_token" in auth)
}

/** Two bounded official requests; never sends authorization to an RSS/feed URL. */
class XHotNewsClient(
    private val client: HttpClient,
    private val authorization: suspend () -> XHotNewsAuthorization,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    suspend fun fetch(): List<HotNewsItem> {
        val auth = authorization()
        if (auth.token.isBlank()) throw XHotNewsException(HotNewsError.X_AUTH_REQUIRED)
        val trendsPath = if (auth.userContext) "/2/users/personalized_trends" else "/2/trends/by/woeid/1"
        val trends = data(request(trendsPath, auth))
        if (trends.isEmpty()) return emptyList()
        val terms = trends.mapNotNull { (it as? JsonObject)?.text("trend_name") }
            .map { name -> name.filterNot { it == '"' || it == '\\' || it.isISOControl() }.trim() }
            .filter { it.length in 1..80 }.distinct().take(3)
        check(terms.isNotEmpty()) { "X trends response contains no usable topics" }
        val query = "(" + terms.joinToString(" OR ") { "\"$it\"" } + ") -is:retweet -is:reply"
        val response = request("/2/tweets/search/recent", auth, mapOf(
            "query" to query,
            "sort_order" to "relevancy",
            "max_results" to "20",
            "start_time" to Instant.fromEpochMilliseconds(nowMillis() - 24 * 60 * 60_000L).toString(),
            "post.fields" to "public_metrics,created_at,note_post",
        ))
        return posts(response)
    }

    private suspend fun request(path: String, auth: XHotNewsAuthorization,
        parameters: Map<String, String> = emptyMap()): JsonObject {
        val response = client.get("https://api.x.com$path") {
            expectSuccess = false
            bearerAuth(auth.token)
            timeout { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000 }
            parameters.forEach { (key, value) -> parameter(key, value) }
        }
        if (response.status.value !in 200..299) throw XHotNewsException(xHotNewsHttpError(response.status.value))
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    private fun data(root: JsonObject): JsonArray {
        val data = root["data"] as? JsonArray
        if (data != null && data.isNotEmpty()) return data
        check((root["errors"] as? JsonArray).isNullOrEmpty()) { "X official API returned errors" }
        if (data != null) return data
        val meta = root["meta"] as? JsonObject
        check(meta?.text("result_count") == "0") { "X official API response has no data" }
        return JsonArray(emptyList())
    }

    private fun posts(root: JsonObject): List<HotNewsItem> {
        val rows = data(root)
        if (rows.isEmpty()) return emptyList()
        val items = rows.mapNotNull { row -> (row as? JsonObject)?.let(::post) }.distinctBy { it.id }
        check(items.isNotEmpty()) { "X search response contains no usable posts" }
        return items.sortedByDescending { it.heat?.toLongOrNull() ?: 0L }
            .take(20).mapIndexed { index, item -> item.copy(rank = index + 1) }
    }

    private fun post(row: JsonObject): HotNewsItem? {
        val id = row.text("id")?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        val body = ((row["note_post"] as? JsonObject)?.text("text")
            ?: (row["note_tweet"] as? JsonObject)?.text("text") ?: row.text("text"))?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        val metrics = row["public_metrics"] as? JsonObject
        fun count(key: String) = metrics?.text(key)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val interactions = count("like_count") + count("retweet_count") + count("reply_count") + count("quote_count")
        return HotNewsItem(id, HotNewsPlatform.X, 0, body.lineSequence().first().take(160),
            "https://x.com/i/status/$id", description = body.take(1_000), heat = interactions.toString(),
            comments = count("reply_count").coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            originalContent = body.take(12_000))
    }
}

private fun xHotNewsHttpError(status: Int): HotNewsError = when (status) {
    401 -> HotNewsError.X_AUTH_REQUIRED
    403 -> HotNewsError.X_ACCESS_DENIED
    402 -> HotNewsError.X_QUOTA_EXCEEDED
    429 -> HotNewsError.X_RATE_LIMITED
    else -> HotNewsError.FETCH_FAILED
}
