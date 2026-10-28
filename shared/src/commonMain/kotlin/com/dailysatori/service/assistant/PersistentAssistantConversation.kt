package com.dailysatori.service.assistant

import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.data.repository.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

class PersistentAssistantConversation(
    driver: SqlDriver,
    private val actions: AssistantActionService,
    private val memory: AssistantMemoryService,
    private val schedule: (Long) -> Unit,
) : AssistantConversation {
    private var contextProvider: com.dailysatori.service.diary.DiaryThoughtChatContextProvider? = null
    constructor(driver: SqlDriver, actions: AssistantActionService, memory: AssistantMemoryService,
        contextProvider: com.dailysatori.service.diary.DiaryThoughtChatContextProvider, schedule: (Long) -> Unit) :
        this(driver, actions, memory, schedule) { this.contextProvider = contextProvider }
    private var backfill: AssistantHistoryBackfill? = null
    constructor(driver: SqlDriver, actions: AssistantActionService, memory: AssistantMemoryService,
        contextProvider: com.dailysatori.service.diary.DiaryThoughtChatContextProvider,
        backfill: AssistantHistoryBackfill, schedule: (Long) -> Unit) :
        this(driver, actions, memory, contextProvider, schedule) {
        this.backfill = backfill
        requestHistory()
    }
    private val inputContext = AssistantInputContext(driver)
    private val store = AssistantStore(driver)
    private val db = DailySatoriDatabase(driver)
    private val chats = ChatConversationRepository(db)
    private val tasks = AsyncTaskRepository(db)
    private val turns = AssistantTurnRepository(driver)
    private val privacy = AssistantPrivacyRepository(driver)
    private val settings = SettingRepository(db)

    override fun sessions(): Flow<List<String>> = store.observe("chat_conversation") { chats.getSessions() }
    override fun sessionSummaries(): Flow<List<AssistantSessionSummary>> = store.observe("chat_conversation") {
        store.rows("""SELECT c.session_id,
            (SELECT substr(first.content,1,240) FROM chat_conversation first
                WHERE first.session_id=c.session_id AND first.role='user'
                ORDER BY first.created_at,first.id LIMIT 1),
            (SELECT substr(last.content,1,240) FROM chat_conversation last
                WHERE last.session_id=c.session_id ORDER BY last.created_at DESC,last.id DESC LIMIT 1),
            MAX(c.created_at)
            FROM chat_conversation c GROUP BY c.session_id
            ORDER BY MAX(c.created_at) DESC,MAX(c.id) DESC""") { row ->
            val preview = row.getString(2).orEmpty().replace(Regex("\\s+"), " ").trim()
            val title = row.getString(1).orEmpty().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            AssistantSessionSummary(row.getString(0)!!, title.ifBlank { preview }, preview, row.getLong(3) ?: 0)
        }
    }
    override fun memories(): Flow<List<AssistantMemoryView>> = store.observe("memory_entry", "assistant_memory_exclusion") { memory.list() }
    override fun cloudConsent(): Boolean = settings.get(CONSENT_KEY) == "true"
    override fun setCloudConsent(accepted: Boolean) {
        store.transaction {
            settings.upsert(CONSENT_KEY, accepted.toString())
            if (!accepted) {
                store.exec("""UPDATE assistant_turn SET requires_resume=1 WHERE response_message_id IS NULL
                    AND task_id IN (SELECT id FROM async_task WHERE status IN ('queued','running','retrying'))""")
                store.exec("""UPDATE async_task SET status='failed',lease_owner=NULL,lease_until_ms=NULL,run_after_ms=NULL,
                    last_error_code='cloud_consent_required',last_error_message='云端授权已撤销，原文与已完成结果保留'
                    WHERE type IN ('assistant_turn','assistant_index','assistant_summary','assistant_history_backfill')
                    AND status IN ('queued','running','retrying')""")
                store.exec("UPDATE async_task SET checkpoint_json='' WHERE type='assistant_turn'")
            }
        }
        store.changed()
        if (accepted) requestHistory()
    }

    private fun requestHistory() { backfill?.enqueue()?.let { runCatching { schedule(it) } } }

    override fun messages(sessionId: String): Flow<List<AssistantMessage>> = store.observe(
        "chat_conversation", "assistant_turn", "async_task", "assistant_action", "assistant_reference", "assistant_memory_exclusion",
        "article", "reminder", "diary", "book", "book_viewpoint",
    ) {
        chats.getBySession(sessionId).flatMap { message ->
            val turnId = store.rows("SELECT id FROM assistant_turn WHERE id=? OR response_message_id=?", message.id, message.id) { it.getLong(0)!! }.firstOrNull()
            val turn = turnId?.let(turns::get)
            val task = turn?.taskId?.let(tasks::getById)
            val actionViews = turnId?.let(actions::list).orEmpty()
            val pendingSchedule = actionViews.any { it.status == "schedule_pending" }
            val saved = AssistantMessage(message.id, message.role, message.content, message.created_at, turnId, turn?.taskId,
                status = if (turn?.requiresResume == true || pendingSchedule) "requires_resume" else if (turn?.responseMessageId != null) "complete" else task?.status ?: "complete",
                error = task?.last_error_message.orEmpty(), excluded = privacy.isExcluded("chat", message.id.toString()),
                references = turnId?.let(::references).orEmpty(), actions = actionViews)
            val partial = if (message.role == "user" && turn != null && turn.responseMessageId == null &&
                !turn.requiresResume && task?.status == "running" && !saved.excluded && cloudConsent()) {
                AssistantAgentCheckpointStore.displayedPartial(task.checkpoint_json, privacy.revision(), message.content, inputContext.prompt(turn.id))
            } else ""
            if (partial.isBlank()) listOf(saved) else listOf(saved, AssistantMessage(
                -message.id, "assistant", partial, message.created_at, turnId, turn?.taskId, status = "streaming",
            ))
        }
    }

    private fun references(turnId: Long): List<AssistantReference> = store.rows(
        "SELECT source_type,source_id,title FROM assistant_reference WHERE turn_id=?", turnId,
    ) { AssistantReference(it.getString(0)!!, it.getString(1)!!, it.getString(2).orEmpty()) }
        .filterNot { privacy.isExcluded(it.source, it.id) }

    override fun send(sessionId: String, clientMessageId: String, content: String): AssistantTurn =
        sendWithContext(sessionId, clientMessageId, content, null, true)

    override fun sendWithContext(sessionId: String, clientMessageId: String, content: String,
        explicitContext: com.dailysatori.service.diary.DiaryThoughtChatContext?, includeThoughts: Boolean): AssistantTurn {
        val turn = store.transactionWithResult {
            val accepted = turns.accept(sessionId, clientMessageId, content, Clock.System.now().toEpochMilliseconds(), TimeZone.currentSystemDefault().id)
            val context = explicitContext ?: if (includeThoughts) runCatching { contextProvider?.getContext() }.getOrNull() else null
            inputContext.capture(accepted.id, context)
            accepted
        }
        if (!cloudConsent()) {
            store.transaction {
                store.exec("UPDATE assistant_turn SET requires_resume=1 WHERE id=? AND response_message_id IS NULL", turn.id)
                store.exec("UPDATE async_task SET status='failed',last_error_code='cloud_consent_required',last_error_message='原文已保存在本机，等待云端授权' WHERE id=? AND status='queued'", turn.taskId)
            }
            store.changed()
        } else {
            // WorkManager scheduling may fail; durable queued work is recovered at startup.
            runCatching { schedule(turn.taskId) }
        }
        return requireNotNull(turns.get(turn.id))
    }

    private fun enqueueIndex(id: Long) {
        val taskId = tasks.enqueue("assistant_index", "{\"sourceId\":$id}", "assistant_index:$id")
        runCatching { schedule(taskId) }
    }

    override fun cancel(turnId: Long) {
        store.transaction {
            turns.cancel(turnId)
            store.exec("UPDATE assistant_action SET status='cancelled' WHERE turn_id=? AND target_id IS NULL", turnId)
        }
        store.changed()
    }

    override fun retry(turnId: Long) {
        val turn = requireNotNull(turns.get(turnId))
        require(turns.input(turn) != null && !privacy.isExcluded("chat", turn.id.toString()))
        store.transaction {
            val task = requireNotNull(tasks.getById(turn.taskId))
            if (task.status in setOf("queued", "running", "retrying")) return@transaction
            store.exec("UPDATE assistant_turn SET requires_resume=0 WHERE id=?", turnId)
            store.exec("""UPDATE async_task SET status='retrying',run_after_ms=NULL,lease_owner=NULL,lease_until_ms=NULL,
                finished_at=NULL,last_error_code='',last_error_message='',attempt_count=0 WHERE id=?""".trimIndent(), turn.taskId)
            store.exec("UPDATE assistant_action SET status='planned' WHERE turn_id=? AND status='cancelled' AND target_id IS NULL", turnId)
            store.exec("""UPDATE async_task SET status='retrying',run_after_ms=NULL,attempt_count=0,finished_at=NULL
                WHERE status='failed' AND id IN (SELECT task_id FROM assistant_action WHERE turn_id=?)""".trimIndent(), turnId)
        }
        store.changed()
        if (cloudConsent() && turn.responseMessageId != null) enqueueIndex(turn.id)
        runCatching { schedule(turn.taskId) }
    }

    override fun excludeMessage(messageId: Long) {
        val originId = store.rows("SELECT id FROM assistant_turn WHERE response_message_id=?", messageId) { it.getLong(0)!! }.firstOrNull() ?: messageId
        privacy.exclude("chat", originId.toString())
        store.exec("DELETE FROM assistant_index_segment WHERE source_id=?", originId)
        store.changed()
    }

    override fun deleteMessage(messageId: Long) {
        privacy.deleteConversationMessage(messageId)
        store.changed()
    }

    override fun deleteSession(sessionId: String) {
        chats.getBySession(sessionId).map { it.id }.forEach(::deleteMessage)
        store.changed()
    }
    override fun forgetMemory(memoryId: Long) = memory.forget(memoryId)
    override fun correctMemory(memoryId: Long, corrected: String) = memory.correct(memoryId, corrected)

    companion object { const val CONSENT_KEY = "assistant.cloud_consent" }
}
