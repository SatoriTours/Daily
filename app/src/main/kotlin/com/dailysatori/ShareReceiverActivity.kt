package com.dailysatori

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.dailysatori.core.worker.ArticleProcessingScheduler
import com.dailysatori.data.repository.ArticleRepository
import org.koin.android.ext.android.inject

class ShareReceiverActivity : Activity() {
    private val articleRepo: ArticleRepository by inject()
    private val articleProcessingScheduler: ArticleProcessingScheduler by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
        finish()
    }

    private fun handleShareIntent(intent: Intent?) {
        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
        val url = extractFirstUrl(text)
        val message = when {
            url == null -> shareInvalidUrlToastMessage()
            retryExistingArticle(url) -> shareSaveStartedToastMessage()
            articleProcessingScheduler.isSavePending(url) -> duplicateUrlSnackbarMessage()
            articleUrlExists(url, articleRepo.getAllSync().mapNotNull { it.url }) -> duplicateUrlSnackbarMessage()
            else -> {
                articleProcessingScheduler.enqueueSave(url)
                shareSaveStartedToastMessage()
            }
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun retryExistingArticle(url: String): Boolean {
        val article = articleRepo.getAllSync()
            .firstOrNull { normalizeArticleUrl(it.url) == normalizeArticleUrl(url) }
            ?: return false
        if (!shouldRetryExistingSharedArticle(article.status)) return false
        articleProcessingScheduler.enqueueRetrySave(url)
        return true
    }
}
