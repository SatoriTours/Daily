package com.dailysatori.data.repository

import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Remote_article_sync_item

class RemoteArticleSyncRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun findByRemoteIdentity(remoteSourceId: Long, remoteArticleId: Long): Remote_article_sync_item? =
        q.selectRemoteArticleSyncItemByRemoteIdentity(remoteSourceId, remoteArticleId).executeAsOneOrNull()

    fun findByUrl(remoteSourceId: Long, url: String): Remote_article_sync_item? =
        q.selectRemoteArticleSyncItemByUrl(remoteSourceId, url).executeAsOneOrNull()

    fun getArticlesBySourceDate(remoteSourceId: Long, sourceDate: String): List<Article> =
        q.selectRemoteArticleSyncItemsBySourceDate(remoteSourceId, sourceDate).executeAsList()

    fun count(): Long = q.countRemoteArticleSyncItems().executeAsOne()

    fun countBySource(remoteSourceId: Long): Long =
        q.countRemoteArticleSyncItemsBySource(remoteSourceId).executeAsOne()

    fun upsertMapping(
        remoteSourceId: Long,
        remoteArticleId: Long,
        articleId: Long,
        url: String?,
        sourceDate: String,
        now: Long,
    ) {
        val existing = findByRemoteIdentity(remoteSourceId, remoteArticleId)
        q.upsertRemoteArticleSyncItem(
            remote_source_id = remoteSourceId,
            remote_article_id = remoteArticleId,
            article_id = articleId,
            url = cleanRemoteArticleText(url),
            source_date = sourceDate,
            first_seen_at = existing?.first_seen_at ?: now,
            last_seen_at = now,
        )
    }
}
