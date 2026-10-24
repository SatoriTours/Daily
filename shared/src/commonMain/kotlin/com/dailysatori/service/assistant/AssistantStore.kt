package com.dailysatori.service.assistant

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import app.cash.sqldelight.Query

/** Internal host SQL only. Neither predicates nor identifiers are supplied by model tools. */
internal class AssistantStore(val connection: SqlDriver) : TransacterImpl(connection) {
    fun exec(sql: String, vararg args: Any?) {
        driver.execute(null, sql, args.size) {
            args.forEachIndexed { i, v -> when (v) {
                null -> bindString(i, null)
                is Number -> bindLong(i, v.toLong())
                is Boolean -> bindLong(i, if (v) 1 else 0)
                else -> bindString(i, v.toString())
            } }
        }
    }
    fun <T> rows(sql: String, vararg args: Any?, map: (SqlCursor) -> T): List<T> = driver.executeQuery(
        null, sql, { c ->
            val rows = mutableListOf<T>()
            while (c.next().value) rows += map(c)
            QueryResult.Value(rows)
        }, args.size,
    ) { args.forEachIndexed { i, v -> if (v is Number) bindLong(i, v.toLong()) else bindString(i, v?.toString()) } }.value
    fun text(sql: String, vararg args: Any?): String? = rows(sql, *args) { it.getString(0) }.firstOrNull()
    fun changed() = driver.notifyListeners("assistant_turn", "assistant_action", "assistant_reference", "assistant_index_segment", "memory_entry", "chat_conversation", "async_task")
    fun <T> observe(vararg tables: String, read: () -> T) = callbackFlow {
        val listener = object : Query.Listener { override fun queryResultsChanged() { trySend(read()) } }
        driver.addListener(*tables, listener = listener)
        trySend(read())
        awaitClose { driver.removeListener(*tables, listener = listener) }
    }.conflate()
}
