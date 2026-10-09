package com.dailysatori.service.security

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

/** Tests storage behavior, rather than source-code strings. Encryption belongs to the driver. */
class SecretStorageSourceTest {
    @Test fun apiCredentialsRemainOrdinaryValuesAcrossRepositoryReadsAndUpdates() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val mcp = McpServerRepository(db)
            mcp.insert("server", "https://mcp", "mcp-token")
            val id = mcp.getEnabled().single().id
            assertEquals("mcp-token", db.dailySatoriQueries.selectMcpServerById(id).executeAsOne().api_key)
            mcp.update(id, "server", "https://mcp", "updated-token", 1)
            assertEquals("updated-token", mcp.getById(id)?.api_key)
            val remote = RemoteNewsSourceRepository(db)
            remote.save(null, "source", "https://news", " news-token ", true)
            assertEquals("news-token", db.dailySatoriQueries.selectRemoteNewsSources().executeAsOne().api_token)
            assertEquals("news-token", RemoteNewsSourceRepository(db).getEnabled().single().api_token)
            SettingRepository(db).upsert("web_server_token", "server-token")
            assertEquals("server-token", SettingRepository(db).get("web_server_token"))
        } finally { driver.close() }
    }
}
