package com.dailysatori.service.crayfishnews

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

class CrayfishNewsService(private val client: HttpClient) {

    suspend fun healthCheck(config: CrayfishNewsConfigValues): CrayfishNewsResult<CrayfishHealthResponse> =
        request { client.get(buildUrl(config.baseUrl, "health")) { bearerAuth(config.token) } }

    suspend fun fetchLatest(config: CrayfishNewsConfigValues): CrayfishNewsResult<CrayfishNewsDetail> =
        request { client.get(buildUrl(config.baseUrl, "news/latest")) { bearerAuth(config.token) } }

    suspend fun fetchDji(config: CrayfishNewsConfigValues): CrayfishNewsResult<CrayfishNewsDetail> =
        request { client.get(buildUrl(config.baseUrl, "news/dji")) { bearerAuth(config.token) } }

    suspend fun fetchNewsList(config: CrayfishNewsConfigValues, category: String? = null, limit: Int = 20): CrayfishNewsResult<CrayfishNewsListResponse> =
        request {
            val builder = URLBuilder("${config.baseUrl.trim().trimEnd('/')}/news")
            if (category != null) builder.parameters.append("category", category)
            builder.parameters.append("limit", limit.toString())
            client.get(builder.buildString()) { bearerAuth(config.token) }
        }

    suspend fun fetchNewsFile(config: CrayfishNewsConfigValues, category: String, filename: String): CrayfishNewsResult<CrayfishNewsDetail> =
        request { client.get(buildUrl(config.baseUrl, "news/$category/$filename")) { bearerAuth(config.token) } }

    fun configOrFailure(baseUrl: String?, token: String?): CrayfishNewsResult<CrayfishNewsConfigValues> {
        val normalizedBaseUrl = baseUrl.orEmpty().trim()
        val normalizedToken = token.orEmpty().trim()
        if (normalizedBaseUrl.isBlank() || normalizedToken.isBlank()) {
            return CrayfishNewsResult.Failure("请先配置小龙虾新闻服务")
        }
        return CrayfishNewsResult.Success(CrayfishNewsConfigValues(normalizeBaseUrl(normalizedBaseUrl), normalizedToken))
    }

    private fun normalizeBaseUrl(baseUrl: String): String = baseUrl
        .trim()
        .trimEnd('/')
        .removeSuffix("/health")
        .removeSuffix("/news/latest")
        .removeSuffix("/news/dji")
        .removeSuffix("/news")

    private fun buildUrl(baseUrl: String, path: String): String {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        return "$normalizedBase/$path"
    }

    private suspend inline fun <reified T> request(crossinline block: suspend () -> HttpResponse): CrayfishNewsResult<T> = try {
        val response = block()
        when {
            response.status.isSuccess() -> CrayfishNewsResult.Success(Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString(response.bodyAsText()))
            response.status.value == 401 -> CrayfishNewsResult.Failure("Token 无效，请检查小龙虾新闻设置")
            response.status.value == 404 -> CrayfishNewsResult.Failure("小龙虾新闻文件不存在")
            response.status.value >= 500 -> CrayfishNewsResult.Failure("小龙虾新闻服务暂时不可用 (${response.status.value})")
            else -> CrayfishNewsResult.Failure("小龙虾新闻请求失败 (${response.status.value})")
        }
    } catch (e: Exception) {
        val detail = e.message?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
        CrayfishNewsResult.Failure("无法连接小龙虾新闻服务$detail")
    }
}
