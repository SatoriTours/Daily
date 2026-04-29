package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.shared.db.Book_viewpoint
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class BookViewpointRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getByBook(bookId: Long): Flow<List<Book_viewpoint>> =
        q.selectViewpointsByBook(bookId).asFlow().mapToList(Dispatchers.IO)

    fun getAll(): Flow<List<Book_viewpoint>> =
        q.selectAllViewpoints().asFlow().mapToList(Dispatchers.IO)

    fun getById(id: Long) = q.selectViewpointById(id).executeAsOneOrNull()

    fun insert(bookId: Long, title: String, content: String, example: String) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertViewpoint(bookId, title, content, example, now, now)
    }

    fun update(id: Long, title: String, content: String, example: String) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateViewpoint(title, content, example, now, id)
    }

    fun delete(id: Long) = q.deleteViewpoint(id)

    fun deleteByBook(bookId: Long) = q.deleteViewpointsByBook(bookId)

    fun getAllSync(): List<Book_viewpoint> = q.selectAllViewpoints().executeAsList()

    fun getByBookSync(bookId: Long): List<Book_viewpoint> = q.selectViewpointsByBook(bookId).executeAsList()

    fun searchByContentSync(keyword: String): List<Book_viewpoint> {
        val kw = keyword.lowercase()
        return q.selectAllViewpoints().executeAsList().filter { vp ->
            vp.content.lowercase().contains(kw) || vp.title.lowercase().contains(kw)
        }
    }

    fun count(): Long = q.selectAllViewpoints().executeAsList().size.toLong()
}
