package com.dailysatori.service.crayfishnews

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.URLBuilder

class CrayfishNewsService(private val client: HttpClient) {

    suspend fun healthCheck(config: CrayfishNewsConfigValues): CrayfishNewsResult<CrayfishHealthResponse> =
        request { client.get(buildUrl(config.baseUrl, "health")) { bearerAuth(config.token) }.body() }

    suspend fun fetchLatest(config: CrayfishNewsConfigValues): CrayfishNewsResult<CrayfishNewsDetail> =
        request { client.get(buildUrl(config.baseUrl, "news/latest")) { bearerAuth(config.token) }.body() }

    suspend fun fetchDji(config: CrayfishNewsConfigValues): CrayfishNewsResult<CrayfishNewsDetail> =
        request { client.get(buildUrl(config.baseUrl, "news/dji")) { bearerAuth(config.token) }.body() }

    suspend fun fetchNewsList(config: CrayfishNewsConfigValues, category: String? = null, limit: Int = 20): CrayfishNewsResult<CrayfishNewsListResponse> =
        request {
            val builder = URLBuilder("${config.baseUrl.trim().trimEnd('/')}/news")
            if (category != null) builder.parameters.append("category", category)
            builder.parameters.append("limit", limit.toString())
            client.get(builder.buildString()) { bearerAuth(config.token) }.body()
        }

    suspend fun fetchNewsFile(config: CrayfishNewsConfigValues, category: String, filename: String): CrayfishNewsResult<CrayfishNewsDetail> =
        request { client.get(buildUrl(config.baseUrl, "news/$category/$filename")) { bearerAuth(config.token) }.body() }

    fun configOrFailure(baseUrl: String?, token: String?): CrayfishNewsResult<CrayfishNewsConfigValues> {
        val normalizedBaseUrl = baseUrl.orEmpty().trim()
        val normalizedToken = token.orEmpty().trim()
        if (normalizedBaseUrl.isBlank() || normalizedToken.isBlank()) {
            return CrayfishNewsResult.Failure("请先配置小龙虾新闻服务")
        }
        return CrayfishNewsResult.Success(CrayfishNewsConfigValues(normalizedBaseUrl, normalizedToken))
    }

    private fun buildUrl(baseUrl: String, path: String): String {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        return "$normalizedBase/$path"
    }

    private suspend fun <T> request(block: suspend () -> T): CrayfishNewsResult<T> = try {
        CrayfishNewsResult.Success(block())
    } catch (_: ClientRequestException) {
        CrayfishNewsResult.Failure("Token 无效，请检查小龙虾新闻设置")
    } catch (_: ServerResponseException) {
        CrayfishNewsResult.Failure("小龙虾新闻服务暂时不可用")
    } catch (_: Exception) {
        CrayfishNewsResult.Failure("无法连接小龙虾新闻服务")
    }
}
