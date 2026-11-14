package com.dailysatori.service.remotenews

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.needsLocalAiReprocessingForChineseOutput
import com.dailysatori.data.repository.sourceContentHash
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class RemoteArticleSyncResult(
    val inserted: Int,
    val updated: Int,
    val skipped: Int,
    val failures: List<String> = emptyList(),
)

/** Article-level progress of one source; [processed] counts articles whose transaction already committed. */
data class RemoteArticleSyncProgress(
    val processed: Int,
    val total: Int,
    val inserted: Int,
    val updated: Int,
    val skipped: Int,
)

class RemoteArticleSyncService(
    private val articleRepo: ArticleRepository,
    private val syncRepo: RemoteArticleSyncRepository,
) {
    /** Reading never depends on the network once an article has been saved. */
    suspend fun readLocalArticle(
        remoteSourceId: Long?,
        remoteArticleId: Long,
        sourceDate: String,
        url: String? = null,
        download: suspend () -> RemoteArticle,
    ): com.dailysatori.shared.db.Article {
        val mapped = remoteSourceId?.let { syncRepo.findByRemoteIdentity(it, remoteArticleId) }
        val local = mapped?.article_id?.let(articleRepo::getById) ?: url?.let(articleRepo::getByUrl)
        if (local != null) return local
        val downloaded = download()
        if (remoteSourceId != null) {
            val result = syncSourceArticles(remoteSourceId, sourceDate, listOf(downloaded),
                kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
            check(result.failures.isEmpty()) { "文章保存失败" }
            return syncRepo.findByRemoteIdentity(remoteSourceId, downloaded.id)?.article_id?.let(articleRepo::getById)
                ?: error("文章保存失败")
        }
        val saved = articleRepo.saveRemoteArticleForSync(downloaded) ?: error("文章保存失败")
        return articleRepo.getById(saved.article.id) ?: error("文章保存失败")
    }

    /**
     * Saves a source article by article. Each article commits its own transaction (article + mapping +
     * source snapshot), so a later failure or cancellation can never roll back an article that already
     * landed, and a failed article leaves no half-written state behind.
     */
    suspend fun syncSourceArticles(
        remoteSourceId: Long,
        sourceDate: String,
        articles: List<RemoteArticle>,
        now: Long,
        onArticleProgress: (suspend (RemoteArticleSyncProgress) -> Unit)? = null,
    ): RemoteArticleSyncResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        val failures = mutableListOf<String>()

        articles.forEachIndexed { index, article ->
            // Cancellation is honoured at the article boundary, before this article starts a transaction.
            currentCoroutineContext().ensureActive()
            try {
                when (syncRepo.transaction { saveArticle(remoteSourceId, sourceDate, article, now) }) {
                    SaveOutcome.INSERTED -> inserted += 1
                    SaveOutcome.UPDATED -> updated += 1
                    SaveOutcome.SKIPPED -> skipped += 1
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                skipped += 1
                failures += "${article.id}: ${error.message.orEmpty().ifBlank { "save failed" }}"
            }
            // Reported outside the transaction, so a slow reporter never holds a database transaction open.
            onArticleProgress?.invoke(
                RemoteArticleSyncProgress(index + 1, articles.size, inserted, updated, skipped),
            )
        }

        return RemoteArticleSyncResult(inserted = inserted, updated = updated, skipped = skipped, failures = failures)
    }

    private enum class SaveOutcome { INSERTED, UPDATED, SKIPPED }

    private fun saveArticle(
        remoteSourceId: Long,
        sourceDate: String,
        article: RemoteArticle,
        now: Long,
    ): SaveOutcome {
        val existingMapping = syncRepo.findByRemoteIdentity(remoteSourceId, article.id)
            ?: article.url?.trim()?.takeIf { it.isNotBlank() }?.let { syncRepo.findByUrl(remoteSourceId, it) }
        val preserveAiContent = existingMapping?.let { mapping ->
            mapping.processed_content_hash.isNotBlank() ||
                articleRepo.getById(mapping.article_id)?.is_favorite == 1L
        } == true
        val saved = articleRepo.saveRemoteArticleForSync(
            remoteArticle = article,
            existingArticleId = existingMapping?.article_id,
            preserveAiContent = preserveAiContent,
        ) ?: return SaveOutcome.SKIPPED
        syncRepo.upsertMapping(
            remoteSourceId = remoteSourceId,
            remoteArticleId = article.id,
            articleId = saved.article.id,
            url = article.url,
            sourceDate = sourceDate,
            now = now,
        )
        syncRepo.recordSourceSnapshot(
            articleId = saved.article.id,
            sourceHash = article.sourceContentHash(),
            requiresProcessing = article.needsLocalAiReprocessingForChineseOutput(),
            hasBody = !article.content.isNullOrBlank(),
        )
        return when {
            saved.inserted -> SaveOutcome.INSERTED
            saved.updated -> SaveOutcome.UPDATED
            else -> SaveOutcome.SKIPPED
        }
    }
}
