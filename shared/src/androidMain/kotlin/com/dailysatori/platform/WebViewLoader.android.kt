package com.dailysatori.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import org.json.JSONTokener
import org.koin.mp.KoinPlatform
import kotlinx.coroutines.CancellationException

actual class WebViewLoadHandle internal constructor(
    private val cancelAction: () -> Unit,
) {
    actual fun cancel() = cancelAction()
}

actual class WebViewLoader actual constructor() {
    private val context: Context by lazy { KoinPlatform.getKoin().get<PlatformContext>().context }

    actual fun loadContent(
        url: String,
        timeoutMs: Long,
        callback: (Result<WebViewPageContent>) -> Unit,
    ): WebViewLoadHandle {
        val handler = Handler(Looper.getMainLooper())
        var cancelled = false
        var cancelWebView: (() -> Unit)? = null
        val handle = WebViewLoadHandle {
            handler.post {
                cancelled = true
                cancelWebView?.invoke()
            }
        }
        handler.post {
            try {
                if (cancelled) return@post
                val webView = WebView(context)
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true

                var finished = false
                var isPolling = false
                var lastStableHtml = ""
                var lastPageContent: WebViewPageContent? = null
                var stableReadCount = 0
                var readCount = 0

                fun complete(result: Result<WebViewPageContent>) {
                    if (finished) return
                    finished = true
                    try {
                        callback(result)
                    } finally {
                        webView.destroy()
                    }
                }

                fun decodeJavascriptString(value: String?): String {
                    val decoded = JSONTokener(value ?: "null").nextValue()
                    return decoded as? String ?: ""
                }

                fun parsePageContent(value: String?): PageSnapshot {
                    val json = JSONObject(decodeJavascriptString(value))
                    return PageSnapshot(
                        stableHtml = json.optString("stableHtml"),
                        content = WebViewPageContent(
                            html = json.optString("html"),
                            text = json.optString("text"),
                        ),
                    )
                }

                fun pollHtmlUntilStable() {
                    if (finished) return
                    webView.evaluateJavascript(PARSE_CONTENT_SCRIPT) { value ->
                        if (finished) return@evaluateJavascript

                        val snapshot = runCatching { parsePageContent(value) }.getOrNull()
                        if (snapshot == null) {
                            handler.postDelayed(::pollHtmlUntilStable, STABILITY_CHECK_INTERVAL_MS)
                            return@evaluateJavascript
                        }
                        lastPageContent = snapshot.content
                        readCount += 1

                        if (snapshot.stableHtml == lastStableHtml) {
                            stableReadCount += 1
                        } else {
                            lastStableHtml = snapshot.stableHtml
                            stableReadCount = 0
                        }

                        if (shouldCompleteWebViewPolling(stableReadCount, readCount)) {
                            complete(Result.success(snapshot.content))
                        } else {
                            handler.postDelayed(::pollHtmlUntilStable, STABILITY_CHECK_INTERVAL_MS)
                        }
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, webpageUrl: String?) {
                        if (finished || isPolling || view == null) return
                        isPolling = true
                        pollHtmlUntilStable()
                    }
                }
                cancelWebView = {
                    webView.stopLoading()
                    complete(Result.failure(CancellationException("WebView load cancelled")))
                }

                webView.loadUrl(url)
                handler.postDelayed({
                    if (!finished && !isPolling) {
                        isPolling = true
                        pollHtmlUntilStable()
                    }
                }, STABILITY_CHECK_INTERVAL_MS)

                handler.postDelayed({
                    if (!finished) {
                        webView.stopLoading()
                        val content = lastPageContent
                        if (content != null && (content.text.isNotBlank() || content.html.isNotBlank())) {
                            complete(Result.success(content))
                        } else {
                            complete(Result.failure(Exception("WebView load timeout")))
                        }
                    }
                }, timeoutMs)
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
        return handle
    }

    private companion object {
        const val STABILITY_CHECK_INTERVAL_MS = 2_000L
        const val PARSE_CONTENT_SCRIPT = """
            (function() {
                const stableHtml = document.documentElement ? (document.documentElement.innerHTML || '') : '';
                const text = document.body ? (document.body.innerText || '') : '';
                const clone = document.documentElement.cloneNode(true);
                const originalImages = Array.from(document.images || []);
                const clonedImages = Array.from(clone.getElementsByTagName('img'));

                function absoluteUrl(value) {
                    if (!value) return '';
                    try { return new URL(value, document.baseURI).href; } catch (e) { return value; }
                }

                function imageSource(img) {
                    return img.currentSrc || img.src || img.getAttribute('data-src') ||
                        img.getAttribute('data-original') || img.getAttribute('data-lazy-src') ||
                        img.getAttribute('data-url') || '';
                }

                function imageWidth(img) {
                    const attrWidth = parseInt(img.getAttribute('width') || '0', 10) || 0;
                    return img.naturalWidth || img.clientWidth || img.width || attrWidth || 0;
                }

                originalImages.forEach(function(img, index) {
                    const clonedImg = clonedImages[index];
                    if (!clonedImg) return;
                    const resolvedSrc = absoluteUrl(imageSource(img));
                    const resolvedWidth = imageWidth(img);
                    if (!resolvedSrc || resolvedWidth < 300) {
                        clonedImg.remove();
                        return;
                    }
                    clonedImg.setAttribute('src', resolvedSrc);
                    clonedImg.setAttribute('width', String(resolvedWidth));
                    clonedImg.removeAttribute('srcset');
                    clonedImg.removeAttribute('data-src');
                    clonedImg.removeAttribute('data-original');
                    clonedImg.removeAttribute('data-lazy-src');
                    clonedImg.removeAttribute('data-url');
                });

                return JSON.stringify({
                    stableHtml: stableHtml,
                    text: text,
                    html: document.documentElement ? (clone.outerHTML || document.documentElement.outerHTML || '') : ''
                });
            })();
        """
    }

    private data class PageSnapshot(
        val stableHtml: String,
        val content: WebViewPageContent,
    )
}
