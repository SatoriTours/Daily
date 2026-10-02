package com.dailysatori.ui.feature.unifiednews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.core.task.UnifiedNewsGenerateTaskPayload
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UnifiedNewsManualRefreshPolicyTest {
    @Test
    fun manualRefreshWaitsForQueuedRunningAndRetryingBackgroundTasks() {
        for (status in listOf("queued", "running", "retrying")) {
            assertTrue(hasActiveNewsRefreshTask(listOf(null, "succeeded", status)))
        }
    }

    @Test
    fun completedFailedCancelledOrMissingTaskDoesNotBlockManualRefresh() {
        assertFalse(hasActiveNewsRefreshTask(listOf(null, "succeeded", "failed", "cancelled")))
        assertFalse(hasActiveNewsRefreshTask(emptyList()))
    }

    @Test
    fun manualRefreshPersistsForcedGenerationAndSchedulesItsTask() = withTasks { tasks ->
        val scheduled = mutableListOf<Long>()
        val submission = enqueueManualNewsRefresh(tasks, false, scheduled::add)
        val task = tasks.getById(requireNotNull(submission.taskId))!!
        val payload = Json.decodeFromString<UnifiedNewsGenerateTaskPayload>(task.payload_json)

        assertEquals(NewsManualRefreshNotice.STARTED, submission.notice)
        assertEquals("queued", task.status)
        assertEquals("remote_news_fetch", task.type)
        assertEquals(listOf(task.id), scheduled)
        assertTrue(payload.force)
        assertFalse(payload.ignoreSourceTimeFilter)
        assertEquals("manual", payload.mode)
    }

    @Test
    fun reopeningWhileManualRefreshIsActiveReusesTheDurableTask() = withTasks { tasks ->
        val first = enqueueManualNewsRefresh(tasks, true) { }
        val second = enqueueManualNewsRefresh(tasks, true) { }

        assertEquals(first.taskId, second.taskId)
        assertEquals(NewsManualRefreshNotice.ALREADY_RUNNING, second.notice)
        assertEquals(1, tasks.runnableTasks(Long.MAX_VALUE).size)
        val payload = Json.decodeFromString<UnifiedNewsGenerateTaskPayload>(tasks.getById(first.taskId!!)!!.payload_json)
        assertTrue(payload.ignoreSourceTimeFilter)
    }

    @Test
    fun automaticSummaryTaskPreventsAnExtraManualGeneration() = withTasks { tasks ->
        for (key in listOf("remote_news_fetch:due", "remote_news_fetch:backfill")) {
            val existing = tasks.enqueue("remote_news_fetch", "{}", uniqueKey = key)
            val submission = enqueueManualNewsRefresh(tasks, false) { }

            assertEquals(existing, submission.taskId)
            assertEquals(NewsManualRefreshNotice.ALREADY_RUNNING, submission.notice)
            assertEquals(1, tasks.runnableTasks(Long.MAX_VALUE).size)
            completeTask(tasks, existing)
        }
    }

    @Test
    fun activeRecommendationsKeepManualSummaryRefreshWaiting() = withTasks { tasks ->
        tasks.enqueue("news_opportunity_analysis", "{}", uniqueKey = "news_opportunity_analysis")
        val submission = enqueueManualNewsRefresh(tasks, false) { }

        assertNull(submission.taskId)
        assertEquals(NewsManualRefreshNotice.ALREADY_RUNNING, submission.notice)
        assertEquals(1, tasks.runnableTasks(Long.MAX_VALUE).size)
    }

    @Test
    fun completedManualTaskAllowsANewRefresh() = withTasks { tasks ->
        val first = enqueueManualNewsRefresh(tasks, false) { }
        completeTask(tasks, first.taskId!!)
        val second = enqueueManualNewsRefresh(tasks, false) { }

        assertNotEquals(first.taskId, second.taskId)
        assertEquals(NewsManualRefreshNotice.STARTED, second.notice)
    }

    @Test
    fun fastCompletionPreservesTheUnconsumedStartNotice() = withTasks { tasks ->
        val id = tasks.enqueue("remote_news_fetch", "{}")
        completeTask(tasks, id)
        val state = UnifiedNewsState(isRegenerating = true, manualRefreshNotice = NewsManualRefreshNotice.STARTED)
        val completed = state.withManualNewsRefreshFinished(tasks.getById(id)!!, null)

        assertFalse(completed.isRegenerating)
        assertEquals(NewsManualRefreshNotice.STARTED, completed.manualRefreshNotice)
        assertEquals(1, completed.summaryRefreshCompletedToken)
    }

    @Test
    fun cancellingTheCallerLeavesTheScheduledTaskRunnable() = withTasks { tasks ->
        runBlocking {
            val caller = launch {
                enqueueManualNewsRefresh(tasks, false) { }
                awaitCancellation()
            }
            yield()
            caller.cancelAndJoin()

            val task = tasks.getLatestByUniqueKey("remote_news_fetch:manual")!!
            assertEquals("queued", task.status)
            completeTask(tasks, task.id)
            assertEquals("succeeded", tasks.getById(task.id)!!.status)
        }
    }

    @Test
    fun retryingTaskIsReusedWithoutResettingItsDeadline() = withTasks { tasks ->
        val first = enqueueManualNewsRefresh(tasks, false) { }
        assertTrue(tasks.claimForRun(first.taskId!!, "test", Long.MAX_VALUE))
        tasks.markRetry(first.taskId, "network", "temporary failure", Long.MAX_VALUE)
        val second = enqueueManualNewsRefresh(tasks, false) { }

        assertEquals(first.taskId, second.taskId)
        assertEquals(NewsManualRefreshNotice.ALREADY_RUNNING, second.notice)
        assertEquals(Long.MAX_VALUE, tasks.getById(first.taskId)!!.run_after_ms)
    }

    @Test
    fun taskFailureIsReportedWithoutClearingTheWaitingNotice() = withTasks { tasks ->
        val id = tasks.enqueue("remote_news_fetch", "{}")
        assertTrue(tasks.claimForRun(id, "test", Long.MAX_VALUE))
        tasks.finishFailure(id, "network", "request failed")
        val state = UnifiedNewsState(isRegenerating = true, manualRefreshNotice = NewsManualRefreshNotice.ALREADY_RUNNING)
        val completed = state.withManualNewsRefreshFinished(tasks.getById(id)!!, null)

        assertFalse(completed.isRegenerating)
        assertEquals("request failed", completed.error)
        assertEquals(NewsManualRefreshNotice.ALREADY_RUNNING, completed.manualRefreshNotice)
    }

    private fun completeTask(tasks: AsyncTaskRepository, id: Long) {
        assertTrue(tasks.claimForRun(id, "test", Long.MAX_VALUE))
        tasks.finishSuccess(id, "")
    }

    private fun withTasks(block: (AsyncTaskRepository) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            block(AsyncTaskRepository(DailySatoriDatabase(driver)))
        }
    }
}
