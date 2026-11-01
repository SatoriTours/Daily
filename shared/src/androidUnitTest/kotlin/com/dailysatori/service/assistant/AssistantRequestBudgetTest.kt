package com.dailysatori.service.assistant

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.ai.*
import com.dailysatori.service.mcp.RemoteMcpClient
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AssistantRequestBudgetTest {
    @Test fun aLongProcessPauseCannotPublishAnExpiredResponse() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val tasks = AsyncTaskRepository(db)
            val memory = AssistantMemoryService(driver)
            val actions = AssistantActionService(driver, RepositoryAssistantBusiness(driver) { _, _ -> })
            val conversation = PersistentAssistantConversation(driver, actions, memory) {}
            conversation.setCloudConsent(true)
            val turn = conversation.send("deadline", "client", "继续")
            assertTrue(tasks.claimForRun(turn.taskId, "owner", Long.MAX_VALUE))
            var now = 1_000L
            val budget = AssistantRequestBudget(driver, turn, "owner", { now })
            budget.beforeSend()
            now += 23 * 60_000
            assertEquals(408, assertFailsWith<AiHttpFailure> { budget.checkDeadline() }.status)
            AssistantRequestBudget(driver, turn, "owner", { now }).beforeSend()
            assertEquals("2", SettingRepository(db).get("assistant.ai.requests.${turn.id}"))
        }
    }

    @Test fun retryAndDiscardingAGraphCannotResetTheInputsRequestBudget() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val tasks = AsyncTaskRepository(db)
            val turns = AssistantTurnRepository(driver)
            val configs = AIConfigRepository(db).also { it.insert("openai", "https://example.test/v1", "test-key", "model", 1) }
            val memory = AssistantMemoryService(driver)
            val actions = AssistantActionService(driver, RepositoryAssistantBusiness(driver) { _, _ -> })
            val conversation = PersistentAssistantConversation(driver, actions, memory) {}
            conversation.setCloudConsent(true)
            val turn = conversation.send("budget", "client", "总结我的待办事项")
            assertTrue(tasks.claimForRun(turn.taskId, "owner", Long.MAX_VALUE))
            var requests = 0
            HttpClient(MockEngine { requests++; respond("{}", HttpStatusCode.ServiceUnavailable) }).use { client ->
                // Reconstructing the runtime simulates separate task executions / process restarts.
                repeat(7) {
                    val runtime = KoogAssistantRuntime(driver, AiService(client), AiConfigService(configs), actions, memory, RemoteMcpClient(client))
                    assertFails { runtime.generate(turn, "owner") }
                    tasks.updateProgress(turn.taskId, 0, 1, "", "")
                }
                assertEquals(6, requests, "An invalidated checkpoint must not replenish paid request attempts")
            }
        }
    }
}
