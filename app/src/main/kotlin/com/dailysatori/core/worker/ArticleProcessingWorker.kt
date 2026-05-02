package com.dailysatori.core.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dailysatori.normalizeArticleUrl
import com.dailysatori.service.parser.WebpageParserService
import org.koin.core.context.GlobalContext
import java.security.MessageDigest

class ArticleProcessingScheduler(private val context: Context) {
    fun enqueueSave(url: String) {
        val normalizedUrl = normalizeArticleUrl(url)
        if (normalizedUrl.isBlank()) return
        markSavePending(normalizedUrl)
        val request = OneTimeWorkRequestBuilder<ArticleProcessingWorker>()
            .setInputData(
                workDataOf(
                    ArticleProcessingWorker.KEY_MODE to ArticleProcessingWorker.MODE_SAVE,
                    ArticleProcessingWorker.KEY_URL to url,
                    ArticleProcessingWorker.KEY_NORMALIZED_URL to normalizedUrl,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            saveWorkName(normalizedUrl),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun isSavePending(url: String): Boolean {
        val normalizedUrl = normalizeArticleUrl(url)
        if (normalizedUrl.isBlank()) return false
        return pendingPrefs().getBoolean(normalizedUrl, false)
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
        pendingPrefs().edit().putBoolean(normalizedUrl, true).apply()
    }

    private fun pendingPrefs() = context.getSharedPreferences("article_processing_pending", Context.MODE_PRIVATE)

    private fun saveWorkName(normalizedUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(normalizedUrl.toByteArray())
        return "article-save-${digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }}"
    }
}

class ArticleProcessingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val parser = GlobalContext.get().get<WebpageParserService>()
        return try {
            when (inputData.getString(KEY_MODE)) {
                MODE_SAVE -> {
                    val url = inputData.getString(KEY_URL).orEmpty()
                    if (normalizeArticleUrl(url).isBlank()) Result.failure() else {
                        try {
                            parser.saveWebpage(url = url, comment = null, title = null, tags = null)
                        } finally {
                            ArticleProcessingScheduler(applicationContext).clearSavePending(
                                inputData.getString(KEY_NORMALIZED_URL).orEmpty(),
                            )
                        }
                        Result.success()
                    }
                }
                MODE_RESUME -> {
                    parser.resumeInterruptedProcessing()
                    Result.success()
                }
                else -> Result.failure()
            }
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_URL = "url"
        const val KEY_NORMALIZED_URL = "normalizedUrl"
        const val MODE_SAVE = "save"
        const val MODE_RESUME = "resume"
    }
}
