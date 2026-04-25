package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class TagRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Tag>> =
        q.selectAllTags().asFlow().mapToList(Dispatchers.IO)

    fun getById(id: Long) = q.selectTagById(id).executeAsOneOrNull()

    fun getByName(name: String) = q.selectTagByName(name).executeAsOneOrNull()

    fun getByArticle(articleId: Long): Flow<List<Tag>> =
        q.getTagsByArticle(articleId).asFlow().mapToList(Dispatchers.IO)

    fun insert(name: String, icon: String? = null) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertTag(name, icon, now, now)
    }

    fun delete(id: Long) = q.deleteTag(id)

    fun addArticleTag(articleId: Long, tagId: Long) =
        q.insertArticleTag(articleId, tagId)

    fun removeArticleTags(articleId: Long) =
        q.deleteArticleTags(articleId)

    fun setTagsForArticle(articleId: Long, tagNames: List<String>) {
        removeArticleTags(articleId)
        tagNames.forEach { name ->
            val tag = getByName(name) ?: run { insert(name); getByName(name) }
            tag?.let { addArticleTag(articleId, it.id) }
        }
    }
}
