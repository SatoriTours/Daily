package com.dailysatori.core.diagnostics

import com.dailysatori.service.diagnostics.SafeDiagnosticEvent
import java.io.File
import java.util.ArrayDeque

/** Called only by DiagnosticStore's writer executor, before ordinary history is pruned. */
internal class DiagnosticCrashArchive(
    private val root: File,
    private val policy: DiagnosticStorePolicy,
    private val clock: () -> Long,
) {
    private val directory = File(root, "crashes")

    fun recover(scan: (Long, Long, (SafeDiagnosticEvent, String) -> Unit) -> Unit) {
        check(directory.mkdirs() || directory.isDirectory)
        val pending = File(root, "crash-pending.json")
        if (pending.isFile) recoverPending(pending, scan)
        prune()
    }

    private fun recoverPending(pending: File, scan: (Long, Long, (SafeDiagnosticEvent, String) -> Unit) -> Unit) {
        if (pending.length() > 16 * 1024) { pending.delete(); return }
        val crash = runCatching { diagnosticJson.decodeFromString(SafeDiagnosticEvent.serializer(), pending.readText()) }.getOrNull()
        if (crash == null || crash.timestampMs < clock() - policy.crashRetentionMs) { pending.delete(); return }
        val target = File(directory, "${crash.timestampMs.toString().padStart(20, '0')}-${crash.sessionId}.jsonl")
        if (target.exists()) { pending.delete(); return }
        val crashLine = diagnosticJson.encodeToString(SafeDiagnosticEvent.serializer(), crash)
        val tail = ArrayDeque<String>()
        var bytes = crashLine.toByteArray().size + 1L
        scan(crash.timestampMs - DiagnosticStore.WINDOW_MS, crash.timestampMs) { _, line ->
            val size = line.toByteArray().size + 1
            if (size + crashLine.toByteArray().size + 1 <= policy.crashBytes) {
                tail.addLast(line)
                bytes += size
                while (bytes > policy.crashBytes) bytes -= tail.removeFirst().toByteArray().size + 1
            }
        }
        val temp = File(directory, "pending.tmp")
        temp.bufferedWriter().use { writer ->
            tail.forEach { writer.write(it); writer.newLine() }
            writer.write(crashLine); writer.newLine()
        }
        check(temp.renameTo(target))
        target.setLastModified(crash.timestampMs)
        pending.delete()
    }

    fun prune() {
        val files = directory.listFiles()?.filter { it.extension == "jsonl" }?.sortedByDescending { it.name }.orEmpty()
        files.forEachIndexed { index, file ->
            if (index >= 3 || file.lastModified() < clock() - policy.crashRetentionMs) file.delete()
        }
        File(directory, "pending.tmp").delete()
    }

    fun latest(): File? = directory.listFiles()?.filter { it.extension == "jsonl" }?.maxByOrNull { it.name }
}
