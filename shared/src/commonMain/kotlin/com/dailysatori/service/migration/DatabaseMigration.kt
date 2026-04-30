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

    private fun runSql(sql: String) {
        driver.execute(null, sql, 0, null)
    }
}
