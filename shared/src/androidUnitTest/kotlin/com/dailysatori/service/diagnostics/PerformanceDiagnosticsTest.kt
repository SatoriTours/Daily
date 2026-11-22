package com.dailysatori.service.diagnostics

import kotlinx.coroutines.CancellationException
import kotlin.test.*

class PerformanceDiagnosticsTest {
    @Test
    fun timingsUseMonotonicTimeAndKeepFailuresPrivate() {
        val events = mutableListOf<SafeDiagnosticEvent>()
        val sink = Diagnostics(DiagnosticSink { events.add(it) }, now = { 1 })
        var elapsed = 10L
        val trace = DiagnosticTrace(DiagnosticLog.newId())
        val span = PerformanceSpan(PerformancePoint.DB_MIGRATE, elapsed, trace, sink = sink, clock = { elapsed })
        val failure = IllegalStateException("private SQL and bindings")
        assertSame(failure, assertFailsWith<IllegalStateException> {
            span.measure { elapsed = 27; throw failure }
        })
        val event = events.single()
        assertEquals(DiagnosticCode.PERFORMANCE, event.eventCode)
        assertEquals(trace.id, event.traceId)
        assertEquals(mapOf("perfPoint" to "DB_MIGRATE", "perfResult" to "FAILED", "durationMs" to "17"), event.attributes)
        assertTrue(event.exception.isEmpty())
    }

    @Test
    fun cancelledTimingDoesNotBecomeFailureOrSuccess() {
        val events = mutableListOf<SafeDiagnosticEvent>()
        val span = PerformanceSpan(PerformancePoint.DB_LOCK, sink = Diagnostics(DiagnosticSink { events.add(it) }))
        val cancelled = CancellationException("private")
        assertSame(cancelled, assertFailsWith<CancellationException> { span.measure { throw cancelled } })
        assertEquals("CANCELLED", events.single().attributes["perfResult"])
    }

    @Test
    fun unknownPerformanceLabelsAreRejected() {
        assertTrue(DiagnosticRedactor.fields(mapOf("perfPoint" to "https://private", "perfResult" to "secret",
            "sql" to "SELECT * FROM private", "args" to "secret")).isEmpty())
    }

    @Test
    fun queryBudgetSamplesFastQueriesAndBoundsSlowBursts() {
        val budget = PerformanceBudget()
        assertEquals(1, (1..128).count { budget.allow(5, 1, false) })
        assertEquals(119, (1..150).count { budget.allow(5, 30, false) })
        assertTrue(budget.allow(60_005, 0, true))
    }

    @Test
    fun physicalTransactionTimesCommitOrRollbackWithoutChangingCalls() {
        val events = mutableListOf<SafeDiagnosticEvent>()
        val calls = mutableListOf<String>()
        var time = 10L
        val db = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,
            arrayOf(androidx.sqlite.db.SupportSQLiteDatabase::class.java)) { _, method, _ ->
            calls += method.name
            if (method.name == "endTransaction") time += 7
            null
        } as androidx.sqlite.db.SupportSQLiteDatabase
        val timed = TimedSQLiteTransactions(db, PerformanceBudget(false), { time }, Diagnostics(DiagnosticSink { events.add(it) }))
        timed.beginTransactionNonExclusive()
        time = 45
        timed.setTransactionSuccessful()
        timed.endTransaction()
        timed.beginTransactionNonExclusive()
        time = 80
        timed.endTransaction()

        assertEquals(listOf("beginTransactionNonExclusive", "setTransactionSuccessful", "endTransaction",
            "beginTransactionNonExclusive", "endTransaction"), calls)
        assertEquals(listOf("DB_TRANSACTION", "DB_COMMIT", "DB_TRANSACTION", "DB_ROLLBACK"),
            events.map { it.attributes["perfPoint"] })
        assertEquals(listOf("42", "7", "35", "7"), events.map { it.attributes["durationMs"] })
        assertEquals(listOf("OK", "OK", "ROLLED_BACK", "ROLLED_BACK"), events.map { it.attributes["perfResult"] })
        assertEquals(events[0].traceId, events[1].traceId)
        assertNotEquals(events[1].traceId, events[2].traceId)
    }
}
