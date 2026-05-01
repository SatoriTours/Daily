package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
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
        return q.selectArticles().executeAsList().maxOfOrNull { it.id } ?: 0L
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

    fun delete(id: Long) = q.deleteArticle(id)

    fun toggleFavorite(id: Long) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.toggleFavorite(now, id)
    }

    fun count(): Long = q.articleCount().executeAsOne()

    fun getAllSync(): List<Article> = q.selectArticles().executeAsList()

    fun searchSync(query: String): List<Article> = q.searchArticles(query, query, query).executeAsList()

    fun getFavoritesSync(): List<Article> = q.selectFavoriteArticles().executeAsList()

    fun getLatestSync(limit: Int = 5): List<Article> =
        q.selectArticlesPaginated(limit.toLong(), 0).executeAsList()
}
