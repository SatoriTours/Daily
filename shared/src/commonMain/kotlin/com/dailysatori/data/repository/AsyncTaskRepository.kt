package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.dailysatori.service.asynctask.AsyncTaskFilter
import com.dailysatori.service.asynctask.AsyncTaskListItem
import com.dailysatori.service.asynctask.AsyncTaskStatus
import com.dailysatori.service.asynctask.filterAsyncTasks
import com.dailysatori.shared.db.Async_task
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

data class AsyncTaskEnqueueRequest(
    val type: String,
    val payloadJson: String,
    val uniqueKey: String? = null,
    val maxAttempts: Long = 5,
    val priority: Long = 0,
)

data class AsyncTaskBatchEnqueueResult(
    val batchId: Long,
    val taskIds: List<Long>,
)

data class AsyncTaskCenterPage(
    val tasks: List<AsyncTaskListItem>,
    val loadedCount: Int,
    val requestedLimit: Int,
) {
    val hasMore: Boolean get() = loadedCount >= requestedLimit
}

class AsyncTaskRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun enqueue(
        type: String,
        payloadJson: String,
        uniqueKey: String? = null,
        batchId: Long? = null,
        maxAttempts: Long = 5,
        priority: Long = 0,
    ): Long = q.transactionWithResult {
        insertTask(type, payloadJson, uniqueKey, batchId, maxAttempts, priority)
    }

    fun enqueueBatch(name: String, requests: List<AsyncTaskEnqueueRequest>): AsyncTaskBatchEnqueueResult =
        q.transactionWithResult {
            val batchId = insertBatch(name, requests.size.toLong())
            val taskIds = requests.map { request ->
                insertTask(
                    type = request.type,
                    payloadJson = request.payloadJson,
                    uniqueKey = request.uniqueKey,
                    batchId = batchId,
                    maxAttempts = request.maxAttempts,
                    priority = request.priority,
                )
            }
            AsyncTaskBatchEnqueueResult(batchId = batchId, taskIds = taskIds)
        }

    private fun insertTask(
        type: String,
        payloadJson: String,
        uniqueKey: String?,
        batchId: Long?,
        maxAttempts: Long,
        priority: Long,
    ): Long {
        val now = Clock.System.now().toEpochMilliseconds()
        val existing = uniqueKey?.let { q.selectAsyncTaskByUniqueKey(it).executeAsOneOrNull() }
        if (existing != null && existing.status in activeStatuses) {
            q.refreshActiveAsyncTaskEnqueue(updated_at = now, id = existing.id)
            return existing.id
        }
        q.insertAsyncTask(
            type = type,
            status = AsyncTaskStatus.queued.name,
            payload_json = payloadJson,
            checkpoint_json = "",
            result_json = "",
            progress_current = 0,
            progress_total = 0,
            progress_message = "",
            attempt_count = 0,
            max_attempts = maxAttempts,
            priority = priority,
            unique_key = uniqueKey,
            batch_id = batchId,
            run_after_ms = null,
            lease_owner = null,
            lease_until_ms = null,
            started_at = null,
            finished_at = null,
            last_error_code = "",
            last_error_message = "",
            created_at = now,
            updated_at = now,
        )
        return q.selectLastInsertedAsyncTaskId().executeAsOne()
    }

    fun createBatch(name: String, totalCount: Long): Long {
        return insertBatch(name, totalCount)
    }

    private fun insertBatch(name: String, totalCount: Long): Long {
        val now = Clock.System.now().toEpochMilliseconds()
        q.insertAsyncTaskBatch(
            name = name,
            status = AsyncTaskStatus.queued.name,
            total_count = totalCount,
            created_at = now,
            updated_at = now,
        )
        return q.selectLastInsertedAsyncTaskBatchId().executeAsOne()
    }

    fun observeTaskCenter(filter: AsyncTaskFilter, limit: Int = DEFAULT_TASK_CENTER_LIMIT): Flow<AsyncTaskCenterPage> {
        val requestedLimit = limit.coerceAtLeast(1)
        return q.selectAsyncTasksForTaskCenterPage(requestedLimit.toLong(), ::AsyncTaskListItem)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { tasks ->
                AsyncTaskCenterPage(
                    tasks = filterAsyncTasks(tasks, filter),
                    loadedCount = tasks.size,
                    requestedLimit = requestedLimit,
                )
            }
    }

    fun observeLatestByUniqueKey(uniqueKey: String): Flow<Async_task?> =
        q.selectAsyncTaskByUniqueKey(uniqueKey)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

    fun observeTaskById(id: Long): Flow<Async_task?> =
        q.selectAsyncTaskById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

    fun getById(id: Long): Async_task? =
        q.selectAsyncTaskById(id).executeAsOneOrNull()

    fun runnableTasks(nowMs: Long, limit: Long = 20): List<Async_task> =
        q.selectRunnableAsyncTasks(nowMs, limit).executeAsList()

    fun claimForRun(id: Long, leaseOwner: String, leaseUntilMs: Long): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        q.claimAsyncTaskForRun(
            status = AsyncTaskStatus.running.name,
            lease_owner = leaseOwner,
            lease_until_ms = leaseUntilMs,
            value = now,
            updated_at = now,
            id = id,
            run_after_ms = now,
        )
        return q.selectAsyncTaskById(id).executeAsOneOrNull()?.lease_owner == leaseOwner
    }

    fun updateProgress(id: Long, current: Long, total: Long, message: String, checkpointJson: String) {
        q.updateAsyncTaskProgress(
            progress_current = current,
            progress_total = total,
            progress_message = message,
            checkpoint_json = checkpointJson,
            updated_at = Clock.System.now().toEpochMilliseconds(),
            id = id,
        )
    }

    fun finishSuccess(id: Long, resultJson: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        q.finishAsyncTask(
            status = AsyncTaskStatus.succeeded.name,
            result_json = resultJson,
            finished_at = now,
            last_error_code = "",
            last_error_message = "",
            updated_at = now,
            id = id,
        )
    }

    fun finishFailure(id: Long, code: String, message: String) {
        val now = Clock.System.now().toEpochMilliseconds()
        q.finishAsyncTask(
            status = AsyncTaskStatus.failed.name,
            result_json = "",
            finished_at = now,
            last_error_code = code,
            last_error_message = message,
            updated_at = now,
            id = id,
        )
    }

    fun markRetry(id: Long, code: String, message: String, runAfterMs: Long) {
        val task = getById(id) ?: return
        q.markAsyncTaskRetry(
            status = AsyncTaskStatus.retrying.name,
            attempt_count = task.attempt_count + 1,
            run_after_ms = runAfterMs,
            last_error_code = code,
            last_error_message = message,
            updated_at = Clock.System.now().toEpochMilliseconds(),
            id = id,
        )
    }

    fun cancel(id: Long) {
        val now = Clock.System.now().toEpochMilliseconds()
        q.cancelAsyncTask(AsyncTaskStatus.cancelled.name, now, now, id)
    }

    fun cancelLatestByUniqueKey(uniqueKey: String) {
        q.selectAsyncTaskByUniqueKey(uniqueKey).executeAsOneOrNull()?.let { task ->
            if (task.status in activeStatuses) cancel(task.id)
        }
    }

    fun markExpiredRunningForRetry(nowMs: Long) {
        q.markExpiredRunningAsyncTasksForRetry(
            status = AsyncTaskStatus.retrying.name,
            updated_at = nowMs,
            lease_until_ms = nowMs,
        )
    }

    private companion object {
        val activeStatuses = setOf(
            AsyncTaskStatus.queued.name,
            AsyncTaskStatus.running.name,
            AsyncTaskStatus.retrying.name,
        )
        const val DEFAULT_TASK_CENTER_LIMIT = 50
    }
}
