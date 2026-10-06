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
import kotlinx.coroutines.flow.combine
import com.dailysatori.service.diary.DiaryTagVocabulary
import com.dailysatori.service.diary.matchesDiaryTag
import kotlinx.serialization.json.Json

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

    fun search(query: String): Flow<List<Diary>> {
        val matches = if (query.isBlank()) {
            q.searchDiaries(query, query).asFlow().mapToList(Dispatchers.IO)
        } else {
            q.searchDiariesFts(query.toFtsPhraseQuery(), query).asFlow().mapToList(Dispatchers.IO)
        }
        if (query.isBlank()) return matches
        return combine(matches, getAll(), q.selectAllSettings().asFlow()) { found, all, _ ->
            includeTagAliases(found, all, query)
        }
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

    fun searchSync(query: String): List<Diary> {
        val matches = if (query.isBlank()) {
            q.searchDiaries(query, query).executeAsList()
        } else {
            q.searchDiariesFts(query.toFtsPhraseQuery(), query).executeAsList()
        }
        return if (query.isBlank()) matches else includeTagAliases(matches, getAllSync(), query)
    }

    private fun includeTagAliases(found: List<Diary>, all: List<Diary>, query: String): List<Diary> {
        val stored = q.selectSettingByKey("diary_tag_vocabulary_v1").executeAsOneOrNull()?.value_
        val vocabulary = stored?.let { runCatching { Json.decodeFromString<DiaryTagVocabulary>(it) }.getOrNull() }
            ?: DiaryTagVocabulary()
        return (found + all.filter { matchesDiaryTag(it.tags, query.trim().removePrefix("#"), vocabulary) })
            .distinctBy { it.id }.sortedByDescending { it.created_at }
    }

    fun getByDateRangeSync(startMs: Long, endMs: Long): List<Diary> =
        q.selectDiariesByDateRange(startMs, endMs).executeAsList()

    fun getLatestSync(limit: Int = 5): List<Diary> =
        q.selectDiariesPaginated(limit.toLong(), 0).executeAsList()
}
