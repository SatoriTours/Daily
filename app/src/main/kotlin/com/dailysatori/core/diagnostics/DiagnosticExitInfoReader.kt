package com.dailysatori.core.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** Android's numeric exit reason is not equivalent to a Java exception. No raw trace/message. */
data class DiagnosticExit(val timestampMs: Long, val reason: Int) {
    val isCrash: Boolean get() = reason in setOf(4, 5, 6) // CRASH, CRASH_NATIVE, ANR (API 30).
}

object DiagnosticExitInfoReader {
    fun select(api: Int, lastSeen: Long, records: List<DiagnosticExit>): DiagnosticExit? =
        if (api < 30) null else records.filter { it.timestampMs > lastSeen }.maxByOrNull { it.timestampMs }

    fun read(context: Context, lastSeen: Long): DiagnosticExit? {
        if (Build.VERSION.SDK_INT < 30) return null
        val manager = context.getSystemService(ActivityManager::class.java)
        val records = manager.getHistoricalProcessExitReasons(null, 0, 5)
            .map { DiagnosticExit(it.timestamp, it.reason) }
        return select(Build.VERSION.SDK_INT, lastSeen, records)
    }
}
