package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ArticleRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Article>> =
        q.selectArticles().asFlow().mapToList(Dispatchers.IO)

    fun getPaginated(limit: Long, offset: Long): Flow<List<Article>> =
        q.selectArticlesPaginated(limit, offset).asFlow().mapToList(Dispatchers.IO)

    fun getByStatus(status: String): Flow<List<Article>> =
        q.selectArticlesByStatus(status).asFlow().mapToList(Dispatchers.IO)

    fun getRecoverableForProcessingSync(): List<Article> =
        q.selectRecoverableArticles().executeAsList()

    fun getByTag(tagId: Long): Flow<List<Article>> =
        q.selectArticlesByTag(tagId).asFlow().mapToList(Dispatchers.IO)

    fun search(query: String): Flow<List<Article>> =
        q.searchArticles(query, query, query).asFlow().mapToList(Dispatchers.IO)

    fun getByDateRange(startMs: Long, endMs: Long): Flow<List<Article>> =
        q.selectArticlesByDateRange(startMs, endMs).asFlow().mapToList(Dispatchers.IO)

    fun getFavorites(): Flow<List<Article>> =
        q.selectFavoriteArticles().asFlow().mapToList(Dispatchers.IO)

    fun getDailyCounts(): Flow<Map<Long, Long>> =
        q.selectArticleDailyCounts().asFlow().mapToList(Dispatchers.IO)
            .map { list -> list.associate { it.article_day to it.article_count } }

    fun getById(id: Long) = q.selectArticleById(id).executeAsOneOrNull()

    fun getByUrl(url: String): Article? = q.selectArticleByUrlNullable(url).executeAsOneOrNull()

    fun insert(
        title: String? = null,
        aiTitle: String? = null,
        aiContent: String? = null,
        aiMarkdownContent: String? = null,
        url: String? = null,
        isFavorite: Long = 0,
        comment: String? = null,
        status: String = "pending",
        coverImage: String? = null,
        coverImageUrl: String? = null,
        pubDate: Long? = null,
    ): Long {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertArticle(
            title, aiTitle, aiContent, aiMarkdownContent,
            url, isFavorite, comment, status, coverImage, coverImageUrl, pubDate, now, now,
        )
        return q.selectArticleByUrl(url ?: error("Article URL required for insert")).executeAsOne().id
    }

    fun update(
        id: Long,
        title: String?,
        aiTitle: String?,
        aiContent: String?,
        aiMarkdownContent: String?,
        url: String?,
        isFavorite: Long,
        comment: String?,
        status: String,
        coverImage: String?,
        coverImageUrl: String?,
        pubDate: Long?,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateArticle(
            title, aiTitle, aiContent, aiMarkdownContent,
            url, isFavorite, comment, status, coverImage, coverImageUrl, pubDate, now, id,
        )
    }

    fun updateAiTitle(id: Long, aiTitle: String?) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateArticleAiTitle(aiTitle, now, id)
    }

    fun updateAiContent(id: Long, aiContent: String?, aiTitle: String?, coverImageUrl: String?) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateArticleAiContent(aiContent, aiTitle, coverImageUrl, now, id)
    }

    fun updateAiMarkdownContent(id: Long, aiMarkdownContent: String?) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateArticleAiMarkdownContent(aiMarkdownContent, now, id)
    }

    fun updateStatus(id: Long, status: String) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateArticleStatus(status, now, id)
    }

    fun updateProcessingCompletion(id: Long, status: String, coverImage: String?, coverImageUrl: String?) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateArticleProcessingCompletion(status, coverImage, coverImageUrl, now, id)
    }

    fun delete(id: Long) = q.deleteArticle(id)

    fun toggleFavorite(id: Long) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.toggleFavorite(now, id)
    }

    fun count(): Long = q.articleCount().executeAsOne()

    fun getAllSync(): List<Article> = q.selectArticles().executeAsList()

    fun searchSync(query: String): List<Article> = q.searchArticles(query, query, query).executeAsList()

    fun searchFavoriteFirstSync(query: String): List<Article> =
        q.searchArticlesFavoriteFirst(query, query, query).executeAsList()

    fun getFavoritesSync(): List<Article> = q.selectFavoriteArticles().executeAsList()

    fun getFavoritesByDateRangeSync(startMs: Long, endMs: Long): List<Article> =
        q.selectFavoriteArticlesByDateRange(startMs, endMs).executeAsList()

    fun getLatestSync(limit: Int = 5): List<Article> =
        q.selectArticlesPaginated(limit.toLong(), 0).executeAsList()

    fun saveRemoteArticleAsFavorite(remoteArticle: RemoteArticle): Article? {
        val fields = remoteArticle.toLocalFavoriteArticleFields()
        val url = fields.url
        if (url.isNullOrBlank()) return insertRemoteArticleFavoriteWithoutUrl(fields)

        val existing = q.selectArticleByUrlNullable(url).executeAsOneOrNull()
        return if (existing == null) {
            val id = insert(
                title = fields.title,
                aiTitle = fields.aiTitle,
                aiContent = fields.aiContent,
                aiMarkdownContent = fields.aiMarkdownContent,
                url = url,
                isFavorite = fields.isFavorite,
                comment = fields.comment,
                status = fields.status,
                coverImage = fields.coverImage,
                coverImageUrl = fields.coverImageUrl,
                pubDate = fields.pubDate,
            )
            getById(id)
        } else {
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            q.markArticleFavoriteByUrl(
                fields.title,
                fields.aiTitle,
                fields.aiContent,
                fields.aiMarkdownContent,
                fields.status,
                fields.coverImageUrl,
                fields.pubDate,
                now,
                url,
            )
            q.selectArticleByUrlNullable(url).executeAsOneOrNull()
        }
    }

    private fun insertRemoteArticleFavoriteWithoutUrl(fields: LocalFavoriteArticleFields): Article? {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertArticle(
            fields.title,
            fields.aiTitle,
            fields.aiContent,
            fields.aiMarkdownContent,
            null,
            fields.isFavorite,
            fields.comment,
            fields.status,
            fields.coverImage,
            fields.coverImageUrl,
            fields.pubDate,
            now,
            now,
        )
        return q.selectArticlesPaginated(1, 0).executeAsOneOrNull()
    }
}
