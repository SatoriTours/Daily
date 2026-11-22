package com.dailysatori.service.diagnostics

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException

internal inline fun <T> databasePhase(point: PerformancePoint, block: () -> T): T =
    DiagnosticLog.withTrace(DiagnosticLog.currentTrace() ?: DiagnosticTrace(DiagnosticLog.newId())) {
        PerformanceSpan(point).measure(block)
    }

class PerformanceBudget(private val sampleFast: Boolean = true) {
    private val fast = AtomicInteger()
    private var windowStart = 0L
    private var count = 0

    fun allow(now: Long, duration: Long, failed: Boolean): Boolean {
        if (sampleFast && !failed && duration < 16 && fast.incrementAndGet() and 127 != 0) return false
        synchronized(this) {
            if (now - windowStart >= 60_000) {
                windowStart = now
                count = 0
            }
            if (count >= 120) return false
            count++
            return true
        }
    }
}

/** Android SQLDelight is synchronous; executeQuery includes stepping/materializing the cursor. */
internal class TimedSqlDriver(private val driver: SqlDriver) : SqlDriver by driver {
    override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<R> =
        measure(PerformancePoint.DB_READ) { driver.executeQuery(identifier, sql, mapper, parameters, binders) }

    override fun execute(identifier: Int?, sql: String, parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> =
        measure(PerformancePoint.DB_WRITE) { driver.execute(identifier, sql, parameters, binders) }

    private inline fun <T> measure(point: PerformancePoint, block: () -> T): T {
        val start = DiagnosticLog.elapsed()
        var result = PerformanceResult.OK
        try {
            return block()
        } catch (failure: Throwable) {
            result = if (failure is CancellationException) PerformanceResult.CANCELLED else PerformanceResult.FAILED
            throw failure
        } finally {
            val end = DiagnosticLog.elapsed()
            if (budget.allow(end, end - start, result != PerformanceResult.OK)) {
                PerformanceSpan(point, start, clock = { end }).finish(result)
            }
        }
    }

    private companion object { val budget = PerformanceBudget() }
}

/** SQLDelight 2.4.1 forwards the outer physical transaction to these three calls; nested scopes are logical. */
internal class TimedSQLiteTransactions(
    private val database: SupportSQLiteDatabase,
    private val budget: PerformanceBudget = PerformanceBudget(),
    private val clock: () -> Long = DiagnosticLog::elapsed,
    private val sink: Diagnostics = DiagnosticLog.diagnostics,
) : SupportSQLiteDatabase by database {
    private class Active(val start: Long, val trace: DiagnosticTrace?) {
        var commit = false
    }

    private val active = ThreadLocal<Active>()

    override fun beginTransactionNonExclusive() {
        val txn = Active(clock(), DiagnosticLog.currentTrace())
        try {
            database.beginTransactionNonExclusive()
        } catch (failure: Throwable) {
            record(txn, PerformancePoint.DB_TRANSACTION_BEGIN, txn.start, outcome(failure))
            throw failure
        }
        active.set(txn)
    }

    override fun setTransactionSuccessful() {
        try {
            database.setTransactionSuccessful()
            active.get()?.commit = true
        } catch (failure: Throwable) {
            active.get()?.let { record(it, PerformancePoint.DB_TRANSACTION, it.start, outcome(failure)) }
            active.remove()
            throw failure
        }
    }

    override fun endTransaction() {
        val txn = active.get()
        val began = clock()
        var result = if (txn?.commit == true) PerformanceResult.OK else PerformanceResult.ROLLED_BACK
        try {
            database.endTransaction()
        } catch (failure: Throwable) {
            result = outcome(failure)
            throw failure
        } finally {
            active.remove()
            if (txn != null) record(txn, PerformancePoint.DB_TRANSACTION, txn.start, result, began)
        }
    }

    private fun record(txn: Active, point: PerformancePoint, began: Long, result: PerformanceResult, endBegan: Long? = null) {
        val now = clock()
        val exceptional = result == PerformanceResult.FAILED || result == PerformanceResult.CANCELLED
        if (!budget.allow(now, now - began, exceptional)) return
        val trace = txn.trace ?: DiagnosticTrace(DiagnosticLog.newId())
        PerformanceSpan(point, began, trace, sink = sink, clock = { now }).finish(result)
        endBegan?.let {
            val endPoint = if (txn.commit) PerformancePoint.DB_COMMIT else PerformancePoint.DB_ROLLBACK
            PerformanceSpan(endPoint, it, trace, sink = sink, clock = { now }).finish(result)
        }
    }

    private fun outcome(failure: Throwable) =
        if (failure is CancellationException) PerformanceResult.CANCELLED else PerformanceResult.FAILED
}
