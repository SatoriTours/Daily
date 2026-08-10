package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeeklySummaryRepositoryTest {
    @Test
    fun weekRangeIsIdempotentAndGenerationHasSingleOwner() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        val repository = WeeklySummaryRepository(db)

        val first = repository.getOrCreate(100, 200)
        val second = repository.getOrCreate(100, 200)

        assertEquals(first.id, second.id)
        assertEquals(1, db.dailySatoriQueries.selectWeeklySummaries().executeAsList().size)
        assertTrue(repository.claimGeneration(first.id))
        assertFalse(repository.claimGeneration(first.id))
        driver.close()
    }
}
