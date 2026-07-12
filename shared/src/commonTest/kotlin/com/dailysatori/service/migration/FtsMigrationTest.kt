package com.dailysatori.service.migration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.db.QueryResult
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class FtsMigrationTest {
    @Test
    fun v21RepairsMissingFtsTablesBeforeDiaryTriggersCanRun() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            driver.execute(null, "DROP TABLE diary_fts", 0)

            DatabaseMigration.migrateFts4Schema { sql -> driver.execute(null, sql, 0) }

            val db = DailySatoriDatabase(driver)
            db.dailySatoriQueries.insertDiary("修复后的语音日记", "语音", null, null, 1, 1)
            assertEquals(1, db.dailySatoriQueries.searchDiariesFts("\"语音日记\"", "语音日记").executeAsList().size)
            val module = driver.executeQuery(
                null,
                "SELECT count(*) FROM sqlite_master WHERE name = 'diary_fts' AND sql LIKE '%fts4%'",
                { cursor ->
                    check(cursor.next().value)
                    QueryResult.Value(cursor.getLong(0) ?: 0)
                },
                0,
            ).value
            assertEquals(1, module)
        } finally {
            driver.close()
        }
    }
}
