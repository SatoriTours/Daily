package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.shared.db.Book
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class BookRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Book>> =
        q.selectAllBooks().asFlow().mapToList(Dispatchers.IO)

    fun getById(id: Long) = q.selectBookById(id).executeAsOneOrNull()

    fun search(query: String): Flow<List<Book>> =
        q.searchBooks(query, query).asFlow().mapToList(Dispatchers.IO)

    fun insert(
        title: String,
        author: String,
        category: String,
        coverImage: String,
        introduction: String,
        hasUpdate: Long = 0,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertBook(title, author, category, coverImage, introduction, hasUpdate, now, now)
    }

    fun update(
        id: Long,
        title: String,
        author: String,
        category: String,
        coverImage: String,
        introduction: String,
        hasUpdate: Long,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateBook(title, author, category, coverImage, introduction, hasUpdate, now, id)
    }

    fun delete(id: Long) = q.deleteBook(id)

    fun count(): Long = q.selectAllBooks().executeAsList().size.toLong()

    fun getAllSync(): List<Book> = q.selectAllBooks().executeAsList()

    fun searchSync(query: String): List<Book> = q.searchBooks(query, query).executeAsList()
}
