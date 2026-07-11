package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryRepositoryInsertTest {
    @Test
    fun diaryCreateUsesLegacySqliteInsertIdPathInsideOneTransaction() {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val repository = File("src/commonMain/kotlin/com/dailysatori/data/repository/DiaryRepository.kt").readText()

        assertFalse(schema.contains("RETURNING"))
        assertFalse(schema.contains("insertDiaryReturningId:"))
        assertTrue(repository.contains("suspend fun create("))
        assertTrue(repository.contains("q.transactionWithResult"))
        assertTrue(repository.contains("q.insertDiary(content, tags, mood, images, now, now)"))
        assertTrue(repository.contains("SELECT last_insert_rowid()"))
        assertTrue(repository.contains("driver.executeQuery"))
    }

    @Test
    fun diaryCreateReturnsTheNewNonzeroIdOnRealSqlite() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val repository = DiaryRepository(db, driver)

            val id = runBlocking { repository.create(content = "legacy sqlite insert") }

            assertTrue(id > 0)
            assertEquals("legacy sqlite insert", db.dailySatoriQueries.selectDiaryById(id).executeAsOne().content)
        } finally {
            driver.close()
        }
    }
}
