package com.dailysatori.service

import android.content.ClipboardManager
import android.content.Context
import co.touchlab.kermit.Logger

class ClipboardMonitorService(private val context: Context) {
    private val log = Logger.withTag("Clipboard")
    private var lastProcessedUrl: String = ""

    fun checkClipboard(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return null
        if (text == lastProcessedUrl) return null
        if (!text.startsWith("http://") && !text.startsWith("https://")) return null
        lastProcessedUrl = text
        log.i { "Found URL in clipboard: $text" }
        return text
    }

    fun markProcessed(url: String) {
        lastProcessedUrl = url
    }
}
