package com.dailysatori.core.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** Android's numeric exit reason is not equivalent to a Java exception. No raw trace/message. */
data class DiagnosticExit(val timestampMs: Long, val reason: Int, val processName: String? = null) {
    val isCrash: Boolean get() = reason in setOf(4, 5, 6) // CRASH, CRASH_NATIVE, ANR (API 30).
}

object DiagnosticExitInfoReader {
    /**
     * Exit history includes other app processes, including `:diagnostics` and WebView services.
     * Filter by process before choosing the newest exit; otherwise an unrelated exit can mask it.
     */
    fun select(api: Int, lastSeen: Long, records: List<DiagnosticExit>, processName: String? = null): DiagnosticExit? =
        if (api < 30) null else records
            .filter { it.timestampMs > lastSeen && (processName == null || it.processName == processName) }
            .maxByOrNull { it.timestampMs }

    fun read(context: Context, lastSeen: Long, processName: String = context.packageName): DiagnosticExit? {
        if (Build.VERSION.SDK_INT < 30) return null
        val manager = context.getSystemService(ActivityManager::class.java)
        val records = manager.getHistoricalProcessExitReasons(null, 0, 0)
            .map { DiagnosticExit(it.timestamp, it.reason, it.processName) }
        return select(Build.VERSION.SDK_INT, lastSeen, records, processName)
    }
}
