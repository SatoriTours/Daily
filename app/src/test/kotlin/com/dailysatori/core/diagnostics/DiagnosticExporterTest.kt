package com.dailysatori.core.diagnostics

import com.dailysatori.service.diagnostics.*
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor

class DiagnosticExporterTest {
    @Test
    fun reportContainsFactsAndCoverageWithoutPrivateData() = runBlocking<Unit> {
        val root = Files.createTempDirectory("diagnostic-report").toFile()
        DiagnosticStore(root).use { store ->
            val log = Diagnostics(store)
            log.emit(DiagnosticCode.HTTP_START, DiagnosticSource.HTTP, requestId = DiagnosticLog.newId(),
                fields = mapOf("url" to "https://example.com/secret-path?password=private"))
            log.emit(DiagnosticCode.HTTP_FAILED, DiagnosticSource.HTTP, DiagnosticLevel.ERROR,
                error = IllegalArgumentException("secret-canary"))
            store.snapshot(System.currentTimeMillis()).use { snapshot ->
                val output = ByteArrayOutputStream()
                DiagnosticExporter("5.1.63", 50163, 36).write(snapshot, output)
                val text = output.toString("UTF-8")
                assertTrue(text.contains("HTTP_FAILED=1"))
                assertTrue(text.contains("startOutsideWindowOrMissing"))
                assertTrue(text.contains("coverage"))
                assertTrue(text.contains("50163"))
                assertFalse(text.contains("secret-canary"))
                assertFalse(text.contains("secret-path"))
                text.lineSequence().filter { it.startsWith("{\"timestampMs\"") }.forEach {
                    diagnosticJson.decodeFromString(SafeDiagnosticEvent.serializer(), it)
                }
            }
        }
        root.deleteRecursively()
    }

    @Test
    fun closeFailureIsNotReportedAsSavedAndSnapshotIsReleased() = runBlocking<Unit> {
        val root = Files.createTempDirectory("diagnostic-save").toFile()
        DiagnosticStore(root).use { store ->
            val session = DiagnosticExportSession(store, DiagnosticExporter("test", 1, 26))
            val request = assertNotNull(session.prepare())
            var deleted = false
            session.save(request.token, object : DiagnosticDestination {
                override fun open(): OutputStream = object : ByteArrayOutputStream() {
                    override fun close() { throw java.io.IOException("disk-full-private") }
                }
                override fun delete(): Boolean { deleted = true; return true }
            })
            assertEquals(DiagnosticExportPhase.FAILED, session.state.value.phase)
            assertTrue(deleted)
            assertTrue(java.io.File(root, "exports").listFiles().orEmpty().isEmpty())
        }
        root.deleteRecursively()
    }

    @Test
    fun cancellingPreparationCannotLeakAnUnownedSnapshot() = runBlocking<Unit> {
        val root = Files.createTempDirectory("diagnostic-cancel").toFile()
        val executor = ScheduledThreadPoolExecutor(1)
        val release = CountDownLatch(1)
        executor.execute { release.await() }
        DiagnosticStore(root, executor = executor).use { store ->
            val session = DiagnosticExportSession(store, DiagnosticExporter("test", 1, 26))
            val preparing = async(start = CoroutineStart.UNDISPATCHED) { session.prepare() }
            assertEquals(DiagnosticExportPhase.PREPARING, session.state.value.phase)
            preparing.cancel()
            release.countDown()
            preparing.join()
            assertEquals(DiagnosticExportPhase.IDLE, session.state.value.phase)
            assertTrue(java.io.File(root, "exports").listFiles().orEmpty().isEmpty())
        }
        root.deleteRecursively()
    }

    @Test
    fun destinationDelayDoesNotMoveTheExportWindow() = runBlocking<Unit> {
        val root = Files.createTempDirectory("diagnostic-delay").toFile()
        var time = 2_000_000L
        DiagnosticStore(root, clock = { time }).use { store ->
            val log = Diagnostics(store, now = { time })
            log.emit(DiagnosticCode.APP_START, DiagnosticSource.APP)
            val session = DiagnosticExportSession(store, DiagnosticExporter("test", 1, 26), clock = { time })
            val request = assertNotNull(session.prepare())
            time += 20 * 60 * 1000
            log.emit(DiagnosticCode.FOREGROUND, DiagnosticSource.APP)
            val output = ByteArrayOutputStream()
            session.save(request.token, object : DiagnosticDestination {
                override fun open(): OutputStream = output
                override fun delete() = false
            })
            assertTrue(output.toString("UTF-8").contains("APP_START=1"))
            assertFalse(output.toString("UTF-8").contains("FOREGROUND"))
        }
        root.deleteRecursively()
    }

    @Test
    fun repeatedPrepareAndStaleResultsCannotOverwriteActiveExport() = runBlocking<Unit> {
        val root = Files.createTempDirectory("diagnostic-state").toFile()
        DiagnosticStore(root).use { store ->
            val session = DiagnosticExportSession(store, DiagnosticExporter("test", 1, 26))
            val first = assertNotNull(session.prepare())
            assertNull(session.prepare())
            session.save(first.token, null)
            assertEquals(DiagnosticExportPhase.IDLE, session.state.value.phase)
            val second = assertNotNull(session.prepare())
            session.cancel(first.token)
            session.save(first.token, null)
            assertEquals(DiagnosticExportPhase.AWAITING_DESTINATION, session.state.value.phase)
            val output = ByteArrayOutputStream()
            session.save(second.token, object : DiagnosticDestination {
                override fun open(): OutputStream = output
                override fun delete() = false
            })
            assertEquals(DiagnosticExportPhase.SAVED, session.state.value.phase)
            assertTrue(output.size() > 0)
        }
        root.deleteRecursively()
    }
}
