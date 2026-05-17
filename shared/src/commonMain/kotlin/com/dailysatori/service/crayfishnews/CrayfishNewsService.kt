package com.dailysatori.service.crayfishnews

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

private const val CrayfishUserAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"

class CrayfishNewsService(private val client: HttpClient) {

    suspend fun healthCheck(config: CrayfishNewsConfigValues): CrayfishNewsResult<CrayfishHealthResponse> =
        request { client.get(buildUrl(config.baseUrl, "health")) { crayfishAuth(config.token) } }

    suspend fun fetchLatest(config: CrayfishNewsConfigValues): CrayfishNewsResult<CrayfishNewsDetail> =
        request { client.get(buildUrl(config.baseUrl, "news", "latest")) { crayfishAuth(config.token) } }

    suspend fun fetchDji(config: CrayfishNewsConfigValues): CrayfishNewsResult<CrayfishNewsDetail> =
        request { client.get(buildUrl(config.baseUrl, "news", "dji")) { crayfishAuth(config.token) } }

    suspend fun fetchNewsList(config: CrayfishNewsConfigValues, category: String? = null, limit: Int = 20): CrayfishNewsResult<CrayfishNewsListResponse> =
        requestWithBody(decode = { body -> decodeCrayfishNewsListResponse(body, category) }) {
            val segments = category?.let { arrayOf("news", category) } ?: arrayOf("news")
            client.get(buildNewsListUrl(config.baseUrl, limit, *segments)) { crayfishAuth(config.token) }
        }

    suspend fun fetchNewsFile(config: CrayfishNewsConfigValues, category: String, filename: String): CrayfishNewsResult<CrayfishNewsDetail> =
        request { client.get(buildUrl(config.baseUrl, "news", category, filename)) { crayfishAuth(config.token) } }

    suspend fun fetchArticleList(config: CrayfishNewsConfigValues, category: String, date: String): CrayfishNewsResult<CrayfishArticleListResponse> =
        request { client.get(buildUrl(config.baseUrl, "news", category, date, "articles")) { crayfishAuth(config.token) } }

    suspend fun fetchArticle(config: CrayfishNewsConfigValues, category: String, date: String, articleId: String): CrayfishNewsResult<CrayfishArticle> =
        request { client.get(buildUrl(config.baseUrl, "news", category, date, "articles", articleId)) { crayfishAuth(config.token) } }

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

    private fun buildUrl(baseUrl: String, vararg pathSegments: String): String = URLBuilder(baseUrl.trim().trimEnd('/')).apply {
        appendPathSegments(*pathSegments)
    }.buildString()

    private fun buildNewsListUrl(baseUrl: String, limit: Int, vararg pathSegments: String): String = URLBuilder(buildUrl(baseUrl, *pathSegments)).apply {
        parameters.append("limit", limit.toString())
    }.buildString()

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

    private suspend fun requestWithBody(
        decode: (String) -> CrayfishNewsListResponse,
        block: suspend () -> HttpResponse,
    ): CrayfishNewsResult<CrayfishNewsListResponse> = try {
        val response = block()
        when {
            response.status.isSuccess() -> CrayfishNewsResult.Success(decode(response.bodyAsText()))
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

private fun HttpRequestBuilder.crayfishAuth(token: String) {
    bearerAuth(token)
    header(HttpHeaders.UserAgent, CrayfishUserAgent)
}

fun decodeCrayfishNewsListResponse(body: String, category: String?): CrayfishNewsListResponse {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    val element = json.parseToJsonElement(body)
    if (element is JsonArray) return categoryListResponse(category, json.decodeFromJsonElement(element))
    val obj = element.jsonObject
    val direct = json.decodeFromJsonElement<CrayfishNewsListResponse>(element)
    val fallback = firstList(obj, json, category, "items", "news", "files", "data")
    return when (category) {
        "dji" -> direct.copy(dji = direct.dji.ifEmpty { fallback })
        "general" -> direct.copy(general = direct.general.ifEmpty { fallback })
        else -> direct
    }
}

private fun categoryListResponse(category: String?, items: List<CrayfishNewsListItem>): CrayfishNewsListResponse =
    if (category == "dji") CrayfishNewsListResponse(dji = items) else CrayfishNewsListResponse(general = items)

private fun firstList(
    obj: JsonObject,
    json: Json,
    category: String?,
    vararg keys: String,
): List<CrayfishNewsListItem> = keys
    .asSequence()
    .mapNotNull { obj[it] ?: category?.let(obj::get) }
    .filterIsInstance<JsonArray>()
    .map { json.decodeFromJsonElement<List<CrayfishNewsListItem>>(it) }
    .firstOrNull()
    .orEmpty()
