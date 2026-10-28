package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.service.diary.isRemoteDiaryImage
import com.dailysatori.service.diary.preferredDiaryImagePath
import com.dailysatori.service.diary.encodeDiaryImagePaths
import com.dailysatori.service.diary.parseDiaryImagePaths
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Diary_record as Diary
import com.dailysatori.shared.db.Diary_attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.Clock

/** images is an API projection only. The retired database column is never read here. */
internal class DiaryImages(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    fun project(diary: Diary): Diary = project(diary,q.selectAttachmentsForDiary(diary.id).executeAsList())
    fun project(diary: Diary,attachments: List<Diary_attachment>): Diary =
        diary.copy(tags=DiaryTagStore(db).tags(diary.id).joinToString(",").ifBlank { null },images=encodeDiaryImagePaths(attachments.filter { it.diary_id==diary.id && it.kind=="image" }
            .map { preferredDiaryImagePath(it.local_path,it.remote_url) }))

    fun project(rows:List<Diary>):List<Diary> {
        val images=q.selectDiaryImageReferences().executeAsList().groupBy { it.diary_id }
        val tags=DiaryTagStore(db).tagsByDiary()
        return rows.map { diary -> diary.copy(tags=tags[diary.id]?.joinToString(","),images=encodeDiaryImagePaths(images[diary.id].orEmpty()
            .map { preferredDiaryImagePath(it.local_path,it.remote_url) })) }
    }

    fun observe(rows:Flow<List<Diary>>):Flow<List<Diary>> =
        combine(rows,q.selectDiaryImageReferences().asFlow().mapToList(Dispatchers.IO),DiaryTagStore(db).signal()) { diaries,refs,_ ->
            val images=refs.groupBy { it.diary_id }
            val tags=DiaryTagStore(db).tagsByDiary()
            diaries.map { diary -> diary.copy(tags=tags[diary.id]?.joinToString(","),images=encodeDiaryImagePaths(images[diary.id].orEmpty()
                .map { preferredDiaryImagePath(it.local_path,it.remote_url) })) }
        }

    /** Import initializes a new association set without turning historical timestamps into edits. */
    fun initializeImported(id:Long,value:String?) = q.transaction {
        check(q.selectAttachmentsForDiary(id).executeAsList().isEmpty()) { "Imported attachments already initialized" }
        val diary=q.selectDiaryById(id).executeAsOne()
        parseDiaryImagePaths(value).forEachIndexed { index,path ->
            q.insertDiaryImage(id,if(isRemoteDiaryImage(path)) "" else path,path.takeIf(::isRemoteDiaryImage),
                index.toLong(),diary.created_at,diary.updated_at)
        }
    }

    /** Match duplicate occurrences independently; a reorder never recreates metadata or IDs. */
    fun replace(id:Long,value:String?):List<String> = q.transactionWithResult {
        val desired=parseDiaryImagePaths(value)
        val attachments=q.selectAttachmentsForDiary(id).executeAsList()
        val available=attachments.filter { it.kind=="image" }.toMutableList()
        val previous=available.map { preferredDiaryImagePath(it.local_path,it.remote_url) }
        if(previous==desired) return@transactionWithResult emptyList()
        val diary=q.selectDiaryById(id).executeAsOne()
        val now=Clock.System.now().toEpochMilliseconds()
        desired.forEachIndexed { index,path ->
            val old=available.firstOrNull { preferredDiaryImagePath(it.local_path,it.remote_url)==path }
            if(old==null) q.insertDiaryImage(id,if(isRemoteDiaryImage(path)) "" else path,
                path.takeIf(::isRemoteDiaryImage),index.toLong(),diary.created_at,now)
            else { available.remove(old); q.setDiaryAttachmentPosition(index.toLong(),old.id) }
        }
        attachments.filter { it.kind!="image" }.forEachIndexed { index,attachment ->
            q.setDiaryAttachmentPosition(desired.size.toLong()+index,attachment.id)
        }
        available.forEach { q.deleteDiaryAttachmentById(it.id) }
        changed(id,now)
        available.map { it.local_path }
    }

    fun changed(id:Long,now:Long=Clock.System.now().toEpochMilliseconds()) {
        q.advanceDiaryImageRevision(now,id)
        q.touchDiaryImageRecords(now,id,id)
    }
}
