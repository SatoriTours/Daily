package com.dailysatori.ui.feature.settings.taskcenter

import com.dailysatori.service.asynctask.AsyncTaskListItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskCenterTabStateTest {
    private val strings = testStrings()

    private fun task(
        status: String,
        type: String = "remote_news_fetch",
        progressMessage: String = "",
        progressCurrent: Long = 0,
        progressTotal: Long = 0,
        lastErrorMessage: String = "",
        runAfterMs: Long? = null,
        checkpointJson: String = "",
        payloadJson: String = "",
    ) = AsyncTaskListItem(
        id = 1485,
        type = type,
        status = status,
        progressCurrent = progressCurrent,
        progressTotal = progressTotal,
        progressMessage = progressMessage,
        checkpointJson = checkpointJson,
        createdAt = 1_000L,
        startedAt = null,
        finishedAt = null,
        updatedAt = 2_000L,
        lastErrorMessage = lastErrorMessage,
        runAfterMs = runAfterMs,
        attemptCount = 1,
        payloadJson = payloadJson,
    )

    @Test
    fun nonTerminalTasksAreCurrentAndTerminalTasksAreHistory() {
        assertEquals(TaskCenterTab.Current, taskCenterTabOf("queued"))
        assertEquals(TaskCenterTab.Current, taskCenterTabOf("running"))
        assertEquals(TaskCenterTab.Current, taskCenterTabOf("retrying"))
        assertEquals(TaskCenterTab.Pending, taskCenterTabOf("failed"))
        assertEquals(TaskCenterTab.History, taskCenterTabOf("succeeded"))
        assertEquals(TaskCenterTab.History, taskCenterTabOf("cancelled"))
    }

    @Test
    fun tabStatusesMatchTheVisibleTabs() {
        assertEquals(setOf("queued", "running", "retrying"), taskCenterTabStatuses(TaskCenterTab.Current))
        assertEquals(setOf("failed"), taskCenterTabStatuses(TaskCenterTab.Pending))
        assertEquals(setOf("succeeded", "cancelled"), taskCenterTabStatuses(TaskCenterTab.History) - "failed")
    }

    @Test
    fun historyTabIncludesFailedTasksSoOldFailuresStayReachable() {
        assertTrue("failed" in taskCenterHistoryStatuses())
    }

    @Test
    fun queuedRowStatesWhatItIsWaitingForWithoutInventingProgress() {
        val text = taskCenterRowText(task("queued", type = "unified_news_generate"), strings)
        assertEquals(strings.waiting, text.headline)
        assertEquals("", text.detail)
        assertNull(taskCenterProgress(task("queued")))
    }

    @Test
    fun retryingRowKeepsTheScheduledRetryTimeAndRealReason() {
        val text = taskCenterRowText(task("retrying", lastErrorMessage = "连接中断", runAfterMs = 5_000L), strings)
        assertEquals(strings.retryingTitle, text.headline)
        assertEquals("连接中断", text.detail)
        assertEquals(5_000L, text.retryAtMs)
    }

    @Test
    fun failedRowShowsTheRecordedFailureInsteadOfTheStatusName() {
        val withMessage = taskCenterRowText(task("failed", lastErrorMessage = "响应读取中断"), strings)
        assertEquals(strings.failedTitle, withMessage.headline)
        assertEquals("响应读取中断", withMessage.detail)
        val withoutMessage = taskCenterRowText(task("failed"), strings)
        assertEquals(strings.failedDetail, withoutMessage.detail)
    }

    @Test
    fun favoriteSyncRowShowsRealItemsSeenInsteadOfPageBudget() {
        val text = taskCenterRowText(
            task(
                status = "running",
                type = "external_favorite_sync",
                progressMessage = "读取完成",
                progressCurrent = 2,
                progressTotal = 5,
                checkpointJson = """{"itemsSeen":42}""",
            ),
            strings,
        )
        assertTrue(text.headline.contains("42"))
        assertTrue(text.detail.contains("of 5 pages"))
    }

    @Test
    fun progressIsOnlyShownWhenARealTotalExists() {
        assertNull(taskCenterProgress(task("running", progressCurrent = 2, progressTotal = 0)))
        val progress = taskCenterProgress(task("running", progressCurrent = 2, progressTotal = 5))
        assertNotNull(progress)
        assertEquals("2 / 5", progress.text)
        assertEquals(0.4f, progress.fraction)
    }

    @Test
    fun loadedCountsAreNeverPresentedAsTotals() {
        assertFalse(taskCenterTabCountText(loaded = 3, total = null).contains("3"))
        assertTrue(taskCenterTabCountText(loaded = 3, total = 12L).contains("12"))
    }

    @Test
    fun durationsAreLanguageNeutralAndUnknownIsExplicit() {
        assertEquals("250ms", taskCenterDurationText(250L, strings))
        assertEquals("1.2s", taskCenterDurationText(1_250L, strings))
        assertEquals("2m 5s", taskCenterDurationText(125_000L, strings))
        assertEquals(strings.unknown, taskCenterDurationText(null, strings))
    }
}
