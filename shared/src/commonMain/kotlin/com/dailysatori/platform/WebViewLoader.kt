package com.dailysatori.platform

expect class WebViewLoader() {
    fun loadContent(url: String, timeoutMs: Long = 25_000, callback: (Result<String>) -> Unit)
}
