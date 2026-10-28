package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.platform.FileManager
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Diary_record as Diary
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
        DiaryImages(db).observe(q.selectDiaryRoots().asFlow().mapToList(Dispatchers.IO))

    fun getPaginated(limit: Long, offset: Long): Flow<List<Diary>> =
        DiaryImages(db).observe(q.selectDiaryRootsPaginated(limit, offset).asFlow().mapToList(Dispatchers.IO))

    fun getById(id: Long) = q.selectDiaryById(id).executeAsOneOrNull()?.let(DiaryImages(db)::project)

    fun search(query: String): Flow<List<Diary>> {
        val matches = if (query.isBlank()) {
            q.searchDiaries(query).asFlow().mapToList(Dispatchers.IO)
        } else {
            q.searchDiariesFts(query.toFtsPhraseQuery(), query).asFlow().mapToList(Dispatchers.IO)
        }
        if (query.isBlank()) return DiaryImages(db).observe(matches)
        return combine(DiaryImages(db).observe(matches), getAll(), DiaryTagStore(db).signal()) { found, all, _ ->
            includeTagAliases(found, all, query)
        }
    }

    fun getByDateRange(startMs: Long, endMs: Long): Flow<List<Diary>> =
        DiaryImages(db).observe(q.selectDiaryRootsByDateRange(startMs, endMs).asFlow().mapToList(Dispatchers.IO))

    fun insert(
        content: String,
        tags: String? = null,
        mood: String? = null,
        images: String? = null,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.transaction {
            q.insertDiary(content, mood, now, now)
            val id=lastInsertRowId()
            DiaryTagStore(db).initializeImported(id,com.dailysatori.service.diary.parseDiaryTags(tags))
            DiaryImages(db).replace(id, images)
        }
    }

    suspend fun create(
        content: String,
        tags: String? = null,
        mood: String? = null,
        images: String? = null,
    ): Long = q.transactionWithResult {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertDiary(content, mood, now, now)
        lastInsertRowId().also {
            DiaryTagStore(db).initializeImported(it,com.dailysatori.service.diary.parseDiaryTags(tags))
            DiaryImages(db).replace(it, images)
        }
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
        val removed = q.transactionWithResult {
            val diary=q.selectDiaryById(id).executeAsOneOrNull() ?: return@transactionWithResult emptyList<String>()
            val store=DiaryTagStore(db)
            val desired=com.dailysatori.service.diary.parseDiaryTags(tags)
            if(desired!=store.tags(id)) {
                val state=store.state(id)
                store.save(id,desired,state.copy(automatic=state.automatic.filter { it in desired },
                    suppressed=(state.suppressed+state.automatic.filterNot { it in desired }).distinct().filterNot { it in desired },
                    revision=state.revision+1))
            }
            q.updateDiary(content, mood, now, id)
            DiaryImages(db).replace(id, images)
        }
        deleteUnreferencedMedia(db, fileManager, removed)
    }

    /** Compare and save in one transaction so an AI request cannot overwrite editor changes. */
    fun prependGeneratedTitleIfUnchanged(expected: Diary, title: String): Boolean = q.transactionWithResult {
        val current = q.selectDiaryById(expected.id).executeAsOneOrNull() ?: return@transactionWithResult false
        if (current.content != expected.content || current.updated_at != expected.updated_at) {
            return@transactionWithResult false
        }
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateDiary("# $title\n\n${current.content}", current.mood, now, current.id)
        true
    }

    /**
     * 删除整串：主记录、全部续写、附件、图片与整理历史键一起收集，事务成功后才清理应用拥有的文件。
     * 子记录与附件在事务内显式删除，不依赖外键开关；任何失败都不会提前删除文件。
     */
    fun delete(id: Long) {
        val attachmentPaths = q.transactionWithResult {
            val root = q.selectDiaryById(id).executeAsOneOrNull()
            val replies = q.selectDiaryRepliesForRoot(id).executeAsList()
            val attachments = (listOfNotNull(root) + replies).flatMap { record ->
                q.selectAttachmentsForDiary(record.id).executeAsList()
            }
            val settings = SettingRepository(db)
            attachments.forEach { attachment ->
                settings.delete("diary_polished_transcript_v1:${attachment.id}")
                q.deleteDiaryAttachmentById(attachment.id)
            }
            val paths = attachments.map { it.local_path }
            (replies.map { it.id }+id).forEach { DiaryTagStore(db).deleteDomain(it) }
            replies.forEach { reply -> q.deleteDiary(reply.id) }
            q.deleteDiary(id)
            paths
        }
        deleteUnreferencedMedia(db, fileManager, attachmentPaths)
    }

    fun count(): Long = q.diaryRootCount().executeAsOne()

    fun getAllSync(): List<Diary> = DiaryImages(db).project(q.selectDiaryRoots().executeAsList())

    fun searchSync(query: String): List<Diary> {
        val matches = if (query.isBlank()) {
            q.searchDiaries(query).executeAsList()
        } else {
            q.searchDiariesFts(query.toFtsPhraseQuery(), query).executeAsList()
        }
        return if (query.isBlank()) DiaryImages(db).project(matches) else includeTagAliases(DiaryImages(db).project(matches), getAllSync(), query)
    }

    private fun includeTagAliases(found: List<Diary>, all: List<Diary>, query: String): List<Diary> {
        val vocabulary = DiaryTagStore(db).vocabulary()
        return (found + all.filter { matchesDiaryTag(it.tags, query.trim().removePrefix("#"), vocabulary) })
            .distinctBy { it.id }.sortedByDescending { it.created_at }
    }

    fun getByDateRangeSync(startMs: Long, endMs: Long): List<Diary> =
        DiaryImages(db).project(q.selectDiaryRootsByDateRange(startMs, endMs).executeAsList())

    fun getLatestSync(limit: Int = 5): List<Diary> =
        DiaryImages(db).project(q.selectDiaryRootsPaginated(limit.toLong(), 0).executeAsList())
}
