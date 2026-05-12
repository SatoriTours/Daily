package com.dailysatori.service.remotenews

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.URLBuilder

class RemoteNewsService(private val client: HttpClient) {
    suspend fun fetchDigests(config: RemoteNewsConfigValues, page: Int, perPage: Int): RemoteNewsResult<RemoteDigestsResponse> =
        request { client.get(buildUrl(config.baseUrl, "digests", page, perPage)) { bearerAuth(config.token) }.body() }

    suspend fun fetchDigest(config: RemoteNewsConfigValues, id: Long): RemoteNewsResult<RemoteDigestResponse> =
        request { client.get(buildUrl(config.baseUrl, "digests/$id")) { bearerAuth(config.token) }.body() }

    suspend fun fetchArticles(config: RemoteNewsConfigValues, page: Int, perPage: Int): RemoteNewsResult<RemoteArticlesResponse> =
        request { client.get(buildUrl(config.baseUrl, "articles", page, perPage)) { bearerAuth(config.token) }.body() }

    suspend fun fetchArticle(config: RemoteNewsConfigValues, id: Long): RemoteNewsResult<RemoteArticleResponse> =
        request { client.get(buildUrl(config.baseUrl, "articles/$id")) { bearerAuth(config.token) }.body() }

    suspend fun fetchFeeds(config: RemoteNewsConfigValues, page: Int, perPage: Int): RemoteNewsResult<RemoteFeedsResponse> =
        request { client.get(buildUrl(config.baseUrl, "feeds", page, perPage)) { bearerAuth(config.token) }.body() }

    fun buildUrl(baseUrl: String, path: String, page: Int? = null, perPage: Int? = null): String {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        val builder = URLBuilder("$normalizedBase/api/v1/external/$path")
        if (page != null) builder.parameters.append("page", page.toString())
        if (perPage != null) builder.parameters.append("per_page", perPage.toString())
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
