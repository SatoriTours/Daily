package com.dailysatori.service.migration

import co.touchlab.kermit.Logger
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.remotenews.normalizeTopArticlesTodayUrl
import com.dailysatori.service.security.SecretCipher
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.QueryResult

class DatabaseMigration(
    private val driver: SqlDriver,
    private val settingRepo: SettingRepository,
    private val secretCipher: SecretCipher,
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
        if (currentVersion < 6) {
            migrateV5ToV6()
        }
        if (currentVersion < 7) {
            migrateV6ToV7()
        }
        if (currentVersion < 8) {
            migrateV7ToV8()
        }
        if (currentVersion < 9) {
            migrateV8ToV9()
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

    /**
     * V5 -> V6: Add MCP provider preset metadata.
     */
    private fun migrateV5ToV6() {
        log.i { "Migration V5 -> V6: MCP preset metadata" }
        val columns = listOf(
            "provider TEXT NOT NULL DEFAULT ''",
            "template_id TEXT NOT NULL DEFAULT ''",
            "template_type TEXT NOT NULL DEFAULT ''",
            "config_json TEXT NOT NULL DEFAULT ''",
        )
        columns.forEach { column ->
            try {
                runSql("ALTER TABLE mcp_server ADD COLUMN $column")
                log.i { "Added mcp_server column: $column" }
            } catch (e: Exception) {
                log.w(e) { "Could not add mcp_server column: $column" }
            }
        }
    }

    private fun migrateV6ToV7() {
        log.i { "Migration V6 -> V7: Unified news summary tables" }
        try {
            runSql("""
                CREATE TABLE IF NOT EXISTS unified_news_summary (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    summary_date TEXT NOT NULL,
                    window_key TEXT NOT NULL,
                    window_start_ms INTEGER NOT NULL,
                    window_end_ms INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    status TEXT NOT NULL,
                    error_message TEXT,
                    source_warnings TEXT,
                    generated_at INTEGER,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    UNIQUE(summary_date, window_key)
                )
            """.trimIndent())
            runSql("""
                CREATE TABLE IF NOT EXISTS unified_news_source (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    summary_id INTEGER NOT NULL REFERENCES unified_news_summary(id) ON DELETE CASCADE,
                    ref_key TEXT NOT NULL,
                    source_type TEXT NOT NULL,
                    source_id INTEGER,
                    source_filename TEXT,
                    source_url TEXT,
                    title TEXT NOT NULL,
                    summary TEXT NOT NULL,
                    source_time INTEGER,
                    UNIQUE(summary_id, ref_key)
                )
            """.trimIndent())
            log.i { "Created unified news tables" }
        } catch (e: Exception) {
            log.w(e) { "Migration V6->V7 failed" }
        }
    }

    private fun migrateV7ToV8() {
        log.i { "Migration V7 -> V8: Remote news source table" }
        try {
            runSql("""
                CREATE TABLE IF NOT EXISTS remote_news_source (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    base_url TEXT NOT NULL,
                    api_token TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
            log.i { "Created remote_news_source table" }
        } catch (e: Exception) {
            log.w(e) { "Could not create remote_news_source table" }
        }

        try {
            migrateExistingRemoteNewsSettings()
        } catch (e: Exception) {
            log.w(e) { "Could not migrate remote news settings" }
        }
    }

    private fun migrateExistingRemoteNewsSettings() {
        val rawBaseUrl = settingRepo.get(SettingKeys.remoteNewsBaseUrl)?.trim().orEmpty()
        val baseUrl = normalizeTopArticlesTodayUrl(rawBaseUrl)
        val apiToken = settingRepo.get(SettingKeys.remoteNewsApiToken)?.trim().orEmpty()
        if (rawBaseUrl.isBlank() || apiToken.isBlank()) return
        if (queryLong("SELECT COUNT(*) FROM remote_news_source") != 0L) return

        runSql("""
            INSERT INTO remote_news_source (name, base_url, api_token, enabled, created_at, updated_at)
            VALUES ('远程新闻', '${baseUrl.sqlEscaped()}', '${apiToken.sqlEscaped()}', 1,
                strftime('%s', 'now') * 1000, strftime('%s', 'now') * 1000)
        """.trimIndent())
    }

    private fun migrateV8ToV9() {
        log.i { "Migration V8 -> V9: Skill config table" }
        try {
            runSql("""
                CREATE TABLE IF NOT EXISTS skill_config (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL DEFAULT '',
                    gateway_url TEXT NOT NULL,
                    api_token TEXT NOT NULL DEFAULT '',
                    skill_version TEXT NOT NULL DEFAULT '',
                    enabled INTEGER NOT NULL DEFAULT 0,
                    builtin INTEGER NOT NULL DEFAULT 0,
                    provider TEXT NOT NULL DEFAULT '',
                    template_id TEXT NOT NULL DEFAULT '',
                    tool_schema_json TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
            log.i { "Created skill_config table" }
        } catch (e: Exception) {
            log.w(e) { "Could not create skill_config table" }
        }

        try {
            insertBuiltInWeReadSkillIfMissing()
        } catch (e: Exception) {
            log.w(e) { "Could not insert WeRead skill" }
        }
    }

    private fun insertBuiltInWeReadSkillIfMissing() {
        if (queryLong("SELECT COUNT(*) FROM skill_config WHERE template_id = 'weread'") != 0L) return
        // Reads legacy "weread_api_key" setting into skill_config.api_token.
        val legacyToken = settingRepo.get(SettingKeys.legacyWeReadApiKey)?.trim().orEmpty()
        val apiToken = migratedWeReadTokenValue(legacyToken)
        val enabled = if (legacyToken.isBlank()) 0 else 1
        runSql("""
            INSERT INTO skill_config (name, description, gateway_url, api_token, skill_version, enabled, builtin, provider, template_id, tool_schema_json, created_at, updated_at)
            VALUES ('微信读书', '微信读书 Skill，用于搜索书籍、获取图书信息、目录和书评。', 'https://i.weread.qq.com/api/agent/gateway',
                '${apiToken.sqlEscaped()}', '1.0.3', $enabled, 1, 'weread', 'weread', '',
                strftime('%s', 'now') * 1000, strftime('%s', 'now') * 1000)
        """.trimIndent())
    }

    private fun migratedWeReadTokenValue(legacyToken: String): String = when {
        legacyToken.isBlank() -> ""
        secretCipher.isEncrypted(legacyToken) -> legacyToken
        else -> secretCipher.encrypt(legacyToken)
    }

    private fun String.sqlEscaped(): String = replace("'", "''")

    private fun queryLong(sql: String): Long {
        var result = 0L
        driver.executeQuery<Long>(0, sql, { cursor ->
            if (cursor.next().value) {
                result = cursor.getLong(0) ?: 0L
            }
            QueryResult.Value(result)
        }, 0)
        return result
    }

    private fun runSql(sql: String) {
        driver.execute(null, sql, 0, null)
    }
}
