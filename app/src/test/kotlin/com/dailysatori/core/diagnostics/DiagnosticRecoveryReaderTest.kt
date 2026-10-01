package com.dailysatori.core.diagnostics

import com.dailysatori.service.diagnostics.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DiagnosticRecoveryReaderTest {
    @Test
    fun pendingCrashCanBeExportedWithoutInitializingOrMutatingTheStore() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("recovery-read-only").toFile()
        try {
            val root = File(directory, "diagnostics")
            val events = File(root, "events").apply { mkdirs() }
            val history = File(events, "00000000000000000001.jsonl").apply {
                writeText(event(DiagnosticCode.APP_START, 9_000) + "\n" + event(DiagnosticCode.HTTP_START, 9_500) + "\n")
            }
            val pending = File(root, "crash-pending.json").apply { writeText(event(DiagnosticCode.CRASH, 10_000)) }
            val health = File(root, "health.json").apply { writeText("{\"ioFailures\":2}") }
            val otherExport = File(root, "exports/in-use.jsonl").apply { parentFile!!.mkdirs(); writeText("leased") }
            val before = listOf(history, pending, health, otherExport).associateWith { it.readBytes().toList() }
            val reader = DiagnosticRecoveryReader(root, File(directory, "recovery"), clock = { 10_001 })
            assertTrue(reader.needsRecovery())
            val session = DiagnosticExportSession(reader::snapshot, DiagnosticExporter("test", 1, 26))
            val request = assertNotNull(session.prepare(crash = true))
            val output = ByteArrayOutputStream()
            session.save(request.token, object : DiagnosticDestination {
                override fun open() = output
                override fun delete() = false
            })
            assertEquals(DiagnosticExportPhase.SAVED, session.state.value.phase)
            assertTrue(output.toString("UTF-8").contains("CRASH=1"))
            assertTrue(output.toString("UTF-8").contains("HTTP_START=1"))
            assertFalse(output.toString("UTF-8").contains("private-canary"))
            assertTrue(output.toString("UTF-8").contains("IllegalStateException"))
            before.forEach { (file, bytes) -> assertEquals(bytes, file.readBytes().toList()) }
            assertTrue(File(directory, "recovery").listFiles().orEmpty().isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun acknowledgedCrashDoesNotBlockStartupButANewCrashDoes() {
        val directory = Files.createTempDirectory("recovery-ack").toFile()
        try {
            val root = File(directory, "diagnostics").apply { mkdirs() }
            val pending = File(root, "crash-pending.json").apply { writeText(event(DiagnosticCode.CRASH, 10_000)) }
            val reader = DiagnosticRecoveryReader(root, File(directory, "recovery"), clock = { 20_000 })
            assertTrue(reader.needsRecovery())
            reader.acknowledgeCrash()
            assertFalse(reader.needsRecovery())
            assertTrue(pending.exists())
            pending.writeText(event(DiagnosticCode.CRASH, 20_000))
            assertTrue(reader.needsRecovery())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun archivedCrashRemainsExportableWhenPendingMarkerHasAlreadyBeenRecovered() {
        val directory = Files.createTempDirectory("recovery-archive").toFile()
        try {
            val root = File(directory, "diagnostics")
            val archive = File(root, "crashes/00000000000000010000-session.jsonl").apply {
                parentFile!!.mkdirs()
                writeText(event(DiagnosticCode.HTTP_START, 9_500) + "\n" + event(DiagnosticCode.CRASH, 10_000) + "\n")
            }
            val reader = DiagnosticRecoveryReader(root, File(directory, "recovery"), clock = { 10_001 })
            assertTrue(reader.needsRecovery())
            reader.snapshot(true, 10_001).use { snapshot ->
                assertEquals(listOf(DiagnosticCode.HTTP_START, DiagnosticCode.CRASH), snapshot.events().map { it.eventCode }.toList())
            }
            assertTrue(archive.exists())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun corruptHistoryIsSkippedAndReportedWithoutLosingTheCrash() {
        val directory = Files.createTempDirectory("recovery-corrupt").toFile()
        try {
            val root = File(directory, "diagnostics").apply { mkdirs() }
            File(root, "events/00000000000000000001.jsonl").apply {
                parentFile!!.mkdirs(); writeText("broken-json\n" + event(DiagnosticCode.HTTP_START, 9_500) + "\n")
            }
            File(root, "crash-pending.json").writeText(event(DiagnosticCode.CRASH, 10_000))
            val reader = DiagnosticRecoveryReader(root, File(directory, "recovery"), clock = { 10_001 })
            reader.snapshot(true, 10_001).use { snapshot ->
                assertTrue(snapshot.health.corruptLines > 0)
                assertEquals(listOf(DiagnosticCode.HTTP_START, DiagnosticCode.CRASH), snapshot.events().map { it.eventCode }.toList())
            }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun boundedSnapshotKeepsTheCrashAndMostRecentContext() {
        val directory = Files.createTempDirectory("recovery-bounded").toFile()
        try {
            val root = File(directory, "diagnostics").apply { mkdirs() }
            File(root, "events/00000000000000000001.jsonl").apply {
                parentFile!!.mkdirs(); writeText((1..100).joinToString("\n", postfix = "\n") { event(DiagnosticCode.HTTP_START, it.toLong()) })
            }
            File(root, "crash-pending.json").writeText(event(DiagnosticCode.CRASH, 101))
            val reader = DiagnosticRecoveryReader(root, File(directory, "recovery"), clock = { 102 },
                policy = DiagnosticStorePolicy(crashBytes = 4_096))
            reader.snapshot(true, 102).use { snapshot ->
                val records = snapshot.events().toList()
                assertEquals(DiagnosticCode.CRASH, records.last().eventCode)
                assertTrue(records.size < 101)
                assertTrue(records.any { it.timestampMs == 100L })
                assertTrue(snapshot.file.length() <= 4_096)
                assertTrue(snapshot.health.truncatedEvents > 0)
            }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun freshInstallCanExportAnEmptyReportWithoutCreatingBusinessDirectories() {
        val directory = Files.createTempDirectory("recovery-empty").toFile()
        try {
            val root = File(directory, "diagnostics")
            val reader = DiagnosticRecoveryReader(root, File(directory, "recovery"), clock = { 10_000 })
            assertFalse(reader.needsRecovery())
            reader.snapshot(false, 10_000).use { assertEquals(0, it.events().count()) }
            assertFalse(root.exists())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun unfinishedStartupOnlyOffersRecoveryWhenSystemConfirmsACrash() {
        val directory = Files.createTempDirectory("recovery-early-startup").toFile()
        try {
            val root = File(directory, "diagnostics")
            var exit: DiagnosticExit? = null
            val reader = DiagnosticRecoveryReader(root, File(directory, "recovery"), previousExit = { exit })
            DiagnosticRecoveryReader.startupStarted(root)
            assertFalse(reader.needsRecovery())
            exit = DiagnosticExit(System.currentTimeMillis(), 10) // User-requested stop.
            assertFalse(reader.needsRecovery())
            exit = DiagnosticExit(System.currentTimeMillis(), 4)
            assertTrue(reader.needsRecovery())
            reader.snapshot(true, System.currentTimeMillis()).use { assertFalse(it.crash) }
            reader.acknowledgeCrash()
            assertFalse(reader.needsRecovery())
            DiagnosticRecoveryReader.startupStarted(root)
            exit = DiagnosticExit(System.currentTimeMillis(), 4)
            assertTrue(reader.needsRecovery())
            DiagnosticRecoveryReader.startupCompleted(root)
            assertFalse(reader.needsRecovery())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun upgradingSkipsOldCrashesAndStartupMarkersWithoutDeletingTheLogs() {
        val directory = Files.createTempDirectory("recovery-upgrade").toFile()
        try {
            val root = File(directory, "diagnostics").apply { mkdirs() }
            val pending = File(root, "crash-pending.json").apply { writeText(event(DiagnosticCode.CRASH, 10_000)) }
            File(root, "crashes/00000000000000010000-old.jsonl").apply {
                parentFile!!.mkdirs(); writeText(event(DiagnosticCode.CRASH, 10_000) + "\n")
            }
            File(root, "startup-pending").writeText("10000:old-launch")
            val reader = DiagnosticRecoveryReader(root, File(directory, "recovery"), clock = { 30_000 },
                installedAtMs = 20_000, previousExit = { DiagnosticExit(10_001, 4) })
            assertFalse(reader.needsRecovery())
            reader.snapshot(true, 30_000).use { assertTrue(it.events().any { event -> event.eventCode == DiagnosticCode.CRASH }) }
            assertTrue(pending.exists())
            pending.writeText(event(DiagnosticCode.CRASH, 25_000))
            assertTrue(reader.needsRecovery())
            reader.acknowledgeCrash()
            assertFalse(reader.needsRecovery())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun staleStartupMarkersAndExitReasonsDoNotBlockAHealthyLaunch() {
        val directory = Files.createTempDirectory("recovery-stale-startup").toFile()
        try {
            val root = File(directory, "diagnostics").apply { mkdirs() }
            File(root, "startup-pending").writeText("20000:launch")
            var exit = DiagnosticExit(19_999, 4)
            val reader = DiagnosticRecoveryReader(root, File(directory, "recovery"), clock = { 30_000 },
                installedAtMs = 10_000, previousExit = { exit })
            assertFalse(reader.needsRecovery())
            exit = DiagnosticExit(21_000, 5)
            assertTrue(reader.needsRecovery())
            File(root, "startup-pending").writeText("invalid-marker")
            assertFalse(reader.needsRecovery())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun anOldProcessExitRecordedAfterUpgradeDoesNotBecomeANewCrash() {
        val directory = Files.createTempDirectory("recovery-old-process-exit").toFile()
        try {
            val root = File(directory, "diagnostics").apply { mkdirs() }
            val pending = File(root, "crash-pending.json")
            pending.writeText(event(DiagnosticCode.PROCESS_EXIT, 25_000,
                mapOf("reason" to "4", "exitTimestampMs" to "10000")))
            val reader = DiagnosticRecoveryReader(root, File(directory, "recovery"), clock = { 30_000 }, installedAtMs = 20_000)
            assertFalse(reader.needsRecovery())
            pending.writeText(event(DiagnosticCode.PROCESS_EXIT, 25_000,
                mapOf("reason" to "5", "exitTimestampMs" to "22000")))
            assertTrue(reader.needsRecovery())
        } finally { directory.deleteRecursively() }
    }

    private fun event(code: DiagnosticCode, time: Long, fields: Map<String, String> = emptyMap()): String {
        var captured: SafeDiagnosticEvent? = null
        Diagnostics(DiagnosticSink { captured = it; true }, now = { time }).emit(
            code, DiagnosticSource.APP, fields = fields, error = if (code == DiagnosticCode.CRASH) IllegalStateException("private-canary") else null)
        return diagnosticJson.encodeToString(SafeDiagnosticEvent.serializer(), assertNotNull(captured))
    }
}
