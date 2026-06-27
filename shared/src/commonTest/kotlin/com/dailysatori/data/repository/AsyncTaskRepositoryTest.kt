package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AsyncTaskRepositoryTest {
    @Test
    fun enqueueRefreshesExistingRetryingUniqueTaskForImmediateManualRun() {
        withRepository { repository ->
            val taskId = repository.enqueue(
                type = AsyncTaskType.external_favorite_sync.name,
                payloadJson = "{}",
                uniqueKey = "external_favorite_sync:1:sync",
            )
            repository.markRetry(
                id = taskId,
                code = "rate_limited",
                message = "retry later",
                runAfterMs = 9_999_999_999_999L,
            )
            val retrying = repository.getById(taskId)!!
            Thread.sleep(2)

            val reusedTaskId = repository.enqueue(
                type = AsyncTaskType.external_favorite_sync.name,
                payloadJson = "{}",
                uniqueKey = "external_favorite_sync:1:sync",
            )

            val refreshed = repository.getById(taskId)!!
            assertEquals(taskId, reusedTaskId)
            assertEquals(AsyncTaskStatus.retrying.name, refreshed.status)
            assertNull(refreshed.run_after_ms)
            assertTrue(refreshed.updated_at > retrying.updated_at)
        }
    }

    @Test
    fun activeUniqueKeyIndexRejectsDuplicateRunnableTasks() {
        withDatabase { db ->
            val q = db.dailySatoriQueries
            q.insertAsyncTask(
                type = AsyncTaskType.external_favorite_sync.name,
                status = AsyncTaskStatus.queued.name,
                payload_json = "{}",
                checkpoint_json = "",
                result_json = "",
                progress_current = 0,
                progress_total = 0,
                progress_message = "",
                attempt_count = 0,
                max_attempts = 5,
                priority = 0,
                unique_key = "external_favorite_sync:1:sync",
                batch_id = null,
                run_after_ms = null,
                lease_owner = null,
                lease_until_ms = null,
                started_at = null,
                finished_at = null,
                last_error_code = "",
                last_error_message = "",
                created_at = 1,
                updated_at = 1,
            )

            assertFailsWith<Exception> {
                q.insertAsyncTask(
                    type = AsyncTaskType.external_favorite_sync.name,
                    status = AsyncTaskStatus.running.name,
                    payload_json = "{}",
                    checkpoint_json = "",
                    result_json = "",
                    progress_current = 0,
                    progress_total = 0,
                    progress_message = "",
                    attempt_count = 0,
                    max_attempts = 5,
                    priority = 0,
                    unique_key = "external_favorite_sync:1:sync",
                    batch_id = null,
                    run_after_ms = null,
                    lease_owner = null,
                    lease_until_ms = null,
                    started_at = 2,
                    finished_at = null,
                    last_error_code = "",
                    last_error_message = "",
                    created_at = 2,
                    updated_at = 2,
                )
            }
        }
    }

    @Test
    fun enqueueCreatesNewTaskForUniqueKeyAfterPreviousTaskSucceeded() {
        withRepository { repository ->
            val firstId = repository.enqueue(
                type = AsyncTaskType.external_favorite_sync.name,
                payloadJson = "{}",
                uniqueKey = "external_favorite_sync:1:sync",
            )
            repository.finishSuccess(firstId, """{"ok":true}""")

            val secondId = repository.enqueue(
                type = AsyncTaskType.external_favorite_sync.name,
                payloadJson = "{}",
                uniqueKey = "external_favorite_sync:1:sync",
            )

            assertTrue(secondId > firstId)
            assertEquals(AsyncTaskStatus.succeeded.name, repository.getById(firstId)!!.status)
            assertEquals(AsyncTaskStatus.queued.name, repository.getById(secondId)!!.status)
        }
    }

    @Test
    fun runningTaskCannotBeClaimedAgain() {
        withRepository { repository ->
            val taskId = repository.enqueue(
                type = AsyncTaskType.external_favorite_sync.name,
                payloadJson = "{}",
            )

            val firstClaim = repository.claimForRun(taskId, leaseOwner = "worker-a", leaseUntilMs = 10_000)
            val secondClaim = repository.claimForRun(taskId, leaseOwner = "worker-b", leaseUntilMs = 10_000)

            assertEquals(true, firstClaim)
            assertEquals(false, secondClaim)
            assertEquals("worker-a", repository.getById(taskId)!!.lease_owner)
        }
    }

    @Test
    fun terminalTaskIgnoresLateProgressUpdates() {
        withRepository { repository ->
            val taskId = repository.enqueue(
                type = AsyncTaskType.external_favorite_sync.name,
                payloadJson = "{}",
            )
            repository.claimForRun(taskId, leaseOwner = "worker-a", leaseUntilMs = 10_000)
            repository.finishSuccess(taskId, "{}")
            val finished = repository.getById(taskId)!!

            repository.updateProgress(taskId, current = 7, total = 9, message = "late", checkpointJson = """{"late":true}""")

            val task = repository.getById(taskId)!!
            assertEquals(AsyncTaskStatus.succeeded.name, task.status)
            assertEquals(finished.progress_current, task.progress_current)
            assertEquals(finished.progress_total, task.progress_total)
            assertEquals(finished.progress_message, task.progress_message)
            assertEquals(finished.checkpoint_json, task.checkpoint_json)
        }
    }

    private fun withRepository(block: (AsyncTaskRepository) -> Unit) {
        withDatabase { db -> block(AsyncTaskRepository(db)) }
    }

    private fun withDatabase(block: (DailySatoriDatabase) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        block(db)
        driver.close()
    }
}
