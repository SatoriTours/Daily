package com.dailysatori.service.assistant

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.ChatConversationRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.datetime.TimeZone

/** No second queue: status, attempts and leases belong exclusively to async_task. */
data class AssistantTurn(
    val id: Long,
    val sessionId: String,
    val clientMessageId: String,
    val taskId: Long,
    val receivedAtMs: Long,
    val timeZoneId: String,
    val responseMessageId: Long?,
    val requiresResume: Boolean,
)

class AssistantTurnRepository(private val driver: SqlDriver) {
    private val db = DailySatoriDatabase(driver)
    private val chats = ChatConversationRepository(db)
    private val tasks = AsyncTaskRepository(db)
    private val privacy = AssistantPrivacyRepository(driver)
    private val transactions = object : TransacterImpl(driver) {}

    /** The caller retains clientMessageId across send retries, but assigns a new ID to a new input. */
    fun accept(sessionId: String, clientMessageId: String, input: String, receivedAtMs: Long, timeZoneId: String): AssistantTurn {
        require(sessionId.isNotBlank() && sessionId.length <= 128)
        require(clientMessageId.isNotBlank() && clientMessageId.length <= 128 && input.isNotBlank())
        TimeZone.of(timeZoneId)
        return transactions.transactionWithResult<AssistantTurn> {
            val existing = byClientId(sessionId, clientMessageId)
            if (existing != null) {
                require(input(existing) == input) { "Client identity already accepted with different or deleted input" }
                return@transactionWithResult existing
            }
            driver.execute(null, "INSERT OR IGNORE INTO assistant_session(id, created_at) VALUES (?, ?)", 2) {
                bindString(0, sessionId); bindLong(1, receivedAtMs)
            }
            chats.insert(sessionId, "user", input, createdAt = receivedAtMs)
            val id = db.dailySatoriQueries.selectLastInsertedChatId().executeAsOne()
            val taskId = tasks.enqueue("assistant_turn", "{\"version\":1,\"turnId\":$id}", "assistant_turn:$id")
            driver.execute(null, """INSERT INTO assistant_turn
                (id, session_id, client_message_id, task_id, received_at, time_zone_id)
                VALUES (?, ?, ?, ?, ?, ?)""".trimIndent(), 6) {
                bindLong(0, id); bindString(1, sessionId); bindString(2, clientMessageId)
                bindLong(3, taskId); bindLong(4, receivedAtMs); bindString(5, timeZoneId)
            }
            requireNotNull(get(id))
        }.also { driver.notifyListeners("assistant_session", "assistant_turn") }
    }

    fun get(id: Long): AssistantTurn? = select("id = ?", listOf(id.toString()))

    fun input(turn: AssistantTurn): String? = chats.getById(turn.id)
        ?.takeIf { it.session_id == turn.sessionId && it.role == "user" }?.content

    fun canExecute(turn: AssistantTurn, leaseOwner: String): Boolean {
        val task = tasks.getById(turn.taskId) ?: return false
        return !turn.requiresResume && task.status == "running" && task.lease_owner == leaseOwner &&
            input(turn) != null && !privacy.isExcluded("chat", turn.id.toString())
    }

    /** Fences stale workers and commits the reply and its turn link together, or neither. */
    fun privacyRevision(): Long = privacy.revision()

    fun complete(id: Long, leaseOwner: String, answer: String, expectedPrivacyRevision: Long? = null): Boolean = transactions.transactionWithResult {
        val turn = get(id) ?: return@transactionWithResult false
        if (!canExecute(turn, leaseOwner) || (expectedPrivacyRevision != null && privacy.revision() != expectedPrivacyRevision)) return@transactionWithResult false
        if (turn.responseMessageId != null) return@transactionWithResult true
        require(answer.isNotBlank())
        chats.insert(turn.sessionId, "assistant", answer)
        val responseId = db.dailySatoriQueries.selectLastInsertedChatId().executeAsOne()
        driver.execute(null, "UPDATE assistant_turn SET response_message_id = ? WHERE id = ?", 2) {
            bindLong(0, responseId); bindLong(1, id)
        }
        tasks.enqueue("assistant_index", """{"sourceId":$id}""", "assistant_index:$id")
        AssistantHistoryBackfill(driver).enqueueSummary(turn.sessionId, turn.id)
        driver.execute(null, "UPDATE async_task SET checkpoint_json='' WHERE id=?", 1) { bindLong(0, turn.taskId) }
        true
    }.also { driver.notifyListeners("assistant_turn") }

    fun cancel(id: Long) {
        get(id)?.let { tasks.cancel(it.taskId) }
    }

    /** Explicit user continuation is required after an archive restore, not process restart. */
    fun resume(id: Long): Boolean = transactions.transactionWithResult {
        val turn = get(id) ?: return@transactionWithResult false
        val task = tasks.getById(turn.taskId) ?: return@transactionWithResult false
        if (!turn.requiresResume || turn.responseMessageId != null || input(turn) == null ||
            privacy.isExcluded("chat", id.toString()) || task.status != "failed" ||
            task.last_error_code != "restore_requires_resume") return@transactionWithResult false
        driver.execute(null, "UPDATE assistant_turn SET requires_resume = 0 WHERE id = ?", 1) { bindLong(0, id) }
        driver.execute(null, """UPDATE async_task SET status = 'retrying', run_after_ms = NULL,
            lease_owner = NULL, lease_until_ms = NULL, finished_at = NULL,
            last_error_code = '', last_error_message = ''
            WHERE id = ? AND status = 'failed' AND last_error_code = 'restore_requires_resume'""".trimIndent(), 1) {
            bindLong(0, turn.taskId)
        }
        true
    }.also { driver.notifyListeners("assistant_turn", "async_task") }

    private fun byClientId(session: String, client: String): AssistantTurn? =
        select("session_id = ? AND client_message_id = ?", listOf(session, client))

    // Only host-owned predicates; never exposed to tools or model data.
    private fun select(predicate: String, values: List<String>): AssistantTurn? = driver.executeQuery(
        null, """SELECT id, session_id, client_message_id, task_id, received_at,
            time_zone_id, response_message_id, requires_resume FROM assistant_turn WHERE $predicate""".trimIndent(),
        { c -> QueryResult.Value(if (!c.next().value) null else AssistantTurn(
            requireNotNull(c.getLong(0)), requireNotNull(c.getString(1)), requireNotNull(c.getString(2)),
            requireNotNull(c.getLong(3)), requireNotNull(c.getLong(4)), requireNotNull(c.getString(5)),
            c.getLong(6), c.getLong(7) == 1L,
        )) }, values.size,
    ) { values.forEachIndexed { i, value -> bindString(i, value) } }.value
}
