package com.dailysatori.core.task

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UnifiedNewsRefreshQueueTest {
    @Test
    fun manualAndAutomaticRefreshesKeepSeparateIdentities() = withTasks { tasks ->
        val manual = enqueueUnifiedNewsRefresh(tasks, true, false, "manual", schedule = {})
        assertTrue(tasks.claimForRun(manual.taskId, "test", Long.MAX_VALUE))
        val automatic = enqueueUnifiedNewsRefresh(
            tasks, true, false, "due", tickDueAtMs = 1_700_000_000_000L, schedule = {},
        )

        assertTrue(automatic.created)
        assertNotEquals(manual.taskId, automatic.taskId)
        assertEquals("running", tasks.getById(manual.taskId)!!.status)
        assertFalse("_afterTaskId" in Json.parseToJsonElement(tasks.getById(manual.taskId)!!.payload_json).jsonObject)
    }

    @Test
    fun concurrentAutomaticEnqueuesForOneTickCreateASingleGenerationTask() = withTasks { tasks ->
        val scheduled = java.util.Collections.synchronizedList(mutableListOf<Long>())
        // JdbcSqliteDriver shares one connection, so true parallel writers cannot be exercised
        // here; the enqueue path itself is guarded by the family uniqueness plus the repository's
        // unique-constraint fallback. What this pins down is that repeated triggers for one tick
        // never add a second generation task.
        val first = enqueueUnifiedNewsRefresh(
            tasks, true, false, "due", schedule = { id -> scheduled.add(id) }, tickDueAtMs = 1_700_000_000_000L,
        )
        val second = enqueueUnifiedNewsRefresh(
            tasks, true, false, "due", schedule = { id -> scheduled.add(id) }, tickDueAtMs = 1_700_000_000_000L,
        )

        assertEquals(first.taskId, second.taskId)
        assertFalse(second.created)
        assertEquals(1, tasks.runnableTasksByType("remote_news_fetch", Long.MAX_VALUE).size)
    }

    @Test
    fun tickIdentityRoundTripsAndLegacyPayloadsStayNullable() = withTasks { tasks ->
        val withTick = enqueueUnifiedNewsRefresh(
            tasks, true, false, "due", schedule = { }, tickDueAtMs = 1_700_000_000_000L,
        )
        val lenient = Json { ignoreUnknownKeys = true }
        val tickPayload = lenient.decodeFromString<UnifiedNewsGenerateTaskPayload>(
            tasks.getById(withTick.taskId)!!.payload_json,
        )
        assertEquals(1_700_000_000_000L, tickPayload.tickDueAtMs)

        // An automatic task already in flight is reused, not duplicated, by another automatic run.
        val automaticAgain = enqueueUnifiedNewsRefresh(
            tasks, true, false, "due", schedule = { }, tickDueAtMs = 1_700_000_000_000L,
        )
        assertFalse(automaticAgain.created)
        assertEquals(withTick.taskId, automaticAgain.taskId)

        val legacy = lenient.decodeFromString<UnifiedNewsGenerateTaskPayload>(
            """{"force":true,"ignoreSourceTimeFilter":false,"mode":"due"}""",
        )
        assertEquals(null, legacy.tickDueAtMs)
    }

    @Test
    fun eachModeOwnsItsIdentityAndOnlySameIdentityMerges() = withTasks { tasks ->
        val scheduled = mutableListOf<Long>()
        val automatic = enqueueUnifiedNewsRefresh(
            tasks, true, false, "due", tickDueAtMs = 1_700_000_000_000L, schedule = { id -> scheduled.add(id) },
        )
        val payload = Json.parseToJsonElement(tasks.getById(automatic.taskId)!!.payload_json).jsonObject
        val predecessor = payload.getValue("_afterTaskId").jsonPrimitive.long
        val manual = enqueueUnifiedNewsRefresh(tasks, true, false, "manual") { }
        val backfill = enqueueUnifiedNewsRefresh(tasks, false, false, "backfill") { }
        val manualAgain = enqueueUnifiedNewsRefresh(tasks, true, false, "manual") { }

        assertNotEquals(automatic.taskId, manual.taskId)
        assertNotEquals(automatic.taskId, backfill.taskId)
        assertEquals(listOf(predecessor, automatic.taskId), scheduled)
        assertEquals(manual.taskId, manualAgain.taskId)
        assertFalse(manualAgain.created)
        assertTrue(tasks.waitingForPredecessor(automatic.taskId))
        assertTrue(tasks.claimForRun(predecessor, "test", Long.MAX_VALUE))
        tasks.finishSuccess(predecessor, "")
        assertFalse(tasks.waitingForPredecessor(automatic.taskId))
    }

    @Test
    fun retryIsReusedWithinTheSameIdentityOnly() = withTasks { tasks ->
        val manual = enqueueUnifiedNewsRefresh(tasks, true, true, "manual", schedule = {})
        assertTrue(tasks.claimForRun(manual.taskId, "test", Long.MAX_VALUE))
        tasks.markRetry(manual.taskId, "network", "offline", Long.MAX_VALUE)
        val manualAgain = enqueueUnifiedNewsRefresh(tasks, true, false, "manual", schedule = {})
        val automatic = enqueueUnifiedNewsRefresh(
            tasks, true, false, "due", tickDueAtMs = 1_700_000_000_000L, schedule = {},
        )

        assertEquals(manual.taskId, manualAgain.taskId)
        assertNotEquals(manual.taskId, automatic.taskId)
        val task = tasks.getById(manual.taskId)!!
        assertEquals("retrying", task.status)
        assertEquals(Long.MAX_VALUE, task.run_after_ms)
        val payload = Json.decodeFromString<UnifiedNewsGenerateTaskPayload>(task.payload_json)
        assertEquals(true, payload.force)
        assertEquals(true, payload.ignoreSourceTimeFilter)
    }

    private fun withTasks(block: (AsyncTaskRepository) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            block(AsyncTaskRepository(DailySatoriDatabase(driver)))
        }
    }
}
