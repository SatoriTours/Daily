package com.dailysatori.core.service

import android.content.ClipboardManager
import android.content.Context
import com.dailysatori.extractFirstUrl
import com.dailysatori.normalizeArticleUrl
import co.touchlab.kermit.Logger

class ClipboardMonitorService(private val context: Context) {
    private val log = Logger.withTag("Clipboard")
    private var lastProcessedUrl: String = ""

    fun checkClipboard(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return null
        val url = extractFirstUrl(text) ?: return null
        if (normalizeArticleUrl(url) == normalizeArticleUrl(lastProcessedUrl)) return null
        log.i { "Found URL in clipboard" }
        return url
    }

    fun markProcessed(url: String) {
        lastProcessedUrl = url
    }
}
