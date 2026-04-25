package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class ImageRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getByArticle(articleId: Long): Flow<List<Image>> =
        q.selectImagesByArticle(articleId).asFlow().mapToList(Dispatchers.IO)

    fun insert(url: String?, path: String?, articleId: Long) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertImage(url, path, articleId, now, now)
    }

    fun deleteByArticle(articleId: Long) = q.deleteImagesByArticle(articleId)
}
