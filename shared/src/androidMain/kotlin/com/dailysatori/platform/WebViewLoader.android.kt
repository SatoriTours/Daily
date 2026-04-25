package com.dailysatori.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import org.koin.mp.KoinPlatform

actual class WebViewLoader actual constructor() {
    private val context: Context by lazy { KoinPlatform.getKoin().get<Context>() }

    actual fun loadContent(url: String, timeoutMs: Long, callback: (Result<String>) -> Unit) {
        try {
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true

            var finished = false
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, webpageUrl: String?) {
                    if (finished) return
                    finished = true
                    view?.evaluateJavascript("document.documentElement.outerHTML") { html ->
                        callback(Result.success(html ?: ""))
                    }
                }
            }

            webView.loadUrl(url)

            Handler(Looper.getMainLooper()).postDelayed({
                if (!finished) {
                    finished = true
                    webView.stopLoading()
                    callback(Result.failure(Exception("WebView load timeout")))
                }
            }, timeoutMs)
        } catch (e: Exception) {
            callback(Result.failure(e))
        }
    }
}
