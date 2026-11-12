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
import kotlin.test.assertNull
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

    @Test
    fun aStalledOlderWindowCannotBlockTheNextWindowGeneration() = withTasks { tasks ->
        // Reported stall: a generation whose sync predecessor never reaches a terminal state stays
        // queued forever; remote_news_fetch is serial, so that zombie would block every later window.
        val stalled = enqueueUnifiedNewsRefresh(
            tasks, true, false, "due", tickDueAtMs = 1_700_000_000_000L, schedule = {},
        )
        val stalledPayload = Json.parseToJsonElement(tasks.getById(stalled.taskId)!!.payload_json).jsonObject
        assertTrue("_afterTaskId" in stalledPayload)

        val next = enqueueUnifiedNewsRefresh(
            tasks, true, false, "due", tickDueAtMs = 1_700_086_400_000L, schedule = {},
        )
        assertEquals("failed", tasks.getById(stalled.taskId)!!.status)

        val nextPayload = Json.parseToJsonElement(tasks.getById(next.taskId)!!.payload_json).jsonObject
        val nextPredecessor = nextPayload.getValue("_afterTaskId").jsonPrimitive.long
        assertTrue(tasks.claimForRun(nextPredecessor, "test", Long.MAX_VALUE))
        tasks.finishSuccess(nextPredecessor, "")
        assertTrue(
            tasks.claimForSerialRun(next.taskId, "remote_news_fetch", "test", Long.MAX_VALUE),
            "the newest window must still reach generation",
        )
    }

    @Test
    fun reenteringAnOlderWindowDoesNotRetireTheNewerPendingWindow() = withTasks { tasks ->
        val newer = enqueueUnifiedNewsRefresh(
            tasks, true, false, "due", tickDueAtMs = 1_700_086_400_000L, schedule = {},
        )
        val older = enqueueUnifiedNewsRefresh(
            tasks, true, false, "due", tickDueAtMs = 1_700_000_000_000L, schedule = {},
        )

        assertNotEquals(newer.taskId, older.taskId)
        assertEquals("queued", tasks.getById(newer.taskId)!!.status)
        assertEquals("queued", tasks.getById(older.taskId)!!.status)
    }

    @Test
    fun scheduledWindowKeepsThePendingManualRefresh() = withTasks { tasks ->
        val manual = enqueueUnifiedNewsRefresh(tasks, true, false, "manual", schedule = {})
        enqueueUnifiedNewsRefresh(tasks, true, false, "due", tickDueAtMs = 1_700_000_000_000L, schedule = {})

        assertEquals("queued", tasks.getById(manual.taskId)!!.status)
    }

    @Test
    fun supersededRunningWindowIsCancelledThroughItsOwnLease() = withTasks { tasks ->
        val cancelledWork = mutableListOf<Long>()
        val older = enqueueUnifiedNewsRefresh(
            tasks, true, false, "due", tickDueAtMs = 1_700_000_000_000L,
            schedule = {}, cancelWork = { cancelledWork.add(it) },
        )
        assertTrue(tasks.claimForRun(older.taskId, "old-run", Long.MAX_VALUE))
        assertEquals("running", tasks.getById(older.taskId)!!.status)

        val newer = enqueueUnifiedNewsRefresh(
            tasks, true, false, "due", tickDueAtMs = 1_700_086_400_000L,
            schedule = {}, cancelWork = { cancelledWork.add(it) },
        )

        val superseded = tasks.getById(older.taskId)!!
        assertEquals("cancelled", superseded.status)
        assertNull(superseded.lease_owner, "the old execution must lose its lease")
        assertFalse(tasks.hasRunningLease(older.taskId, "old-run"), "a late write from the old run is rejected")
        assertEquals(listOf(older.taskId), cancelledWork)
        assertEquals("queued", tasks.getById(newer.taskId)!!.status)
    }

    @Test
    fun aRunningWindowIsReusedInsteadOfRestarted() = withTasks { tasks ->
        val first = enqueueUnifiedNewsRefresh(
            tasks, true, false, "due", tickDueAtMs = 1_700_000_000_000L, schedule = {},
        )
        assertTrue(tasks.claimForRun(first.taskId, "live-run", Long.MAX_VALUE))
        val again = enqueueUnifiedNewsRefresh(
            tasks, true, false, "due", tickDueAtMs = 1_700_000_000_000L,
            schedule = {}, cancelWork = { error("a healthy run must not be cancelled") },
        )

        assertEquals(first.taskId, again.taskId)
        assertFalse(again.created)
        assertEquals("running", tasks.getById(first.taskId)!!.status)
    }

    private fun withTasks(block: (AsyncTaskRepository) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            block(AsyncTaskRepository(DailySatoriDatabase(driver)))
        }
    }
}
