package com.dailysatori.service.parser

import co.touchlab.kermit.Logger
import com.dailysatori.config.WebViewConfig
import com.dailysatori.data.repository.ExternalFavoriteItemRepository
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.platform.WebViewLoader
import com.dailysatori.platform.WebViewPageContent
import com.dailysatori.service.retryTransientFailure
import com.dailysatori.service.externalfavorites.ExternalFavoriteProvider
import com.dailysatori.service.externalfavorites.XBookmarksConnector
import com.dailysatori.service.externalfavorites.canonicalizeXArticleUrl
import com.dailysatori.service.externalfavorites.favoriteHasArticleCache
import com.dailysatori.service.externalfavorites.favoriteMetadata
import com.dailysatori.service.externalfavorites.isXArticleUrl
import com.dailysatori.service.externalfavorites.withXContentFetchSession
import com.dailysatori.service.externalfavorites.xArticleOwningPostId
import com.dailysatori.service.externalfavorites.xIsArticle
import com.dailysatori.service.externalfavorites.xOfficialContentRequestLimit
import com.dailysatori.service.externalfavorites.xPostIdFromStatusUrl
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal class ArticleContentFetcher(
    private val webViewLoader: WebViewLoader,
    private val externalFavoriteSourceRepo: ExternalFavoriteSourceRepository,
    private val xBookmarksConnector: XBookmarksConnector,
    private val externalFavoriteItemRepo: ExternalFavoriteItemRepository?,
) {
    private val log = Logger.withTag("WebpageParser")

    suspend fun extractContent(url: String): ExtractedContent {
        return withContext(Dispatchers.Default) {
            try {
                retryTransientFailure(
                    maxAttempts = ARTICLE_EXTRACTION_MAX_ATTEMPTS,
                    initialDelayMs = ARTICLE_EXTRACTION_RETRY_DELAY_MS,
                    shouldRetry = ::isRetryableArticleExtractionFailure,
                ) {
                    extractContentOnce(url)
                }
            } catch (e: Exception) {
                if (!shouldPersistArticleProcessingError(e)) throw e
                log.e { webpageExtractionFailureLogMessage(url, e) }
                if (isXArticleUrl(url)) throw e
                failedExtractionFallback(url)
            }
        }
    }

    /** Loads a public page without applying article-length gates; cancellation stops the WebView load. */
    suspend fun fetchPublicPage(url: String): ExtractedContent = withContext(Dispatchers.Default) {
        retryTransientFailure(
            maxAttempts = ARTICLE_EXTRACTION_MAX_ATTEMPTS,
            initialDelayMs = ARTICLE_EXTRACTION_RETRY_DELAY_MS,
            shouldRetry = ::isRetryableArticleExtractionFailure,
        ) {
            fetchPublicPageOnce(url)
        }
    }

    private suspend fun extractContentOnce(url: String): ExtractedContent {
        val extracted = if (isTwitterStatusUrl(url) || isXArticleUrl(url)) fetchXPostExtractedContent(url) else fetchPublicPageOnce(url)
        return extracted.copy(
            content = usableArticleContentOrThrow(extracted.content, url),
        )
    }

    private suspend fun fetchPublicPageOnce(url: String): ExtractedContent {
        val page = suspendCancellableCoroutine<WebViewPageContent> { cont ->
            val handle = webViewLoader.loadContent(url, WebViewConfig.timeoutMs) { result ->
                if (!cont.isActive) return@loadContent
                result.fold(
                    onSuccess = { cont.resume(it) },
                    onFailure = { cont.resumeWithException(it) },
                )
            }
            cont.invokeOnCancellation { handle.cancel() }
        }
        val html = page.html
        if (isWebViewNetworkErrorContent(page.text, html)) throw IllegalStateException("WebView loaded a network error page")
        val title = page.readableTitle?.trim()?.takeIf { it.isNotBlank() } ?: extractTitle(html)
        val coverImageUrl = extractCoverImageUrl(html, url)
        val imageUrls = extractContentImageUrls(html, url)
        val textContent = page.summaryTextOrHtmlFallback().let { if (it == html) extractTextContent(html) else it }
        return ExtractedContent(
            title = title,
            content = textContent,
            htmlContent = html,
            coverImageUrl = coverImageUrl,
            readableHtmlContent = page.readableContent,
            imageUrls = imageUrls,
        )
    }

    suspend fun fetchXPostExtractedContent(url: String): ExtractedContent {
        val articleUrl = canonicalizeXArticleUrl(url)
        val reference = if (articleUrl != null) externalFavoriteItemRepo?.findXArticleReference(articleUrl)
            ?: throw IllegalStateException("未找到 X 文章对应的父推文，请先同步收藏后重试")
        else xPostIdFromStatusUrl(url)?.let { externalFavoriteItemRepo?.findXPostReference(it) }
        val expectedArticle = articleUrl != null || reference?.let {
            xIsArticle(it.canonical_url, favoriteMetadata(it.normalized_json)) || favoriteHasArticleCache(it)
        } == true
        val postId = if (expectedArticle && reference != null) xArticleOwningPostId(reference) else xPostIdFromStatusUrl(url)
        if (postId == null) throw IllegalStateException("未找到有效的父推文，请先同步收藏后重试")
        val config = reference?.source_id?.let { externalFavoriteSourceRepo.getById(it)?.config_json }.orEmpty()
        return withXContentFetchSession(xOfficialContentRequestLimit(config)) {
            val draft = xBookmarksConnector.fetchPostById(null, postId, expectedArticle = expectedArticle) {
                resolveXSource(reference)
            } ?: throw IllegalStateException("未能获取 X 完整正文，请稍后重试")
            if (articleUrl != null && canonicalizeXArticleUrl(draft.canonicalUrl.orEmpty()) != articleUrl) {
                throw IllegalStateException("文章与父推文映射不一致，请重新同步收藏")
            }
            xPostLookupDraftExtractedContent(draft)
        }
    }

    private suspend fun resolveXSource(reference: com.dailysatori.shared.db.External_favorite_item?): com.dailysatori.shared.db.External_favorite_source {
        val source = if (reference != null) externalFavoriteSourceRepo.getById(reference.source_id)
            ?.takeIf { it.provider == ExternalFavoriteProvider.X.id && it.enabled == 1L }
            ?: throw IllegalStateException("该收藏所属 X 来源已删除或停用")
        else externalFavoriteSourceRepo.getEnabled().filter { it.provider == ExternalFavoriteProvider.X.id }.singleOrNull()
            ?: throw IllegalStateException("无法确定 X 授权来源，请先同步该链接的收藏")
        return xBookmarksConnector.refreshAuth(source).also { refreshed ->
            if (refreshed.auth_json != source.auth_json) externalFavoriteSourceRepo.updateAuthJson(source.id, refreshed.auth_json)
        }
    }


    private fun extractTitle(html: String): String? {
        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val titleRegex = Regex("""<title[^>]*>(.*?)</title>""", options)
        return titleRegex.find(html)?.groupValues?.get(1)?.trim()?.let { title ->
            htmlDecode(title).replace(Regex("\\s+"), " ")
        }
    }

    private fun extractTextContent(html: String): String {
        val opts = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val body = html.replace(Regex("<head[^>]*>.*?</head>", opts), "")
        val withoutScripts = body.replace(Regex("<script[^>]*>.*?</script>", opts), "")
        val withoutStyles = withoutScripts.replace(Regex("<style[^>]*>.*?</style>", opts), "")
        val withoutTags = withoutStyles.replace(Regex("<[^>]+>"), " ")
        val cleaned = htmlDecode(withoutTags).replace(Regex("\\s+"), " ").trim()
        return cleaned
    }

    private fun htmlDecode(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: it.value }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: it.value }
    }

    private companion object {
        const val ARTICLE_EXTRACTION_MAX_ATTEMPTS = 2
        const val ARTICLE_EXTRACTION_RETRY_DELAY_MS = 500L
    }
}

internal fun isRetryableArticleExtractionFailure(error: Throwable): Boolean {
    val status = Regex("""WebView HTTP error:\s*(\d{3})""", RegexOption.IGNORE_CASE)
        .find(error.message.orEmpty())
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    return status == null || status == 408 || status == 425 || status == 429 || status >= 500
}

internal fun webpageExtractionFailureLogMessage(url: String, error: Throwable): String {
    val parsed = runCatching { URLBuilder().takeFrom(url).build() }.getOrNull()
    val origin = parsed?.takeIf { it.protocol.name in setOf("http", "https") && it.host.isNotBlank() }
        ?.let { "${it.protocol.name}://${it.host}" }
        ?: "[redacted URL]"
    return "Content extraction failed for $origin (${error::class.simpleName ?: "Exception"})"
}
