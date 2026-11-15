package com.dailysatori.service.remotenews

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.dailysatori.service.diagnostics.*

/** The subscription answered with a document larger than the run may read. */
class RemoteFeedTooLargeException(limit: Int) :
    IllegalArgumentException("订阅文档超过本轮可读取的大小上限（$limit 字节），已停止且未推进同步边界")

/** What a configured subscription can do, as observed now; nothing here is cached across runs. */
data class RemoteFeedProbe(
    /** "plain", "paged" or "archive". */
    val mode: String,
    val articles: Int,
    val pagesChecked: Int,
    val hasBody: Boolean,
    val message: String,
)

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
        limit: Int = REMOTE_FEED_DISPLAY_LIMIT,
        timeoutMillis: Long = DEFAULT_FETCH_TIMEOUT_MS,
        maxBytes: Int = DEFAULT_MAX_DOCUMENT_BYTES,
    ): RemoteNewsResult<RemoteArticlesResponse> = requestFeed {
        val feedUrl = entryUrl(config)
        val document = fetchFeedDocument(config, feedUrl, feedUrl, timeoutMillis, maxBytes)
        val articles = parseRemoteFeedArticles(document.body, document.finalUrl, limit)
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

    /**
     * Reads one already linked feed document of the same subscription. Only the document address is
     * passed, so no paging scheme is guessed: the caller must supply an href the feed itself declared.
     * [timeoutMillis] and [maxBytes] are the remaining budget of the current run, so one oversized or
     * slow document can never outlive the run.
     */
    suspend fun fetchFeedPage(
        config: RemoteNewsConfigValues,
        pageUrl: String,
        timeoutMillis: Long = DEFAULT_FETCH_TIMEOUT_MS,
        maxBytes: Int = DEFAULT_MAX_DOCUMENT_BYTES,
    ): RemoteNewsResult<RemoteFeedPage> = requestFeed {
        val entry = entryUrl(config)
        val target = pageUrl.trim()
        if (!isRemoteFeedHttpUrl(target)) throw RemoteFeedFormatException("分页或归档链接不是有效的 http(s) 地址")
        if (remoteFeedDowngradesTransport(target, entry)) {
            throw RemoteFeedFormatException("分页或归档链接会降级到非加密地址，已停止")
        }
        val document = fetchFeedDocument(config, target, entry, timeoutMillis, maxBytes)
        parseRemoteFeedPage(document.body, document.finalUrl, document.finalUrl, document.bytes)
    }

    /**
     * Read-only compatibility check used by the settings screen: the entry document must answer with a
     * usable feed, then a bounded number of declared paging/archive documents are verified. Nothing is
     * stored, nothing is queued and no sync starts; the capability is judged again on every sync run.
     */
    suspend fun probeSourceFeed(
        config: RemoteNewsConfigValues,
        timeoutMillis: Long = PROBE_TIMEOUT_MS,
        maxBytes: Int = PROBE_MAX_BYTES,
    ): RemoteNewsResult<RemoteFeedProbe> = requestFeed {
        val entry = entryUrl(config)
        val entryDocument = fetchFeedDocument(config, entry, entry, timeoutMillis, maxBytes)
        val entryPage = parseRemoteFeedPage(entryDocument.body, entryDocument.finalUrl, entryDocument.finalUrl, entryDocument.bytes)
        val mode = remoteFeedPagingMode(entryPage)
        var articles = entryPage.articles.size
        var pagesChecked = 1
        var verified = 0
        var warning: String? = null
        var remainingBytes = maxBytes - entryDocument.bytes
        var cursor = if (mode == RemoteFeedPagingMode.PLAIN) null else remoteFeedOlderDocumentUrl(entryPage, mode)
        val visited = mutableSetOf(entryDocument.finalUrl)
        while (cursor != null && pagesChecked < PROBE_PAGE_LIMIT) {
            if (!visited.add(cursor)) {
                warning = "分页链接出现循环"
                break
            }
            if (!isRemoteFeedHttpUrl(cursor) || remainingBytes <= 0) {
                warning = if (remainingBytes <= 0) "已到大小上限，未继续验证" else "分页链接不是有效的 http(s) 地址"
                break
            }
            when (val page = fetchFeedPage(config, cursor, timeoutMillis, remainingBytes)) {
                is RemoteNewsResult.Success -> {
                    pagesChecked += 1
                    verified += 1
                    articles += page.value.articles.size
                    remainingBytes -= page.value.bytes
                    cursor = remoteFeedOlderDocumentUrl(page.value, mode)
                }
                is RemoteNewsResult.Failure -> {
                    warning = "后续页读取失败：" + page.message
                    cursor = null
                }
            }
        }
        val hasBody = entryPage.articles.any { !it.content.isNullOrBlank() }
        RemoteFeedProbe(
            mode = when {
                warning != null || (mode != RemoteFeedPagingMode.PLAIN && verified == 0) -> RemoteFeedPagingMode.PLAIN.name.lowercase()
                else -> mode.name.lowercase()
            },
            articles = articles,
            pagesChecked = pagesChecked,
            hasBody = hasBody,
            message = remoteFeedProbeMessage(mode, verified, articles, hasBody, warning),
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
            // A missing address never heals on its own.
            return RemoteNewsResult.Failure("请先配置远程新闻 RSS 地址", transient = false)
        }
        return RemoteNewsResult.Success(RemoteNewsConfigValues(normalizedBaseUrl, token.orEmpty().trim()))
    }

    private fun entryUrl(config: RemoteNewsConfigValues): String {
        val feedUrl = config.baseUrl.trim()
        if (!feedUrl.startsWith("http://") && !feedUrl.startsWith("https://")) {
            throw RemoteFeedFormatException("RSS 地址必须以 http:// 或 https:// 开头")
        }
        return feedUrl
    }

    private class FeedDocument(val body: String, val finalUrl: String, val bytes: Int)

    /**
     * One feed document read inside the run budget: redirects are followed here (not by the engine) so
     * the final address is known, a downgrade or an endless chain is refused, and the credential is
     * attached only when the requested address shares the subscription origin. The body is streamed and
     * aborted as soon as it exceeds [maxBytes], so an oversized document never has to fit in memory.
     */
    private suspend fun fetchFeedDocument(
        config: RemoteNewsConfigValues,
        url: String,
        entryUrl: String,
        timeoutMillis: Long,
        maxBytes: Int,
    ): FeedDocument {
        if (timeoutMillis <= 0) throw RemoteFeedTimeoutException("本轮剩余时间不足，未继续读取订阅")
        val response = client.get(url) {
            expectSuccess = false
            if (config.token.isNotBlank() && remoteFeedSameOrigin(url, entryUrl)) bearerAuth(config.token)
            header(HttpHeaders.Accept, FEED_ACCEPT)
            timeout { requestTimeoutMillis = timeoutMillis }
        }
        // The address that actually answered: relative links inside the document resolve against it, so
        // a redirected feed never produces links against the stale configured address.
        val finalUrl = response.call.request.url.toString()
        if (remoteFeedDowngradesTransport(finalUrl, entryUrl)) {
            throw RemoteFeedFormatException("订阅地址被重定向到非加密地址，已停止")
        }
        if (response.status.value !in 200..299) {
            val message = bodyOrEmpty(response)
            val text = parseRemoteNewsErrorMessage(message) ?: feedFailureMessage(response.status.value)
            throw RemoteFeedStatusException(response.status.value, text, message)
        }
        val body = readBounded(response, maxBytes)
        TaskHttpCapture.body(currentCaptureTaskId(), currentCaptureHandle(), TaskHttpDirection.RESPONSE, body.text)
        return FeedDocument(body.text, finalUrl, body.bytes)
    }

    private class BoundedBody(val text: String, val bytes: Int)

    private suspend fun readBounded(response: HttpResponse, maxBytes: Int): BoundedBody {
        if (maxBytes <= 0) throw RemoteFeedTooLargeException(maxBytes)
        val channel = response.bodyAsChannel()
        val chunk = ByteArray(READ_CHUNK_BYTES)
        val parts = ArrayList<ByteArray>()
        var total = 0
        while (true) {
            val read = channel.readAvailable(chunk, 0, chunk.size)
            if (read <= 0) break
            total += read
            if (total > maxBytes) throw RemoteFeedTooLargeException(maxBytes)
            parts += chunk.copyOf(read)
        }
        val bytes = ByteArray(total)
        var offset = 0
        for (part in parts) {
            part.copyInto(bytes, offset)
            offset += part.size
        }
        return BoundedBody(bytes.decodeToString(), total)
    }

    private suspend fun bodyOrEmpty(response: HttpResponse): String =
        runCatching { response.bodyAsText() }.getOrDefault("")

    private suspend fun <T> requestFeed(block: suspend () -> T): RemoteNewsResult<T> = try {
        RemoteNewsResult.Success(block())
    } catch (e: RemoteFeedStatusException) {
        TaskHttpCapture.body(currentCaptureTaskId(), currentCaptureHandle(), TaskHttpDirection.RESPONSE, e.body)
        RemoteNewsResult.Failure(e.message ?: "无法获取 RSS 订阅", transient = e.status == 408 || e.status == 429 || e.status >= 500)
    } catch (e: RemoteFeedTooLargeException) {
        RemoteNewsResult.Failure(e.message ?: "订阅文档过大", transient = false)
    } catch (e: RemoteFeedTimeoutException) {
        RemoteNewsResult.Failure(e.message ?: "读取订阅超时", transient = true)
    } catch (e: RemoteFeedFormatException) {
        // The address answered, but not with a feed: waiting cannot fix that, so it is not retryable.
        RemoteNewsResult.Failure(
            e.message ?: "返回内容不是有效的 RSS/Atom 订阅，请检查远程新闻地址",
            transient = false,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        RemoteNewsResult.Failure("无法连接 RSS 订阅地址", transient = true)
    }

    private fun feedFailureMessage(status: Int): String = when (status) {
        401, 403 -> "认证失败，请检查 RSS 地址或 Token"
        404, 410 -> "RSS 地址不存在，请检查远程新闻设置"
        429 -> "请求过于频繁，请稍后重试"
        else -> "无法获取 RSS 订阅（HTTP $status）"
    }

    private suspend fun <T> request(block: suspend () -> T): RemoteNewsResult<T> = try {
        val handle = com.dailysatori.service.diagnostics.TaskHttpCapture.session.openRequest(
            com.dailysatori.service.diagnostics.currentCaptureTaskId(),
        )
        val value = kotlinx.coroutines.withContext(handle ?: kotlin.coroutines.EmptyCoroutineContext) { block() }
        RemoteNewsResult.Success(value)
    } catch (e: ClientRequestException) {
        val body = e.response.bodyAsText()
        TaskHttpCapture.body(currentCaptureTaskId(), currentCaptureHandle(), TaskHttpDirection.RESPONSE, body)
        val status = e.response.status.value
        RemoteNewsResult.Failure(
            parseRemoteNewsErrorMessage(body) ?: clientFailureMessage(status),
            // 4xx answers are configuration/permission facts; only 408/429 are worth waiting for.
            transient = status == 408 || status == 429,
        )
    } catch (e: ServerResponseException) {
        val body = e.response.bodyAsText()
        TaskHttpCapture.body(currentCaptureTaskId(), currentCaptureHandle(), TaskHttpDirection.RESPONSE, body)
        RemoteNewsResult.Failure(parseRemoteNewsErrorMessage(body) ?: "RSS 服务暂时不可用", transient = true)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        RemoteNewsResult.Failure("无法连接远程服务", transient = true)
    }

    private fun clientFailureMessage(status: Int): String = when (status) {
        401, 403 -> "认证失败，请检查 RSS 地址或 Token"
        404 -> "RSS 地址不存在，请检查远程新闻设置"
        429 -> "请求过于频繁，请稍后重试"
        else -> "无法获取 RSS 订阅（HTTP $status）"
    }

    private companion object {
        const val FEED_ACCEPT = "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.8"

        /** The probe never walks a whole history: it verifies at most two declared follow-up documents. */
        const val PROBE_PAGE_LIMIT = 3
        const val PROBE_TIMEOUT_MS = 15_000L
        const val PROBE_MAX_BYTES = 4_000_000
        const val DEFAULT_FETCH_TIMEOUT_MS = 20_000L
        const val DEFAULT_MAX_DOCUMENT_BYTES = 4_000_000
        const val READ_CHUNK_BYTES = 16 * 1024
    }
}

private class RemoteFeedStatusException(val status: Int, message: String, val body: String) : Exception(message)

private class RemoteFeedTimeoutException(message: String) : Exception(message)

/**
 * Human readable result of the settings test action. It states the observed mode, how much was verified
 * and that the check is a sample, so the answer never promises a complete history or body.
 */
internal fun remoteFeedProbeMessage(
    mode: RemoteFeedPagingMode,
    verifiedPages: Int,
    articles: Int,
    hasBody: Boolean,
    warning: String?,
): String {
    val head = "连接成功，获取到 $articles 篇文章"
    val bodyNote = if (hasBody) "" else "；未检测到正文内容，只有标题或链接"
    val modeNote = when {
        mode == RemoteFeedPagingMode.ARCHIVE && verifiedPages > 0 ->
            "；支持标准归档同步：已验证 $verifiedPages 个归档页"
        mode == RemoteFeedPagingMode.PAGED && verifiedPages > 0 ->
            "；支持标准分页同步：已验证 $verifiedPages 个分页"
        mode == RemoteFeedPagingMode.ARCHIVE || mode == RemoteFeedPagingMode.PAGED ->
            "；发现分页或归档链接但未能验证后续页"
        else -> "；普通订阅模式：每次同步当前 URL"
    }
    val warningNote = warning?.let { "；$it" }.orEmpty()
    val sampleNote = if (mode == RemoteFeedPagingMode.PLAIN) "" else "。仅验证少量页面，不保证全部历史或全文完整"
    return head + bodyNote + modeNote + warningNote + sampleNote
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
