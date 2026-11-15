package com.dailysatori.service.migration

import app.cash.sqldelight.db.SqlDriver

/**
 * RFC 5005 sync state of one subscription. [migrate] builds the current (V49) schema; the legacy
 * factories reproduce the shapes earlier versions shipped so historical upgrade tests stay honest.
 */
internal object RemoteFeedStateMigration {
    const val name = "remote_feed_sync_state"

    private const val state = """CREATE TABLE IF NOT EXISTS remote_feed_sync_state (
        remote_source_id INTEGER PRIMARY KEY REFERENCES remote_news_source(id) ON DELETE CASCADE,
        entry_url TEXT NOT NULL DEFAULT '',
        paging_mode TEXT NOT NULL DEFAULT 'plain',
        baseline_ready INTEGER NOT NULL DEFAULT 0,
        checkpoint_ids TEXT NOT NULL DEFAULT '',
        target_entry_ids TEXT NOT NULL DEFAULT '',
        confirmed_anchor_url TEXT,
        confirmed_at INTEGER,
        generation INTEGER NOT NULL DEFAULT 0,
        gap_open INTEGER NOT NULL DEFAULT 0,
        scan_start_url TEXT,
        scan_start_fingerprint TEXT,
        cursor_url TEXT,
        last_page_url TEXT,
        last_page_fingerprint TEXT,
        scan_pages INTEGER NOT NULL DEFAULT 0,
        staged_bytes INTEGER NOT NULL DEFAULT 0,
        closure_reached INTEGER NOT NULL DEFAULT 0,
        blocked INTEGER NOT NULL DEFAULT 0,
        blocked_reason TEXT NOT NULL DEFAULT '',
        updated_at INTEGER NOT NULL
    )"""

    private const val staged = """CREATE TABLE IF NOT EXISTS remote_feed_staged_article (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        remote_source_id INTEGER NOT NULL REFERENCES remote_news_source(id) ON DELETE CASCADE,
        generation INTEGER NOT NULL,
        remote_article_id INTEGER NOT NULL,
        page_url TEXT NOT NULL DEFAULT '',
        sequence INTEGER NOT NULL DEFAULT 0,
        stale INTEGER NOT NULL DEFAULT 0,
        title TEXT,
        url TEXT,
        summary TEXT,
        content TEXT,
        published_at TEXT,
        bytes INTEGER NOT NULL DEFAULT 0,
        created_at INTEGER NOT NULL,
        UNIQUE(remote_source_id, generation, remote_article_id)
    )"""

    private const val stagedIndex = """CREATE INDEX IF NOT EXISTS idx_remote_feed_staged_source
        ON remote_feed_staged_article(remote_source_id, generation, stale, sequence)"""

    private const val scanNode = """CREATE TABLE IF NOT EXISTS remote_feed_scan_node (
        remote_source_id INTEGER NOT NULL REFERENCES remote_news_source(id) ON DELETE CASCADE,
        generation INTEGER NOT NULL,
        document_url TEXT NOT NULL,
        fingerprint TEXT NOT NULL DEFAULT '',
        created_at INTEGER NOT NULL,
        PRIMARY KEY(remote_source_id, generation, document_url)
    )"""

    /** Current schema: state + bounded staging candidates + persisted scan nodes. */
    fun migrate(driver: SqlDriver) {
        driver.execute(null, state, 0)
        driver.execute(null, staged, 0)
        driver.execute(null, stagedIndex, 0)
        driver.execute(null, scanNode, 0)
    }

    /** V49 -> V50 adds the fixed generation target; existing progress rows stay readable. */
    fun addGenerationTarget(driver: SqlDriver) {
        driver.execute(null, "ALTER TABLE remote_feed_sync_state ADD COLUMN target_entry_ids TEXT NOT NULL DEFAULT ''", 0)
    }

    /** Drops the progress tables only: articles, sources and AI state are never touched. */
    fun drop(driver: SqlDriver) {
        driver.execute(null, "DROP TABLE IF EXISTS remote_feed_scan_node", 0)
        driver.execute(null, "DROP TABLE IF EXISTS remote_feed_staged_article", 0)
        driver.execute(null, "DROP TABLE IF EXISTS remote_feed_sync_state", 0)
    }

    /** The shape schema 47 shipped; never created by the app any more, only reproduced by tests. */
    fun createLegacyV47(driver: SqlDriver) {
        driver.execute(
            null,
            """CREATE TABLE IF NOT EXISTS remote_feed_sync_state (
                remote_source_id INTEGER PRIMARY KEY REFERENCES remote_news_source(id) ON DELETE CASCADE,
                entry_url TEXT NOT NULL DEFAULT '',
                paging_mode TEXT NOT NULL DEFAULT 'plain',
                pending INTEGER NOT NULL DEFAULT 0,
                older_url TEXT,
                frontier_article_id INTEGER,
                entry_older_url TEXT,
                updated_at INTEGER NOT NULL
            )""",
            0,
        )
    }

    /** The shape schema 49 shipped (no fixed generation target). */
    fun createLegacyV49(driver: SqlDriver) {
        createLegacyV49Table(driver)
        driver.execute(null, staged, 0)
        driver.execute(null, stagedIndex, 0)
        driver.execute(null, scanNode, 0)
    }

    private fun createLegacyV49Table(driver: SqlDriver) {
        driver.execute(
            null,
            """CREATE TABLE IF NOT EXISTS remote_feed_sync_state (
                remote_source_id INTEGER PRIMARY KEY REFERENCES remote_news_source(id) ON DELETE CASCADE,
                entry_url TEXT NOT NULL DEFAULT '',
                paging_mode TEXT NOT NULL DEFAULT 'plain',
                baseline_ready INTEGER NOT NULL DEFAULT 0,
                checkpoint_ids TEXT NOT NULL DEFAULT '',
                confirmed_anchor_url TEXT,
                confirmed_at INTEGER,
                generation INTEGER NOT NULL DEFAULT 0,
                gap_open INTEGER NOT NULL DEFAULT 0,
                scan_start_url TEXT,
                scan_start_fingerprint TEXT,
                cursor_url TEXT,
                last_page_url TEXT,
                last_page_fingerprint TEXT,
                scan_pages INTEGER NOT NULL DEFAULT 0,
                staged_bytes INTEGER NOT NULL DEFAULT 0,
                closure_reached INTEGER NOT NULL DEFAULT 0,
                blocked INTEGER NOT NULL DEFAULT 0,
                blocked_reason TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL
            )""",
            0,
        )
    }

    /** The shape schema 48 shipped (fixed baseline markers, no staging). */
    fun createLegacyV48(driver: SqlDriver) {
        driver.execute(
            null,
            """CREATE TABLE IF NOT EXISTS remote_feed_sync_state (
                remote_source_id INTEGER PRIMARY KEY REFERENCES remote_news_source(id) ON DELETE CASCADE,
                entry_url TEXT NOT NULL DEFAULT '',
                paging_mode TEXT NOT NULL DEFAULT 'plain',
                baseline_ready INTEGER NOT NULL DEFAULT 0,
                baseline_anchor_url TEXT,
                baseline_marker_ids TEXT NOT NULL DEFAULT '',
                entry_older_url TEXT,
                scan_start_url TEXT,
                cursor_url TEXT,
                processed_older_url TEXT,
                pending INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )""",
            0,
        )
    }
}
