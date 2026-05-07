package com.dailysatori.platform

data class WebViewPageContent(
    val html: String,
    val text: String,
)

expect class WebViewLoadHandle {
    fun cancel()
}

expect class WebViewLoader() {
    fun loadContent(url: String, timeoutMs: Long = 25_000, callback: (Result<WebViewPageContent>) -> Unit): WebViewLoadHandle
}
