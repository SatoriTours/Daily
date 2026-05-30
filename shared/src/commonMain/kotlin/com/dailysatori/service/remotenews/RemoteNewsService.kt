package com.dailysatori.service.remotenews

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

class RemoteNewsService(private val client: HttpClient) {
    suspend fun fetchDigests(config: RemoteNewsConfigValues, page: Int, perPage: Int): RemoteNewsResult<RemoteDigestsResponse> =
        request { client.get(buildUrl(config.baseUrl, "digests", page, perPage)) { bearerAuth(config.token) }.body() }

    suspend fun fetchDigest(config: RemoteNewsConfigValues, id: Long): RemoteNewsResult<RemoteDigestResponse> =
        request { client.get(buildUrl(config.baseUrl, "digests/$id")) { bearerAuth(config.token) }.body() }

    suspend fun fetchArticles(config: RemoteNewsConfigValues, page: Int, perPage: Int): RemoteNewsResult<RemoteArticlesResponse> =
        request { client.get(buildUrl(config.baseUrl, "articles", page, perPage)) { bearerAuth(config.token) }.body() }

    suspend fun fetchTopArticlesToday(
        config: RemoteNewsConfigValues,
        page: Int = 1,
        limit: Int = 50,
    ): RemoteNewsResult<RemoteArticlesResponse> = request {
        client.get(buildTopArticlesTodayUrl(config.baseUrl, page, limit)) {
            bearerAuth(config.token)
            header("X-Api-Token", config.token)
        }.bodyAsText().let(::parseTopArticlesTodayResponse)
    }

    suspend fun fetchArticle(config: RemoteNewsConfigValues, id: Long): RemoteNewsResult<RemoteArticleResponse> =
        request { client.get(buildUrl(config.baseUrl, "articles/$id")) { bearerAuth(config.token) }.body() }

    suspend fun fetchFeeds(config: RemoteNewsConfigValues, page: Int, perPage: Int): RemoteNewsResult<RemoteFeedsResponse> =
        request { client.get(buildUrl(config.baseUrl, "feeds", page, perPage)) { bearerAuth(config.token) }.body() }

    fun buildUrl(baseUrl: String, path: String, page: Int? = null, perPage: Int? = null): String {
        val normalizedBase = externalApiRootFromRemoteNewsUrl(baseUrl)
        val builder = URLBuilder("$normalizedBase/api/v1/external/$path")
        if (page != null) builder.parameters.append("page", page.toString())
        if (perPage != null) builder.parameters.append("per_page", perPage.toString())
        return builder.buildString()
    }

    fun buildTopArticlesTodayUrl(baseUrl: String, page: Int = 1, limit: Int = 50): String {
        val builder = URLBuilder(normalizeTopArticlesTodayUrl(baseUrl))
        builder.parameters.append("page", page.toString())
        builder.parameters.append("per_page", limit.toString())
        builder.parameters.append("limit", limit.toString())
        return builder.buildString()
    }

    fun configOrFailure(baseUrl: String?, token: String?): RemoteNewsResult<RemoteNewsConfigValues> {
        val normalizedBaseUrl = baseUrl.orEmpty().trim()
        val normalizedToken = token.orEmpty().trim()
        if (normalizedBaseUrl.isBlank() || normalizedToken.isBlank()) {
            return RemoteNewsResult.Failure("请先配置远程新闻服务")
        }
        return RemoteNewsResult.Success(RemoteNewsConfigValues(normalizedBaseUrl, normalizedToken))
    }

    private suspend fun <T> request(block: suspend () -> T): RemoteNewsResult<T> = try {
        RemoteNewsResult.Success(block())
    } catch (_: ClientRequestException) {
        RemoteNewsResult.Failure("Token 无效，请检查远程新闻设置")
    } catch (_: ServerResponseException) {
        RemoteNewsResult.Failure("远程新闻服务暂时不可用")
    } catch (_: Exception) {
        RemoteNewsResult.Failure("无法连接远程新闻服务")
    }
}

