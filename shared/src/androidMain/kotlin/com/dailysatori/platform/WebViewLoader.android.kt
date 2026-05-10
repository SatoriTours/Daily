package com.dailysatori.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import org.json.JSONTokener
import org.koin.mp.KoinPlatform
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayInputStream

actual class WebViewLoadHandle internal constructor(
    private val cancelAction: () -> Unit,
) {
    actual fun cancel() = cancelAction()
}

actual class WebViewLoader actual constructor() {
    private val context: Context by lazy { KoinPlatform.getKoin().get<PlatformContext>().context }
    private val loadLock = Any()
    private val pendingLoads = ArrayDeque<QueuedWebViewLoad>()
    private var activeLoad: QueuedWebViewLoad? = null
    private val readabilityJs: String by lazy {
        runCatching {
            context.assets.open("js/Readability.js").bufferedReader().use { it.readText() }
        }.getOrElse { "" }
    }

    actual fun loadContent(
        url: String,
        timeoutMs: Long,
        callback: (Result<WebViewPageContent>) -> Unit,
    ): WebViewLoadHandle {
        val load = QueuedWebViewLoad(url, timeoutMs, callback)
        synchronized(loadLock) {
            pendingLoads.addLast(load)
        }
        startNextLoad()
        return WebViewLoadHandle {
            var notifyCancelled = false
            val runningHandle = synchronized(loadLock) {
                if (load.completed) return@synchronized null
                load.cancelled = true
                if (activeLoad === load) {
                    load.runningHandle
                } else {
                    notifyCancelled = pendingLoads.remove(load)
                    null
                }
            }
            if (runningHandle != null) {
                runningHandle.cancel()
            } else if (notifyCancelled) {
                callback(Result.failure(CancellationException("WebView load cancelled")))
            }
        }
    }

    private fun startNextLoad() {
        val load = synchronized(loadLock) {
            if (activeLoad != null || pendingLoads.isEmpty()) return
            pendingLoads.removeFirst().also { activeLoad = it }
        }
        if (load.cancelled) {
            finishLoad(load)
            return
        }
        val handle = startWebViewLoad(load.url, load.timeoutMs) { result ->
            try {
                if (!load.cancelled) load.callback(result)
            } finally {
                finishLoad(load)
            }
        }
        val shouldCancel = synchronized(loadLock) {
            load.runningHandle = handle
            load.cancelled
        }
        if (shouldCancel) handle.cancel()
    }

    private fun finishLoad(load: QueuedWebViewLoad) {
        val shouldStartNext = synchronized(loadLock) {
            if (activeLoad !== load) return@synchronized false
            load.completed = true
            activeLoad = null
            true
        }
        if (shouldStartNext) startNextLoad()
    }

    private fun startWebViewLoad(
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
                webView.settings.useWideViewPort = true
                webView.settings.loadWithOverviewMode = true
                webView.settings.userAgentString = DESKTOP_USER_AGENT

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
                        hasUsableContent = json.optBoolean("hasUsableContent"),
                        content = WebViewPageContent(
                            html = json.optString("html"),
                            text = json.optString("text"),
                            readableTitle = json.optString("readableTitle").takeIf { it.isNotBlank() },
                            readableContent = json.optString("readableContent").takeIf { it.isNotBlank() },
                            readableExcerpt = json.optString("readableExcerpt").takeIf { it.isNotBlank() },
                        ),
                    )
                }

                fun pollHtmlUntilStable() {
                    if (finished) return
                    webView.evaluateJavascript(parseContentScript(readabilityJs)) { value ->
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

                        if (shouldCompleteWebViewPolling(
                                stableReadCount,
                                readCount,
                                requireUsableContent = true,
                                hasUsableContent = snapshot.hasUsableContent,
                            )
                        ) {
                            complete(Result.success(snapshot.content))
                        } else {
                            handler.postDelayed(::pollHtmlUntilStable, STABILITY_CHECK_INTERVAL_MS)
                        }
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val host = request?.url?.host?.lowercase().orEmpty()
                        if (host.isNotBlank() && AD_DOMAINS.any { host.contains(it) }) {
                            return WebResourceResponse(
                                "text/plain",
                                "utf-8",
                                ByteArrayInputStream(ByteArray(0)),
                            )
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, webpageUrl: String?) {
                        if (finished || isPolling || view == null) return
                        isPolling = true
                        pollHtmlUntilStable()
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        if (request?.isForMainFrame == true) {
                            complete(Result.failure(Exception("WebView load failed: ${error?.description ?: "unknown error"}")))
                            return
                        }
                        super.onReceivedError(view, request, error)
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400) {
                            complete(Result.failure(Exception("WebView HTTP error: ${errorResponse?.statusCode}")))
                            return
                        }
                        super.onReceivedHttpError(view, request, errorResponse)
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

    private class QueuedWebViewLoad(
        val url: String,
        val timeoutMs: Long,
        val callback: (Result<WebViewPageContent>) -> Unit,
        var cancelled: Boolean = false,
        var completed: Boolean = false,
        var runningHandle: WebViewLoadHandle? = null,
    )

    private companion object {
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
        const val STABILITY_CHECK_INTERVAL_MS = 2_000L
        val AD_DOMAINS = listOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "google-analytics.com",
            "googletagmanager.com",
            "googletagservices.com",
            "adservice.google.com",
            "pagead2.googlesyndication.com",
            "analytics.twitter.com",
            "ads.twitter.com",
            "facebook.com",
            "connect.facebook.net",
            "outbrain.com",
            "taboola.com",
        )

        fun parseContentScript(readabilityJs: String): String = """
            (function() {
                const stableHtml = document.documentElement ? (document.documentElement.innerHTML || '') : '';
                const text = document.body ? (document.body.innerText || '') : '';
                const clone = document.documentElement.cloneNode(true);
                const originalImages = Array.from(document.images || []);
                const clonedImages = Array.from(clone.getElementsByTagName('img'));
                let readableTitle = '';
                let readableContent = '';
                let readableExcerpt = '';

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

                function ensureReadability() {
                    if (typeof Readability === 'function') return true;
                    const source = ${JSONObject.quote(readabilityJs)};
                    if (!source) return false;
                    const script = document.createElement('script');
                    script.textContent = source;
                    document.head.appendChild(script);
                    return typeof Readability === 'function';
                }

                function fallbackReadableContent() {
                    const tweetText = document.querySelector('[data-testid="tweetText"]');
                    if (tweetText) {
                        const parts = [];
                        document.querySelectorAll('[data-testid="tweetText"]').forEach(function(t) {
                            if (t.innerText && t.innerText.trim()) parts.push(t.innerText.trim());
                        });
                        const userName = document.querySelector('[data-testid="User-Name"]');
                        const author = userName ? (userName.innerText || '').split('\n')[0] : '';
                        return (author ? '**' + author + '**\n\n' : '') + parts.join('\n\n---\n\n');
                    }

                    const selectors = ['main', 'article', '[role="main"]', '.post-content', '.article-content', '.entry-content', '#content'];
                    let el = null;
                    for (let i = 0; i < selectors.length; i++) {
                        const candidate = document.querySelector(selectors[i]);
                        if (candidate && (candidate.innerText || '').trim().length > 100) {
                            el = candidate;
                            break;
                        }
                    }
                    if (!el) el = document.body;
                    if (!el) return '';
                    const fallbackClone = el.cloneNode(true);
                    fallbackClone.querySelectorAll('script,style,nav,footer,header,[role="navigation"],[role="banner"]').forEach(function(n) {
                        n.remove();
                    });
                    const fallbackText = (fallbackClone.innerText || '').trim();
                    return fallbackText.length >= 50 ? fallbackText : '';
                }

                function isUsableContent(value) {
                    if (!value) return false;
                    const compact = String(value).replace(/\s+/g, '').trim();
                    if (compact.length < 24) return false;
                    if (!/[A-Za-z0-9\u4e00-\u9fff]/.test(value)) return false;

                    const lines = String(value)
                        .split('\n')
                        .map(function(line) { return String(line || '').trim().toLowerCase(); })
                        .filter(function(line) { return line.length > 0; });
                    if (lines.length === 0) return false;

                    const noiseWords = [
                        '登录',
                        '注册',
                        '首页',
                        'login',
                        'log in',
                        'sign in',
                        'sign up',
                        'menu',
                        'search',
                        'subscribe',
                        'subscribe to',
                        'follow',
                        'following',
                        'home',
                        'explore',
                        'notifications',
                    ];

                    const noiseLines = lines.filter(function(line) {
                        return noiseWords.includes(line);
                    });
                    return !(lines.length >= 3 && noiseLines.length >= lines.length * 0.6);
                }

                try {
                    if (ensureReadability()) {
                        const article = new Readability(document.cloneNode(true)).parse();
                        if (article) {
                            readableTitle = article.title || '';
                            readableContent = article.content || '';
                            readableExcerpt = article.excerpt || '';
                        }
                    }
                } catch (e) {}

                const readableContentCandidate = readableContent || fallbackReadableContent();
                if (!readableContent) {
                    readableContent = readableContentCandidate;
                    readableTitle = readableTitle || document.title || '';
                    readableExcerpt = readableExcerpt || readableContentCandidate.substring(0, 200);
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
                    html: document.documentElement ? (clone.outerHTML || document.documentElement.outerHTML || '') : '',
                    readableTitle: readableTitle,
                    readableContent: readableContent,
                    readableExcerpt: readableExcerpt,
                    hasUsableContent: isUsableContent(readableContentCandidate),
                });
            })();
        """
    }

    private data class PageSnapshot(
        val stableHtml: String,
        val hasUsableContent: Boolean,
        val content: WebViewPageContent,
    )
}
