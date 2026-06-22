package com.dailysatori.service.asynctask

enum class AsyncTaskStatus {
    queued,
    running,
    retrying,
    succeeded,
    failed,
    cancelled,
}

val AsyncTaskStatus.visibleByDefault: Boolean
    get() = this == AsyncTaskStatus.queued ||
        this == AsyncTaskStatus.running ||
        this == AsyncTaskStatus.retrying

enum class AsyncTaskType(val displayName: String) {
    save_article("保存文章"),
    remote_article_sync("同步远程文章"),
    remote_news_fetch("获取远程新闻"),
    external_favorite_sync("外部收藏同步"),
    book_viewpoint_generate("书籍观点生成"),
}

data class AsyncTaskFilter(
    val type: String? = null,
    val status: String? = null,
    val showTerminal: Boolean = false,
)

data class AsyncTaskListItem(
    val id: Long,
    val type: String,
    val status: String,
    val progressCurrent: Long,
    val progressTotal: Long,
    val progressMessage: String,
    val updatedAt: Long,
    val lastErrorMessage: String,
)

data class AsyncTaskBatchProgress(
    val finishedCount: Int,
    val totalCount: Int,
    val fraction: Float?,
    val complete: Boolean,
)

fun filterAsyncTasks(tasks: List<AsyncTaskListItem>, filter: AsyncTaskFilter): List<AsyncTaskListItem> =
    tasks.filter { task ->
        val status = asyncTaskStatus(task.status)
        val defaultVisible = filter.showTerminal || filter.status != null || status?.visibleByDefault == true
        defaultVisible &&
            (filter.type == null || task.type == filter.type) &&
            (filter.status == null || task.status == filter.status)
    }

fun asyncTaskStatus(value: String): AsyncTaskStatus? =
    AsyncTaskStatus.entries.firstOrNull { it.name == value }

fun asyncTaskTypeDisplayName(value: String): String =
    AsyncTaskType.entries.firstOrNull { it.name == value }?.displayName ?: value

fun asyncTaskStatusDisplayName(value: String): String = when (asyncTaskStatus(value)) {
    AsyncTaskStatus.queued -> "等待中"
    AsyncTaskStatus.running -> "执行中"
    AsyncTaskStatus.retrying -> "等待重试"
    AsyncTaskStatus.succeeded -> "已完成"
    AsyncTaskStatus.failed -> "已失败"
    AsyncTaskStatus.cancelled -> "已取消"
    null -> value
}

fun asyncTaskProgressFraction(current: Long, total: Long): Float? {
    if (total <= 0L) return null
    return (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

fun asyncTaskBatchProgress(tasks: List<AsyncTaskListItem>, totalCount: Int): AsyncTaskBatchProgress {
    val finished = tasks.count { asyncTaskStatus(it.status)?.terminal == true }
    val total = totalCount.coerceAtLeast(tasks.size)
    return AsyncTaskBatchProgress(
        finishedCount = finished,
        totalCount = total,
        fraction = asyncTaskProgressFraction(finished.toLong(), total.toLong()),
        complete = total > 0 && finished >= total,
    )
}

fun asyncTaskNextRetryDelayMs(attemptCount: Long): Long = when (attemptCount) {
    0L -> 30_000L
    1L -> 2 * 60_000L
    2L -> 10 * 60_000L
    3L -> 30 * 60_000L
    else -> 2 * 60 * 60_000L
}

sealed interface AsyncTaskExecutionResult {
    data class Success(val resultJson: String = "") : AsyncTaskExecutionResult
    data class RetryableFailure(
        val code: String,
        val message: String,
        val retryAfterMs: Long? = null,
    ) : AsyncTaskExecutionResult
    data class PermanentFailure(val code: String, val message: String) : AsyncTaskExecutionResult
}

interface AsyncTaskProgressReporter {
    suspend fun report(
        current: Long,
        total: Long,
        message: String = "",
        checkpointJson: String = "",
    )
}

interface AsyncTaskHandler {
    val type: String

    suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult
}

class AsyncTaskHandlerRegistry(handlers: List<AsyncTaskHandler>) {
    private val byType = handlers.associateBy { it.type }

    fun get(type: String): AsyncTaskHandler? = byType[type]
}

private val AsyncTaskStatus.terminal: Boolean
    get() = this == AsyncTaskStatus.succeeded ||
        this == AsyncTaskStatus.failed ||
        this == AsyncTaskStatus.cancelled
