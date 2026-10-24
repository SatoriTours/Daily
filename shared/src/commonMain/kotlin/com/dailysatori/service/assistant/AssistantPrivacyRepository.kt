package com.dailysatori.service.assistant

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlinx.datetime.Clock

data class AssistantHistoryMessage(val id: String, val role: String, val content: String)

/** Host-owned privacy controls, never exposed as arbitrary SQL or a model-controlled allow-list. */
class AssistantPrivacyRepository(private val driver: SqlDriver) {
    private val transactions = object : TransacterImpl(driver) {}

    /** Tombstones are append-only: checked again inside the final reply transaction. */
    fun revision(): Long = driver.executeQuery(null, "SELECT COUNT(*) FROM assistant_memory_exclusion", {
        it.next(); QueryResult.Value(it.getLong(0)!!)
    }, 0).value

    fun isExcluded(source: String, id: String): Boolean = driver.executeQuery(
        null, "SELECT 1 FROM assistant_memory_exclusion WHERE source_type = ? AND source_id = ?",
        { QueryResult.Value(it.next().value) }, 2
    ) { bindString(0, source); bindString(1, id) }.value

    fun exclude(source: String, id: String) {
        require(source.matches(Regex("[a-z_]{1,64}")) && id.isNotBlank() && id.length <= 128)
        transactions.transaction {
            putExclusion(source, id)
            // A checkpoint can contain any previously authorized tool/context result.
            driver.execute(null, "UPDATE async_task SET checkpoint_json='' WHERE type IN ('assistant_turn','assistant_index','assistant_summary','assistant_history_backfill')", 0)
            // Old conversations lack turn links: conservatively invalidate later AI-derived answers.
            if (source == "chat") {
                driver.execute(null, "UPDATE assistant_session SET summary='', summary_sources_json='[]', summary_version=summary_version+1", 0)
                excludeDerivedReplies(id)
                driver.execute(null, "DELETE FROM assistant_index_segment WHERE CAST(source_id AS TEXT)=?", 1) { bindString(0, id) }
            }
            driver.execute(null, """DELETE FROM memory_entry WHERE EXISTS (
                SELECT 1 FROM assistant_memory_exclusion x
                WHERE x.source_type = memory_entry.source_type
                AND x.source_id = CAST(memory_entry.source_id AS TEXT)
            )""".trimIndent(), 0)
        }
        driver.notifyListeners("memory_entry", "assistant_memory_exclusion")
    }

    fun deleteChatMessage(id: String) {
        transactions.transaction {
            exclude("chat", id)
            driver.execute(null, "DELETE FROM chat_conversation WHERE CAST(id AS TEXT) = ?", 1) { bindString(0, id) }
        }
        driver.notifyListeners("chat_conversation")
    }

    /** Bounded, oldest-first context; original messages remain unchanged in the durable log. */
    fun history(sessionId: String, limit: Int = 24, beforeInputId: Long? = null): List<AssistantHistoryMessage> = driver.executeQuery(
        null, """SELECT c.id, c.role, c.content FROM chat_conversation c
            WHERE c.session_id = ? AND c.role IN ('user', 'assistant')
            AND (? IS NULL OR c.id < ? OR EXISTS (
                SELECT 1 FROM assistant_turn t WHERE t.session_id = c.session_id
                AND t.id < ? AND t.response_message_id = c.id
            ))
            AND NOT EXISTS (SELECT 1 FROM assistant_memory_exclusion x
                WHERE x.source_type = 'chat' AND x.source_id = CAST(c.id AS TEXT))
            ORDER BY c.id DESC LIMIT ?""".trimIndent(),
        { cursor ->
            val rows = mutableListOf<AssistantHistoryMessage>()
            while (cursor.next().value) rows += AssistantHistoryMessage(
                requireNotNull(cursor.getLong(0)).toString(),
                requireNotNull(cursor.getString(1)), requireNotNull(cursor.getString(2))
            )
            QueryResult.Value(rows.asReversed())
        }, 5
    ) {
        bindString(0, sessionId); bindLong(1, beforeInputId); bindLong(2, beforeInputId)
        bindLong(3, beforeInputId); bindLong(4, limit.coerceIn(1, 48).toLong())
    }.value

    private fun putExclusion(source: String, id: String) {
        driver.execute(null, """INSERT OR IGNORE INTO assistant_memory_exclusion
            (source_type, source_id, created_at) VALUES (?, ?, ?)""".trimIndent(), 3) {
            bindString(0, source); bindString(1, id)
            bindLong(2, Clock.System.now().toEpochMilliseconds())
        }
    }

    private fun excludeDerivedReplies(id: String) {
        driver.execute(null, """INSERT OR IGNORE INTO assistant_memory_exclusion
            (source_type, source_id, created_at)
            SELECT 'chat', CAST(c.id AS TEXT), ? FROM chat_conversation c
            JOIN chat_conversation origin ON origin.session_id = c.session_id
            WHERE CAST(origin.id AS TEXT) = ? AND c.id > origin.id AND c.role = 'assistant'""".trimIndent(), 2) {
            bindLong(0, Clock.System.now().toEpochMilliseconds()); bindString(1, id)
        }
    }
}
