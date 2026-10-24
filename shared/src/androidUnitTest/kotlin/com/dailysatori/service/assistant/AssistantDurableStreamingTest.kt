package com.dailysatori.service.assistant

import ai.koog.agents.core.tools.*
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.ai.*
import com.dailysatori.service.asynctask.*
import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.service.mcp.RemoteMcpClient
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import com.dailysatori.service.assistant.data.AssistantDataGateway
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import kotlin.test.*

class AssistantDurableStreamingTest {
    @Test fun livePartialIsNotHistoryAndCancellationPreventsLateFinalCommit() = runBlocking<Unit> {
        Fixture().use { f ->
            val turn = f.conversation.send("s", "client", "继续回答")
            val body = ByteChannel(autoFlush = true)
            HttpClient(MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream")) }).use { client ->
                val runtime = KoogAssistantRuntime(f.driver, AiService(client), f.configs, f.actions, f.memory, RemoteMcpClient(client))
                val runner = AsyncTaskRunner(f.tasks, AsyncTaskHandlerRegistry(listOf(AssistantTurnTaskHandler(f.turns, f.tasks, runtime))))
                val running = async { runner.run(turn.taskId) }
                try {
                    body.writeStringUtf8(event("这是仍在生成中的普通回答。".repeat(40) + "configu"))
                    val visible = withTimeout(5000) { f.conversation.messages("s").first { rows -> rows.any { it.status == "streaming" } } }
                    val partial = visible.single { it.status == "streaming" }
                    assertFalse(partial.content.contains("configu"))
                    assertFalse(running.isCompleted)
                    assertEquals(1, ChatConversationRepository(f.db).getBySession("s").size)
                    assertTrue(AssistantPrivacyRepository(f.driver).history("s").none { it.role == "assistant" })
                    f.conversation.cancel(turn.id)
                    assertTrue(f.conversation.messages("s").first().none { it.status == "streaming" })
                    body.writeStringUtf8(event("red-secret 生成结束"))
                    body.writeStringUtf8("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n")
                    body.close()
                    assertEquals(AsyncTaskRunOutcome.Skipped, running.await())
                    assertNull(f.turns.get(turn.id)!!.responseMessageId)
                    assertEquals(1, ChatConversationRepository(f.db).getBySession("s").size)
                } finally { running.cancelAndJoin(); body.cancel() }
            }
        }
    }

    @Test fun oldStreamWriterCannotOverwriteNewOwnersPartial() = runBlocking<Unit> {
        Fixture().use { f ->
            val turn = f.conversation.send("s", "client", "继续回答")
            assertTrue(f.tasks.claimForRun(turn.taskId, "old", Long.MAX_VALUE))
            val old = f.checkpoint(turn, "old", "context")
            old.partial("旧执行端的片段")
            f.tasks.markRunningForRetryAfterProcessRestart(Long.MAX_VALUE)
            assertTrue(f.tasks.claimForRun(turn.taskId, "new", Long.MAX_VALUE))
            f.checkpoint(turn, "new", "context").partial("新执行端的片段")
            assertFailsWith<CancellationException> { old.partial("") }
            val partial = f.conversation.messages("s").first().single { it.status == "streaming" }
            assertEquals("新执行端的片段", partial.content)
        }
    }

