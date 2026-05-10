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
    maxReadCount: Int = 10,
): Boolean = stableReadCount >= 3 || readCount >= maxReadCount

expect class WebViewLoadHandle {
    fun cancel()
}

expect class WebViewLoader() {
    fun loadContent(url: String, timeoutMs: Long = 10_000, callback: (Result<WebViewPageContent>) -> Unit): WebViewLoadHandle
}
