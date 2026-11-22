package com.dailysatori.core.diagnostics

import com.dailysatori.service.diagnostics.*
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class UiPerformanceTest {
    @Test
    fun placeholderDrawAndTerminalContentAreSeparateAndShareAnId() {
        val events = mutableListOf<SafeDiagnosticEvent>()
        var now = 10L
        val visit = UiVisit(PerformanceScreen.ArticleDetailRoute, began = now, clock = { now },
            sink = Diagnostics(DiagnosticSink { events.add(it) }))
        now = 20L
        visit.firstDraw()
        visit.firstDraw()
        now = 35L
        visit.contentDraw(PerformanceResult.EMPTY)
        visit.contentDraw(PerformanceResult.OK)
        visit.cancel()

        assertEquals(listOf("UI_FIRST_DRAW", "UI_CONTENT_DRAW"), events.map { it.attributes["perfPoint"] })
        assertEquals(listOf("10", "25"), events.map { it.attributes["durationMs"] })
        assertEquals(listOf("OK", "EMPTY"), events.map { it.attributes["perfResult"] })
        assertEquals(listOf(visit.trace.id, visit.trace.id), events.map { it.traceId })
    }

    @Test
    fun navigationAwayDoesNotCreditLateContentToTheVisit() {
        val events = mutableListOf<SafeDiagnosticEvent>()
        val visit = UiVisit(PerformanceScreen.ARTICLE_LIST, sink = Diagnostics(DiagnosticSink { events.add(it) }))
        visit.firstDraw()
        visit.cancel()
        visit.contentDraw(PerformanceResult.OK)
        assertEquals(listOf("OK", "CANCELLED"), events.map { it.attributes["perfResult"] })
    }

    @Test
    fun existingExportIncludesSafeTimingsAndCoverageLimits() = runBlocking {
        val root = Files.createTempDirectory("performance-export").toFile()
        try {
            DiagnosticStore(root).use { store ->
                val sink = Diagnostics(store)
                sink.emit(DiagnosticCode.PERFORMANCE, DiagnosticSource.DATABASE, fields = mapOf(
                    "perfPoint" to "DB_READ", "perfResult" to "OK", "durationMs" to "21",
                    "sql" to "SECRET_SQL", "perfScreen" to "SECRET_ROUTE",
                ))
                val output = ByteArrayOutputStream()
                store.snapshot(System.currentTimeMillis()).use { DiagnosticExporter("test", 1, 26).write(it, output) }
                val report = output.toString("UTF-8")
                assertTrue(report.contains("PERFORMANCE=1"))
                assertTrue(report.contains("\"perfPoint\":\"DB_READ\""))
                assertTrue(report.contains("not compositor presentation"))
                assertFalse(report.contains("SECRET_"))
            }
        } finally { root.deleteRecursively() }
    }
}
