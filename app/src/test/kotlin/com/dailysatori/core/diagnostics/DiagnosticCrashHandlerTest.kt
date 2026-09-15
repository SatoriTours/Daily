package com.dailysatori.core.diagnostics

import com.dailysatori.service.diagnostics.*
import java.nio.file.Files
import java.io.File
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class DiagnosticCrashHandlerTest {
    @Test
    fun crashWriterAlwaysDelegatesAndOmitsExceptionMessage() {
        val root = Files.createTempDirectory("diagnostic-crash").toFile()
        var calls = 0
        val handler = DiagnosticCrashHandler(root, Thread.UncaughtExceptionHandler { _, _ -> calls++ })
        handler.uncaughtException(Thread.currentThread(), IllegalStateException("secret-canary"))
        assertEquals(1, calls)
        val text = File(root, "crash-pending.json").readText()
        assertTrue(text.contains("IllegalStateException"))
        assertFalse(text.contains("secret-canary"))
        root.deleteRecursively()
    }

    @Test
    fun ioFailureDoesNotPreventOriginalHandler() {
        val file = Files.createTempFile("diagnostic-failure", ".tmp").toFile()
        var calls = 0
        DiagnosticCrashHandler(file, Thread.UncaughtExceptionHandler { _, _ -> calls++ })
            .uncaughtException(Thread.currentThread(), IllegalStateException())
        assertEquals(1, calls)
        file.delete()
    }

    @Test
    fun overnightRecoveryProtectsCrashWindowBeforeNormalRetentionPrunesIt() = runBlocking<Unit> {
        val root = Files.createTempDirectory("diagnostic-recovery").toFile()
        var time = 2_000_000L
        DiagnosticStore(root, clock = { time }).use { store ->
            val log = Diagnostics(store, now = { time })
            log.emit(DiagnosticCode.HTTP_START, DiagnosticSource.HTTP)
            store.snapshot(time).close()
        }
        DiagnosticCrashHandler(root, Thread.UncaughtExceptionHandler { _, _ -> }, now = { time })
            .uncaughtException(Thread.currentThread(), IllegalStateException("private"))
        time += 2 * 24 * 60 * 60 * 1000L
        DiagnosticStore(root, clock = { time }).use { store ->
            store.crashSnapshot().use {
                assertTrue(it.crash)
                assertEquals(listOf(DiagnosticCode.HTTP_START, DiagnosticCode.CRASH), it.events().map { e -> e.eventCode }.toList())
                assertEquals(2_000_000L, it.endMs)
            }
            assertEquals(0, store.snapshot(time).use { it.events().count() })
        }
        root.deleteRecursively()
    }
}
