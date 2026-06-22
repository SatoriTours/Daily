package com.dailysatori.core.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dailysatori.core.task.UnifiedNewsGenerateTaskHandler
import com.dailysatori.core.task.remoteArticleSyncTaskPayloadJson
import com.dailysatori.core.task.unifiedNewsGenerateTaskPayloadJson
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.unifiednews.UnifiedNewsGenerationResult
import com.dailysatori.service.unifiednews.nextUnifiedNewsWindow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class UnifiedNewsScheduler(private val context: Context) {
    fun ensureScheduled() {
        ensureNextScheduled()
    }

    fun ensureNextScheduled(now: Instant = Clock.System.now()) {
        enqueueNext(now, ExistingWorkPolicy.KEEP)
    }

    fun scheduleNext(now: Instant = Clock.System.now()) {
        enqueueNext(now, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueueNext(now: Instant, policy: ExistingWorkPolicy) {
        val request = buildUnifiedNewsNextWorkRequest(now)
        WorkManager.getInstance(context).enqueueUniqueWork(WorkNameNext, policy, request)
    }

    private companion object {
        const val WorkNameNext = "unified-news-next"
    }
}

class UnifiedNewsWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            when (unifiedNewsWorkerMode(inputData.getString(KEY_MODE))) {
                UnifiedNewsWorkerMode.DUE -> enqueueDailySummaryTask(mode = UnifiedNewsWorkerMode.DUE, force = true)
                UnifiedNewsWorkerMode.BACKFILL -> enqueueDailySummaryTask(mode = UnifiedNewsWorkerMode.BACKFILL, force = false)
                else -> Result.failure()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun enqueueDailySummaryTask(mode: UnifiedNewsWorkerMode, force: Boolean): Result {
        val asyncTaskRepo = GlobalContext.get().get<AsyncTaskRepository>()
        val asyncTaskScheduler = AsyncTaskScheduler(applicationContext)
        val modeValue = when (mode) {
            UnifiedNewsWorkerMode.DUE -> UnifiedNewsGenerateTaskHandler.MODE_DUE
            UnifiedNewsWorkerMode.BACKFILL -> UnifiedNewsGenerateTaskHandler.MODE_BACKFILL
        }
        val syncTaskId = asyncTaskRepo.enqueue(
            type = AsyncTaskType.remote_article_sync.name,
            payloadJson = remoteArticleSyncTaskPayloadJson(mode = modeValue),
            uniqueKey = "remote_article_sync:${mode.name.lowercase()}",
        )
        asyncTaskScheduler.enqueue(syncTaskId)
        val summaryTaskId = asyncTaskRepo.enqueue(
            type = AsyncTaskType.remote_news_fetch.name,
            payloadJson = unifiedNewsGenerateTaskPayloadJson(
                force = force,
                ignoreSourceTimeFilter = false,
                mode = modeValue,
            ),
            uniqueKey = "remote_news_fetch:${mode.name.lowercase()}",
        )
        asyncTaskScheduler.enqueue(summaryTaskId)
        return Result.success()
    }

    companion object {
        const val KEY_MODE = "mode"
        const val MODE_DUE = "due"
        const val MODE_BACKFILL = "backfill"
    }
}

internal fun buildUnifiedNewsNextWorkRequest(now: Instant = Clock.System.now()): OneTimeWorkRequest {
    val next = nextUnifiedNewsWindow(now)
    val delayMs = (next.dueAt.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0L)
    return OneTimeWorkRequestBuilder<UnifiedNewsWorker>()
        .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
        .setInputData(workDataOf(UnifiedNewsWorker.KEY_MODE to UnifiedNewsWorker.MODE_DUE))
        .build()
}

internal fun buildUnifiedNewsBackfillWorkRequest(): OneTimeWorkRequest {
    return OneTimeWorkRequestBuilder<UnifiedNewsWorker>()
        .setInputData(workDataOf(UnifiedNewsWorker.KEY_MODE to UnifiedNewsWorker.MODE_BACKFILL))
        .build()
}

internal enum class UnifiedNewsWorkerMode { DUE, BACKFILL }

internal fun unifiedNewsWorkerMode(value: String?): UnifiedNewsWorkerMode? = when (value) {
    UnifiedNewsWorker.MODE_DUE -> UnifiedNewsWorkerMode.DUE
    UnifiedNewsWorker.MODE_BACKFILL -> UnifiedNewsWorkerMode.BACKFILL
    else -> null
}

internal fun shouldRetryUnifiedNews(result: UnifiedNewsGenerationResult): Boolean {
    if (result.success) return false
    val message = result.message.orEmpty()
    if (message.contains("请先配置")) return false
    if (message.contains("无效引用")) return false
    if (message.startsWith("AI ")) return false
    return true
}
