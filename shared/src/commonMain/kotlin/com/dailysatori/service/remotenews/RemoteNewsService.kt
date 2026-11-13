package com.dailysatori.service.remotenews

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.dailysatori.service.diagnostics.*

class RemoteNewsService(private val client: HttpClient) {
    suspend fun fetchDigests(config: RemoteNewsConfigValues, page: Int, perPage: Int): RemoteNewsResult<RemoteDigestsResponse> =
        request { client.get(buildUrl(config.baseUrl, "digests", page, perPage)) { bearerAuth(config.token) }.body() }

    suspend fun fetchDigest(config: RemoteNewsConfigValues, id: Long): RemoteNewsResult<RemoteDigestResponse> =
        request { client.get(buildUrl(config.baseUrl, "digests/$id")) { bearerAuth(config.token) }.body() }

    suspend fun fetchArticles(config: RemoteNewsConfigValues, page: Int, perPage: Int): RemoteNewsResult<RemoteArticlesResponse> =
        request { client.get(buildUrl(config.baseUrl, "articles", page, perPage)) { bearerAuth(config.token) }.body() }

    /**
     * Reads the subscription the user configured as an RSS/Atom feed. The saved URL is requested
     * exactly as written (query and path preserved); no JSON endpoint and no page parameters are
     * invented. A token is optional and only sent when one is configured.
     */
    suspend fun fetchSourceArticles(
        config: RemoteNewsConfigValues,
        limit: Int = DEFAULT_ARTICLE_LIMIT,
    ): RemoteNewsResult<RemoteArticlesResponse> = request {
        val feedUrl = config.baseUrl.trim()
        if (!feedUrl.startsWith("http://") && !feedUrl.startsWith("https://")) {
            throw RemoteFeedFormatException("RSS 地址必须以 http:// 或 https:// 开头")
        }
        val body = client.get(feedUrl) {
            expectSuccess = true
            if (config.token.isNotBlank()) bearerAuth(config.token)
            header(HttpHeaders.Accept, FEED_ACCEPT)
        }.bodyAsText()
        // The business already decodes this exact text; capture is a pure side observation.
        TaskHttpCapture.body(currentCaptureTaskId(), currentCaptureHandle(), TaskHttpDirection.RESPONSE, body)
        val articles = parseRemoteFeedArticles(body, feedUrl, limit)
        RemoteArticlesResponse(
            articles = articles,
            pagination = RemoteNewsPagination(
                page = 1,
                perPage = limit,
                total = articles.size,
                totalPages = 1,
                next = null,
            ),
        )
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

    fun configOrFailure(baseUrl: String?, token: String?): RemoteNewsResult<RemoteNewsConfigValues> {
        val normalizedBaseUrl = baseUrl.orEmpty().trim()
        if (normalizedBaseUrl.isBlank()) {
            return RemoteNewsResult.Failure("请先配置远程新闻 RSS 地址")
        }
        return RemoteNewsResult.Success(RemoteNewsConfigValues(normalizedBaseUrl, token.orEmpty().trim()))
    }

    private suspend fun <T> request(block: suspend () -> T): RemoteNewsResult<T> = try {
        val handle = com.dailysatori.service.diagnostics.TaskHttpCapture.session.openRequest(
            com.dailysatori.service.diagnostics.currentCaptureTaskId(),
        )
        val value = kotlinx.coroutines.withContext(handle ?: kotlin.coroutines.EmptyCoroutineContext) { block() }
        RemoteNewsResult.Success(value)
    } catch (e: RemoteFeedFormatException) {
        RemoteNewsResult.Failure(e.message ?: "返回内容不是有效的 RSS/Atom 订阅")
    } catch (e: ClientRequestException) {
        val body = e.response.bodyAsText()
        TaskHttpCapture.body(currentCaptureTaskId(), currentCaptureHandle(), TaskHttpDirection.RESPONSE, body)
        RemoteNewsResult.Failure(parseRemoteNewsErrorMessage(body) ?: clientFailureMessage(e))
    } catch (e: ServerResponseException) {
        val body = e.response.bodyAsText()
        TaskHttpCapture.body(currentCaptureTaskId(), currentCaptureHandle(), TaskHttpDirection.RESPONSE, body)
        RemoteNewsResult.Failure(parseRemoteNewsErrorMessage(body) ?: "RSS 服务暂时不可用")
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        RemoteNewsResult.Failure("无法连接 RSS 订阅地址")
    }

    private fun clientFailureMessage(error: ClientRequestException): String = when (error.response.status.value) {
        401, 403 -> "认证失败，请检查 RSS 地址或 Token"
        404 -> "RSS 地址不存在，请检查远程新闻设置"
        429 -> "请求过于频繁，请稍后重试"
        else -> "无法获取 RSS 订阅（HTTP ${error.response.status.value}）"
    }

    private companion object {
        const val DEFAULT_ARTICLE_LIMIT = 50
        const val FEED_ACCEPT = "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.8"
    }
}

private val remoteNewsJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun parseRemoteNewsErrorMessage(body: String): String? {
    val root = runCatching { remoteNewsJson.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
    val error = root["error"]
    val nestedMessage = (error as? JsonObject)?.stringValue("message")
    val topMessage = root.stringValue("message")
    val directError = (error as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    return nestedMessage ?: topMessage ?: directError
}

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

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
