package com.dailysatori.service.remotenews

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteArticleSyncRepository
import com.dailysatori.data.repository.needsLocalAiReprocessingForChineseOutput
import com.dailysatori.data.repository.sourceContentHash
import com.dailysatori.service.articleai.ArticleBodyKind
import com.dailysatori.service.articleai.analyzeArticleBody
import com.dailysatori.shared.db.Article
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
    private val articleAi: com.dailysatori.data.repository.ArticleAiRepository? = null,
    private val onNewBody: (com.dailysatori.shared.db.Article) -> Unit = {},
) {
    private enum class FeedAdmission { HISTORICAL, PASSIVE, INCREMENTAL, REFRESH }

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
                kotlinx.datetime.Clock.System.now().toEpochMilliseconds(), enqueueAi = true)
            check(result.failures.isEmpty()) { "文章保存失败" }
            return syncRepo.findByRemoteIdentity(remoteSourceId, downloaded.id)?.article_id?.let(articleRepo::getById)
                ?: error("文章保存失败")
        }
        val (saved, localArticle) = syncRepo.transaction {
            val saved = articleRepo.saveRemoteArticleForSync(downloaded) ?: error("文章保存失败")
            val row = articleRepo.getById(saved.article.id) ?: error("文章保存失败")
            if (row.source_type == "remote_news") articleAi?.record(row, downloaded)
            saved to row
        }
        if (saved.inserted) onNewBody(localArticle)
        return localArticle
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
    ): RemoteArticleSyncResult = store(remoteSourceId, sourceDate, articles, now, onArticleProgress, FeedAdmission.HISTORICAL)

    suspend fun syncSourceArticles(
        remoteSourceId: Long,
        sourceDate: String,
        articles: List<RemoteArticle>,
        now: Long,
        onArticleProgress: (suspend (RemoteArticleSyncProgress) -> Unit)? = null,
        enqueueAi: Boolean,
    ): RemoteArticleSyncResult = store(remoteSourceId, sourceDate, articles, now, onArticleProgress,
        if (enqueueAi) FeedAdmission.INCREMENTAL else FeedAdmission.HISTORICAL)

    /** Lists can land data early without claiming the feed cursor or authorizing AI. */
    suspend fun storePassively(remoteSourceId: Long, sourceDate: String, articles: List<RemoteArticle>, now: Long) =
        store(remoteSourceId, sourceDate, articles, now, null, FeedAdmission.PASSIVE)

    /** Refresh already mapped entries below the boundary; never import a new historical row here. */
    internal suspend fun refreshMapped(
        remoteSourceId: Long, sourceDate: String, articles: List<RemoteArticle>, now: Long,
    ) = store(remoteSourceId, sourceDate, articles, now, null, FeedAdmission.REFRESH)

    private suspend fun store(
        remoteSourceId: Long,
        sourceDate: String,
        articles: List<RemoteArticle>,
        now: Long,
        onArticleProgress: (suspend (RemoteArticleSyncProgress) -> Unit)?,
        admission: FeedAdmission,
    ): RemoteArticleSyncResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        val failures = mutableListOf<String>()

        articles.forEachIndexed { index, article ->
            // Cancellation is honoured at the article boundary, before this article starts a transaction.
            currentCoroutineContext().ensureActive()
            try {
                val (outcome, row) = syncRepo.transaction { saveArticle(remoteSourceId, sourceDate, article, now, admission) }
                if (row != null) onNewBody(row)
                when (outcome) {
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
        admission: FeedAdmission,
    ): Pair<SaveOutcome, Article?> {
        val existingMapping = syncRepo.findByRemoteIdentity(remoteSourceId, article.id)
            ?: article.url?.trim()?.takeIf { it.isNotBlank() }?.let { syncRepo.findByUrl(remoteSourceId, it) }
        if (admission == FeedAdmission.REFRESH && existingMapping == null) return SaveOutcome.SKIPPED to null
        val nextAdmission = existingMapping?.ai_feed_admission?.takeUnless { it == "passive" }
            ?: when (admission) {
                FeedAdmission.REFRESH, FeedAdmission.HISTORICAL -> "historical"
                FeedAdmission.PASSIVE -> "passive"
                FeedAdmission.INCREMENTAL -> "incremental"
            }
        val candidateKind = analyzeArticleBody(article.content, article.bodyKind).kind
        val sourceHash = article.sourceContentHash()
        val existingArticle = existingMapping?.article_id?.let(articleRepo::getById)
        if (admission == FeedAdmission.REFRESH && existingMapping?.ai_feed_admission == nextAdmission &&
            existingMapping.source_content_hash == sourceHash &&
            existingArticle != null && (articleAi == null || articleAi.input(existingArticle).kind == candidateKind)) {
            articleAi?.record(existingArticle, article)
            return SaveOutcome.SKIPPED to existingArticle.takeIf { nextAdmission == "incremental" && candidateKind == ArticleBodyKind.BODY }
        }
        val preserveAiContent = existingMapping?.let { mapping ->
            mapping.processed_content_hash.isNotBlank() ||
                articleRepo.getById(mapping.article_id)?.is_favorite == 1L
        } == true
        val saved = articleRepo.saveRemoteArticleForSync(
            remoteArticle = article,
            existingArticleId = existingMapping?.article_id,
            preserveAiContent = preserveAiContent,
            preserveOriginal = existingMapping?.article_id?.let { articleAi?.keepVerifiedOriginal(it) } == true &&
                candidateKind != ArticleBodyKind.BODY,
        ) ?: return SaveOutcome.SKIPPED to null
        val row = articleRepo.getById(saved.article.id)?.takeIf { it.source_type == "remote_news" }
        if (row != null) articleAi?.record(row, article)
        syncRepo.upsertMapping(
            remoteSourceId = remoteSourceId,
            remoteArticleId = article.id,
            articleId = saved.article.id,
            url = article.url,
            sourceDate = if (admission == FeedAdmission.REFRESH) requireNotNull(existingMapping).source_date else sourceDate,
            now = now,
            aiAdmission = nextAdmission,
        )
        syncRepo.recordSourceSnapshot(
            articleId = saved.article.id,
            sourceHash = sourceHash,
            requiresProcessing = article.needsLocalAiReprocessingForChineseOutput(),
            hasBody = !article.content.isNullOrBlank(),
        )
        val outcome = when {
            saved.inserted -> SaveOutcome.INSERTED
            saved.updated -> SaveOutcome.UPDATED
            else -> SaveOutcome.SKIPPED
        }
        val automatic = nextAdmission == "incremental" && admission in setOf(FeedAdmission.INCREMENTAL, FeedAdmission.REFRESH)
        return outcome to row.takeIf { automatic && candidateKind == ArticleBodyKind.BODY }
    }
}
