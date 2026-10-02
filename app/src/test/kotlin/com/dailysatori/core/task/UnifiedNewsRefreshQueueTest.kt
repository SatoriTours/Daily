package com.dailysatori.core.task

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedNewsRefreshQueueTest {
    @Test
    fun automaticRefreshAfterManualReusesTheSameTaskWithoutAddingASyncDependency() = withTasks { tasks ->
        val manual = enqueueUnifiedNewsRefresh(tasks, true, false, "manual") { }
        assertTrue(tasks.claimForRun(manual.taskId, "test", Long.MAX_VALUE))
        val automatic = enqueueUnifiedNewsRefresh(tasks, true, false, "due") { }

        assertEquals(manual.taskId, automatic.taskId)
        assertFalse(automatic.created)
        assertTrue(tasks.runnableTasks(Long.MAX_VALUE).isEmpty())
        assertFalse(tasks.waitingForPredecessor(manual.taskId))
        assertEquals("running", tasks.getById(manual.taskId)!!.status)
        assertFalse("_afterTaskId" in Json.parseToJsonElement(tasks.getById(manual.taskId)!!.payload_json).jsonObject)
    }

    @Test
    fun manualAndBackfillReuseAutomaticTaskAndItsOriginalPredecessor() = withTasks { tasks ->
        val scheduled = mutableListOf<Long>()
        val automatic = enqueueUnifiedNewsRefresh(tasks, true, false, "due", scheduled::add)
        val payload = Json.parseToJsonElement(tasks.getById(automatic.taskId)!!.payload_json).jsonObject
        val predecessor = payload.getValue("_afterTaskId").jsonPrimitive.long
        val manual = enqueueUnifiedNewsRefresh(tasks, true, false, "manual") { }
        val backfill = enqueueUnifiedNewsRefresh(tasks, false, false, "backfill") { }

        assertEquals(automatic.taskId, manual.taskId)
        assertEquals(automatic.taskId, backfill.taskId)
        assertEquals(listOf(predecessor, automatic.taskId), scheduled)
        assertEquals(2, tasks.runnableTasks(Long.MAX_VALUE).size)
        assertTrue(tasks.waitingForPredecessor(automatic.taskId))
        assertTrue(tasks.claimForRun(predecessor, "test", Long.MAX_VALUE))
        tasks.finishSuccess(predecessor, "")
        assertFalse(tasks.waitingForPredecessor(automatic.taskId))
    }

    @Test
    fun retryIsReusedWithoutChangingItsDeadlineOrGenerationOptions() = withTasks { tasks ->
        val manual = enqueueUnifiedNewsRefresh(tasks, true, true, "manual") { }
        assertTrue(tasks.claimForRun(manual.taskId, "test", Long.MAX_VALUE))
        tasks.markRetry(manual.taskId, "network", "offline", Long.MAX_VALUE)
        val automatic = enqueueUnifiedNewsRefresh(tasks, true, false, "due") { }

        assertEquals(manual.taskId, automatic.taskId)
        val task = tasks.getById(manual.taskId)!!
        assertEquals("retrying", task.status)
        assertEquals(Long.MAX_VALUE, task.run_after_ms)
        val payload = Json.decodeFromString<UnifiedNewsGenerateTaskPayload>(task.payload_json)
        assertTrue(payload.ignoreSourceTimeFilter)
        assertEquals("manual", payload.mode)
    }

    private fun withTasks(block: (AsyncTaskRepository) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            block(AsyncTaskRepository(DailySatoriDatabase(driver)))
        }
    }
}
