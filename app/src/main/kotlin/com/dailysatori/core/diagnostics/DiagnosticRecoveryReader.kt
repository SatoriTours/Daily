package com.dailysatori.core.diagnostics

import com.dailysatori.service.diagnostics.DiagnosticCode
import com.dailysatori.service.diagnostics.DiagnosticLog
import com.dailysatori.service.diagnostics.SafeDiagnosticEvent
import java.io.File
import java.io.RandomAccessFile
import java.util.ArrayDeque

/** Reads business logs without a writer/DI; export leases and acknowledgements live separately. */
class DiagnosticRecoveryReader(
    private val root: File,
    private val recoveryRoot: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val policy: DiagnosticStorePolicy = DiagnosticStorePolicy(),
    private val installedAtMs: Long = 0,
    private val previousExit: (Long) -> DiagnosticExit? = { null },
) {
    init {
        recoveryRoot.listFiles()?.filter { it.name.startsWith("snapshot-") && it.lastModified() < clock() - policy.retentionMs }
            ?.forEach { it.delete() }
    }

    fun needsRecovery(): Boolean {
        val crash = latestCrash(currentInstallOnly = true)
        if (crash != null && readAcknowledgement("crash") != crash.key) return true
        val startup = startupMarker() ?: return false
        val startedAt = startup.substringBefore(':').toLongOrNull() ?: return false
        if (startedAt < installedAtMs || startedAt < clock() - policy.crashRetentionMs ||
            readAcknowledgement("startup") == startup) return false
        // An interrupted launch can also mean force-stop, reboot or an ordinary system kill.
        val exit = runCatching { previousExit(startedAt - 1) }.getOrNull() ?: return false
        return exit.isCrash && exit.timestampMs >= startedAt
    }

    fun acknowledgeCrash() {
        check(recoveryRoot.mkdirs() || recoveryRoot.isDirectory)
        latestCrash()?.let { File(recoveryRoot, "acknowledged-crash").writeText(it.key) }
        startupMarker()?.let { File(recoveryRoot, "acknowledged-startup").writeText(it) }
    }

    fun snapshot(crash: Boolean, endTimeMs: Long): DiagnosticSnapshot {
        check(recoveryRoot.mkdirs() || recoveryRoot.isDirectory)
        val lastCrash = if (crash) latestCrash() else null
        val startupTime = if (crash) startupMarker()?.substringBefore(':')?.toLongOrNull() else null
        val end = lastCrash?.event?.timestampMs ?: startupTime?.let { minOf(endTimeMs, it + DiagnosticStore.WINDOW_MS) } ?: endTimeMs
        val start = end - DiagnosticStore.WINDOW_MS
        val target = File(recoveryRoot, "snapshot-${DiagnosticLog.newId()}.jsonl")
        val tail = ArrayDeque<String>()
        var bytes = 0L
        var corrupt = 0L
        var truncated = 0L
        val savedHealth = runCatching {
            diagnosticJson.decodeFromString(DiagnosticHealth.serializer(), File(root, "health.json").readText())
        }.getOrDefault(DiagnosticHealth())
        fun append(event: SafeDiagnosticEvent) {
            if (event.timestampMs !in start..end) return
            val line = diagnosticJson.encodeToString(SafeDiagnosticEvent.serializer(), event)
            val size = line.toByteArray(Charsets.UTF_8).size + 1
            if (size > policy.crashBytes) { truncated++; return }
            tail.addLast(line)
            bytes += size
            while (bytes > policy.crashBytes) { bytes -= tail.removeFirst().toByteArray(Charsets.UTF_8).size + 1; truncated++ }
        }
        try {
            val files = if (lastCrash?.archived == true) listOf(lastCrash.file) else eventFiles()
            for (file in files) {
                try {
                    file.bufferedReader().useLines { lines -> lines.forEach { line ->
                        val event = decode(line)
                        if (event == null) corrupt++ else append(event)
                    } }
                } catch (_: java.io.IOException) { corrupt++ } // Another process may prune a segment while we read.
            }
            if (lastCrash != null && !lastCrash.archived) append(lastCrash.event)
            target.bufferedWriter().use { writer -> tail.forEach { writer.write(it); writer.newLine() } }
            return DiagnosticSnapshot(target, start, end,
                savedHealth.copy(corruptLines = savedHealth.corruptLines + corrupt,
                    truncatedEvents = savedHealth.truncatedEvents + truncated), crash = lastCrash != null)
        } catch (failure: Exception) { target.delete(); throw failure }
    }

    private fun eventFiles() = File(root, "events").listFiles()
        ?.filter { it.extension == "jsonl" }?.sortedBy { it.name }.orEmpty()

    private fun latestCrash(currentInstallOnly: Boolean = false): Crash? {
        fun eligible(event: SafeDiagnosticEvent) = isRecentCrash(event) &&
            (!currentInstallOnly || occurredAt(event) >= installedAtMs)
        val pending = File(root, "crash-pending.json")
        val pendingEvent = runCatching {
            if (pending.length() in 1..MAX_LINE_BYTES) decode(pending.readText()) else null
        }.getOrNull()?.takeIf(::eligible)
        val archived = File(root, "crashes").listFiles()?.filter { it.extension == "jsonl" }
            ?.sortedByDescending { it.name }.orEmpty().firstNotNullOfOrNull { file ->
                runCatching {
                    // Archive recovery writes the crash last; launching need not decode 10 MiB of context.
                    RandomAccessFile(file, "r").use { input ->
                        val size = minOf(input.length(), MAX_LINE_BYTES + 2).toInt()
                        val bytes = ByteArray(size)
                        input.seek(input.length() - size)
                        input.readFully(bytes)
                        String(bytes, Charsets.UTF_8).lineSequence().mapNotNull(::decode)
                            .lastOrNull(::eligible)?.let { Crash(file, it, true) }
                    }
                }.getOrNull()
            }
        val candidate = pendingEvent?.let { Crash(pending, it, false) }
        return listOfNotNull(candidate, archived).maxByOrNull { occurredAt(it.event) }
    }

    private fun isRecentCrash(event: SafeDiagnosticEvent): Boolean =
        occurredAt(event) >= clock() - policy.crashRetentionMs &&
            (event.eventCode == DiagnosticCode.CRASH ||
                (event.eventCode == DiagnosticCode.PROCESS_EXIT && event.attributes["reason"] in setOf("4", "5", "6")))

    private fun occurredAt(event: SafeDiagnosticEvent): Long =
        if (event.eventCode == DiagnosticCode.PROCESS_EXIT)
            event.attributes["exitTimestampMs"]?.toLongOrNull() ?: event.timestampMs else event.timestampMs

    private fun decode(line: String): SafeDiagnosticEvent? =
        if (line.length > MAX_LINE_BYTES) null else runCatching {
            diagnosticJson.decodeFromString(SafeDiagnosticEvent.serializer(), line)
        }.getOrNull()

    private fun readAcknowledgement(kind: String) = runCatching { File(recoveryRoot, "acknowledged-$kind").readText() }.getOrNull()
    private fun startupMarker() = runCatching { File(root, STARTUP_MARKER).readText().takeIf { it.length <= 100 } }.getOrNull()

    private data class Crash(val file: File, val event: SafeDiagnosticEvent, val archived: Boolean) {
        val key get() = "${event.timestampMs}:${event.sessionId}"
    }

    companion object {
        private const val MAX_LINE_BYTES = 16 * 1024L
        private const val STARTUP_MARKER = "startup-pending"

        fun startupStarted(root: File) {
            runCatching {
                check(root.mkdirs() || root.isDirectory)
                File(root, STARTUP_MARKER).writeText("${System.currentTimeMillis()}:${DiagnosticLog.newId()}")
            }
        }

        fun startupCompleted(root: File) { runCatching { File(root, STARTUP_MARKER).delete() } }
    }
}
