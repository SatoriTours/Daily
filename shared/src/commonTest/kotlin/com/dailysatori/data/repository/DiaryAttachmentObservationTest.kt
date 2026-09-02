package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class DiaryAttachmentObservationTest {
    @Test
    fun observingAllAttachmentsKeepsPerDiaryOrderAndEmitsUpdates() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            db.dailySatoriQueries.insertDiary("first", null, null, null, 1, 1)
            db.dailySatoriQueries.insertDiary("second", null, null, null, 2, 2)
            val repository = DiaryAttachmentRepository(db, driver)
            repository.create(2, DiaryAttachmentDraft(DiaryAttachmentKind.image, "/second.jpg"))

            val emissions = async { repository.observeAll().take(2).toList() }
            yield()
            repository.create(1, DiaryAttachmentDraft(DiaryAttachmentKind.audio, "/first.m4a"))
            val updated = emissions.await().last()

            assertEquals(listOf(1L, 2L), updated.map { it.diary_id })
            assertEquals(listOf("/first.m4a", "/second.jpg"), updated.map { it.local_path })
        } finally {
            driver.close()
        }
    }
}
