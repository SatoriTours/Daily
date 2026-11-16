package com.dailysatori.service.migration

import app.cash.sqldelight.db.SqlDriver

/**
 * First-import history walk of one subscription (schema 51). It is stored apart from the RFC 5005
 * incremental gap state: opening a history walk never blocks the daily increment, and pausing it never
 * invalidates the confirmed checkpoint.
 */
internal object RemoteFeedHistoryStateMigration {
    const val name = "remote_feed_history_state"

    private const val createSql = """CREATE TABLE IF NOT EXISTS remote_feed_history_state (
        remote_source_id INTEGER PRIMARY KEY REFERENCES remote_news_source(id) ON DELETE CASCADE,
        entry_url TEXT NOT NULL DEFAULT '',
        paging_mode TEXT NOT NULL DEFAULT 'plain',
        strategy TEXT NOT NULL DEFAULT 'all_history',
        status TEXT NOT NULL DEFAULT 'off',
        control_epoch INTEGER NOT NULL DEFAULT 0,
        start_url TEXT,
        cursor_url TEXT,
        last_page_url TEXT,
        last_page_fingerprint TEXT,
        imported_count INTEGER NOT NULL DEFAULT 0,
        target_count INTEGER NOT NULL DEFAULT 0,
        pages_read INTEGER NOT NULL DEFAULT 0,
        requests INTEGER NOT NULL DEFAULT 0,
        last_error TEXT NOT NULL DEFAULT '',
        blocked_reason TEXT NOT NULL DEFAULT '',
        started_at INTEGER,
        completed_at INTEGER,
        updated_at INTEGER NOT NULL
    )"""

    fun create(driver: SqlDriver) {
        driver.execute(null, createSql, 0)
    }

    /**
     * V51 -> V52 adds the ownership epoch that protects a user pause/restart from stale run snapshots.
     * Idempotent: a database that already received the column (or was created from the current schema
     * and only relabelled) must migrate again without failing.
     */
    fun addControlEpoch(driver: SqlDriver) {
        val columns = driver.executeQuery(
            null,
            "SELECT name FROM pragma_table_info('remote_feed_history_state')",
            { cursor ->
                val names = mutableListOf<String>()
                while (cursor.next().value) cursor.getString(0)?.let(names::add)
                app.cash.sqldelight.db.QueryResult.Value(names)
            },
            0,
        ).value
        if ("control_epoch" in columns) return
        driver.execute(
            null,
            "ALTER TABLE remote_feed_history_state ADD COLUMN control_epoch INTEGER NOT NULL DEFAULT 0",
            0,
        )
    }
}