private val remoteNewsJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun parseTopArticlesTodayResponse(body: String): RemoteArticlesResponse {
    val root = remoteNewsJson.parseToJsonElement(body)
    val direct = runCatching { remoteNewsJson.decodeFromJsonElement<RemoteArticlesResponse>(root) }.getOrNull()
    if (direct != null && (direct.articles.isNotEmpty() || root.hasObjectKey("articles"))) return direct

    val articles = topArticlesElement(root)
        ?.asArticleArray()
        ?.mapNotNull(::decodeTopArticle)
        .orEmpty()
    val pagination = (root as? JsonObject)
        ?.get("pagination")
        ?.let { runCatching { remoteNewsJson.decodeFromJsonElement<RemoteNewsPagination>(it) }.getOrNull() }
        ?: RemoteNewsPagination()
    return RemoteArticlesResponse(articles = articles, pagination = pagination)
}

private fun topArticlesElement(root: JsonElement): JsonElement? {
    if (root is JsonArray) return root
    val obj = root as? JsonObject ?: return null
    return obj["articles"]
        ?: obj["data"]?.let { data -> (data as? JsonObject)?.get("articles") ?: data }
        ?: obj["top_articles"]
        ?: obj["items"]
        ?: obj["results"]
}

private fun JsonElement.asArticleArray(): JsonArray? = this as? JsonArray

private fun decodeTopArticle(element: JsonElement): RemoteArticle? {
    val articleElement = (element as? JsonObject)?.get("article") ?: element
    return runCatching { remoteNewsJson.decodeFromJsonElement<RemoteArticle>(articleElement) }.getOrNull()
        ?: decodeTopArticleObject(articleElement as? JsonObject)
}

private fun decodeTopArticleObject(obj: JsonObject?): RemoteArticle? {
    obj ?: return null
    val id = obj.longValue("id") ?: obj.longValue("article_id") ?: return null
    return RemoteArticle(
        id = id,
        title = obj.stringValue("title") ?: obj.stringValue("headline"),
        url = obj.stringValue("url") ?: obj.stringValue("link"),
        summary = obj.stringValue("summary") ?: obj.stringValue("description"),
        viewpoints = obj.stringListValue("viewpoints"),
        status = obj.stringValue("status"),
        sourceType = obj.stringValue("source_type") ?: obj.stringValue("sourceType"),
        feedId = obj.longValue("feed_id") ?: obj.longValue("feedId"),
        feedName = obj.stringValue("feed_name") ?: obj.stringValue("feedName"),
        domain = obj.stringValue("domain"),
        importanceScore = obj.doubleValue("importance_score") ?: obj.doubleValue("importanceScore"),
        coverUrl = obj.stringValue("cover_url") ?: obj.stringValue("coverUrl"),
        createdAt = obj.stringValue("created_at") ?: obj.stringValue("createdAt"),
        processedAt = obj.stringValue("processed_at") ?: obj.stringValue("processedAt"),
        content = obj.stringValue("content"),
    )
}

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.longValue(key: String): Long? {
    val value = this[key] as? JsonPrimitive ?: return null
    return value.longOrNull ?: value.contentOrNull?.toLongOrNull()
}

private fun JsonObject.doubleValue(key: String): Double? {
    val value = this[key] as? JsonPrimitive ?: return null
    return value.doubleOrNull ?: value.contentOrNull?.toDoubleOrNull()
}

private fun JsonObject.stringListValue(key: String): List<String> = when (val value = this[key]) {
    is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
    is JsonPrimitive -> value.contentOrNull?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    else -> emptyList()
}

private fun JsonElement.hasObjectKey(key: String): Boolean =
    (this as? JsonObject)?.containsKey(key) == true

fun normalizeTopArticlesTodayUrl(value: String): String {
    val trimmed = value.trim().substringBefore('?').substringBefore('#').trimEnd('/')
    if (trimmed.endsWith("/api/v1/external/top_articles_today")) return trimmed
    return "$trimmed/api/v1/external/top_articles_today"
}

fun externalApiRootFromRemoteNewsUrl(value: String): String =
    value.trim()
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
        .substringBefore("/api/v1/external/top_articles_today")
        .substringBefore("/api/v1/external")