    @Test fun restartBetweenTwoToolsDoesNotExecuteFirstCompletedToolAgain() = runBlocking<Unit> {
        Fixture().use { f ->
            val turn = f.conversation.send("s", "client", "执行两步")
            assertTrue(f.tasks.claimForRun(turn.taskId, "first-owner", Long.MAX_VALUE))
            var firstEffects = 0
            var secondEffects = 0
            var requests = 0
            val tools = ToolRegistry {
                tool(EffectTool("first_effect") { firstEffects++ })
                tool(EffectTool("second_effect") { secondEffects++ })
            }
            HttpClient(MockEngine { request ->
                requests++
                val json = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
                val hasResult = json["messages"]!!.jsonArray.any { it.jsonObject["role"]?.jsonPrimitive?.content == "tool" }
                respond(if (hasResult) """{"choices":[{"message":{"content":"两步已完成"}}]}""" else calls(),
                    HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { client ->
                val first = KoogReadOnlyAssistant(AiService(client), f.configs, AssistantDataGateway(f.driver),
                    AssistantPrivacyRepository(f.driver), ChatConversationRepository(f.db), tools,
                    checkpointFactory = { hash ->
                        val storage = f.checkpoint(turn, "first-owner", hash)
                        object : PersistencyStorageProvider by storage {
                            override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
                                storage.saveCheckpoint(agentCheckpointData)
                                if (agentCheckpointData.nodeId == "execute_one_tool" &&
                                    agentCheckpointData.lastInput.jsonObject["remaining"]?.jsonArray?.singleOrNull()
                                        ?.jsonObject?.get("tool")?.jsonPrimitive?.content == "second_effect")
                                    throw CancellationException("simulated process interruption after first committed step")
                            }
                        }
                    })
                assertFailsWith<CancellationException> { first.generateAccepted("s", turn.id) }
                assertEquals(1, firstEffects)
                assertEquals(0, secondEffects)
                f.tasks.markRunningForRetryAfterProcessRestart(Long.MAX_VALUE)
                assertTrue(f.tasks.claimForRun(turn.taskId, "second-owner", Long.MAX_VALUE))
                val second = KoogReadOnlyAssistant(AiService(client), f.configs, AssistantDataGateway(f.driver),
                    AssistantPrivacyRepository(f.driver), ChatConversationRepository(f.db), tools,
                    checkpointFactory = { hash -> f.checkpoint(turn, "second-owner", hash) })
                assertEquals("两步已完成", second.generateAccepted("s", turn.id))
                assertEquals(1, firstEffects)
                assertEquals(1, secondEffects)
                assertEquals(2, requests, "completed first model request is not repeated")
            }
        }
    }

    @Test fun savedKoogPromptCannotResurrectInvalidatedOldSummarySource() = runBlocking<Unit> {
        Fixture().use { f ->
            val chats = ChatConversationRepository(f.db)
            val old = chats.acceptUserInput("s", "旧偏好原文")
            repeat(30) { chats.acceptUserInput("s", "近期原文$it".repeat(200)) }
            val turn = f.conversation.send("s", "client", "根据历史回答")
            val store = AssistantStore(f.driver)
            val sources = """[{"id":$old,"version":"${sha256Hex("旧偏好原文")}","offset":5,"total":5,"ai":true,"coordinate":"redacted-utf16"}]"""
            store.exec("UPDATE assistant_session SET summary=?,summary_sources_json=? WHERE id='s'", "不可复活的旧归纳", sources)
            assertTrue(f.memory.summary("s", turn.id).contains("不可复活的旧归纳"))
            var requests = 0
            HttpClient(MockEngine { request ->
                requests++
                val json = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
                val messages = json["messages"]!!.jsonArray
                if (requests >= 3) assertFalse(messages.toString().contains("不可复活的旧归纳"), "recovered prompt must validate its own saved source dependencies")
                val hasTool = messages.any { it.jsonObject["role"]?.jsonPrimitive?.content == "tool" }
                respond(if (requests == 2) """{"error":{"message":"unavailable"}}""" else if (hasTool)
                    """{"choices":[{"message":{"content":"基于当前资料回答"}}]}""" else
                    """{"choices":[{"message":{"tool_calls":[{"id":"catalog","type":"function","function":{"name":"list_data_sources","arguments":"{}"}}]}}]}""",
                    if (requests == 2) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { client ->
                val runtime = KoogAssistantRuntime(f.driver, AiService(client), f.configs, f.actions, f.memory, RemoteMcpClient(client))
                assertTrue(f.tasks.claimForRun(turn.taskId, "a", Long.MAX_VALUE))
                assertFails { runtime.generate(turn, "a") }
                store.exec("UPDATE chat_conversation SET content='修订后的原文' WHERE id=?", old)
                assertEquals("", f.memory.summary("s", turn.id))
                f.tasks.markRunningForRetryAfterProcessRestart(Long.MAX_VALUE)
                assertTrue(f.tasks.claimForRun(turn.taskId, "b", Long.MAX_VALUE))
                assertEquals("基于当前资料回答", runtime.generate(turn, "b"))
                assertEquals(4, requests, "invalid derived context rebuilds the graph rather than replaying a stale saved prompt")
            }
        }
    }

    private class EffectTool(name: String, private val effect: () -> Unit) : SimpleTool<ToolArgs.Empty>() {
        override val argsSerializer = ToolArgs.Empty.serializer()
        override val descriptor = ToolDescriptor(name, name)
        override suspend fun doExecute(args: ToolArgs.Empty): String { effect(); return "done" }
    }

    private class Fixture : AutoCloseable {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { DailySatoriDatabase.Schema.create(it) }
        val db = DailySatoriDatabase(driver)
        val tasks = AsyncTaskRepository(db)
        val turns = AssistantTurnRepository(driver)
        val memory = AssistantMemoryService(driver)
        val actions = AssistantActionService(driver, RepositoryAssistantBusiness(driver) { _, _ -> })
        val conversation = PersistentAssistantConversation(driver, actions, memory) {}
        val configs = AiConfigService(AIConfigRepository(db).also { it.insert("openai", "https://example.test/v1", "configured-secret", "model", 1) })
        init { conversation.setCloudConsent(true) }
        fun checkpoint(turn: AssistantTurn, owner: String, hash: String) = AssistantAgentCheckpointStore(
            driver, turn, owner, hash, AssistantPrivacyRepository(driver).revision(), sha256Hex(AssistantInputContext(driver).prompt(turn.id)), { true },
        )
        override fun close() = driver.close()
    }

    private fun event(content: String) = "data: " + buildJsonObject { put("choices", buildJsonArray {
        add(buildJsonObject { put("delta", buildJsonObject { put("content", content) }) })
    }) } + "\n\n"

    private fun calls() = buildJsonObject { put("choices", buildJsonArray { add(buildJsonObject {
        put("message", buildJsonObject { put("tool_calls", buildJsonArray {
            listOf("first_effect", "second_effect").forEach { name -> add(buildJsonObject {
                put("id", name); put("type", "function"); put("function", buildJsonObject { put("name", name); put("arguments", "{}") })
            }) }
        }) })
    }) }) }.toString()
}
