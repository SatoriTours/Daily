package com.dailysatori.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import org.koin.mp.KoinPlatform

actual class WebViewLoader actual constructor() {
    private val context: Context by lazy { KoinPlatform.getKoin().get<PlatformContext>().context }

    actual fun loadContent(url: String, timeoutMs: Long, callback: (Result<String>) -> Unit) {
        Handler(Looper.getMainLooper()).post {
            try {
                val handler = Handler(Looper.getMainLooper())
                val webView = WebView(context)
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true

                var finished = false
                var isPolling = false
                var lastHtml = ""
                var stableReadCount = 0

                fun complete(result: Result<String>) {
                    if (finished) return
                    finished = true
                    callback(result)
                    webView.destroy()
                }

                fun decodeJavascriptString(value: String?): String {
                    val raw = value ?: return ""
                    if (raw == "null") return ""
                    return raw
                        .removeSurrounding("\"")
                        .replace("\\u003C", "<")
                        .replace("\\u003E", ">")
                        .replace("\\u0026", "&")
                        .replace("\\/", "/")
                        .replace("\\\"", "\"")
                        .replace("\\n", "\n")
                        .replace("\\r", "")
                        .replace("\\t", "\t")
                        .replace("\\\\", "\\")
                }

                fun pollHtmlUntilStable() {
                    if (finished) return
                    webView.evaluateJavascript(PARSE_CONTENT_SCRIPT) { html ->
                        if (finished) return@evaluateJavascript

                        val currentHtml = decodeJavascriptString(html)
                        if (currentHtml == lastHtml) {
                            stableReadCount += 1
                        } else {
                            lastHtml = currentHtml
                            stableReadCount = 0
                        }

                        if (stableReadCount >= 2) {
                            complete(Result.success(currentHtml))
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

                webView.loadUrl(url)

                handler.postDelayed({
                    if (!finished) {
                        webView.stopLoading()
                        complete(Result.failure(Exception("WebView load timeout")))
                    }
                }, timeoutMs)
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    private companion object {
        const val STABILITY_CHECK_INTERVAL_MS = 2_000L
        const val PARSE_CONTENT_SCRIPT = """
            (function() {
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

                return clone.outerHTML;
            })();
        """
    }
}
