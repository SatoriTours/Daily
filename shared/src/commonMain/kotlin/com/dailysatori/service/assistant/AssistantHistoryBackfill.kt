package com.dailysatori.service.assistant

import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.asynctask.*
import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val ASSISTANT_HISTORY_BACKFILL_TYPE = "assistant_history_backfill"
const val ASSISTANT_SUMMARY_TYPE = "assistant_summary"
const val ASSISTANT_HISTORY_RESTORE_HOLD = "assistant.history.restore_hold"

/** Bounded local pages enqueue the existing index pipeline; this class never calls a provider. */
class AssistantHistoryBackfill(driver: SqlDriver, private val pageSize: Int = 10) {
    private val store = AssistantStore(driver)
    private val db = DailySatoriDatabase(driver)
    private val tasks = AsyncTaskRepository(db)
    private val settings = SettingRepository(db)
    init { require(pageSize in 1..50) }

    fun enqueue(): Long? = store.transactionWithResult {
        if (!authorized()) return@transactionWithResult null
        val upper = store.rows("SELECT MAX(id) FROM chat_conversation WHERE role='user'") { it.getLong(0) ?: 0 }.single()
        if (upper == 0L) return@transactionWithResult null
        val latest = tasks.getLatestByUniqueKey(ASSISTANT_HISTORY_BACKFILL_TYPE)
        if (latest?.status in setOf("queued", "running", "retrying")) return@transactionWithResult latest?.id
        val previous = latest?.checkpoint_json?.let(::cursor)
        if (settings.get(ASSISTANT_HISTORY_RESTORE_HOLD) != "true") {
            if (latest?.status in setOf("failed", "cancelled") && latest?.last_error_code != "cloud_consent_required") return@transactionWithResult null
            if (previous?.done == true && previous.upperBound >= upper) return@transactionWithResult null
        }
        val start = if (previous?.done == true) previous.upperBound else previous?.cursor ?: 0
        enqueueProtected(ASSISTANT_HISTORY_BACKFILL_TYPE, Json.encodeToString(HistoryCursor(start, upper)), ASSISTANT_HISTORY_BACKFILL_TYPE, -20)
    }

    fun enqueueSummary(sessionId: String, upperId: Long): Long? = store.transactionWithResult {
        if (!authorized()) return@transactionWithResult null
        val count = store.rows("SELECT COUNT(*) FROM chat_conversation WHERE session_id=? AND role='user' AND id<=?", sessionId, upperId) { it.getLong(0)!! }.single()
        if (count <= 12) return@transactionWithResult null
        enqueueProtected(ASSISTANT_SUMMARY_TYPE, Json.encodeToString(SummaryRequest(sessionId, upperId)),
            "assistant_summary:$sessionId:$upperId", -15)
    }

    private fun enqueueProtected(type: String, payload: String, key: String, priority: Long): Long? {
        val existing = tasks.getLatestByUniqueKey(key)
        if (existing?.status in setOf("queued", "running", "retrying")) return existing?.id
        val held = settings.get(ASSISTANT_HISTORY_RESTORE_HOLD) == "true"
        if (held && existing?.last_error_code == "restore_requires_resume") return null
        val id = tasks.enqueue(type, payload, key, priority = priority)
        if (held) {
            tasks.parkAssistantBackground(id)
            return null
        }
        return id
    }

    internal fun process(taskId: Long, payload: String, checkpoint: String, reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
        val lease = reporter.executionLease?.takeIf { it.taskId == taskId }
            ?: return AsyncTaskExecutionResult.PermanentFailure("assistant_lease_required", "历史整理需要有效运行身份")
        val state = cursor(checkpoint) ?: cursor(payload)
            ?: return AsyncTaskExecutionResult.PermanentFailure("invalid_backfill", "历史整理范围无效")
        return tasks.withRunningLease(taskId, lease.owner) {
            if (!authorized() || tasks.getById(taskId)?.type != ASSISTANT_HISTORY_BACKFILL_TYPE)
                return@withRunningLease AsyncTaskExecutionResult.PermanentFailure("cloud_consent_required", "原文保留，等待云端授权")
            // Enqueue/restore never dispatch a held task. A running lease here follows explicit resume.
            if (settings.get(ASSISTANT_HISTORY_RESTORE_HOLD) == "true") settings.upsert(ASSISTANT_HISTORY_RESTORE_HOLD, "false")
            val page = store.rows("""SELECT id,session_id,content FROM chat_conversation c WHERE role='user' AND id>? AND id<=?
                AND NOT EXISTS(SELECT 1 FROM assistant_memory_exclusion x WHERE x.source_type='chat' AND x.source_id=CAST(c.id AS TEXT))
                AND NOT EXISTS(SELECT 1 FROM assistant_turn t WHERE t.id=c.id AND t.response_message_id IS NULL)
                ORDER BY id LIMIT ?""", state.cursor, state.upperBound, pageSize) {
                Triple(it.getLong(0)!!, it.getString(1)!!, it.getString(2)!!)
            }
            page.forEach { (id, _, content) ->
                val segments = store.rows("SELECT source_version,ai_state FROM assistant_index_segment WHERE source_id=?", id) { it.getString(0) to it.getString(1) }
                if (segments.isEmpty() || segments.any { it.first != sha256Hex(content) || it.second != "done" })
                    tasks.enqueue("assistant_index", """{"sourceId":$id}""", "assistant_index:$id", priority = -10)
            }
            page.map { it.second }.distinct().forEach { enqueueSummary(it, state.upperBound) }
            val done = page.size < pageSize
            val next = HistoryCursor(if (done) state.upperBound else page.last().first, state.upperBound, done)
            tasks.updateProgress(taskId, next.cursor, state.upperBound,
                if (done) "历史已分批排队，提取和归纳结果请查看关联后台任务" else "正在分批整理较早的聊天原文", Json.encodeToString(next))
            if (done) AsyncTaskExecutionResult.Success() else AsyncTaskExecutionResult.Yield()
        } ?: AsyncTaskExecutionResult.PermanentFailure("assistant_lease_lost", "历史整理执行权已失效")
    }

    private fun authorized() = settings.get(PersistentAssistantConversation.CONSENT_KEY) == "true"
    private fun cursor(raw: String): HistoryCursor? = runCatching { Json.decodeFromString<HistoryCursor>(raw) }
        .getOrNull()?.takeIf { it.cursor >= 0 && it.upperBound >= it.cursor }
}

@Serializable internal data class HistoryCursor(val cursor: Long = 0, val upperBound: Long, val done: Boolean = false)
@Serializable internal data class SummaryRequest(val sessionId: String, val upperId: Long)

class AssistantHistoryBackfillTaskHandler(private val backfill: AssistantHistoryBackfill) : AsyncTaskHandler {
    override val type = ASSISTANT_HISTORY_BACKFILL_TYPE
    override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String, reporter: AsyncTaskProgressReporter) =
        backfill.process(taskId, payloadJson, checkpointJson, reporter)
}
