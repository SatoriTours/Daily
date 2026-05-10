package com.dailysatori.platform

data class WebViewPageContent(
    val html: String,
    val text: String,
    val readableTitle: String? = null,
    val readableContent: String? = null,
    val readableExcerpt: String? = null,
)

internal fun shouldCompleteWebViewPolling(
    stableReadCount: Int,
    readCount: Int,
    maxReadCount: Int = 5,
    requireUsableContent: Boolean = false,
    hasUsableContent: Boolean = false,
): Boolean = if (requireUsableContent) {
    (stableReadCount >= 2 && hasUsableContent) || readCount >= maxReadCount
} else {
    stableReadCount >= 2 || readCount >= maxReadCount
}

expect class WebViewLoadHandle {
    fun cancel()
}

expect class WebViewLoader() {
    fun loadContent(url: String, timeoutMs: Long = 25_000, callback: (Result<WebViewPageContent>) -> Unit): WebViewLoadHandle
}
