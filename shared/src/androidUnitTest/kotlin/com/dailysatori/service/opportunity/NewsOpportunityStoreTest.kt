package com.dailysatori.service.opportunity

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.service.migration.OpportunityArchiveMigration
import com.dailysatori.service.security.MigrationContentSnapshot
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class NewsOpportunityStoreTest {
    @Test
    fun oversizedUnchunkedLegacyAndNewSnapshotsUseBoundedReads() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { jdbc ->
            val driver = BoundedReadDriver(jdbc)
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val settings = SettingRepository(db)
            val text = "中文🙂𠮷\u0000正文".repeat(120_000)
            val article = ReadNewsArticle("legacy", "旧新闻", text, source = "来源", readAt = 123)
            val item = NewsOpportunity("id", article, "完整产品方案", "效率工具", "事实", text, "方案", "风险", "正文", 456)
            val expected = OpportunityArchive(items = listOf(item), articles = listOf(article))
            val encoded = Json.encodeToString(expected)
            settings.upsert(OpportunityArchiveMigration.KEY, encoded)
            settings.upsert(SettingKeys.schemaVersion, "54")
            val before = MigrationContentSnapshot.capture(driver, expected)

            DatabaseMigration(driver, settings).runMigrations()

            before.requirePreserved(driver, migratedOpportunities = true)
            assertEquals(encoded, settings.getLargeValue(OpportunityArchiveMigration.KEY))
            assertEquals(expected, NewsOpportunityStore(db).load())
            assertNull(NewsOpportunityStore(db).load().items.single().preview)
            val migrated = MigrationContentSnapshot.capture(driver)
            driver.execute(null, """UPDATE opportunity_item
                SET analysis_json=substr(analysis_json,1,50000)||'X'||substr(analysis_json,50002)""", 0)
            assertFails { migrated.requirePreserved(driver) }
        }
    }

    // JDBC has no CursorWindow. Limit individual query cells to 128 KiB during the full conversion/read.
    private class BoundedReadDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
        override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>,
            parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<R> =
            delegate.executeQuery(identifier, sql, { cursor ->
                mapper(object : SqlCursor by cursor {
                    override fun getString(index: Int): String? = cursor.getString(index).also {
                        check((it?.encodeToByteArray()?.size ?: 0) <= 128 * 1024) { "SQL value exceeds read window" }
                    }
                })
            }, parameters, binders)
    }
}
