package com.dailysatori.core.diagnostics

import com.dailysatori.service.diagnostics.DiagnosticLog
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface DiagnosticDestination {
    fun open(): OutputStream
    fun delete(): Boolean
}

enum class DiagnosticExportPhase { IDLE, PREPARING, AWAITING_DESTINATION, SAVING, SAVED, FAILED }
data class DiagnosticExportState(val phase: DiagnosticExportPhase = DiagnosticExportPhase.IDLE, val residualFile: Boolean = false)
data class DiagnosticExportRequest(val token: String, val fileName: String)

/** One owner for a private snapshot, independently testable without Android URI or UI. */
class DiagnosticExportSession(
    private val store: DiagnosticStore,
    private val exporter: DiagnosticExporter,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(DiagnosticExportState())
    val state = mutableState.asStateFlow()
    private var snapshot: DiagnosticSnapshot? = null
    private var token: String? = null

    suspend fun prepare(crash: Boolean = false): DiagnosticExportRequest? {
        if (!mutex.tryLock()) return null
        try {
            if (snapshot != null) return null
            mutableState.value = DiagnosticExportState(DiagnosticExportPhase.PREPARING)
            val end = clock()
            // Assign ownership inside the non-cancellable boundary, even if the UI is disposed.
            withContext(NonCancellable + Dispatchers.IO) { snapshot = if (crash) store.crashSnapshot() else store.snapshot(end) }
            currentCoroutineContext().ensureActive()
            val id = DiagnosticLog.newId()
            token = id
            mutableState.value = DiagnosticExportState(DiagnosticExportPhase.AWAITING_DESTINATION)
            val time = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(snapshot!!.endMs))
            return DiagnosticExportRequest(id, "DailySatori-${if (crash) "crash" else "diagnostics"}-$time.txt")
        } catch (cancelled: CancellationException) {
            release(); mutableState.value = DiagnosticExportState(); throw cancelled
        } catch (_: Exception) {
            release(); mutableState.value = DiagnosticExportState(DiagnosticExportPhase.FAILED); return null
        } finally { mutex.unlock() }
    }

    suspend fun save(requestToken: String, destination: DiagnosticDestination?): Boolean = mutex.withLock {
        if (requestToken != token || snapshot == null) return@withLock false
        if (destination == null) {
            release(); mutableState.value = DiagnosticExportState(); return@withLock true
        }
        mutableState.value = DiagnosticExportState(DiagnosticExportPhase.SAVING)
        try {
            withContext(Dispatchers.IO) { destination.open().use { exporter.write(checkNotNull(snapshot), it) } }
            mutableState.value = DiagnosticExportState(DiagnosticExportPhase.SAVED)
        } catch (failure: Exception) {
            val deleted = withContext(NonCancellable) {
                withContext(Dispatchers.IO) { runCatching { destination.delete() }.getOrDefault(false) }
            }
            mutableState.value = DiagnosticExportState(DiagnosticExportPhase.FAILED, residualFile = !deleted)
            if (failure is CancellationException) throw failure
        } finally { release() }
        true
    }

    suspend fun cancel(requestToken: String? = null) = mutex.withLock {
        if (requestToken != null && token != requestToken) return@withLock
        release()
        mutableState.value = DiagnosticExportState()
    }

    suspend fun clear() = mutex.withLock {
        release()
        try {
            store.clear()
            mutableState.value = DiagnosticExportState()
        } catch (_: Exception) { mutableState.value = DiagnosticExportState(DiagnosticExportPhase.FAILED) }
    }

    private suspend fun release() {
        // Keep the return boundary non-cancellable too, so the owner can finish resetting state.
        withContext(NonCancellable) {
            val owned = snapshot
            snapshot = null
            token = null
            withContext(Dispatchers.IO) { owned?.close() }
        }
    }
}
