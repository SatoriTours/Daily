package com.dailysatori.service.assistant

import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.memory.PersonalMemoryCandidate
import com.dailysatori.shared.db.DailySatoriDatabase

/** Incremental data extraction, not an Agent loop or a second scheduler. */
class AssistantMemoryIndexer(
    driver: SqlDriver, private val memory: AssistantMemoryService,
    private val extract: suspend (String) -> List<PersonalMemoryCandidate>,
) {
    private val tasks = AsyncTaskRepository(DailySatoriDatabase(driver))
    private val settings = SettingRepository(DailySatoriDatabase(driver))
    suspend fun process(sourceId: Long, reporter: AsyncTaskProgressReporter) {
        val lease = reporter.executionLease ?: error("Index execution lease required")
        if (tasks.withRunningLease(lease.taskId,lease.owner) { memory.index(sourceId); true } != true) return
        if (settings.get(PersistentAssistantConversation.CONSENT_KEY) != "true") return
        val pending = memory.pendingSegments(sourceId)
        val total = memory.segments(sourceId).size.toLong()
        var completed = total - pending.size
        for (segment in pending) {
            if (!tasks.hasRunningLease(lease.taskId,lease.owner) || settings.get(PersistentAssistantConversation.CONSENT_KEY) != "true") return
            if (memory.segments(sourceId).isEmpty()) return
            val result = extract(segment.content)
            if (!memory.saveIndexed(sourceId,segment,lease,result)) return
            reporter.report(++completed,total,"正在提取有原文依据的个人记忆")
        }
    }
}
