package com.dailysatori.core.worker

import kotlinx.coroutines.delay

/** Best-effort WorkManager wake-up; the database task remains authoritative and startup recovery is the fallback. */
internal suspend fun wakeReminderAiTask(
    taskId: Long,
    enqueue: (Long) -> Unit,
    pause: suspend (Long) -> Unit = { delay(it) },
): Boolean {
    for (delayMs in listOf(0L, 1_000L, 5_000L, 30_000L)) {
        if (delayMs > 0) pause(delayMs)
        if (runCatching { enqueue(taskId) }.isSuccess) return true
    }
    return false
}
