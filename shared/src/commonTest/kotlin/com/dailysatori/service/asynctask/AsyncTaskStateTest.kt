package com.dailysatori.service.asynctask

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsyncTaskStateTest {
    @Test
    fun defaultTaskFilterIncludesTerminalTasks() {
        assertTrue(AsyncTaskStatus.queued.visibleByDefault)
        assertTrue(AsyncTaskStatus.running.visibleByDefault)
        assertTrue(AsyncTaskStatus.retrying.visibleByDefault)

        assertFalse(AsyncTaskStatus.succeeded.visibleByDefault)
        assertFalse(AsyncTaskStatus.failed.visibleByDefault)
        assertFalse(AsyncTaskStatus.cancelled.visibleByDefault)
    }

    @Test
    fun taskFilterMatchesTypeAndStatusWhenProvided() {
        val tasks = listOf(
            asyncTaskListItem(id = 1, type = AsyncTaskType.save_article.name, status = AsyncTaskStatus.queued.name),
            asyncTaskListItem(id = 2, type = AsyncTaskType.external_favorite_sync.name, status = AsyncTaskStatus.running.name),
            asyncTaskListItem(id = 3, type = AsyncTaskType.save_article.name, status = AsyncTaskStatus.succeeded.name),
        )

        assertEquals(listOf(1L, 2L, 3L), filterAsyncTasks(tasks, AsyncTaskFilter()).map { it.id })
        assertEquals(
            listOf(1L, 3L),
            filterAsyncTasks(tasks, AsyncTaskFilter(types = setOf(AsyncTaskType.save_article.name))).map { it.id },
        )
        assertEquals(
            listOf(2L),
            filterAsyncTasks(tasks, AsyncTaskFilter(statuses = setOf(AsyncTaskStatus.running.name))).map { it.id },
        )
        assertEquals(
            listOf(3L),
            filterAsyncTasks(
                tasks,
                AsyncTaskFilter(statuses = setOf(AsyncTaskStatus.succeeded.name), showTerminal = true),
            ).map { it.id },
        )
    }

    @Test
    fun taskFilterMatchesMultipleTypesAndStatuses() {
        val tasks = listOf(
            asyncTaskListItem(id = 1, type = AsyncTaskType.save_article.name, status = AsyncTaskStatus.queued.name),
            asyncTaskListItem(id = 2, type = AsyncTaskType.external_favorite_sync.name, status = AsyncTaskStatus.running.name),
            asyncTaskListItem(id = 3, type = AsyncTaskType.remote_news_fetch.name, status = AsyncTaskStatus.succeeded.name),
            asyncTaskListItem(id = 4, type = AsyncTaskType.external_favorite_sync.name, status = AsyncTaskStatus.failed.name),
        )

        val filtered = filterAsyncTasks(
            tasks,
            AsyncTaskFilter(
                types = setOf(AsyncTaskType.external_favorite_sync.name, AsyncTaskType.remote_news_fetch.name),
                statuses = setOf(AsyncTaskStatus.succeeded.name, AsyncTaskStatus.failed.name),
            ),
        )

        assertEquals(listOf(3L, 4L), filtered.map { it.id })
    }

    @Test
    fun progressFractionHandlesEmptyAndOverflowProgress() {
        assertEquals(null, asyncTaskProgressFraction(0, 0))
        assertEquals(0.5f, asyncTaskProgressFraction(2, 4))
        assertEquals(1f, asyncTaskProgressFraction(6, 4))
    }

    @Test
    fun batchProgressAdvancesWhenEachTaskReachesTerminalState() {
        val progress = asyncTaskBatchProgress(
            tasks = listOf(
                asyncTaskListItem(id = 1, type = AsyncTaskType.save_article.name, status = AsyncTaskStatus.succeeded.name),
                asyncTaskListItem(id = 2, type = AsyncTaskType.save_article.name, status = AsyncTaskStatus.failed.name),
                asyncTaskListItem(id = 3, type = AsyncTaskType.save_article.name, status = AsyncTaskStatus.running.name),
            ),
            totalCount = 3,
        )

        assertEquals(2, progress.finishedCount)
        assertEquals(3, progress.totalCount)
        assertEquals(0.6666667f, progress.fraction)
        assertFalse(progress.complete)
    }

    private fun asyncTaskListItem(id: Long, type: String, status: String) = AsyncTaskListItem(
        id = id,
        type = type,
        status = status,
        progressCurrent = 0,
        progressTotal = 1,
        progressMessage = "",
        checkpointJson = "",
        updatedAt = id,
        createdAt = id,
        startedAt = null,
        finishedAt = null,
        lastErrorMessage = "",
    )
}
