package com.dailysatori.service.remotenews

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.needsLocalAiReprocessingForChineseOutput
import com.dailysatori.data.repository.sourceContentHash
import kotlinx.coroutines.CancellationException

data class RemoteArticleSyncResult(
    val inserted: Int,
    val updated: Int,
    val skipped: Int,
    val failures: List<String> = emptyList(),
)

class RemoteArticleSyncService(
    private val articleRepo: ArticleRepository,
    private val syncRepo: RemoteArticleSyncRepository,
) {
    fun syncSourceArticles(
        remoteSourceId: Long,
        sourceDate: String,
        articles: List<RemoteArticle>,
        now: Long,
    ): RemoteArticleSyncResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        val failures = mutableListOf<String>()

        articles.forEach { article ->
            try {
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
                )
                if (saved == null) {
                    skipped += 1
                    return@forEach
                }
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
                )
                if (saved.inserted) inserted += 1 else if (saved.updated) updated += 1 else skipped += 1
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                skipped += 1
                failures += "${article.id}: ${error.message.orEmpty().ifBlank { "save failed" }}"
            }
        }

        return RemoteArticleSyncResult(inserted = inserted, updated = updated, skipped = skipped, failures = failures)
    }
}
