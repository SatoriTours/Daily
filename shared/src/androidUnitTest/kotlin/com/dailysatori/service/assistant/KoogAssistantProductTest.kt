package com.dailysatori.service.assistant

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.ai.*
import com.dailysatori.service.asynctask.*
import com.dailysatori.service.mcp.RemoteMcpClient
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class KoogAssistantProductTest {
    @Test fun sessionsObserveGeneratedSqlDelightInsertNotificationsWithoutManualBroadcast() = runBlocking<Unit> {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val memory = AssistantMemoryService(driver)
            val actions = AssistantActionService(driver,RepositoryAssistantBusiness(driver) { _, _ -> })
            val conversation = PersistentAssistantConversation(driver,actions,memory) {}
            conversation.setCloudConsent(true)
            val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
            val received = async {
                conversation.sessions().onEach { ready.complete(Unit) }.first { "new-session" in it }
            }
            ready.await()
            conversation.send("new-session","client","原文落库立即刷新会话")
            assertEquals(listOf("new-session"),kotlinx.coroutines.withTimeout(5000) { received.await() })
        }
    }
    @Test fun explicitContextDoesNotAlterOriginalAndInvalidatesOnSourceDeletionOrExclusion() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            driver.execute(null, "INSERT INTO diary(id,content,created_at,updated_at) VALUES(7,'旧日记依据',1,1)", 0)
            val memory = AssistantMemoryService(driver)
            val actions = AssistantActionService(driver, RepositoryAssistantBusiness(driver) { _, _ -> })
            val conversation = PersistentAssistantConversation(driver, actions, memory) {}
            val context = com.dailysatori.service.diary.DiaryThoughtChatContext("参考旧日记，不代表当前指令",
                listOf(com.dailysatori.service.mcp.McpSearchResult(7, "diary", "来源", "旧日记依据", null)))
            val turn = conversation.sendWithContext("s", "context-client", "我的真实问题", context, false)
            assertEquals("我的真实问题", ChatConversationRepository(db).getById(turn.id)?.content)
            assertTrue(AssistantInputContext(driver).prompt(turn.id).contains("参考旧日记"))
            driver.execute(null, "UPDATE diary SET content='已修改来源' WHERE id=7", 0)
            assertEquals("", AssistantInputContext(driver).prompt(turn.id))
            val next = conversation.sendWithContext("s", "next", "另一个问题", context, false)
            AssistantPrivacyRepository(driver).exclude("diary", "7")
            assertEquals("", AssistantInputContext(driver).prompt(next.id))
        }
    }

    @Test fun originalSurvivesDeniedConsentAndAcceptingThenRetryingUsesSameTurn() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val tasks = AsyncTaskRepository(db)
            val memory = AssistantMemoryService(driver)
            val actions = AssistantActionService(driver, RepositoryAssistantBusiness(driver) { _, _ -> })
            val scheduled = mutableListOf<Long>()
            val conversation = PersistentAssistantConversation(driver, actions, memory, scheduled::add)
            val turn = conversation.send("s", "client", "离线原文")
            assertEquals("离线原文", ChatConversationRepository(db).getById(turn.id)?.content)
            assertEquals("cloud_consent_required", tasks.getById(turn.taskId)?.last_error_code)
            assertTrue(turn.requiresResume)
            assertFalse(scheduled.contains(turn.taskId))
            assertEquals(turn.id, conversation.send("s", "client", "离线原文").id)
            conversation.setCloudConsent(true)
            conversation.retry(turn.id)
            assertEquals("retrying", tasks.getById(turn.taskId)?.status)
            assertEquals(turn.taskId, scheduled.last())
            assertFalse(AssistantTurnRepository(driver).get(turn.id)!!.requiresResume)
        }
    }

    @Test fun actualKoogRunsFrozenBusinessPlanMemoryAndReadToolsThenReplaysCompletedReply() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val configs = AIConfigRepository(db)
            configs.insert("openai", "https://example.test/v1", "configured-secret", "model", 1)
            val input = "保存 https://example.com/read 。明天下午提醒我读书。我喜欢徒步。"
            val tasks = AsyncTaskRepository(db)
            val turns = AssistantTurnRepository(driver)
            val memory = AssistantMemoryService(driver)
            val actions = AssistantActionService(driver, RepositoryAssistantBusiness(driver) { _, _ -> })
            val conversation = PersistentAssistantConversation(driver, actions, memory) { }
            conversation.setCloudConsent(true)
            val turn = conversation.send("s", "client", input)
            var requests = 0
            HttpClient(MockEngine { request ->
                val body = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
                assertFalse(body.toString().contains("configured-secret"))
                if (requests == 0) assertFalse(body.toString().contains("https://example.com/read"))
                val message = if (requests++ == 0) buildJsonObject {
                    put("tool_calls", buildJsonArray {
                        fun call(id: String, name: String, args: JsonObject) = add(buildJsonObject {
                            put("id", id); put("type", "function"); put("function", buildJsonObject { put("name", name); put("arguments", args.toString()) })
                        })
                        call("plan", "submit_actions", buildJsonObject { put("plan", Json.encodeToString(listOf(
                            AssistantActionRequest("article", "保存 [input_link_0]", urlRef = "input_link_0"),
                            AssistantActionRequest("reminder", "明天下午提醒我读书", content = "读书", startDate = "2026-10-25", time = "15:00"),
                        ))) })
                        call("memory", "remember_fact", buildJsonObject { put("kind", "preference"); put("content", "用户喜欢徒步"); put("quote", "我喜欢徒步") })
                        call("read", "read_record", buildJsonObject { put("source", "chat"); put("id", turn.id.toString()) })
                    })
                } else buildJsonObject { put("content", "链接已接收处理中；提醒已保存；已记住徒步偏好。") }
                respond(buildJsonObject { put("choices", buildJsonArray { add(buildJsonObject { put("message", message) }) }) }.toString(),
                    HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { client ->
                val runtime = KoogAssistantRuntime(driver, AiService(client), AiConfigService(configs), actions, memory, RemoteMcpClient(client))
                val handler = AssistantTurnTaskHandler(turns, tasks, runtime)
                val runner = AsyncTaskRunner(tasks, AsyncTaskHandlerRegistry(listOf(handler)))
                assertEquals(AsyncTaskRunOutcome.Succeeded, runner.run(turn.taskId))
                assertEquals(2, actions.list(turn.id).size)
                assertEquals(1, memory.list().count { it.active })
                val messages = conversation.messages("s").first()
                assertEquals(2, messages.size)
                assertTrue(messages.last().references.any { it.source == "chat" && it.id == turn.id.toString() })
                conversation.retry(turn.id)
                assertEquals(AsyncTaskRunOutcome.Succeeded, runner.run(turn.taskId))
                assertEquals(2, requests)
                assertEquals(1, db.dailySatoriQueries.selectAllReminders().executeAsList().size)
                assertEquals(1, ArticleRepository(db).getAllSync().size)
                conversation.deleteSession("s")
                assertTrue(ChatConversationRepository(db).getBySession("s").isEmpty())
                assertEquals(1, db.dailySatoriQueries.selectAllReminders().executeAsList().size)
                assertTrue(memory.list().none { it.active })
            }
        }
    }
}
