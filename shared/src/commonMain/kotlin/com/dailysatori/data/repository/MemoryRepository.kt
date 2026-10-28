package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.Memory_entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val db: DailySatoriDatabase) {
    private val q get() = db.dailySatoriQueries

    data class AutomaticWrite(val version:String,val existing:Memory_entry?)
    data class DiarySource(val rootId:Long,val content:String)
    fun diarySource(diaryId:Long):DiarySource? = q.transactionWithResult {
        val diary=q.selectDiaryById(diaryId).executeAsOneOrNull() ?: return@transactionWithResult null
        val rootId=diary.parent_diary_id ?: diary.id
        source("diary",rootId)?.let { DiarySource(rootId,it.second) }
    }
    fun automaticSnapshot(sourceType:String,sourceId:Long,title:String,content:String):AutomaticWrite? = q.transactionWithResult {
        val actual=source(sourceType,sourceId) ?: return@transactionWithResult null
        if(actual!=title to content || excluded(sourceType,sourceId)) return@transactionWithResult null
        val rows=q.selectMemoryBySource(sourceType,sourceId).executeAsList()
        if(rows.size>1 || rows.any { it.content_origin!="automatic" }) return@transactionWithResult null
        AutomaticWrite(stamp(actual),rows.singleOrNull())
    }
    fun saveAutomatic(sourceType:String,sourceId:Long,title:String,content:String,snapshot:AutomaticWrite):Boolean = q.transactionWithResult {
        val actual=source(sourceType,sourceId) ?: return@transactionWithResult false
        if(title!=actual.first || stamp(actual)!=snapshot.version || excluded(sourceType,sourceId)) return@transactionWithResult false
        val rows=q.selectMemoryBySource(sourceType,sourceId).executeAsList()
        if(rows.size>1 || rows.singleOrNull()!=snapshot.existing || rows.any { it.content_origin!="automatic" }) return@transactionWithResult false
        val now=kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        if(snapshot.existing==null) q.insertAutomaticMemory(sourceType,sourceId,title,content,now,now,snapshot.version)
        else q.updateAutomaticMemory(title,content,now,snapshot.version,snapshot.existing.id)
        true
    }
    private fun excluded(type:String,id:Long)=q.selectMemorySourceExclusion(type,id.toString()).executeAsOne()>0
    private fun stamp(source:Pair<String,String>)=com.dailysatori.service.externalfavorites.sha256Hex("${source.first.length}:${source.first}${source.second.length}:${source.second}")
    private fun source(type:String,id:Long):Pair<String,String>? = when(type) {
        "article"->q.selectArticleById(id).executeAsOneOrNull()?.let { (it.ai_title ?: it.title ?: "未命名") to it.ai_markdown_content.orEmpty() }
        "book"->q.selectBookById(id).executeAsOneOrNull()?.let { it.title to it.introduction }
        "book_viewpoint"->q.selectViewpointById(id).executeAsOneOrNull()?.let { it.title to it.content }
        "diary"->q.selectDiaryById(id).executeAsOneOrNull()?.takeIf { it.parent_diary_id==null }?.let { root ->
            val entries=listOf(root)+q.selectDiaryRepliesForRoot(id).executeAsList()
            val parts=listOf(com.dailysatori.service.diary.renderDiaryThreadContent(entries))+
                q.selectDiaryThreadAttachments(id).executeAsList().filter { it.transcript_status==DiaryAttachmentProcessingStatus.completed && it.transcript.isNotBlank() }.map { it.transcript }
            "日记" to parts.filter { it.isNotBlank() }.joinToString("\n\n")
        }
        else->null
    }

    fun getByType(type: String): Flow<List<Memory_entry>> =
        q.selectMemoryByType(type).asFlow().mapToList(Dispatchers.IO)

    fun getBySource(sourceType: String, sourceId: Long): Memory_entry? =
        q.selectMemoryBySource(sourceType, sourceId).executeAsOneOrNull()

    fun search(query: String, limit: Long = 10): List<Memory_entry> =
        if (query.isBlank()) {
            q.searchMemory(query, query, limit).executeAsList()
        } else {
            q.searchMemoryFts(query.toFtsPhraseQuery(), query, limit).executeAsList()
        }

    fun getAllSync(): List<Memory_entry> =
        q.selectAllMemory().executeAsList()

    fun countByType(type: String): Long =
        q.memoryCountByType(type).executeAsOne()

    fun insert(
        type: String,
        sourceType: String?,
        sourceId: Long?,
        title: String,
        content: String,
        tags: String? = null,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.insertMemory(type, sourceType, sourceId, title, content, tags, now, now)
    }

    fun update(id: Long, title: String, content: String, tags: String?) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        q.updateMemory(title, content, tags, now, id)
    }

    fun delete(id: Long) = q.deleteMemory(id)

    fun deleteBySource(sourceType: String, sourceId: Long) =
        q.deleteMemoryBySource(sourceType, sourceId)

    fun deleteAllByType(type: String) =
        q.deleteAllMemoryByType(type)
}
