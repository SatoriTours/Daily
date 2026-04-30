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

    fun getByType(type: String): Flow<List<Memory_entry>> =
        q.selectMemoryByType(type).asFlow().mapToList(Dispatchers.IO)

    fun getBySource(sourceType: String, sourceId: Long): Memory_entry? =
        q.selectMemoryBySource(sourceType, sourceId).executeAsOneOrNull()

    fun search(query: String, limit: Long = 10): List<Memory_entry> =
        q.searchMemory(query, query, limit).executeAsList()

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
