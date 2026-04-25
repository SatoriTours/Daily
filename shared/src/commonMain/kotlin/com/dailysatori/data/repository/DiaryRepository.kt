package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Diary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class DiaryRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Diary>> =
        q.selectAllDiaries().asFlow().mapToList(Dispatchers.IO)

    fun getPaginated(limit: Long, offset: Long): Flow<List<Diary>> =
        q.selectDiariesPaginated(limit, offset).asFlow().mapToList(Dispatchers.IO)

    fun getById(id: Long) = q.selectDiaryById(id).executeAsOneOrNull()

    fun search(query: String): Flow<List<Diary>> =
        q.searchDiaries(query, query).asFlow().mapToList(Dispatchers.IO)

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

    fun delete(id: Long) = q.deleteDiary(id)

    fun count(): Long = q.diaryCount().executeAsOne()
}
