package com.dailysatori.service.diagnostics

import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.asContextElement

actual object DiagnosticLog {
    @Volatile actual var diagnostics = Diagnostics()
    actual val sessionId = UUID.randomUUID().toString()
    private val trace = ThreadLocal<DiagnosticTrace?>()
    private val sequence = AtomicLong()
    private val started = System.nanoTime()
    private val registered = java.util.concurrent.ConcurrentHashMap<DiagnosticSource, DiagnosticCoverage>()
    actual fun registerCoverage(source: DiagnosticSource, coverage: DiagnosticCoverage) { registered[source] = coverage }
    actual fun coverage(): Map<DiagnosticSource, DiagnosticCoverage> = registered.toMap()

    actual fun currentTrace(): DiagnosticTrace? = trace.get()
    actual fun traceContext(trace: DiagnosticTrace): CoroutineContext = this.trace.asContextElement(trace)
    actual fun newId(): String = UUID.randomUUID().toString()
    actual fun nextSequence(): Long = sequence.incrementAndGet()
    actual fun now(): Long = System.currentTimeMillis()
    actual fun threadCategory(): String = if (Thread.currentThread().name == "main") "main" else "worker"
    actual fun elapsed(): Long = (System.nanoTime() - started) / 1_000_000
    actual fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).take(6).joinToString("") { "%02x".format(it) }

    actual fun safeStack(error: Throwable): List<String> {
        val result = mutableListOf<String>()
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        val pending = java.util.ArrayDeque<Throwable>().apply { add(error) }
        var frames = 0
        while (pending.isNotEmpty() && visited.size < 8) {
            val current = pending.removeFirst()
            if (!visited.add(current)) continue
            result.add(current.javaClass.name.take(120))
            for (frame in current.stackTrace.take(64)) {
                if (frames++ >= 32) break
                result.add("at ${frame.className.take(100)}.${frame.methodName.take(64)}(${frame.fileName?.take(64)}:${frame.lineNumber})")
            }
            current.cause?.let(pending::addLast)
            current.suppressed.take(8).forEach(pending::addLast)
        }
        result.add("[exception messages omitted; stack bounded to 8 causes / 32 frames]")
        return result
    }
}
