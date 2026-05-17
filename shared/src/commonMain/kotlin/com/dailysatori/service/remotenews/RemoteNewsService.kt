package com.dailysatori.service.remotenews

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.URLBuilder

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
        }.body()
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
