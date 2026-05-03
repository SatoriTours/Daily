package com.dailysatori.service.migration

import co.touchlab.kermit.Logger
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.QueryResult

class DatabaseMigration(
    private val driver: SqlDriver,
    private val settingRepo: SettingRepository,
) {
    private val log = Logger.withTag("DBMigration")

    fun runMigrations() {
        val currentVersion = getCurrentVersion()
        log.i { "Current schema version: $currentVersion, target: ${DatabaseConfig.currentSchemaVersion}" }

        if (currentVersion >= DatabaseConfig.currentSchemaVersion) {
            log.i { "Database is up to date" }
            return
        }

        if (currentVersion < 1) {
            migrateV0ToV1()
        }
        if (currentVersion < 2) {
            migrateV1ToV2()
        }
        if (currentVersion < 3) {
            migrateV2ToV3()
        }
        if (currentVersion < 4) {
            migrateV3ToV4()
        }
        if (currentVersion < 5) {
            migrateV4ToV5()
        }

        // After migrations, update version
        settingRepo.upsert(SettingKeys.schemaVersion, DatabaseConfig.currentSchemaVersion.toString())
        log.i { "Migration complete, updated to version ${DatabaseConfig.currentSchemaVersion}" }
    }

    private fun getCurrentVersion(): Long {
        return settingRepo.get(SettingKeys.schemaVersion)?.toLongOrNull() ?: 0L
    }

    /**
     * V0 -> V1: Initial schema. Create schema_version setting.
     * This is the baseline - the .sq file already defines all tables.
     * We just record the version.
     */
    private fun migrateV0ToV1() {
        log.i { "Migration V0 -> V1: Recording initial schema version" }
        settingRepo.upsert(SettingKeys.schemaVersion, "1")
    }

    /**
     * V1 -> V2: AI config simplified, MCP server table added.
     * - Add 'provider' column to ai_config (default 'openai')
     * - Create mcp_server table
     */
    private fun migrateV1ToV2() {
        log.i { "Migration V1 -> V2: AI config + MCP server" }

        try {
            runSql("ALTER TABLE ai_config ADD COLUMN provider TEXT NOT NULL DEFAULT 'openai'")
            log.i { "Added provider column to ai_config" }
        } catch (e: Exception) {
            log.w(e) { "Could not add provider column (may already exist)" }
        }

        try {
            runSql("""
                CREATE TABLE IF NOT EXISTS mcp_server (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    server_url TEXT NOT NULL,
                    api_key TEXT NOT NULL DEFAULT '',
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
            log.i { "Created mcp_server table" }
        } catch (e: Exception) {
            log.w(e) { "Could not create mcp_server table" }
        }
    }

    /**
     * V2 -> V3: Memory system tables.
     * - Create memory_entry table
     * - Create chat_conversation table
     */
    private fun migrateV2ToV3() {
        log.i { "Migration V2 -> V3: Memory system tables" }

        try {
            runSql("""
                CREATE TABLE IF NOT EXISTS memory_entry (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL,
                    source_type TEXT,
                    source_id INTEGER,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    tags TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
            log.i { "Created memory_entry table" }
        } catch (e: Exception) {
            log.w(e) { "Could not create memory_entry table" }
        }

        try {
            runSql("""
                CREATE TABLE IF NOT EXISTS chat_conversation (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    search_results TEXT,
                    steps TEXT,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent())
            log.i { "Created chat_conversation table" }
        } catch (e: Exception) {
            log.w(e) { "Could not create chat_conversation table" }
        }
    }

    /**
     * V3 -> V4: Remove content and html_content columns from article table.
     * Uses table recreation since DROP COLUMN requires SQLite 3.35+.
     */
    private fun migrateV3ToV4() {
        log.i { "Migration V3 -> V4: Remove content/html_content from article" }
        try {
            runSql("""
                CREATE TABLE article_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT,
                    ai_title TEXT,
                    ai_content TEXT,
                    ai_markdown_content TEXT,
                    url TEXT UNIQUE,
                    is_favorite INTEGER DEFAULT 0,
                    comment TEXT,
                    status TEXT DEFAULT 'pending',
                    cover_image TEXT,
                    cover_image_url TEXT,
                    pub_date INTEGER,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
            runSql("""
                INSERT INTO article_new (id, title, ai_title, ai_content, ai_markdown_content,
                    url, is_favorite, comment, status, cover_image, cover_image_url,
                    pub_date, created_at, updated_at)
                SELECT id, title, ai_title, ai_content, ai_markdown_content,
                    url, is_favorite, comment, status, cover_image, cover_image_url,
                    pub_date, created_at, updated_at
                FROM article
            """.trimIndent())
            runSql("DROP TABLE article")
            runSql("ALTER TABLE article_new RENAME TO article")
            log.i { "Migrated article table to V4" }
        } catch (e: Exception) {
            log.w(e) { "Migration V3->V4 failed" }
        }
    }

    /**
     * V4 -> V5: Remove manual name from AI config.
     */
    private fun migrateV4ToV5() {
        log.i { "Migration V4 -> V5: Remove ai_config.name" }
        try {
            runSql("""
                CREATE TABLE ai_config_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    provider TEXT NOT NULL DEFAULT 'openai',
                    api_address TEXT NOT NULL,
                    api_token TEXT NOT NULL,
                    model_name TEXT NOT NULL,
                    is_default INTEGER DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
            runSql("""
                INSERT INTO ai_config_new (id, provider, api_address, api_token, model_name,
                    is_default, created_at, updated_at)
                SELECT id, provider, api_address, api_token, model_name,
                    is_default, created_at, updated_at
                FROM ai_config
            """.trimIndent())
            runSql("DROP TABLE ai_config")
            runSql("ALTER TABLE ai_config_new RENAME TO ai_config")
            log.i { "Migrated ai_config table to V5" }
        } catch (e: Exception) {
            log.w(e) { "Migration V4->V5 failed" }
        }
    }

    private fun runSql(sql: String) {
        driver.execute(null, sql, 0, null)
    }
}
