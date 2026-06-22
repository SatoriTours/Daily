package com.dailysatori.service.remotenews

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository

data class RemoteArticleSyncResult(
    val inserted: Int,
    val updated: Int,
    val skipped: Int,
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

        articles.forEach { article ->
            val existingMapping = syncRepo.findByRemoteIdentity(remoteSourceId, article.id)
                ?: article.url?.trim()?.takeIf { it.isNotBlank() }?.let { syncRepo.findByUrl(remoteSourceId, it) }
            val saved = articleRepo.saveRemoteArticleForSync(article, existingMapping?.article_id)
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
            if (saved.inserted) inserted += 1 else if (saved.updated) updated += 1 else skipped += 1
        }

        return RemoteArticleSyncResult(inserted = inserted, updated = updated, skipped = skipped)
    }
}
