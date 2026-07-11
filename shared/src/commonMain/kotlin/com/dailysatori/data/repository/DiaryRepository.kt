package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.platform.FileManager
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Diary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class DiaryRepository(
    private val db: DailySatoriDatabase,
    private val driver: SqlDriver,
    private val fileManager: FileManager? = null,
) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Diary>> =
        q.selectAllDiaries().asFlow().mapToList(Dispatchers.IO)

    fun getPaginated(limit: Long, offset: Long): Flow<List<Diary>> =
        q.selectDiariesPaginated(limit, offset).asFlow().mapToList(Dispatchers.IO)

    fun getById(id: Long) = q.selectDiaryById(id).executeAsOneOrNull()

    fun search(query: String): Flow<List<Diary>> =
        if (query.isBlank()) {
            q.searchDiaries(query, query).asFlow().mapToList(Dispatchers.IO)
        } else {
            q.searchDiariesFts(query.toFtsPhraseQuery(), query).asFlow().mapToList(Dispatchers.IO)
        }

    fun getByDateRange(startMs: Long, endMs: Long): Flow<List<Diary>> =
        q.selectDiariesByDateRange(startMs, endMs).asFlow().mapToList(Dispatchers.IO)

    fun insert(
        content: String,
        tags: String? = null,
        mood: String? = null,
        images: String? = null,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertDiary(content, tags, mood, images, now, now)
    }

    suspend fun create(
        content: String,
        tags: String? = null,
        mood: String? = null,
        images: String? = null,
    ): Long = q.transactionWithResult {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertDiary(content, tags, mood, images, now, now)
        lastInsertRowId()
    }

    private fun lastInsertRowId(): Long =
        driver.executeQuery(0, "SELECT last_insert_rowid()", { cursor ->
            check(cursor.next().value) { "last_insert_rowid() returned no row" }
            QueryResult.Value(checkNotNull(cursor.getLong(0)) { "last_insert_rowid() was null" })
        }, 0).value

    fun update(
        id: Long,
        content: String,
        tags: String?,
        mood: String?,
        images: String?,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateDiary(content, tags, mood, images, now, id)
    }

    fun delete(id: Long) {
        val attachmentPaths = q.transactionWithResult {
            val paths = q.selectAttachmentsForDiary(id).executeAsList().map { it.local_path }
            q.deleteDiary(id)
            paths
        }
        attachmentPaths.forEach { path -> fileManager?.deleteAppOwnedFile(path) }
    }

    fun count(): Long = q.diaryCount().executeAsOne()

    fun getAllSync(): List<Diary> = q.selectAllDiaries().executeAsList()

    fun searchSync(query: String): List<Diary> =
        if (query.isBlank()) {
            q.searchDiaries(query, query).executeAsList()
        } else {
            q.searchDiariesFts(query.toFtsPhraseQuery(), query).executeAsList()
        }

    fun getByDateRangeSync(startMs: Long, endMs: Long): List<Diary> =
        q.selectDiariesByDateRange(startMs, endMs).executeAsList()

    fun getLatestSync(limit: Int = 5): List<Diary> =
        q.selectDiariesPaginated(limit.toLong(), 0).executeAsList()
}
