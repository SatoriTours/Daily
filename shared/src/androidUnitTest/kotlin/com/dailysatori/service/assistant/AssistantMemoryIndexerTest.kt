package com.dailysatori.service.assistant

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.asynctask.*
import com.dailysatori.service.memory.PersonalMemoryCandidate
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AssistantMemoryIndexerTest {
    @Test fun segmentsResumeWithoutRepeatingCompletedCloudWorkAndOldLeaseCannotSaveLateFacts() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            SettingRepository(db).upsert(PersistentAssistantConversation.CONSENT_KEY,"true")
            val tasks = AsyncTaskRepository(db)
            val source = ChatConversationRepository(db).acceptUserInput("s", "我喜欢徒步。" + "普通资料。".repeat(1500) + "我正在准备长跑项目。")
            val memory = AssistantMemoryService(driver)
            val id = tasks.enqueue("assistant_index", "{\"sourceId\":$source}")
            tasks.claimForRun(id,"owner",Long.MAX_VALUE)
            val reporter = object : AsyncTaskProgressReporter {
                override val executionLease = AsyncTaskExecutionLease(id,"owner")
                override suspend fun report(current:Long,total:Long,message:String,checkpointJson:String) {}
            }
            var calls = 0
            var fail = true
            val indexer = AssistantMemoryIndexer(driver,memory) { segment ->
                calls++
                if (calls == 2 && fail) error("provider secret failure")
                listOf(PersonalMemoryCandidate("note","有原文依据的分段笔记",segment.take(10)))
            }
            assertFails { indexer.process(source, reporter) }
            assertEquals(2,calls)
            fail=false
            indexer.process(source,reporter)
            val after = calls
            indexer.process(source,reporter)
            assertEquals(after,calls)
            assertTrue(memory.list().any { it.active })
            val other = ChatConversationRepository(db).acceptUserInput("s","我喜欢游泳")
            val stale = AssistantMemoryIndexer(driver,memory) { segment ->
                tasks.markRunningForRetryAfterProcessRestart(1L)
                tasks.claimForRun(id,"new-owner",Long.MAX_VALUE)
                listOf(PersonalMemoryCandidate("preference","迟到不应写入",segment))
            }
            stale.process(other,reporter)
            assertFalse(memory.list().any { it.content.contains("迟到不应写入") })
        }
    }
}
