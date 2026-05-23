package com.dailysatori.core.worker

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dailysatori.normalizeArticleUrl
import com.dailysatori.service.parser.WebpageParserService
import kotlinx.coroutines.CancellationException
import org.koin.core.context.GlobalContext
import java.security.MessageDigest

class ArticleProcessingScheduler(private val context: Context) {
    fun enqueueSave(url: String) {
        enqueueSave(url, ExistingWorkPolicy.KEEP)
    }

    fun enqueueRetrySave(url: String) {
        enqueueSave(url, ExistingWorkPolicy.REPLACE)
    }

    private fun enqueueSave(url: String, policy: ExistingWorkPolicy) {
        val normalizedUrl = normalizeArticleUrl(url)
        if (normalizedUrl.isBlank()) return
        markSavePending(normalizedUrl)
        val request = buildArticleSaveWorkRequest(url, normalizedUrl)
        WorkManager.getInstance(context).enqueueUniqueWork(
            saveWorkName(normalizedUrl),
            policy,
            request,
        )
    }

    fun isSavePending(url: String): Boolean {
        val normalizedUrl = normalizeArticleUrl(url)
        if (normalizedUrl.isBlank()) return false
        val markedAt = try {
            pendingPrefs().getLong(normalizedUrl, 0L)
        } catch (_: ClassCastException) {
            clearSavePending(normalizedUrl)
            return false
        }
        if (markedAt == 0L) return false
        if (System.currentTimeMillis() - markedAt <= SAVE_PENDING_TTL_MS) return true
        clearSavePending(normalizedUrl)
        return false
    }

    fun clearSavePending(url: String) {
        val normalizedUrl = normalizeArticleUrl(url)
        if (normalizedUrl.isBlank()) return
        pendingPrefs().edit().remove(normalizedUrl).apply()
    }

    fun enqueueResume() {
        val request = OneTimeWorkRequestBuilder<ArticleProcessingWorker>()
            .setInputData(workDataOf(ArticleProcessingWorker.KEY_MODE to ArticleProcessingWorker.MODE_RESUME))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "article-processing-resume",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun markSavePending(normalizedUrl: String) {
        pendingPrefs().edit().putLong(normalizedUrl, System.currentTimeMillis()).apply()
    }

    private fun pendingPrefs() = context.getSharedPreferences("article_processing_pending", Context.MODE_PRIVATE)

    private fun saveWorkName(normalizedUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(normalizedUrl.toByteArray())
        return "article-save-${digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }}"
    }

    private companion object {
        const val SAVE_PENDING_TTL_MS = 10 * 60 * 1000L
    }
}

internal fun buildArticleSaveWorkRequest(url: String, normalizedUrl: String): OneTimeWorkRequest {
    return OneTimeWorkRequestBuilder<ArticleProcessingWorker>()
        .setInputData(
            workDataOf(
                ArticleProcessingWorker.KEY_MODE to ArticleProcessingWorker.MODE_SAVE,
                ArticleProcessingWorker.KEY_URL to url,
                ArticleProcessingWorker.KEY_NORMALIZED_URL to normalizedUrl,
            ),
        )
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()
}

class ArticleProcessingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo()

    override suspend fun doWork(): Result {
        val parser = GlobalContext.get().get<WebpageParserService>()
        return try {
            when (inputData.getString(KEY_MODE)) {
                MODE_SAVE -> {
                    val url = inputData.getString(KEY_URL).orEmpty()
                    if (normalizeArticleUrl(url).isBlank()) Result.failure() else {
                        try {
                            parser.saveWebpage(url = url, comment = null, title = null, tags = null)
                            clearPendingSave()
                            Result.success()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (runAttemptCount < MAX_SAVE_ATTEMPTS - 1) {
                                Result.retry()
                            } else {
                                clearPendingSave()
                                Result.failure()
                            }
                        }
                    }
                }
                MODE_RESUME -> {
                    parser.resumeInterruptedProcessing()
                    Result.success()
                }
                else -> Result.failure()
            }
        } catch (_: CancellationException) {
            Result.retry()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private fun clearPendingSave() {
        ArticleProcessingScheduler(applicationContext).clearSavePending(
            inputData.getString(KEY_NORMALIZED_URL).orEmpty(),
        )
    }

    private fun createForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Daily Satori")
            .setContentText("正在保存文章...")
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "文章处理",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_URL = "url"
        const val KEY_NORMALIZED_URL = "normalizedUrl"
        const val MODE_SAVE = "save"
        const val MODE_RESUME = "resume"
        private const val MAX_SAVE_ATTEMPTS = 3
        private const val CHANNEL_ID = "article_processing"
        private const val NOTIFICATION_ID = 1001
    }
}
