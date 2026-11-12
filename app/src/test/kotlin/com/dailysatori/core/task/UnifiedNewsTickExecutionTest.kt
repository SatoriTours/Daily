package com.dailysatori.core.task

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.core.worker.latestDueUnifiedNewsWindow
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.unifiednews.UnifiedNewsGenerationResult
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryStatus
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Execution-level identity tests: one due tick is one task identity, a manual refresh owns its own
 * task, and the handler guard decides whether an AI call may happen.
 */
class UnifiedNewsTickExecutionTest {
    // Fixed wall-clock moments in the process time zone: the handler derives its latest due tick
    // from dueUnifiedNewsWindows() with TimeZone.currentSystemDefault(), so the fixture must use
    // the same zone or two windows can collapse into one (e.g. both into FINAL under UTC).
    private val zone = TimeZone.currentSystemDefault()
    private val at0830 = localEpochMillis(hour = 8, minute = 30)
    private val at1335 = localEpochMillis(hour = 13, minute = 35)

    private fun localEpochMillis(hour: Int, minute: Int): Long =
        LocalDateTime(LocalDate(2026, 11, 10), LocalTime(hour, minute)).toInstant(zone).toEpochMilliseconds()
    private val reporter = object : AsyncTaskProgressReporter {
        override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) = Unit
    }

    @Test
    fun olderTickSkipsWhileTheNewerTickStillGenerates() = withTasks { tasks ->
        val nightTick = latestDueUnifiedNewsWindow(Instant.fromEpochMilliseconds(at0830)).endMs
        val noonTick = latestDueUnifiedNewsWindow(Instant.fromEpochMilliseconds(at1335)).endMs
        assertTrue(noonTick > nightTick, "fixture window ordering")

        val early = enqueueUnifiedNewsRefresh(tasks, true, false, "due", nightTick, schedule = {})
        val late = enqueueUnifiedNewsRefresh(tasks, true, false, "due", noonTick, schedule = {})

        assertTrue(early.created)
        assertTrue(late.created)
        assertNotEquals(early.taskId, late.taskId, "a newer tick must not be absorbed by the running one")

        val generation = RecordingGeneration()
        val handler = handler(nowMs = at1335, latestSuccessfulAt = { null }, generation = generation)

        execute(handler, early.taskId, tasks)
        assertEquals(0, generation.calls, "a superseded tick must not spend an AI call")

        execute(handler, late.taskId, tasks)
        assertEquals(1, generation.calls, "the newest tick must still generate")
        assertEquals(true, generation.lastForce)
    }

    @Test
    fun manualRefreshOwnsItsTaskAndKeepsForceSemantics() = withTasks { tasks ->
        val tick = latestDueUnifiedNewsWindow(Instant.fromEpochMilliseconds(at1335)).endMs
        val automatic = enqueueUnifiedNewsRefresh(tasks, true, false, "due", tick, schedule = {})
        val manual = enqueueUnifiedNewsRefresh(tasks, true, true, "manual", schedule = {})

        assertTrue(manual.created)
        assertNotEquals(automatic.taskId, manual.taskId, "manual must not reuse the due task")

        val generation = RecordingGeneration()
        // The automatic tick already produced a summary; manual must still be allowed to force.
        val handler = handler(nowMs = at1335, latestSuccessfulAt = { tick + 1 }, generation = generation)
        execute(handler, manual.taskId, tasks)

        assertEquals(1, generation.calls)
        assertEquals(true, generation.lastForce)
        assertEquals(true, generation.lastIgnoreSourceTimeFilter)
    }

    @Test
    fun secondRunOfTheSameTickSkipsAiAfterSuccess() = withTasks { tasks ->
        val tick = latestDueUnifiedNewsWindow(Instant.fromEpochMilliseconds(at1335)).endMs
        var latestSuccessful: Long? = null
        val generation = RecordingGeneration()
        val handler = handler(nowMs = at1335, latestSuccessfulAt = { latestSuccessful }, generation = generation)

        val first = enqueueUnifiedNewsRefresh(tasks, true, false, "due", tick, schedule = {})
        execute(handler, first.taskId, tasks)
        assertEquals(1, generation.calls)

        // Same tick runs again after the summary exists: no second AI call.
        latestSuccessful = tick + 1
        execute(handler, first.taskId, tasks)
        assertEquals(1, generation.calls, "a covered tick must not regenerate")

        // Even a fresh task row for the same tick is stopped by the handler guard.
        assertTrue(tasks.claimForRun(first.taskId, "test", Long.MAX_VALUE))
        tasks.finishSuccess(first.taskId, "")
        val again = enqueueUnifiedNewsRefresh(tasks, true, false, "due", tick, schedule = {})
        assertTrue(again.created, "a finished tick row may be re-created for retry")
        execute(handler, again.taskId, tasks)
        assertEquals(1, generation.calls)
    }

    private fun execute(handler: UnifiedNewsGenerateTaskHandler, taskId: Long, tasks: AsyncTaskRepository) {
        val payload = requireNotNull(tasks.getById(taskId)).payload_json
        val result = runBlocking { handler.execute(taskId, payload, "", reporter) }
        assertEquals(AsyncTaskExecutionResult.Success(), result)
    }

    private fun handler(
        nowMs: Long,
        latestSuccessfulAt: () -> Long?,
        generation: RecordingGeneration,
    ) = UnifiedNewsGenerateTaskHandler(
        nowMs = { nowMs },
        latestSuccessfulAt = latestSuccessfulAt,
        generateDaily = { force, ignore, _ -> generation.generate(force, ignore) },
    )

    private fun withTasks(block: (AsyncTaskRepository) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            block(AsyncTaskRepository(DailySatoriDatabase(driver)))
        }
    }

    private class RecordingGeneration {
        var calls = 0
        var lastForce: Boolean? = null
        var lastIgnoreSourceTimeFilter: Boolean? = null

        suspend fun generate(force: Boolean, ignoreSourceTimeFilter: Boolean): UnifiedNewsGenerationResult {
            calls++
            lastForce = force
            lastIgnoreSourceTimeFilter = ignoreSourceTimeFilter
            return UnifiedNewsGenerationResult(true, UnifiedNewsSummaryStatus.SUCCESS)
        }
    }
}
