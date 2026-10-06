package com.dailysatori.service.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.asynctask.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DiaryTagCoordinatorTest {
    @Test
    fun durableTasksDeduplicateAndSkipUnchangedContentAfterSuccess() = runBlocking {
        Fixture { _, _ -> """{"tags":[{"name":"读书","newTopic":true}]}""" }.use { fixture ->
            val taskId = assertNotNull(fixture.coordinator.enqueue(fixture.id))
            assertEquals(taskId, fixture.coordinator.enqueue(fixture.id))
            assertFalse(assertNotNull(fixture.tasks.getById(taskId)).payload_json.contains("私密正文"))
            assertIs<AsyncTaskExecutionResult.Success>(fixture.execute(taskId))
            assertEquals(listOf("读书"), fixture.tags.tags(fixture.id))
            assertNull(fixture.coordinator.enqueue(fixture.id))
        }
    }

    @Test
    fun queuedTasksRespectManualEditsAndDisablingBeforeExecution() = runBlocking {
        var requests = 0
        Fixture { _, _ -> requests++; """{"tags":[]}""" }.use { fixture ->
            val first = assertNotNull(fixture.coordinator.enqueue(fixture.id))
            fixture.tags.edit(fixture.id, listOf("家庭"))
            assertIs<AsyncTaskExecutionResult.Success>(fixture.execute(first))
            val second = assertNotNull(fixture.coordinator.enqueue(fixture.id))
            fixture.tags.setEnabled(false)
            assertIs<AsyncTaskExecutionResult.Success>(fixture.execute(second))
            assertEquals(0, requests)
            assertEquals(listOf("家庭"), fixture.tags.tags(fixture.id))
        }
    }

    @Test
    fun failureAndCancellationPreserveTagsAndDoNotExposeProviderText() = runBlocking {
        Fixture { _, _ -> error("私密正文和密钥") }.use { fixture ->
            fixture.tags.edit(fixture.id, listOf("家庭"))
            val result = assertIs<AsyncTaskExecutionResult.RetryableFailure>(fixture.execute(assertNotNull(fixture.coordinator.enqueue(fixture.id))))
            assertFalse(result.message.contains("私密正文"))
            assertEquals(listOf("家庭"), fixture.tags.tags(fixture.id))
        }
        Fixture { _, _ -> throw CancellationException() }.use { fixture ->
            assertFailsWith<CancellationException> { fixture.execute(assertNotNull(fixture.coordinator.enqueue(fixture.id))) }
            assertTrue(fixture.tags.tags(fixture.id).isEmpty())
        }
    }

    @Test
    fun backfillOnlyProcessesEntriesStillWithoutTags() = runBlocking {
        var requests = 0
        Fixture { _, _ -> requests++; """{"tags":[{"name":"读书","newTopic":true}]}""" }.use { fixture ->
            val task = assertNotNull(fixture.coordinator.enqueue(fixture.id, force = true, missingOnly = true))
            fixture.tags.edit(fixture.id, listOf("家庭"))
            fixture.execute(task)
            assertEquals(0, requests)
            assertNull(fixture.coordinator.enqueue(fixture.id, force = true, missingOnly = true))
        }
    }

    private class Fixture(complete: suspend (String, String) -> String) : AutoCloseable {
        private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        init { DailySatoriDatabase.Schema.create(driver) }
        private val db = DailySatoriDatabase(driver)
        val diaries = DiaryRepository(db, driver)
        val tags = DiaryTagRepository(db)
        val tasks = AsyncTaskRepository(db)
        val id = runBlocking { diaries.create("私密正文") }
        val coordinator = DiaryTagCoordinator(diaries, tags, tasks, DiaryTagGenerator(complete)) { true }
        suspend fun execute(id: Long): AsyncTaskExecutionResult = coordinator.execute(id,
            assertNotNull(tasks.getById(id)).payload_json, "", object : AsyncTaskProgressReporter {
                override suspend fun report(current: Long, total: Long, message: String, checkpointJson: String) {}
            })
        override fun close() = driver.close()
    }
}
