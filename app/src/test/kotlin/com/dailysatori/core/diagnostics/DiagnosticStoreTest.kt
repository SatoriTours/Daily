package com.dailysatori.core.diagnostics

import com.dailysatori.service.diagnostics.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class DiagnosticStoreTest {
    /** 前台生命周期事件必须真的进入存储与导出，且只保留白名单字段值。 */
    @Test
    fun foregroundLifecycleEventSurvivesStorageAndExport() = runBlocking<Unit> {
        val root = Files.createTempDirectory("diagnostics-foreground").toFile()
        var time = 2_000_000L
        DiagnosticStore(root, clock = { time }).use { store ->
            val log = Diagnostics(store, now = { time })
            log.emit(
                DiagnosticCode.OPERATION_START, DiagnosticSource.TASK,
                fields = mapOf(
                    "foregroundWorker" to "async_task",
                    "foregroundStage" to "request",
                    "notificationId" to "2007",
                    "foregroundServiceType" to "2",
                    "taskId" to "42",
                ),
            )
            log.emit(
                DiagnosticCode.OPERATION_PROGRESS, DiagnosticSource.TASK,
                fields = mapOf(
                    "foregroundWorker" to "evil-worker",
                    "foregroundStage" to "request_ok",
                    "notificationId" to "secret-canary",
                ),
            )
            store.snapshot(time).use { snapshot ->
                val output = java.io.ByteArrayOutputStream()
                DiagnosticExporter("5.1.66", 175117, 37).write(snapshot, output)
                val text = output.toString("UTF-8")
                assertTrue(text.contains("\"foregroundWorker\":\"async_task\""))
                assertTrue(text.contains("\"foregroundStage\":\"request\""))
                assertTrue(text.contains("\"foregroundServiceType\":\"2\""))
                assertTrue(text.contains("\"notificationId\":\"2007\""))
                assertFalse(text.contains("evil-worker"))
                assertFalse(text.contains("secret-canary"))
            }
        }
        root.deleteRecursively()
    }

    @Test
    fun snapshotUsesFixedInclusiveWindowAndSurvivesLaterWritesAndClear() = runBlocking<Unit> {
        val root = Files.createTempDirectory("diagnostics").toFile()
        var time = 2_000_000L
        DiagnosticStore(root, clock = { time }).use { store ->
            val log = Diagnostics(store, now = { time })
            listOf(199_999L, 200_000L, 2_000_000L).forEach {
                time = it
                log.emit(DiagnosticCode.APP_START, DiagnosticSource.APP)
            }
            val snapshot = store.snapshot(2_000_000L)
            assertEquals(listOf(200_000L, 2_000_000L), snapshot.events().map { it.timestampMs }.toList())
            time = 2_000_001L
            log.emit(DiagnosticCode.APP_START, DiagnosticSource.APP)
            assertEquals(2, snapshot.events().count())
            snapshot.close()
            store.clear()
            assertEquals(0, store.snapshot(time).use { it.events().count() })
        }
        root.deleteRecursively()
    }

    @Test
    fun rotationIsBoundedAndReportsLostHistory() = runBlocking<Unit> {
        val root = Files.createTempDirectory("diagnostics").toFile()
        DiagnosticStore(root, policy = DiagnosticStorePolicy(segmentBytes = 600, maxBytes = 1800)).use { store ->
            val log = Diagnostics(store)
            repeat(30) { log.emit(DiagnosticCode.HTTP_START, DiagnosticSource.HTTP) }
            store.snapshot(System.currentTimeMillis()).use { snapshot ->
                assertTrue(snapshot.events().count() in 1..10)
                assertTrue(snapshot.health.prunedFiles > 0)
                assertTrue(File(root, "events").walk().filter { it.isFile }.sumOf { it.length() } <= 1800)
            }
        }
        root.deleteRecursively()
    }

    @Test
    fun queueOverflowIsNonBlockingAndVisibleInSnapshot() = runBlocking<Unit> {
        val root = Files.createTempDirectory("diagnostics").toFile()
        val executor = ScheduledThreadPoolExecutor(1)
        val release = CountDownLatch(1)
        executor.execute { release.await() }
        DiagnosticStore(root, executor = executor, policy = DiagnosticStorePolicy(queueCapacity = 2)).use { store ->
            val log = Diagnostics(store)
            repeat(8) { log.emit(DiagnosticCode.HTTP_START, DiagnosticSource.HTTP) }
            release.countDown()
            store.snapshot(System.currentTimeMillis()).use {
                assertEquals(6, it.health.droppedEvents)
                assertEquals(2, it.events().count())
            }
        }
        root.deleteRecursively()
    }

    @Test
    fun lowVolumeLogsExpireWithoutDeletingNewerEvents() = runBlocking<Unit> {
        val root = Files.createTempDirectory("diagnostic-retention").toFile()
        var time = 1_000_000L
        DiagnosticStore(root, clock = { time }, policy = DiagnosticStorePolicy(retentionMs = 120_000)).use { store ->
            val log = Diagnostics(store, now = { time })
            log.emit(DiagnosticCode.APP_START, DiagnosticSource.APP)
            store.snapshot(time).close()
            time += 100_000
            log.emit(DiagnosticCode.FOREGROUND, DiagnosticSource.APP)
            store.snapshot(time).close()
            time += 30_000
            log.emit(DiagnosticCode.BACKGROUND, DiagnosticSource.APP)
            store.snapshot(time).use { snapshot ->
                assertEquals(listOf(DiagnosticCode.FOREGROUND, DiagnosticCode.BACKGROUND), snapshot.events().map { it.eventCode }.toList())
            }
        }
        root.deleteRecursively()
    }

    @Test
    fun diskFailureDoesNotEscapeEmitterAndIsReported() = runBlocking<Unit> {
        val root = Files.createTempFile("not-a-directory", ".tmp").toFile()
        DiagnosticStore(root).use { store ->
            Diagnostics(store).emit(DiagnosticCode.APP_START, DiagnosticSource.APP)
            assertFails { store.snapshot(System.currentTimeMillis()) }
            assertTrue(store.health().ioFailures > 0)
        }
        root.delete()
    }

    @Test
    fun restartToleratesDamagedTailAndPreservesValidRecords() = runBlocking<Unit> {
        val root = Files.createTempDirectory("diagnostics").toFile()
        DiagnosticStore(root).use { store ->
            Diagnostics(store).emit(DiagnosticCode.APP_START, DiagnosticSource.APP)
            store.snapshot(System.currentTimeMillis()).close()
        }
        File(root, "events").listFiles()!!.first().appendText("{broken-tail")
        DiagnosticStore(root).use { store ->
            store.snapshot(System.currentTimeMillis()).use {
                assertEquals(1, it.events().count())
                assertTrue(it.health.corruptLines > 0)
            }
        }
        root.deleteRecursively()
    }
}
