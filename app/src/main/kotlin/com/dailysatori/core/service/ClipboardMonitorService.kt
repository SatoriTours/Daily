package com.dailysatori.core.service

import android.content.ClipboardManager
import android.content.Context
import com.dailysatori.ClipboardReadGate
import com.dailysatori.extractFirstUrl
import com.dailysatori.normalizeArticleUrl
import co.touchlab.kermit.Logger

class ClipboardMonitorService(private val context: Context) {
    private val log = Logger.withTag("Clipboard")
    private var lastProcessedUrl: String = ""
    private val readGate = ClipboardReadGate()

    fun checkClipboard(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val timestamp = clipboard.primaryClipDescription?.timestamp ?: 0L
        if (!readGate.shouldRead(timestamp)) return null
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return null
        readGate.markRead(timestamp)
        val url = extractFirstUrl(text) ?: return null
        if (normalizeArticleUrl(url) == normalizeArticleUrl(lastProcessedUrl)) return null
        log.i { "Found URL in clipboard" }
        return url
    }

    fun markProcessed(url: String) {
        lastProcessedUrl = url
    }
}
