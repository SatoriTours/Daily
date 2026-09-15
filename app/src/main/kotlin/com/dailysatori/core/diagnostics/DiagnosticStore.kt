package com.dailysatori.core.diagnostics

import com.dailysatori.service.diagnostics.*
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** All ordinary file access is owned by this executor, including snapshots and cleanup. */
class DiagnosticStore(
    val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
    internal val policy: DiagnosticStorePolicy = DiagnosticStorePolicy(),
    private val previousExit: (Long) -> DiagnosticExit? = { null },
    private val executor: ScheduledThreadPoolExecutor = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "diagnostic-writer").apply { isDaemon = true }
    },
) : DiagnosticSink, AutoCloseable {
    private val errorReserve = if (policy.queueCapacity >= 8) policy.queueCapacity / 8 else 0
    private val permits = Semaphore(policy.queueCapacity - errorReserve)
    private val errorPermits = Semaphore(errorReserve)
    private val eventBudget = policy.maxBytes - minOf(4096L, policy.maxBytes / 8)
    private val dropped = AtomicLong()
    private val failures = AtomicLong()
    private val pruned = AtomicLong()
    private val corrupt = AtomicLong()
    private val truncated = AtomicLong()
    private val eventsDir = File(root, "events")
    private val exportsDir = File(root, "exports")
    private val crashes = DiagnosticCrashArchive(root, policy, clock)
    private var writer: BufferedWriter? = null
    private var active: File? = null
    private var activeBytes = 0L
    private var activeFirstMs = 0L
    private var segment = 0L
    private var closed = false

    init {
        executor.execute { guarded {
            check(eventsDir.mkdirs() || eventsDir.isDirectory)
            check(exportsDir.mkdirs() || exportsDir.isDirectory)
            exportsDir.listFiles()?.forEach { it.delete() }
            loadHealth()
            segment = segments().maxOfOrNull { it.nameWithoutExtension.toLongOrNull() ?: 0L } ?: 0L
            // Crash recovery runs before retention, so an overnight crash is not pruned first.
            recoverExits()
            prune()
        } }
        executor.scheduleWithFixedDelay({ guarded { flushWriter(); saveHealth(); prune(); crashes.prune() } }, 1, 1, TimeUnit.SECONDS)
    }

    override fun tryEmit(event: SafeDiagnosticEvent): Boolean {
        val acquired = when {
            permits.tryAcquire() -> permits
            event.level == DiagnosticLevel.ERROR && errorPermits.tryAcquire() -> errorPermits
            else -> { dropped.incrementAndGet(); return false }
        }
        return try {
            executor.execute {
                try { guarded { append(event) } } finally { acquired.release() }
            }
            true
        } catch (_: RuntimeException) {
            acquired.release()
            dropped.incrementAndGet()
            false
        }
    }

    private fun append(event: SafeDiagnosticEvent) {
        val line = diagnosticJson.encodeToString(SafeDiagnosticEvent.serializer(), event)
        val bytes = line.toByteArray(Charsets.UTF_8).size + 1
        if (bytes > 16 * 1024 || bytes > policy.segmentBytes || bytes > eventBudget) {
            truncated.incrementAndGet()
            return
        }
        val oldSlice = active != null && (event.timestampMs < activeFirstMs || event.timestampMs - activeFirstMs >= minOf(60_000L, policy.retentionMs))
        if (writer == null || activeBytes + bytes > policy.segmentBytes || oldSlice) rotate(event.timestampMs)
        writer!!.apply { write(line); newLine() }
        activeBytes += bytes
        prune()
    }

    private fun flushWriter() {
        writer?.flush()
        active?.setLastModified(activeFirstMs)
    }

    private fun rotate(firstTimestampMs: Long) {
        flushWriter()
        writer?.close()
        check(eventsDir.mkdirs() || eventsDir.isDirectory)
        active = File(eventsDir, "${(++segment).toString().padStart(20, '0')}.jsonl")
        writer = active!!.bufferedWriter()
        activeBytes = 0
        activeFirstMs = firstTimestampMs
        active!!.setLastModified(activeFirstMs)
    }

    private fun segments(): List<File> = eventsDir.listFiles()
        ?.filter { it.extension == "jsonl" }?.sortedBy { it.name }.orEmpty()

    private fun prune() {
        val files = segments()
        var bytes = files.sumOf { if (it == active) activeBytes else it.length() }
        for (file in files) {
            val firstTimestamp = if (file == active) activeFirstMs else file.lastModified()
            if (firstTimestamp >= clock() - policy.retentionMs && bytes <= eventBudget) continue
            if (file == active) { writer?.close(); writer = null; active = null; activeBytes = 0 }
            val length = file.length()
            if (file.delete()) { bytes -= length; pruned.incrementAndGet() }
        }
    }

    suspend fun snapshot(endTimeMs: Long): DiagnosticSnapshot = command {
        flushWriter()
        prune()
        check(exportsDir.isDirectory)
        check(exportsDir.listFiles().orEmpty().isEmpty()) { "A diagnostic export is already active" }
        val file = File(exportsDir, "snapshot-${DiagnosticLog.newId()}.jsonl")
        try {
            file.bufferedWriter().use { output ->
                scanEvents(endTimeMs - WINDOW_MS, endTimeMs) { event, line ->
                    output.write(line)
                    output.newLine()
                }
            }
            saveHealth()
            DiagnosticSnapshot(file, endTimeMs - WINDOW_MS, endTimeMs, health())
        } catch (error: Exception) { file.delete(); throw error }
    }

    internal fun scanEvents(start: Long, end: Long, consume: (SafeDiagnosticEvent, String) -> Unit) {
        for (file in segments()) {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val event = runCatching { diagnosticJson.decodeFromString(SafeDiagnosticEvent.serializer(), line) }.getOrNull()
                    if (event == null) corrupt.incrementAndGet()
                    else if (event.timestampMs in start..end) consume(event, line)
                }
            }
        }
    }

    suspend fun clear() = command {
        writer?.close(); writer = null; active = null; activeBytes = 0
        listOf(eventsDir, exportsDir, File(root, "crashes")).forEach { dir ->
            dir.listFiles()?.forEach { check(it.deleteRecursively()) { "Unable to clear diagnostic file" } }
        }
        listOf("crash-pending.json", "crash-pending.tmp").forEach { name ->
            val file = File(root, name)
            check(!file.exists() || file.delete()) { "Unable to clear crash marker" }
        }
        dropped.set(0); failures.set(0); pruned.set(0); corrupt.set(0); truncated.set(0)
        saveHealth()
    }

    fun health() = DiagnosticHealth(dropped.get(), failures.get(), pruned.get(), corrupt.get(), truncated.get())

    private fun loadHealth() {
        val saved = runCatching { diagnosticJson.decodeFromString(DiagnosticHealth.serializer(), File(root, "health.json").readText()) }.getOrNull() ?: return
        dropped.addAndGet(saved.droppedEvents); failures.addAndGet(saved.ioFailures)
        pruned.addAndGet(saved.prunedFiles); corrupt.addAndGet(saved.corruptLines); truncated.addAndGet(saved.truncatedEvents)
    }

    private fun saveHealth() {
        if (!root.isDirectory) return
        val temp = File(root, "health.tmp")
        temp.writeText(diagnosticJson.encodeToString(DiagnosticHealth.serializer(), health()))
        check(temp.renameTo(File(root, "health.json")))
    }

    internal suspend fun <T> command(block: () -> T): T = withContext(Dispatchers.IO) {
        executor.submit<T> {
            try { block() } catch (failure: Exception) { failures.incrementAndGet(); throw failure }
        }.get()
    }

    private fun guarded(block: () -> Unit) {
        try { block() } catch (_: Exception) { failures.incrementAndGet() }
    }

    private fun recoverExits() {
        val checkpoint = File(root, "last-exit")
        val lastSeen = runCatching { checkpoint.readText().toLong() }.getOrDefault(0L)
        val exit = runCatching { previousExit(lastSeen) }.getOrNull()
        var exitEvent: SafeDiagnosticEvent? = null
        if (exit != null) {
            Diagnostics(DiagnosticSink { exitEvent = it; true }, now = { exit.timestampMs })
                .emit(DiagnosticCode.PROCESS_EXIT, DiagnosticSource.APP,
                    if (exit.isCrash) DiagnosticLevel.ERROR else DiagnosticLevel.INFO,
                    fields = mapOf("reason" to exit.reason.toString(), "exitTimestampMs" to exit.timestampMs.toString()))
            val pending = File(root, "crash-pending.json")
            if (exit.isCrash && !pending.exists()) {
                pending.writeText(diagnosticJson.encodeToString(SafeDiagnosticEvent.serializer(), checkNotNull(exitEvent)))
            }
        }
        crashes.recover(::scanEvents)
        exitEvent?.let(::append)
        if (exit != null) checkpoint.writeText(exit.timestampMs.toString())
    }

    suspend fun usageBytes(): Long = command { root.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    suspend fun latestCrashTime(): Long? = command {
        crashes.prune()
        crashes.latest()?.name?.substringBefore('-')?.toLongOrNull()
    }

    suspend fun crashSnapshot(): DiagnosticSnapshot = command {
        crashes.prune()
        val crash = checkNotNull(crashes.latest()) { "No crash snapshot available" }
        check(exportsDir.listFiles().orEmpty().isEmpty()) { "A diagnostic export is already active" }
        val time = crash.name.substringBefore('-').toLong()
        val target = File(exportsDir, "crash-export.jsonl")
        try {
            crash.copyTo(target)
            DiagnosticSnapshot(target, time - WINDOW_MS, time, health(), crash = true)
        } catch (failure: Exception) { target.delete(); throw failure }
    }

    override fun close() {
        if (closed) return
        closed = true
        executor.submit { guarded { flushWriter(); writer?.close(); writer = null; saveHealth() } }.get()
        executor.shutdownNow()
    }

    companion object { const val WINDOW_MS = 30 * 60 * 1000L }
}
